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
        lemonlight = GroveVisionI2CHelper.bindLemonlight(hardwareMap, telemetry);
        if (lemonlight == null) {
            sleep(5000);
            return;
        }
        boolean initOk = lemonlight.ping();
        String fwVer = lemonlight.getFirmwareVersion();

        telemetry.addLine(initOk ? "Driver initialized ✓" : "Driver init failed ✗");
        telemetry.addData("Address", "0x%02X", LemonlightConstants.I2C_ADDRESS_7BIT);
        telemetry.addData("Firmware", fwVer != null && !fwVer.isEmpty() ? fwVer : "n/a");
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
                LemonlightResult result = lemonlight.readInference();
                if (result != null && result.isValid()) {
                    displayResponse(result.getRaw());
                    telemetry.addLine("Parsed: count=" + result.getDetectionCount() + " topScore=" + result.getTopScorePercent() + "%");
                    telemetry.addLine("Boxes=" + result.getDetections().size() +
                        " Classes=" + result.getClassifications().size() +
                        " Points=" + result.getKeypoints().size());
                    return true;
                }
            } catch (LemonlightException e) {
                telemetry.addData("Error Code", e.getShortCode());
                telemetry.addData("Message", e.getUserMessage());
                telemetry.addData("Recoverable", e.isRecoverable() ? "Yes" : "No");
            } catch (Exception e) {
                telemetry.addData("Error", e.getClass().getSimpleName());
                telemetry.addData("Message", e.getMessage());
            }
            if (attempt < MAX_RETRIES) {
                telemetry.addData("Retry", "Attempt " + (attempt + 1) + "/" + MAX_RETRIES);
                telemetry.update();
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
