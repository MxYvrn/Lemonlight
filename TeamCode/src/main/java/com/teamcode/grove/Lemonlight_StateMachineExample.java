package com.teamcode.grove;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

/**
 * Example OpMode demonstrating DeviceStateMachine usage with Lemonlight.
 *
 * <p>Shows how to:
 * <ul>
 *   <li>Integrate state machine with device lifecycle</li>
 *   <li>Use state change listeners for logging and telemetry</li>
 *   <li>Enforce state preconditions for operations</li>
 *   <li>Handle state transitions during errors</li>
 *   <li>Display state information in telemetry</li>
 * </ul>
 *
 * <p>This demonstrates enterprise-grade state management for production
 * code that needs to track device health and lifecycle.
 */
@TeleOp(name = "Lemonlight State Machine Example", group = "Example")
public class Lemonlight_StateMachineExample extends LinearOpMode {

    private Lemonlight lemonlight;
    private DeviceStateMachine stateMachine;
    private int successfulReads = 0;
    private int failedReads = 0;
    private int stateErrorRejects = 0;

    @Override
    public void runOpMode() throws InterruptedException {
        telemetry.addLine("=== Device State Machine Example ===");
        telemetry.addLine();
        telemetry.addLine("Demonstrates lifecycle state management:");
        telemetry.addLine("UNINITIALIZED → INITIALIZING → READY");
        telemetry.addLine("                    ↓           ↓");
        telemetry.addLine("              ERROR ← ─ ─ ─ ─ ─ ┘");
        telemetry.update();

        sleep(2000);

        // Create state machine
        stateMachine = new DeviceStateMachine();

        // Add state change listener for telemetry
        stateMachine.addListener((from, to, reason) -> {
            telemetry.addLine(String.format("STATE: %s → %s", from, to));
            telemetry.addLine(String.format("REASON: %s", reason));
            telemetry.update();
            try {
                Thread.sleep(1000);  // Show transition to user
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Initialize device with state machine
        telemetry.clear();
        telemetry.addLine("=== Initialization ===");
        telemetry.update();

        if (!initializeWithStateMachine()) {
            telemetry.addLine("ERROR: Initialization failed");
            telemetry.addLine("State: " + stateMachine.getState());
            telemetry.update();
            sleep(5000);
            return;
        }

        telemetry.clear();
        telemetry.addLine("=== Device Ready ===");
        telemetry.addLine("State: " + stateMachine.getState());
        telemetry.addLine();
        telemetry.addLine("Press START to begin operations");
        telemetry.update();

        waitForStart();

        // Main loop with state enforcement
        while (opModeIsActive()) {
            long loopStart = System.currentTimeMillis();

            try {
                // Enforce READY state precondition
                if (!stateMachine.isReady()) {
                    stateErrorRejects++;
                    displayNotReadyError();

                    // Attempt recovery if in ERROR state
                    if (stateMachine.isError()) {
                        attemptRecovery();
                    }

                    sleep(1000);
                    continue;
                }

                // Perform operation
                LemonlightResult result = performReadOperation();
                long latency = System.currentTimeMillis() - loopStart;

                if (result != null && result.isValid()) {
                    successfulReads++;
                    displaySuccess(result, latency);
                } else {
                    failedReads++;
                    handleReadFailure(latency);
                }

            } catch (LemonlightException e) {
                failedReads++;
                handleException(e);
            }

            displayStatistics();
            displayStateMachineStatus();

            telemetry.update();
            sleep(500);
        }
    }

    /**
     * Initializes device with state machine tracking.
     */
    private boolean initializeWithStateMachine() {
        // Transition to INITIALIZING
        if (!stateMachine.transition(DeviceStateMachine.State.INITIALIZING, "User initiated")) {
            return false;
        }

        try {
            // Bind device
            lemonlight = GroveVisionI2CHelper.bindLemonlight(hardwareMap, telemetry);

            if (lemonlight == null) {
                stateMachine.transition(DeviceStateMachine.State.ERROR, "Failed to bind device");
                return false;
            }

            // Transition to READY
            if (stateMachine.transition(DeviceStateMachine.State.READY, "Initialization complete")) {
                return true;
            }

            return false;

        } catch (Exception e) {
            stateMachine.transition(DeviceStateMachine.State.ERROR,
                "Exception during init: " + e.getMessage());
            return false;
        }
    }

    /**
     * Performs read operation with state tracking.
     */
    private LemonlightResult performReadOperation() throws LemonlightException {
        try {
            LemonlightResult result = lemonlight.readInference();

            // If we got a null/invalid result multiple times, transition to ERROR
            if ((result == null || !result.isValid()) && failedReads > 5) {
                stateMachine.transition(DeviceStateMachine.State.ERROR,
                    "Too many failed reads");
            }

            return result;

        } catch (LemonlightException e) {
            // Determine if error is critical
            if (e.getSeverity() == LemonlightException.ErrorSeverity.FATAL) {
                stateMachine.transition(DeviceStateMachine.State.DISCONNECTED,
                    "Fatal error: " + e.getShortCode());
            } else if (failedReads > 10) {
                stateMachine.transition(DeviceStateMachine.State.ERROR,
                    "Excessive errors: " + e.getShortCode());
            }

            throw e;
        }
    }

    /**
     * Attempts to recover from ERROR state.
     */
    private void attemptRecovery() {
        telemetry.addLine("=== Attempting Recovery ===");
        telemetry.update();

        if (stateMachine.transition(DeviceStateMachine.State.INITIALIZING, "Recovery attempt")) {
            sleep(500);

            // Simple recovery: just transition back to READY
            // In real code, you might re-ping the device, reset config, etc.
            if (stateMachine.transition(DeviceStateMachine.State.READY, "Recovery successful")) {
                failedReads = 0;  // Reset error counter
                telemetry.addLine("✓ Recovery successful");
            } else {
                telemetry.addLine("✗ Recovery failed");
            }
            telemetry.update();
            sleep(1000);
        }
    }

    /**
     * Handles case where device is not in READY state.
     */
    private void displayNotReadyError() {
        telemetry.clear();
        telemetry.addLine("=== ⚠ DEVICE NOT READY ===");
        telemetry.addLine();
        telemetry.addData("Current State", stateMachine.getState());
        telemetry.addData("Last Transition", stateMachine.getLastTransitionReason());
        telemetry.addData("Time in State", "%dms", stateMachine.getTimeSinceLastTransition());
        telemetry.addLine();

        if (stateMachine.isError()) {
            telemetry.addLine("Device is in ERROR state");
            telemetry.addLine("Attempting automatic recovery...");
        } else if (stateMachine.isDisconnected()) {
            telemetry.addLine("Device is DISCONNECTED");
            telemetry.addLine("Manual restart required");
        } else if (stateMachine.isInitializing()) {
            telemetry.addLine("Initialization in progress...");
        }
    }

    /**
     * Handles read failure.
     */
    private void handleReadFailure(long latency) {
        // After too many failures, transition to ERROR
        if (failedReads > 5) {
            stateMachine.transition(DeviceStateMachine.State.ERROR,
                String.format("%d consecutive failures", failedReads));
        }
    }

    /**
     * Handles exception during read.
     */
    private void handleException(LemonlightException e) {
        telemetry.addLine("=== ✗ EXCEPTION ===");
        telemetry.addData("Error Code", e.getShortCode());
        telemetry.addData("Message", e.getUserMessage());
        telemetry.addData("Severity", e.getSeverity());

        // State machine already updated in performReadOperation()
    }

    /**
     * Displays successful read.
     */
    private void displaySuccess(LemonlightResult result, long latency) {
        telemetry.clear();
        telemetry.addLine("=== ✓ SUCCESS ===");
        telemetry.addData("Latency", "%dms", latency);
        telemetry.addData("Detections", result.getDetectionCount());
        telemetry.addData("Boxes", result.getDetections().size());
        telemetry.addData("Classes", result.getClassifications().size());
        telemetry.addData("Keypoints", result.getKeypoints().size());
    }

    /**
     * Displays statistics.
     */
    private void displayStatistics() {
        telemetry.addLine();
        telemetry.addLine("=== Statistics ===");
        telemetry.addData("Successful Reads", successfulReads);
        telemetry.addData("Failed Reads", failedReads);
        telemetry.addData("State Rejects", stateErrorRejects);

        if (successfulReads + failedReads > 0) {
            double successRate = successfulReads / (double) (successfulReads + failedReads) * 100;
            telemetry.addData("Success Rate", "%.1f%%", successRate);
        }
    }

    /**
     * Displays state machine status.
     */
    private void displayStateMachineStatus() {
        DeviceStateMachine.StateSummary summary = stateMachine.getSummary();

        telemetry.addLine();
        telemetry.addLine("=== State Machine ===");
        telemetry.addData("Current State", summary.state);
        telemetry.addData("Time in State", "%dms", summary.timeInStateMs);
        telemetry.addData("Last Transition", summary.lastReason);
        telemetry.addData("Listeners", stateMachine.getListenerCount());

        // Show available transitions
        telemetry.addLine();
        telemetry.addLine("Valid Transitions:");
        for (DeviceStateMachine.State state : DeviceStateMachine.State.values()) {
            if (stateMachine.canTransitionTo(state)) {
                telemetry.addLine("  → " + state);
            }
        }
    }
}
