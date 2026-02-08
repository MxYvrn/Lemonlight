package com.teamcode.grove;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

/**
 * Stream telemetry OpMode: polls Lemonlight at ~10Hz, shows loop time,
 * last inference age, detections summary, metrics, and raw data.
 *
 * <p>Enhanced with:
 * <ul>
 *   <li>Exception handling for LemonlightException</li>
 *   <li>Metrics display (success rate, latency, health)</li>
 *   <li>Sensor health monitoring</li>
 * </ul>
 */
@TeleOp(name = "Lemonlight_StreamTelemetry", group = "Test")
public class Lemonlight_StreamTelemetry extends LinearOpMode {

    private static final String DEVICE_NAME = LemonlightConstants.CONFIG_DEVICE_NAME;
    private static final double POLL_HZ = 10.0;
    private static final long POLL_MS = (long) (1000.0 / POLL_HZ);
    private static final int RAW_DEBUG_BYTES = 32;

    private Lemonlight lemonlight;
    private LemonlightSensor sensorWrapper;
    private long lastInferenceMs;
    private final StringBuilder hexBuilder = new StringBuilder(RAW_DEBUG_BYTES * 3 + 4);
    private int consecutiveErrors = 0;

    @Override
    public void runOpMode() throws InterruptedException {
        // Initialize device
        lemonlight = GroveVisionI2CHelper.bindLemonlight(hardwareMap, telemetry);
        if (lemonlight == null) {
            telemetry.addLine("ERROR: Failed to bind Lemonlight device");
            telemetry.addLine("Check Robot Configuration!");
            telemetry.update();
            sleep(5000);
            return;
        }

        sensorWrapper = new LemonlightSensor(lemonlight);

        telemetry.addLine("Lemonlight initialized");
        telemetry.addLine("Ready. Press START for stream.");
        telemetry.update();

        waitForStart();

        lastInferenceMs = 0;

        // Main loop
        while (opModeIsActive()) {
            long loopStart = System.currentTimeMillis();

            try {
                // Update sensor (may throw LemonlightException)
                sensorWrapper.update();
                LemonlightResult result = sensorWrapper.getLastResult();

                if (result != null && result.isValid()) {
                    lastInferenceMs = result.getTimestampMs();
                    consecutiveErrors = 0;  // Reset error counter
                } else {
                    consecutiveErrors++;
                }

                // Display telemetry
                displayTelemetry(result, loopStart);

            } catch (LemonlightException e) {
                consecutiveErrors++;
                displayErrorTelemetry(e, loopStart);

                // If too many consecutive errors, suggest reset
                if (consecutiveErrors > 10) {
                    telemetry.addLine("⚠ Too many errors - consider restarting OpMode");
                }
            } catch (Exception e) {
                consecutiveErrors++;
                telemetry.addLine("=== Unexpected Error ===");
                telemetry.addData("Error", e.getClass().getSimpleName());
                telemetry.addData("Message", e.getMessage());
                telemetry.update();
            }

            // Maintain polling rate
            long sleepMs = POLL_MS - (System.currentTimeMillis() - loopStart);
            if (sleepMs > 0) sleep(sleepMs);
        }
    }

    private void displayTelemetry(LemonlightResult result, long loopStart) {
        long loopElapsed = System.currentTimeMillis() - loopStart;
        long inferenceAge = lastInferenceMs > 0 ?
            System.currentTimeMillis() - lastInferenceMs : -1;

        telemetry.addLine("=== Lemonlight Stream ===");
        telemetry.addData("Loop time", "%dms", loopElapsed);
        telemetry.addData("Inference age", inferenceAge >= 0 ? inferenceAge + "ms" : "n/a");

        // Display result data
        if (result != null) {
            telemetry.addData("Valid", result.isValid() ? "✓" : "✗");
            telemetry.addData("Detections", result.getDetectionCount());
            telemetry.addData("Boxes", result.getDetections().size());
            telemetry.addData("Classes", result.getClassifications().size());
            telemetry.addData("Keypoints", result.getKeypoints().size());
            telemetry.addData("Top score", "%d%%", result.getTopScorePercent());

            // Show raw hex data
            if (result.getRaw() != null && result.getRaw().length > 0) {
                appendRawHex(result.getRaw(), RAW_DEBUG_BYTES);
                telemetry.addData("Raw (first " + RAW_DEBUG_BYTES + ")",
                    hexBuilder.toString());
            }
        } else {
            telemetry.addData("Result", "null");
        }

        // Display metrics
        LemonlightMetrics metrics = lemonlight.getMetrics();
        telemetry.addLine();
        telemetry.addLine("=== Metrics ===");
        telemetry.addData("Success Rate", "%.1f%%", metrics.getSuccessRate() * 100);
        telemetry.addData("Avg Latency", "%.0fms", metrics.getAverageLatencyMs());
        telemetry.addData("Total Reads", metrics.getTotalReads());

        // Display sensor health
        LemonlightSensor.SensorHealth health = sensorWrapper.getHealth();
        telemetry.addLine();
        telemetry.addLine("=== Sensor Health ===");
        telemetry.addData("Status", health.isHealthy ? "✓ Healthy" : "✗ Unhealthy");
        telemetry.addData("Updates", "%d (✗%d)",
            health.totalUpdates, health.failedUpdates);
        telemetry.addData("Result Age", health.resultAgeMs >= 0 ?
            health.resultAgeMs + "ms" : "Never updated");

        if (consecutiveErrors > 0) {
            telemetry.addData("Consecutive Errors", consecutiveErrors);
        }

        telemetry.update();
    }

    private void displayErrorTelemetry(LemonlightException e, long loopStart) {
        long loopElapsed = System.currentTimeMillis() - loopStart;

        telemetry.addLine("=== Error Occurred ===");
        telemetry.addData("Loop time", "%dms", loopElapsed);
        telemetry.addData("Error Code", e.getShortCode());
        telemetry.addData("Message", e.getUserMessage());
        telemetry.addData("Severity", e.getSeverity().toString());
        telemetry.addData("Recoverable", e.isRecoverable() ? "Yes" : "No");
        telemetry.addData("Consecutive", consecutiveErrors);

        // Display metrics even on error
        LemonlightMetrics metrics = lemonlight.getMetrics();
        telemetry.addLine();
        telemetry.addData("Success Rate", "%.1f%%", metrics.getSuccessRate() * 100);
        telemetry.addData("Last Error", metrics.getLastErrorMessage());

        telemetry.update();
    }

    private void appendRawHex(byte[] raw, int maxBytes) {
        hexBuilder.setLength(0);
        if (raw == null) return;

        int n = Math.min(raw.length, maxBytes);
        for (int i = 0; i < n; i++) {
            hexBuilder.append(String.format("%02X ", raw[i] & 0xFF));
        }

        if (raw.length > maxBytes) {
            hexBuilder.append("...");
        }
    }
}
