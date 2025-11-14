package com.teamcode.opmodes.auto;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.util.ElapsedTime;
import com.teamcode.Constants;
import com.teamcode.subsystems.Drive;
import com.teamcode.subsystems.Odometry;
import com.teamcode.util.Pose2d;

@Autonomous(name = "AutoRightBasic", group = "Auto")
public class AutoRightBasic extends LinearOpMode {

    private enum State {
        DRIVE_TO_SCORE,
        SCORE_PRELOAD,
        DRIVE_TO_PARK,
        IDLE
    }

    private Odometry odo;
    private Drive drive;
    private State state;
    private ElapsedTime stateTimer;
    private ElapsedTime runtime;

    @Override
    public void runOpMode() throws InterruptedException {
        // Initialize subsystems
        odo = new Odometry(hardwareMap);
        drive = new Drive(hardwareMap);
        stateTimer = new ElapsedTime();
        runtime = new ElapsedTime();

        // Set starting pose
        Pose2d startPose = new Pose2d(
            Constants.AUTO_START_X,
            Constants.AUTO_START_Y,
            Constants.AUTO_START_HEADING_RAD
        );
        odo.setPose(startPose);

        telemetry.addData("Status", "Initialized");
        telemetry.addData("Start Pose", "X=%.1f Y=%.1f H=%.1f°",
            startPose.x, startPose.y, Math.toDegrees(startPose.heading));
        telemetry.update();

        waitForStart();
        runtime.reset();

        // Begin autonomous
        state = State.DRIVE_TO_SCORE;
        stateTimer.reset();

        while (opModeIsActive()) {
            // 1) Update odometry
            odo.update();

            // 2) State machine
            switch (state) {
                case DRIVE_TO_SCORE:
                    handleDriveToScore();
                    break;
                case SCORE_PRELOAD:
                    handleScorePreload();
                    break;
                case DRIVE_TO_PARK:
                    handleDriveToPark();
                    break;
                case IDLE:
                    drive.stop();
                    break;
            }

            // 3) Telemetry
            updateTelemetry();
            telemetry.update();

            idle();
        }

        // Safety stop
        drive.stop();
    }

    private void handleDriveToScore() {
        Pose2d target = new Pose2d(
            Constants.AUTO_SCORE_X,
            Constants.AUTO_SCORE_Y,
            Constants.AUTO_SCORE_HEADING_RAD
        );

        if (driveToTarget(target) || stateTimer.seconds() > Constants.AUTO_STEP_TIMEOUT_S) {
            // Reached target or timeout
            drive.stop();
            transitionTo(State.SCORE_PRELOAD);
        }
    }

    private void handleScorePreload() {
        // Simulate scoring action (e.g., actuator movement)
        // In real implementation, control servos/motors here
        drive.stop();

        if (stateTimer.seconds() > Constants.AUTO_SCORE_DURATION_S) {
            transitionTo(State.DRIVE_TO_PARK);
        }
    }

    private void handleDriveToPark() {
        Pose2d target = new Pose2d(
            Constants.AUTO_PARK_X,
            Constants.AUTO_PARK_Y,
            Constants.AUTO_PARK_HEADING_RAD
        );

        if (driveToTarget(target) || stateTimer.seconds() > Constants.AUTO_STEP_TIMEOUT_S) {
            drive.stop();
            transitionTo(State.IDLE);
        }
    }

    /**
     * Proportional controller to drive to target pose.
     * @return true if within tolerance, false otherwise
     */
    private boolean driveToTarget(Pose2d target) {
        Pose2d current = odo.getPose();

        // Compute field-relative error
        double errorX = target.x - current.x;
        double errorY = target.y - current.y;
        double errorHeading = normalizeAngle(target.heading - current.heading);

        // Check if within tolerance
        double posError = Math.hypot(errorX, errorY);
        if (posError < Constants.AUTO_POS_TOLERANCE_IN &&
            Math.abs(errorHeading) < Constants.AUTO_ANGLE_TOLERANCE_RAD) {
            return true;
        }

        // Transform field error to robot frame
        double cosH = Math.cos(current.heading);
        double sinH = Math.sin(current.heading);
        double errorForward = errorX * cosH + errorY * sinH;
        double errorStrafe  = -errorX * sinH + errorY * cosH;

        // Proportional control
        double forward = errorForward * Constants.DRIVE_KP_POS;
        double strafe  = errorStrafe * Constants.DRIVE_KP_POS;
        double turn    = errorHeading * Constants.DRIVE_KP_ANGLE;

        // Clamp to max speed
        double maxLinear = Math.max(Math.abs(forward), Math.abs(strafe));
        if (maxLinear > Constants.AUTO_MAX_SPEED) {
            double scale = Constants.AUTO_MAX_SPEED / maxLinear;
            forward *= scale;
            strafe *= scale;
        }
        turn = clamp(turn, -Constants.AUTO_MAX_SPEED, Constants.AUTO_MAX_SPEED);

        drive.driveRobotCentric(forward, strafe, turn);
        return false;
    }

    private void transitionTo(State newState) {
        state = newState;
        stateTimer.reset();
    }

    private void updateTelemetry() {
        Pose2d pose = odo.getPose();
        telemetry.addData("State", state);
        telemetry.addData("Runtime", "%.1f / 30.0 s", runtime.seconds());
        telemetry.addData("State Time", "%.2f s", stateTimer.seconds());
        telemetry.addData("Pose", "X=%.1f Y=%.1f H=%.1f°",
            pose.x, pose.y, Math.toDegrees(pose.heading));
        telemetry.addData("Velocity", "Vx=%.1f Vy=%.1f Ω=%.2f",
            odo.getVx(), odo.getVy(), Math.toDegrees(odo.getOmega()));
    }

    private double normalizeAngle(double angle) {
        while (angle > Math.PI) angle -= 2 * Math.PI;
        while (angle < -Math.PI) angle += 2 * Math.PI;
        return angle;
    }

    private double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }
}
