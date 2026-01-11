package com.teamcode.subsystems;

import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.teamcode.Constants;

public class ShooterSubsystem {
    public enum SpeedMode {
        LOW(Constants.SHOOTER_SPEED_LOW),
        MEDIUM(Constants.SHOOTER_SPEED_MEDIUM),
        MAX(Constants.SHOOTER_SPEED_MAX);

        public final double rpm;
        SpeedMode(double rpm) { this.rpm = rpm; }
    }

    private final DcMotorEx shooterMotor;
    // Cache last commanded encoder velocity for diagnostics when motor missing
    private double lastTargetEncoderVelocity = 0.0;
    private SpeedMode currentMode = SpeedMode.MEDIUM;
    private boolean shootCommandActive = false;
    private boolean shooterEnabled = true; // Can be disabled by A button

    // Cached target velocity (RPM)
    private double targetVelocityRPM = 0.0;

    // --- Added fields for spin-up assist ---
    private double lastTargetEncoderVelocityCmd = 0.0;
    private long lastUpdateNanos = 0;

    public ShooterSubsystem(HardwareMap hw) {
        DcMotorEx sm = null;
        try {
            sm = hw.get(DcMotorEx.class, Constants.SHOOTER_MOTOR_NAME);
           

            sm.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
            sm.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
            sm.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.FLOAT); // Coast for flywheel
            // Note: Motor direction should be set in Robot Configuration if needed
            // If motor spins backwards, either reverse in config or add: sm.setDirection(DcMotorSimple.Direction.REVERSE);

            // Set PIDF for velocity control
            sm.setVelocityPIDFCoefficients(
                Constants.SHOOTER_KP,
                Constants.SHOOTER_KI,
                Constants.SHOOTER_KD,
                Constants.SHOOTER_KF
            );
        } catch (Exception e) {
            // Hardware missing or misconfigured - operate in degraded mode (no motor)
            sm = null;
        }
        shooterMotor = sm;

        // Start at medium idle speed
        setTargetSpeed(currentMode);
    }

    /**
     * Set shooter speed mode (does not require shoot command to be active).
     * Shooter will idle at this speed.
     */
    public void setSpeedMode(SpeedMode mode) {
        if (mode == null) return; // Safety check - ignore null mode
        currentMode = mode;
        setTargetSpeed(mode);
        // Update lastTargetEncoderVelocityCmd to prevent acceleration feedforward spikes
        // when speed mode changes (especially if called while disabled)
        double newTargetEncoderVelocity = targetVelocityRPM * Constants.SHOOTER_ENCODER_VELOCITY_PER_RPM;
        lastTargetEncoderVelocityCmd = newTargetEncoderVelocity;
    }

    /**
     * Activate shooting (tightens control to exact target speed).
     * Call this while shoot button is held.
     */
    public void setShootCommand(boolean active) {
        shootCommandActive = active;
    }

    /**
     * Main update - call every loop to maintain velocity setpoint.
     **/
    
    public void update() {
        // Always update timing state to prevent huge dt spikes when re-enabled
        long now = System.nanoTime();
        double dt = 0.0;
        if (lastUpdateNanos != 0) {
            long delta = now - lastUpdateNanos;
            // Handle System.nanoTime() overflow (happens every ~292 years, but be safe)
            if (delta < 0) {
                // Overflow occurred - reset timing
                lastUpdateNanos = now;
                delta = 0;
            }
            dt = delta / 1e9;
        }
        // Cap dt to prevent huge acceleration feedforward spikes (max 0.1s)
        if (dt > 0.1) dt = 0.0; // Treat as first update if dt is too large
        lastUpdateNanos = now;

        if (!shooterEnabled) {
            if (shooterMotor != null) shooterMotor.setPower(0.0);
            // Update lastTargetEncoderVelocityCmd to prevent spikes on re-enable
            double targetEncoderVelocity = targetVelocityRPM * Constants.SHOOTER_ENCODER_VELOCITY_PER_RPM;
            lastTargetEncoderVelocityCmd = targetEncoderVelocity;
            lastTargetEncoderVelocity = targetEncoderVelocity;
            return;
        }

        // Convert target RPM -> encoder velocity
        double targetEncoderVelocity = targetVelocityRPM * Constants.SHOOTER_ENCODER_VELOCITY_PER_RPM;
        lastTargetEncoderVelocity = targetEncoderVelocity;

        if (shooterMotor == null) {
            // Update lastTargetEncoderVelocityCmd even when motor is null
            lastTargetEncoderVelocityCmd = targetEncoderVelocity;
            return;
        }

        // Current RPM for error computation
        double currentRPM = getVelocityRPM();
        double errorRPM = targetVelocityRPM - currentRPM;

        // --- Spin-up detection (adaptive threshold for low RPM targets) ---
        // Use percentage-based threshold for low RPM, fixed threshold for high RPM
        double spinupThreshold;
        if (targetVelocityRPM <= 0.0) {
            // Target is zero or negative - no spin-up needed
            spinupThreshold = 0.0;
        } else if (targetVelocityRPM < Constants.SHOOTER_SPINUP_SWITCH_RPM) {
            // Low RPM: use percentage of target (e.g., 50% of 75 RPM = 37.5 RPM threshold)
            spinupThreshold = targetVelocityRPM * Constants.SHOOTER_SPINUP_ERROR_PERCENT;
            // Minimum threshold of 10 RPM to ensure spin-up works even for very low targets
            spinupThreshold = Math.max(spinupThreshold, 10.0);
        } else {
            // High RPM: use fixed threshold
            spinupThreshold = Constants.SHOOTER_SPINUP_ERROR_RPM;
        }
        boolean spinup = Math.abs(errorRPM) > spinupThreshold;

        // Swap PIDF based on spin-up vs steady-state
        // NOTE: Setting PIDF every loop is usually fine, but if your SDK/hardware jitters,
        // you can gate it (only set when spinup state changes). This is minimal and safe.
        if (spinup) {
            shooterMotor.setVelocityPIDFCoefficients(
                Constants.SHOOTER_KP_SPINUP,
                Constants.SHOOTER_KI_SPINUP,
                Constants.SHOOTER_KD_SPINUP,
                Constants.SHOOTER_KF_SPINUP
            );
        } else {
            shooterMotor.setVelocityPIDFCoefficients(
                Constants.SHOOTER_KP,
                Constants.SHOOTER_KI,
                Constants.SHOOTER_KD,
                Constants.SHOOTER_KF
            );
        }

        // Optional acceleration feedforward to help jump to speed faster:
        // cmd = targetEncVel + kA * (d/dt targetEncVel)
        double accelFF = 0.0;
        if (dt > 1e-4 && dt <= 0.1 && Constants.SHOOTER_KA != 0.0) {
            double dTarget = (targetEncoderVelocity - lastTargetEncoderVelocityCmd) / dt;
            accelFF = Constants.SHOOTER_KA * dTarget;
        }

        double cmd = targetEncoderVelocity + accelFF;

        // Safety clamp
        cmd = Math.max(-Constants.SHOOTER_MAX_ENCODER_VELOCITY,
              Math.min(Constants.SHOOTER_MAX_ENCODER_VELOCITY, cmd));

        lastTargetEncoderVelocityCmd = targetEncoderVelocity;

        // Note: setVelocity() expects encoder velocity in encoder units per second
        // The sign of cmd determines direction: positive = forward, negative = reverse
        // For shooter flywheel, typically only forward rotation is needed
        // If motor spins backwards with positive values, set direction in Robot Configuration
        shooterMotor.setVelocity(cmd);
    }

    /**
     * Enable/disable shooter motor (called by A button).
     */
    public void setEnabled(boolean enabled) {
        shooterEnabled = enabled;
        if (!enabled) {
            if (shooterMotor != null) shooterMotor.setPower(0.0);
            // Reset timing state to prevent huge dt spike when re-enabled
            lastUpdateNanos = 0;
            lastTargetEncoderVelocityCmd = targetVelocityRPM * Constants.SHOOTER_ENCODER_VELOCITY_PER_RPM;
        } else {
            // When enabling, reset timing state to prevent issues if it was disabled for a long time
            lastUpdateNanos = 0;
            // Keep lastTargetEncoderVelocityCmd as-is (already set correctly by setSpeedMode or previous state)
        }
    }

    /**
     * Check if shooter is enabled.
     */
    public boolean isEnabled() {
        return shooterEnabled;
    }


    /**
     * Check if shooter is at target speed (within tolerance).
     * Uses adaptive tolerance: percentage-based for low RPM, fixed for high RPM.
     */
    public boolean isAtSpeed() {
        double currentRPM = getVelocityRPM();
        double targetRPM = targetVelocityRPM;
        
        // Handle zero/negative target as special case
        if (targetRPM <= 0.0) {
            // If target is zero, consider "at speed" if current is also near zero
            return Math.abs(currentRPM) < 5.0;
        }
        
        // Adaptive tolerance: percentage for low RPM, fixed for high RPM
        double tolerance;
        if (targetRPM < Constants.SHOOTER_TOLERANCE_SWITCH_RPM) {
            // Low RPM: use percentage of target (e.g., 10% of 75 RPM = 7.5 RPM tolerance)
            tolerance = targetRPM * Constants.SHOOTER_VELOCITY_TOLERANCE_PERCENT;
            // Minimum tolerance of 5 RPM to avoid being too strict
            tolerance = Math.max(tolerance, 5.0);
        } else {
            // High RPM: use fixed tolerance
            tolerance = Constants.SHOOTER_VELOCITY_TOLERANCE_RPM;
        }
        
        return Math.abs(currentRPM - targetRPM) < tolerance;
    }

    /**
     * Get current shooter velocity in RPM.
     * BUGFIX: Take absolute value to handle both directions safely.
     */
    public double getVelocityRPM() {
        if (shooterMotor == null) return 0.0;
        double encoderVelocity = Math.abs(shooterMotor.getVelocity()); // encoder velocity (absolute value for safety)
        return encoderVelocity / Constants.SHOOTER_ENCODER_VELOCITY_PER_RPM; // convert encoder velocity -> RPM
    }

    /**
     * Get diagnostic data for telemetry debugging.
     * Returns encoder velocity and RPM calculations for troubleshooting.
     */
    public DiagnosticData getDiagnosticData() {
        if (shooterMotor == null) {
            return new DiagnosticData(0.0, 0.0, 0.0, 0.0, 0.0);
        }
        double actualEncoderVelocity = Math.abs(shooterMotor.getVelocity());
        double targetEncoderVelocity = targetVelocityRPM * Constants.SHOOTER_ENCODER_VELOCITY_PER_RPM;
        double calculatedRPM = actualEncoderVelocity / Constants.SHOOTER_ENCODER_VELOCITY_PER_RPM;
        // Compare with old wrong value (5202 ticks) for diagnostic purposes
        double rpmWithOldWrongTicks = actualEncoderVelocity / (537.7 / 60.0);
        double encoderVelocityError = targetEncoderVelocity - actualEncoderVelocity;
        
        return new DiagnosticData(
            targetEncoderVelocity,
            actualEncoderVelocity,
            encoderVelocityError,
            calculatedRPM,
            rpmWithOldWrongTicks
        );
    }

    /**
     * Diagnostic data container for telemetry.
     */
    public static class DiagnosticData {
        public final double targetEncoderVelocity;
        public final double actualEncoderVelocity;
        public final double encoderVelocityError;
        public final double calculatedRPM;
        public final double rpmWithOldWrongTicks; // For comparison - shows what RPM would be with wrong encoder config

        public DiagnosticData(double targetEncVel, double actualEncVel, double encVelError, 
                            double calcRPM, double rpmOldWrong) {
            this.targetEncoderVelocity = targetEncVel;
            this.actualEncoderVelocity = actualEncVel;
            this.encoderVelocityError = encVelError;
            this.calculatedRPM = calcRPM;
            this.rpmWithOldWrongTicks = rpmOldWrong;
        }
    }

    /**
     * Get current speed mode.
     */
    public SpeedMode getSpeedMode() {
        return currentMode;
    }

    /**
     * Get target RPM for current mode.
     * Returns the actual target velocity (targetVelocityRPM) for consistency with isAtSpeed().
     */
    public double getTargetRPM() {
        return targetVelocityRPM;
    }

    /**
     * Check if shoot command is active.
     */
    public boolean isShootCommandActive() {
        return shootCommandActive;
    }

    // Internal helper to set target RPM (conversion to encoder velocity done at update())
    private void setTargetSpeed(SpeedMode mode) {
        targetVelocityRPM = mode.rpm;
    }

    /**
     * Stop shooter motor immediately (no-op if motor missing).
     * Added to satisfy callers that expect a cleanup method.
     */
    public void stop() {
        if (shooterMotor != null) shooterMotor.setPower(0.0);
        targetVelocityRPM = 0.0;
        shootCommandActive = false;
        // Reset timing state to prevent issues on restart
        lastUpdateNanos = 0;
        lastTargetEncoderVelocityCmd = 0.0;
    }
}
