package com.teamcode.grove;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

/**
 * One-shot test for Lemonlight (Grove Vision AI V2): binds driver, shows init status, performs one read and displays parsed + raw hex.
 */

@TeleOp(name = "GroveVisionI2C_OneShotTest", group = "Test")
public class GroveVisionI2C_OneShotTest extends LinearOpMode {

    private static final String DEVICE_NAME = LemonlightConstants.CONFIG_DEVICE_NAME;
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 150;
    private static final int WRITE_TO_READ_DELAY_MS = 50;

    private Lemonlight lemonlight;

    @Override
    public void runOpMode() throws InterruptedException {
        telemetry.addLine("Binding I2C device '" + DEVICE_NAME + "'...");
        telemetry.update();

        try {
            lemonlight = hardwareMap.get(Lemonlight.class, DEVICE_NAME);
        } catch (Exception e) {
            lemonlight = null;
        }

        if (lemonlight == null) {
            telemetry.addLine("ACTIVE Robot Configuration does not include an I2C device named 'lemonlight'.");
            telemetry.addData("Error", "Add an I2C device, type 'Lemonlight (Grove Vision AI V2)', name: " + DEVICE_NAME);
            telemetry.update();
            sleep(5000);
            return;
        }

        lemonlight.initialize();
        boolean initOk = lemonlight.ping();

        telemetry.addLine(initOk ? "Driver initialized" : "Driver init failed");
        telemetry.addData("Address", "0x%02X", LemonlightConstants.I2C_ADDRESS_7BIT);
        if (lemonlight.getLastError() != null) {
            telemetry.addData("Last error", lemonlight.getLastError());
        }
        telemetry.update();
        sleep(1000);

        telemetry.addLine();
        telemetry.addLine("Ready. Press START to perform one read.");
        telemetry.update();

        waitForStart();

        telemetry.clear();
        telemetry.addLine("=== One-shot read ===");
        telemetry.update();

        boolean success = performOneRead();

        telemetry.addLine();
        telemetry.addLine(success ? "Read completed" : "Read failed after retries");
        telemetry.update();

        while (opModeIsActive()) {
            sleep(100);
        }
    }

    private boolean performOneRead() {
        for (int attempt = 1; attempt <= MAX_RETRIES && opModeIsActive(); attempt++) {
            try {
                sleep(WRITE_TO_READ_DELAY_MS);
                byte[] raw = lemonlight.readFrameRaw(LemonlightConstants.MAX_READ_LEN);
                if (raw != null && raw.length > 0) {
                    displayResponse(raw);
                    LemonlightResult result = lemonlight.readInference();
                    if (result != null) {
                        telemetry.addLine("Parsed: count=" + result.getDetectionCount() + " topScore=" + result.getTopScorePercent() + "%");
                    }
                    return true;
                }
            } catch (Exception e) {
                if (lemonlight.getLastError() != null) {
                    telemetry.addData("Error", lemonlight.getLastError());
                }
            }
            if (attempt < MAX_RETRIES) {
                sleep(RETRY_DELAY_MS);
            }
        }
        return false;
    }

    private void displayResponse(byte[] data) {
        telemetry.addLine("=== Raw (hex) ===");
        StringBuilder hex = new StringBuilder();
        int show = Math.min(data.length, 64);
        for (int i = 0; i < show; i++) {
            hex.append(String.format("%02X ", data[i] & 0xFF));
        }
        if (data.length > show) hex.append("...");
        telemetry.addData("Hex", hex.toString().trim());
    }
}
