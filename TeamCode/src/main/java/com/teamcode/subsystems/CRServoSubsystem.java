package com.teamcode.subsystems;

import com.qualcomm.robotcore.hardware.CRServo;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.teamcode.Constants;

/**
 * Subsystem for managing continuous rotation servos.
 * These servos run continuously at a set speed (like motors).
 * Gracefully handles missing servos (they may not be configured).
 */
public class CRServoSubsystem {
    private final CRServo servo1;
    private final CRServo servo2;

    private double servo1Power = Constants.CR_SERVO_SPEED;
    private double servo2Power = Constants.CR_SERVO_SPEED;

    public CRServoSubsystem(HardwareMap hw) {
        CRServo s1 = null;
        CRServo s2 = null;
        try {
            s1 = hw.get(CRServo.class, Constants.CR_SERVO_1_NAME);
        } catch (Exception e) {
            // Servo 1 not found in config
            s1 = null;
        }
        try {
            s2 = hw.get(CRServo.class, Constants.CR_SERVO_2_NAME);
        } catch (Exception e) {
            // Servo 2 not found in config
            s2 = null;
        }
        servo1 = s1;
        servo2 = s2;

        // Start servos immediately if present
        start();
    }

    /**
     * Start both servos at configured speed.
     */
    public void start() {
        if (servo1 != null) servo1.setPower(servo1Power);
        if (servo2 != null) servo2.setPower(-servo2Power);
    }

    /**
     * Stop both servos.
     */
    public void stop() {
        if (servo1 != null) servo1.setPower(0.5); // 0.5 = stopped for CR servos
        if (servo2 != null) servo2.setPower(0.5);
    }

    /**
     * Set custom power for servo 1.
     * @param power 0.0 (full reverse) to 1.0 (full forward), 0.5 = stopped
     */
    public void setServo1Power(double power) {
        servo1Power = power;
        if (servo1 != null) servo1.setPower(power);
    }

    /**
     * Set custom power for servo 2.
     * @param power 0.0 (full reverse) to 1.0 (full forward), 0.5 = stopped
     */
    public void setServo2Power(double power) {
        servo2Power = power;
        if (servo2 != null) servo2.setPower(power);
    }

    /**
     * Check if servo 1 is present.
     */
    public boolean isServo1Present() {
        return servo1 != null;
    }

    /**
     * Check if servo 2 is present.
     */
    public boolean isServo2Present() {
        return servo2 != null;
    }

    /**
     * Get current power setting for servo 1.
     */
    public double getServo1Power() {
        return servo1Power;
    }

    /**
     * Get current power setting for servo 2.
     */
    public double getServo2Power() {
        return servo2Power;
    }
}
