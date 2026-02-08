package com.teamcode.grove;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

/**
 * Example OpMode demonstrating resilience patterns (retry logic + circuit breaker).
 *
 * <p>Shows how to use:
 * <ul>
 *   <li>{@link RetryPolicy} for automatic retries with exponential backoff</li>
 *   <li>{@link CircuitBreaker} for fail-fast when device is down</li>
 *   <li>Combined patterns for maximum resilience</li>
 * </ul>
 *
 * <p>This OpMode demonstrates enterprise-grade error handling with automatic
 * recovery from transient failures and circuit protection.
 */
@TeleOp(name = "Lemonlight Resilient Example", group = "Example")
public class Lemonlight_ResilientExample extends LinearOpMode {

    private Lemonlight lemonlight;
    private RetryPolicy retryPolicy;
    private CircuitBreaker circuitBreaker;

    @Override
    public void runOpMode() throws InterruptedException {
        telemetry.addLine("=== Resilient Lemonlight Example ===");
        telemetry.addLine("Demonstrates retry logic + circuit breaker");
        telemetry.update();

        // Initialize device
        lemonlight = GroveVisionI2CHelper.bindLemonlight(hardwareMap, telemetry);
        if (lemonlight == null) {
            telemetry.addLine("ERROR: Failed to bind Lemonlight");
            telemetry.update();
            sleep(5000);
            return;
        }

        // Create resilience components
        retryPolicy = RetryPolicy.defaultPolicy();      // 3 retries, exponential backoff
        circuitBreaker = CircuitBreaker.defaultBreaker(); // Opens after 5 failures

        telemetry.addLine();
        telemetry.addLine("Resilience Configuration:");
        telemetry.addData("Retry Policy", retryPolicy.toString());
        telemetry.addData("Circuit Breaker", "5 failures, 3s cooldown");
        telemetry.addLine();
        telemetry.addLine("Press START to begin");
        telemetry.update();

        waitForStart();

        int successfulReads = 0;
        int failedReads = 0;
        int circuitBreakerRejects = 0;

        // Main loop with resilience
        while (opModeIsActive()) {
            long loopStart = System.currentTimeMillis();

            try {
                // Execute with both retry AND circuit breaker
                LemonlightResult result = executeResilient();

                if (result != null && result.isValid()) {
                    successfulReads++;
                    displaySuccessfulRead(result, loopStart);
                } else {
                    failedReads++;
                    displayFailedRead(loopStart);
                }

            } catch (LemonlightException e) {
                if (e.getErrorCode() == LemonlightException.ErrorCode.CIRCUIT_BREAKER_OPEN) {
                    circuitBreakerRejects++;
                    displayCircuitBreakerOpen(e, loopStart);
                } else {
                    failedReads++;
                    displayError(e, loopStart);
                }
            }

            // Display statistics
            displayStatistics(successfulReads, failedReads, circuitBreakerRejects);

            telemetry.update();
            sleep(500);  // 2 Hz polling
        }
    }

    /**
     * Executes read with retry logic wrapped in circuit breaker.
     * This is the recommended pattern for maximum resilience.
     */
    private LemonlightResult executeResilient() throws LemonlightException {
        return circuitBreaker.execute(() -> {
            // Inside circuit breaker, use retry policy
            return retryPolicy.execute(() -> {
                return lemonlight.readInference();
            });
        });
    }

    private void displaySuccessfulRead(LemonlightResult result, long loopStart) {
        long elapsed = System.currentTimeMillis() - loopStart;

        telemetry.addLine("=== ✓ SUCCESS ===");
        telemetry.addData("Loop Time", "%dms", elapsed);
        telemetry.addData("Detections", result.getDetectionCount());
        telemetry.addData("Boxes", result.getDetections().size());
        telemetry.addData("Classes", result.getClassifications().size());
        telemetry.addData("Keypoints", result.getKeypoints().size());
        telemetry.addData("Top Score", "%d%%", result.getTopScorePercent());
    }

    private void displayFailedRead(long loopStart) {
        long elapsed = System.currentTimeMillis() - loopStart;

        telemetry.addLine("=== ✗ FAILED ===");
        telemetry.addData("Loop Time", "%dms", elapsed);
        telemetry.addLine("Invalid result after retries");
    }

    private void displayCircuitBreakerOpen(LemonlightException e, long loopStart) {
        long elapsed = System.currentTimeMillis() - loopStart;

        telemetry.addLine("=== ⚠ CIRCUIT OPEN ===");
        telemetry.addData("Loop Time", "%dms", elapsed);
        telemetry.addData("Message", e.getUserMessage());

        CircuitBreaker.Statistics stats = circuitBreaker.getStatistics();
        telemetry.addLine();
        telemetry.addLine("Circuit Breaker:");
        telemetry.addData("State", stats.state.toString());
        telemetry.addData("Cooldown", "%dms remaining", stats.remainingCooldownMs);
        telemetry.addLine("Device is failing, waiting for recovery...");
    }

    private void displayError(LemonlightException e, long loopStart) {
        long elapsed = System.currentTimeMillis() - loopStart;

        telemetry.addLine("=== ✗ ERROR ===");
        telemetry.addData("Loop Time", "%dms", elapsed);
        telemetry.addData("Error Code", e.getShortCode());
        telemetry.addData("Message", e.getUserMessage());
        telemetry.addData("Recoverable", e.isRecoverable() ? "Yes" : "No");
    }

    private void displayStatistics(int successful, int failed, int rejected) {
        telemetry.addLine();
        telemetry.addLine("=== Statistics ===");
        telemetry.addData("Successful", successful);
        telemetry.addData("Failed", failed);
        telemetry.addData("Circuit Rejects", rejected);

        if (successful + failed > 0) {
            double successRate = successful / (double) (successful + failed) * 100;
            telemetry.addData("Success Rate", "%.1f%%", successRate);
        }

        // Metrics
        LemonlightMetrics metrics = lemonlight.getMetrics();
        telemetry.addLine();
        telemetry.addData("Avg Latency", "%.0fms", metrics.getAverageLatencyMs());
        telemetry.addData("Total Reads", metrics.getTotalReads());

        // Circuit breaker state
        CircuitBreaker.Statistics cbStats = circuitBreaker.getStatistics();
        telemetry.addLine();
        telemetry.addData("Circuit State", cbStats.state.toString());
        telemetry.addData("CB Failures", cbStats.failureCount);
        telemetry.addData("CB Successes", cbStats.successCount);
    }
}
