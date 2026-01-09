package com.teamcode.subsystems;

import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
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

    public ShooterSubsystem(HardwareMap hw) {
        DcMotorEx sm = null;
        try {
            sm = hw.get(DcMotorEx.class, Constants.SHOOTER_MOTOR_NAME);
           

            sm.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
            sm.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
            sm.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.FLOAT); // Coast for flywheel

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
        currentMode = mode;
        setTargetSpeed(mode);
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
        if (!shooterEnabled) {
            if (shooterMotor != null) shooterMotor.setPower(0.0);
            return;
        }
        // Convert target RPM to encoder velocity for the motor controller
        double targetEncoderVelocity = targetVelocityRPM * Constants.SHOOTER_ENCODER_VELOCITY_PER_RPM;
        lastTargetEncoderVelocity = targetEncoderVelocity;
        if (shooterMotor != null) {
            // Velocity control is handled by DcMotorEx internally via PIDF
            shooterMotor.setVelocity(targetEncoderVelocity);
        }
    }

    /**
     * Enable/disable shooter motor (called by A button).
     */
    public void setEnabled(boolean enabled) {
        shooterEnabled = enabled;
        if (!enabled) {
            if (shooterMotor != null) shooterMotor.setPower(0.0);
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
     */
    public boolean isAtSpeed() {
        double currentRPM = getVelocityRPM();
        double targetRPM = currentMode.rpm;
        return Math.abs(currentRPM - targetRPM) < Constants.SHOOTER_VELOCITY_TOLERANCE_RPM;
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
     */
    public double getTargetRPM() {
        return currentMode.rpm;
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
    }
}
