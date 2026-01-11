package com.teamcode.opmodes.auto;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.util.ElapsedTime;

/**
 * Test/simulation version of SimpleAuto that prints actions instead of controlling hardware.
 * Useful for verifying the state machine logic without needing hardware.
 */
@Autonomous(name = "Simple Auto Test", group = "Test")
public class SimpleAutoTest extends LinearOpMode {

    private enum State {
        REV_UP,        // Wait for shooter to reach target speed
        SHOOT_BALL_1,  // Shoot first ball
        WAIT_BALL_1,   // Wait between shots
        SHOOT_BALL_2,  // Shoot second ball
        WAIT_BALL_2,   // Wait between shots
        SHOOT_BALL_3,  // Shoot third ball
        DONE           // Finished
    }

    private State state;
    private ElapsedTime stateTimer;
    private ElapsedTime runtime;
    
    // Timing constants
    private static final double SHOOT_DURATION_S = 1.0;    // How long to run feeder per ball
    private static final double WAIT_BETWEEN_SHOTS_S = 0.25; // Wait time between shots
    private static final double SIMULATED_SPINUP_TIME_S = 2.0; // Simulate shooter spin-up time

    @Override
    public void runOpMode() throws InterruptedException {
        stateTimer = new ElapsedTime();
        runtime = new ElapsedTime();

        telemetry.addData("Status", "Initialized");
        telemetry.addData("Mode", "Simple Auto TEST - Prints actions only");
        telemetry.update();

        waitForStart();
        runtime.reset();

        // Initialize state
        println(">>> INIT: Setting shooter to LOW speed mode");
        println(">>> INIT: Enabling shooter motor");
        state = State.REV_UP;
        stateTimer.reset();

        while (opModeIsActive()) {
            // Safety timeout at 29.5 seconds
            if (runtime.seconds() > 29.5) {
                println(">>> TIMEOUT: Auto stopped at 29.5s (safety limit)");
                break;
            }

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
                    println(">>> DONE: All 3 balls shot - stopping");
                    break;
            }

            // Telemetry
            updateTelemetry();
            sleep(50); // Small delay for readability
        }

        println(">>> CLEANUP: Stopping shooter");
        println(">>> CLEANUP: Stopping feeder");
    }

    private void handleRevUp() {
        println(">>> ACTION: Checking if shooter is at speed...");
        
        // Simulate spin-up time
        if (stateTimer.seconds() >= SIMULATED_SPINUP_TIME_S) {
            println(">>> ACTION: Shooter reached target speed (75 RPM)");
            transitionTo(State.SHOOT_BALL_1);
        }
    }

    private void handleShootBall1() {
        if (stateTimer.seconds() == 0) {
            println(">>> ACTION: Activating shoot command (shooter tightens control)");
            println(">>> ACTION: Running feeder motor at FEEDER_SHOOT_POWER (0.5)");
            println(">>> ACTION: Shooting BALL 1");
        }
        
        if (stateTimer.seconds() >= SHOOT_DURATION_S) {
            println(">>> ACTION: Stopping shoot command");
            println(">>> ACTION: Stopping feeder motor");
            transitionTo(State.WAIT_BALL_1);
        }
    }

    private void handleWaitBall1() {
        if (stateTimer.seconds() == 0) {
            println(">>> ACTION: Waiting 0.25s between shots");
        }
        
        if (stateTimer.seconds() >= WAIT_BETWEEN_SHOTS_S) {
            transitionTo(State.SHOOT_BALL_2);
        }
    }

    private void handleShootBall2() {
        if (stateTimer.seconds() == 0) {
            println(">>> ACTION: Activating shoot command (shooter tightens control)");
            println(">>> ACTION: Running feeder motor at FEEDER_SHOOT_POWER (0.5)");
            println(">>> ACTION: Shooting BALL 2");
        }
        
        if (stateTimer.seconds() >= SHOOT_DURATION_S) {
            println(">>> ACTION: Stopping shoot command");
            println(">>> ACTION: Stopping feeder motor");
            transitionTo(State.WAIT_BALL_2);
        }
    }

    private void handleWaitBall2() {
        if (stateTimer.seconds() == 0) {
            println(">>> ACTION: Waiting 0.25s between shots");
        }
        
        if (stateTimer.seconds() >= WAIT_BETWEEN_SHOTS_S) {
            transitionTo(State.SHOOT_BALL_3);
        }
    }

    private void handleShootBall3() {
        if (stateTimer.seconds() == 0) {
            println(">>> ACTION: Activating shoot command (shooter tightens control)");
            println(">>> ACTION: Running feeder motor at FEEDER_SHOOT_POWER (0.5)");
            println(">>> ACTION: Shooting BALL 3");
        }
        
        if (stateTimer.seconds() >= SHOOT_DURATION_S) {
            println(">>> ACTION: Stopping shoot command");
            println(">>> ACTION: Stopping feeder motor");
            transitionTo(State.DONE);
        }
    }

    private void transitionTo(State newState) {
        println(String.format(">>> STATE TRANSITION: %s -> %s (at %.2f s)", state, newState, runtime.seconds()));
        state = newState;
        stateTimer.reset();
    }

    private void updateTelemetry() {
        telemetry.clearAll();
        telemetry.addData("State", state);
        telemetry.addData("Runtime", "%.2f / 30.0 s", runtime.seconds());
        telemetry.addData("State Time", "%.2f s", stateTimer.seconds());
        telemetry.addLine();
        telemetry.addLine("=== SIMULATION ===");
        telemetry.addData("Mode", "TEST - No hardware control");
        telemetry.addData("Shooter Speed", "LOW (75 RPM)");
        telemetry.update();
    }

    private void println(String message) {
        telemetry.addLine(message);
        telemetry.update();
    }
}
