package com.teamcode.subsystems;

import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.HardwareMap;

public class Drive {
    private final DcMotor frontLeft, frontRight, backLeft, backRight;

    public Drive(HardwareMap hw) {
        frontLeft  = hw.get(DcMotor.class, "front_left");
        frontRight = hw.get(DcMotor.class, "front_right");
        backLeft   = hw.get(DcMotor.class, "back_left");
        backRight  = hw.get(DcMotor.class, "back_right");

        // Standard mecanum motor direction setup (tune if needed)
        frontLeft.setDirection(DcMotorSimple.Direction.REVERSE);
        backLeft.setDirection(DcMotorSimple.Direction.REVERSE);
        frontRight.setDirection(DcMotorSimple.Direction.FORWARD);
        backRight.setDirection(DcMotorSimple.Direction.FORWARD);

        // Run without encoders for autonomous (using odometry for position)
        setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
    }

    /**
     * Drive in robot-centric frame.
     * @param forward  forward velocity [-1, 1] (positive = forward)
     * @param strafe   strafe velocity [-1, 1] (positive = left)
     * @param turn     turn velocity [-1, 1] (positive = CCW)
     */
    public void driveRobotCentric(double forward, double strafe, double turn) {
        double fl = forward + strafe + turn;
        double fr = forward - strafe - turn;
        double bl = forward - strafe + turn;
        double br = forward + strafe - turn;

        // Normalize if any power exceeds 1.0
        double max = Math.max(1.0, Math.max(Math.max(Math.abs(fl), Math.abs(fr)),
                                             Math.max(Math.abs(bl), Math.abs(br))));
        fl /= max;
        fr /= max;
        bl /= max;
        br /= max;

        setDrivePowers(fl, fr, bl, br);
    }

    /**
     * Directly set individual motor powers (clamped to [-1, 1]).
     */
    public void setDrivePowers(double fl, double fr, double bl, double br) {
        frontLeft.setPower(clamp(fl, -1.0, 1.0));
        frontRight.setPower(clamp(fr, -1.0, 1.0));
        backLeft.setPower(clamp(bl, -1.0, 1.0));
        backRight.setPower(clamp(br, -1.0, 1.0));
    }

    /**
     * Stop all drive motors.
     */
    public void stop() {
        setDrivePowers(0, 0, 0, 0);
    }

    public void setMode(DcMotor.RunMode mode) {
        frontLeft.setMode(mode);
        frontRight.setMode(mode);
        backLeft.setMode(mode);
        backRight.setMode(mode);
    }

    public void setZeroPowerBehavior(DcMotor.ZeroPowerBehavior behavior) {
        frontLeft.setZeroPowerBehavior(behavior);
        frontRight.setZeroPowerBehavior(behavior);
        backLeft.setZeroPowerBehavior(behavior);
        backRight.setZeroPowerBehavior(behavior);
    }

    private double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }
}
