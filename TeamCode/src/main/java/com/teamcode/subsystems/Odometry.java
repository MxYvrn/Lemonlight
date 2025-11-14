package com.teamcode.subsystems;

import static com.teamcode.Constants.*;

import com.qualcomm.hardware.lynx.LynxModule;
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.IMU;
import com.qualcomm.robotcore.util.ElapsedTime;
import com.teamcode.hardware.Encoder;
import com.teamcode.util.Angle;
import com.teamcode.util.Pose2d;

import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;

import java.util.List;

public class Odometry {
    private final HardwareMap hw;

    private final Encoder leftEnc;
    private final Encoder rightEnc;
    private final Encoder strafeEnc; // may be null

    private final IMU imu;
    private double imuHeadingRad = 0.0;

    private Pose2d pose = new Pose2d();
    private double vx = 0.0, vy = 0.0, omega = 0.0; // robot-centric velocities

    private final ElapsedTime loopTimer = new ElapsedTime();
    private double lastHeadingRad = 0.0;

    private static double ticksToInches(int ticks) {
        double wheelCircum = Math.PI * WHEEL_DIAMETER_IN;
        return (ticks / TICKS_PER_REV) * (wheelCircum * GEAR_RATIO);
    }

    public Odometry(HardwareMap hw) {
        this.hw = hw;

        // Bulk caching: minimize read latency
        for (LynxModule module : hw.getAll(LynxModule.class)) {
            module.setBulkCachingMode(LynxModule.BulkCachingMode.AUTO);
        }

        leftEnc   = new Encoder(hw, ENC_LEFT,   LEFT_DIR);
        rightEnc  = new Encoder(hw, ENC_RIGHT,  RIGHT_DIR);
        strafeEnc = new Encoder(hw, ENC_STRAFE, STRAFE_DIR);

        if (USE_IMU) {
            imu = hw.get(IMU.class, IMU_NAME);
            IMU.Parameters params = new IMU.Parameters(new RevHubOrientationOnRobot(
                    RevHubOrientationOnRobot.LogoFacingDirection.UP,
                    RevHubOrientationOnRobot.UsbFacingDirection.FORWARD));
            imu.initialize(params);
            imu.resetYaw();
            imuHeadingRad = 0.0;
        } else {
            imu = null;
        }

        reset(new Pose2d(0,0,0));
        loopTimer.reset();
    }

    public void reset(Pose2d start) {
        if (leftEnc.isPresent())  leftEnc.reset();
        if (rightEnc.isPresent()) rightEnc.reset();
        if (strafeEnc != null && strafeEnc.isPresent()) strafeEnc.reset();
        pose = start.copy();
        lastHeadingRad = pose.heading;
        if (imu != null) {
            imu.resetYaw();
            imuHeadingRad = 0.0;
        }
        loopTimer.reset();
        vx = vy = omega = 0.0;
    }

    public void setPose(Pose2d p) { this.pose = p.copy(); this.lastHeadingRad = pose.heading; }

    public Pose2d getPose() { return pose.copy(); }

    /** @return Forward velocity in robot frame (inches/s) */
    public double getVx() { return vx; }

    /** @return Lateral velocity in robot frame (inches/s, left+) */
    public double getVy() { return vy; }

    /** @return Angular velocity (rad/s, CCW+) */
    public double getOmega() { return omega; }

    // Main update; call every loop
    public void update() {
        double dt = loopTimer.seconds();
        loopTimer.reset();
        if (dt <= 0) return;
        if (dt > MAX_DT_S) dt = MAX_DT_S;

        // --- readSensors()
        int dL_ticks = leftEnc.getDeltaTicks();
        int dR_ticks = rightEnc.getDeltaTicks();
        int dS_ticks = (strafeEnc != null && strafeEnc.isPresent()) ? strafeEnc.getDeltaTicks() : 0;

        double dL = ticksToInches(dL_ticks);
        double dR = ticksToInches(dR_ticks);
        double dS = ticksToInches(dS_ticks);

        // --- compute() three-wheel kinematics
        double dTheta_enc = (dR - dL) / TRACK_WIDTH_IN;
        double headingEnc = Angle.norm(lastHeadingRad + dTheta_enc);

        // Lateral correction for wheel offset
        double lateral = dS - dTheta_enc * LATERAL_WHEEL_OFFSET_IN;
        double forward = (dL + dR) * 0.5;

        // Approx rotate by average heading over interval
        double midHeading = Angle.norm(lastHeadingRad + dTheta_enc * 0.5);
        double cos = Math.cos(midHeading);
        double sin = Math.sin(midHeading);

        double dX_field = forward * cos - lateral * sin;
        double dY_field = forward * sin + lateral * cos;

        double fusedHeading = headingEnc;
        if (imu != null) {
            try {
                // Use the correct SDK 8.0+ IMU API
                double imuYawRad = Math.toRadians(imu.getRobotYawPitchRollAngles().getYaw(AngleUnit.DEGREES));
                imuYawRad *= IMU_YAW_SIGN;
                imuHeadingRad = Angle.norm(imuYawRad);
                fusedHeading = Angle.lerpAngle(headingEnc, imuHeadingRad, IMU_WEIGHT);
            } catch (Exception e) {
                // If IMU read fails, fall back to encoder-only heading
                fusedHeading = headingEnc;
            }
        }

        // integrate
        pose.x += dX_field;
        pose.y += dY_field;
        pose.heading = Angle.norm(fusedHeading);
        omega = dTheta_enc / dt;
        // robot-centric velocity from increments
        double vx_r = forward / dt;
        double vy_r = lateral / dt;
        // robot-centric velocities (not field frame)
        vx = vx_r;
        vy = vy_r;

        // --- writeActuators() none
        lastHeadingRad = pose.heading;
    }
}
