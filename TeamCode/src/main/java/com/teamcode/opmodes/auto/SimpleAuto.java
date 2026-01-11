package com.teamcode.opmodes.auto;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.util.ElapsedTime;
import com.teamcode.subsystems.FeederSubsystem;
import com.teamcode.subsystems.ShooterSubsystem;

/**
 * Simple autonomous that revs shooter to low speed and shoots 3 balls.
 * Starts at starting area right next to the goal - no movement needed.
 */
@Autonomous(name = "Simple Auto", group = "Auto")
public class SimpleAuto extends LinearOpMode {

    private enum State {
        REV_UP,        // Wait for shooter to reach target speed
        SHOOT_BALL_1,  // Shoot first ball
        WAIT_BALL_1,   // Wait between shots
        SHOOT_BALL_2,  // Shoot second ball
        WAIT_BALL_2,   // Wait between shots
        SHOOT_BALL_3,  // Shoot third ball
        DONE           // Finished
    }

    private ShooterSubsystem shooter;
    private FeederSubsystem feeder;
    private State state;
    private ElapsedTime stateTimer;
    private ElapsedTime runtime;
    
    // Timing constants
    private static final double SHOOT_DURATION_S = 1.0;    // How long to run feeder per ball
    private static final double WAIT_BETWEEN_SHOTS_S = 0.25; // Wait time between shots

    @Override
    public void runOpMode() throws InterruptedException {
        // Initialize subsystems
        shooter = new ShooterSubsystem(hardwareMap);
        feeder = new FeederSubsystem(hardwareMap);
        
        stateTimer = new ElapsedTime();
        runtime = new ElapsedTime();

        telemetry.addData("Status", "Initialized");
        telemetry.addData("Mode", "Simple Auto - Shoot 3 balls");
        telemetry.update();

        waitForStart();
        runtime.reset();

        // Set shooter to low speed and enable it
        shooter.setSpeedMode(ShooterSubsystem.SpeedMode.LOW);
        shooter.setEnabled(true);
        state = State.REV_UP;
        stateTimer.reset();

        while (opModeIsActive()) {
            // Safety timeout at 29.5 seconds
            if (runtime.seconds() > 29.5) {
                shooter.stop();
                feeder.stop();
                telemetry.addLine("AUTO TIMEOUT - STOPPED AT 29.5s");
                telemetry.update();
                break;
            }

            // Update subsystems
            shooter.update();
            feeder.update();

            // State machine
            switch (state) {
                case REV_UP:
                    handleRevUp();
                    break;
                case SHOOT_BALL_1:
                    handleShootBall1();
                    break;
                case WAIT_BALL_1:
                    handleWaitBall1();
                    break;
                case SHOOT_BALL_2:
                    handleShootBall2();
                    break;
                case WAIT_BALL_2:
                    handleWaitBall2();
                    break;
                case SHOOT_BALL_3:
                    handleShootBall3();
                    break;
                case DONE:
                    shooter.stop();
                    feeder.stop();
                    break;
            }

            // Telemetry
            updateTelemetry();
            idle();
        }

        // Cleanup
        shooter.stop();
        feeder.stop();
        sleep(100);
    }

    private void handleRevUp() {
        // Wait for shooter to reach target speed
        if (shooter.isAtSpeed()) {
            telemetry.addLine(">>> Shooter at speed! Starting to shoot...");
            transitionTo(State.SHOOT_BALL_1);
        }
        // Safety timeout: if shooter doesn't spin up in 10 seconds, proceed anyway
        else if (stateTimer.seconds() > 10.0) {
            telemetry.addLine(">>> WARNING: Shooter timeout, proceeding anyway...");
            transitionTo(State.SHOOT_BALL_1);
        }
    }

    private void handleShootBall1() {
        // Activate shoot command and feeder
        shooter.setShootCommand(true);
        feeder.setFeedCommand(true, 1.0);
        
        if (stateTimer.seconds() >= SHOOT_DURATION_S) {
            shooter.setShootCommand(false);
            feeder.setFeedCommand(false, 0.0);
            transitionTo(State.WAIT_BALL_1);
        }
    }

    private void handleWaitBall1() {
        if (stateTimer.seconds() >= WAIT_BETWEEN_SHOTS_S) {
            transitionTo(State.SHOOT_BALL_2);
        }
    }

    private void handleShootBall2() {
        // Activate shoot command and feeder
        shooter.setShootCommand(true);
        feeder.setFeedCommand(true, 1.0);
        
        if (stateTimer.seconds() >= SHOOT_DURATION_S) {
            shooter.setShootCommand(false);
            feeder.setFeedCommand(false, 0.0);
            transitionTo(State.WAIT_BALL_2);
        }
    }

    private void handleWaitBall2() {
        if (stateTimer.seconds() >= WAIT_BETWEEN_SHOTS_S) {
            transitionTo(State.SHOOT_BALL_3);
        }
    }

    private void handleShootBall3() {
        // Activate shoot command and feeder
        shooter.setShootCommand(true);
        feeder.setFeedCommand(true, 1.0);
        
        if (stateTimer.seconds() >= SHOOT_DURATION_S) {
            shooter.setShootCommand(false);
            feeder.setFeedCommand(false, 0.0);
            transitionTo(State.DONE);
        }
    }

    private void transitionTo(State newState) {
        state = newState;
        stateTimer.reset();
    }

    private void updateTelemetry() {
        telemetry.addData("State", state);
        telemetry.addData("Runtime", "%.1f / 30.0 s", runtime.seconds());
        telemetry.addData("State Time", "%.2f s", stateTimer.seconds());
        
        telemetry.addLine();
        telemetry.addLine("=== SHOOTER ===");
        telemetry.addData("Speed Mode", shooter.getSpeedMode().name());
        telemetry.addData("Target RPM", "%.0f", shooter.getTargetRPM());
        telemetry.addData("Current RPM", "%.0f", shooter.getVelocityRPM());
        telemetry.addData("At Speed", shooter.isAtSpeed() ? "YES" : "NO");
        telemetry.addData("Enabled", shooter.isEnabled() ? "YES" : "NO");
        
        telemetry.addLine();
        telemetry.addLine("=== FEEDER ===");
        telemetry.addData("Power", "%.2f", feeder.getFeederPower());
    }
}
