package com.teamcode.subsystems;

import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.util.ElapsedTime;
import com.teamcode.Constants;

/**
 * Feeder + Intake coupling (minimal integration).
 *
 * IMPORTANT: This class now owns BOTH the feeder motor and intake motor to ensure
 * they follow each other based on the same inputs.
 *
 * Safer architecture is to keep IntakeSubsystem separate and use a coordinator,
 * but this matches your request with minimal external changes.
 */
public class FeederSubsystem {

    private final DcMotor feederMotor;
    // Cache feeder power when motor missing so telemetry and logic can still observe commanded value
    private double feederPowerCache = 0.0;

    private final ElapsedTime rampTimer = new ElapsedTime();

    // Inputs
    private boolean shootCommandActive = false;
    private double rtValue = 0.0;

    // Intake command (from intake/outtake buttons)
    // + = intake, - = outtake, 0 = stop
    private double intakeCommandPower = 0.0;

    public FeederSubsystem(HardwareMap hw) {
        DcMotor fm = null;
        try {
            fm = hw.get(DcMotor.class, Constants.FEEDER_MOTOR_NAME);
            fm.setDirection(DcMotorSimple.Direction.FORWARD);
            fm.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
            fm.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        } catch (Exception e) {
            // Motor missing or misconfigured - operate in degraded mode
            fm = null;
        }
        feederMotor = fm;

        // NOTE: Feeder no longer acquires the intake motor. IntakeSubsystem remains
        // the canonical owner of the intake motor hardware. The feeder receives
        // intake commands via `setIntakeCommandPower()`.
    }

    /**
     * Set intake command power from driver input.
     * Example: +Constants.INTAKE_POWER_COLLECT or Constants.INTAKE_POWER_EJECT or 0.
     */
    public void setIntakeCommandPower(double power) {
        intakeCommandPower = clamp(power, -1.0, 1.0);
    }

    /**
     * Command feeder with RT trigger + optional shoot command.
     * RT is treated as a "feed now" command (0.0 to 1.0).
     */
    public void setFeedCommand(boolean shootActive, double rtTriggerValue) {
        // Reset ramp timer when we detect a fresh shoot activation.
        // Also reset if the previous ramp completed to ensure repeated activations restart properly.
        if (shootActive && (!shootCommandActive || rampTimer.milliseconds() >= Constants.FEEDER_RAMP_TIME_MS)) {
            rampTimer.reset();
        }
        shootCommandActive = shootActive;
        rtValue = clamp(rtTriggerValue, 0.0, 1.0);
    }

    /**
     * Update both motors. Call every loop.
     *
     * Coupling behavior:
     * - If SHOOT is active: feeder ramps up to FEEDER_SHOOT_POWER. Intake runs forward to help feed.
     * - Else if RT is pressed: feeder power = RT * FEEDER_SHOOT_POWER, direction follows intake sign if intake is running,
     *   otherwise defaults forward. Intake follows RT in the same direction (so "vice versa").
     * - Else: intake runs at intakeCommandPower, feeder mirrors intake power (scaled) so it follows intake automatically.
     */
    public void update() {
        double feederPower = 0.0;
        // intakePower is computed for logic only; the IntakeSubsystem owns the motor.
        double intakePower = 0.0;

        // Determine current "intake direction sign" from intake command
        // +1 intake, -1 outtake, 0 stopped
        double intakeDirSign = sign(intakeCommandPower);

        if (shootCommandActive) {
            // Shooter mode: ramp feeder up smoothly
            double elapsed = rampTimer.milliseconds();
            double rampFraction = Math.min(1.0, elapsed / Constants.FEEDER_RAMP_TIME_MS);
            feederPower = Constants.FEEDER_SHOOT_POWER * rampFraction;

            // Intake assists feeding during shooting (forward)
            intakePower = Math.max(intakeCommandPower, Constants.INTAKE_POWER_COLLECT * 0.5);
        }
        else if (rtValue > Constants.TRIGGER_THRESHOLD) {
            // RT feed: feeder responds to RT
            // If intake is active, match its direction. Otherwise default forward.
            if (intakeDirSign != 0.0) {
                feederPower = rtValue * Constants.FEEDER_SHOOT_POWER * intakeDirSign;
            } else {
                feederPower = rtValue * Constants.FEEDER_SHOOT_POWER;
            }
        }
        else {
            // Normal: intake runs from its command, feeder follows intake automatically
            intakePower = intakeCommandPower;

            // Follow intake with a safe scale so feeder doesn't overpower intake.
            double followScale = (Constants.FEEDER_SHOOT_POWER <= 0.0) ? 1.0 : Constants.FEEDER_SHOOT_POWER;
            feederPower = clamp(intakeCommandPower * followScale, -1.0, 1.0);
        }

        // Feeder only controls the feeder motor; intake motor is controlled by IntakeSubsystem.
        if (feederMotor != null) {
            feederMotor.setPower(feederPower);
        }
        feederPowerCache = feederPower;
    }

    public void stop() {
        if (feederMotor != null) feederMotor.setPower(0.0);
        feederPowerCache = 0.0;
    }

    public double getFeederPower() {
        return (feederMotor != null) ? feederMotor.getPower() : feederPowerCache;
    }

    public double getIntakePower() {
        return intakeCommandPower;
    }

    // ---------- helpers ----------
    private static double sign(double x) {
        if (x > 0.01) return 1.0;
        if (x < -0.01) return -1.0;
        return 0.0;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
