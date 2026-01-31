package com.teamcode.grove;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

/**
 * Stream telemetry OpMode: polls Lemonlight at ~10Hz, shows loop time, last inference age, detections summary, raw first N bytes. Reuses buffers.
 */
@TeleOp(name = "Lemonlight_StreamTelemetry", group = "Test")
public class Lemonlight_StreamTelemetry extends LinearOpMode {

    private static final String DEVICE_NAME = LemonlightConstants.CONFIG_DEVICE_NAME;
    private static final double POLL_HZ = 10.0;
    private static final long POLL_MS = (long) (1000.0 / POLL_HZ);
    private static final int RAW_DEBUG_BYTES = 32;

    private Lemonlight lemonlight;
    private LemonlightSensor sensorWrapper;
    private long lastPollMs;
    private long lastInferenceMs;
    private final StringBuilder hexBuilder = new StringBuilder(RAW_DEBUG_BYTES * 3 + 4);

    @Override
    public void runOpMode() throws InterruptedException {
        lemonlight = GroveVisionI2CHelper.bindLemonlight(hardwareMap, telemetry);
        if (lemonlight == null) {
            sleep(5000);
            return;
        }
        sensorWrapper = new LemonlightSensor(lemonlight);

        telemetry.addLine("Ready. Press START for stream.");
        telemetry.update();
        waitForStart();

        lastPollMs = System.currentTimeMillis();
        lastInferenceMs = 0;

        while (opModeIsActive()) {
            long loopStart = System.currentTimeMillis();

            sensorWrapper.update();
            LemonlightResult result = sensorWrapper.getLastResult();
            
            if (result != null && result.isValid()) {
                lastInferenceMs = result.getTimestampMs();
            }

            long loopElapsed = System.currentTimeMillis() - loopStart;
            long inferenceAge = lastInferenceMs > 0 ? System.currentTimeMillis() - lastInferenceMs : -1;

            telemetry.addLine("=== Lemonlight stream ===");
            telemetry.addData("Loop ms", loopElapsed);
            telemetry.addData("Inference age ms", inferenceAge >= 0 ? inferenceAge : "n/a");
            if (lemonlight.getLastError() != null) {
                telemetry.addData("Last error", lemonlight.getLastError());
            }
            if (result != null) {
                telemetry.addData("Detections", result.getDetectionCount());
                telemetry.addData("Top score %", result.getTopScorePercent());
                appendRawHex(result.getRaw(), RAW_DEBUG_BYTES);
                telemetry.addData("Raw (first " + RAW_DEBUG_BYTES + ")", hexBuilder.toString());
            }
            telemetry.update();

            long sleepMs = POLL_MS - (System.currentTimeMillis() - loopStart);
            if (sleepMs > 0) sleep(sleepMs);
        }
    }

    private void appendRawHex(byte[] raw, int maxBytes) {
        hexBuilder.setLength(0);
        if (raw == null) return;
        int n = Math.min(raw.length, maxBytes);
        for (int i = 0; i < n; i++) {
            hexBuilder.append(String.format("%02X ", raw[i] & 0xFF));
        }
        if (raw.length > maxBytes) hexBuilder.append("...");
    }
}
