package com.teamcode.grove;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.hardware.DcMotor;

/**
 * Example autonomous OpMode demonstrating Lemonlight vision-based robot control.
 *
 * This OpMode shows how to:
 * 1. Initialize and configure Lemonlight
 * 2. Read vision detection data (boxes, classes, keypoints)
 * 3. Use detection data for autonomous navigation
 * 4. Implement vision-guided robot behaviors
 *
 * Hardware Requirements:
 * - Lemonlight device configured as "lemonlight" in Robot Configuration
 * - Drive motors: leftDrive, rightDrive (optional - for movement demo)
 */
@Autonomous(name = "Lemonlight Autonomous Example", group = "Example")
public class Lemonlight_AutonomousExample extends LinearOpMode {

    // Vision constants
    private static final int IMAGE_WIDTH = 240;  // Grove Vision AI V2 default width
    private static final int IMAGE_CENTER_X = IMAGE_WIDTH / 2;
    private static final int CENTER_TOLERANCE = 20;  // pixels
    private static final int MIN_CONFIDENCE = 50;  // minimum detection confidence (0-100)

    // Motor control constants
    private static final double TURN_SPEED = 0.3;
    private static final double DRIVE_SPEED = 0.5;

    // Hardware
    private Lemonlight lemonlight;
    private LemonlightSensor sensor;
    private DcMotor leftDrive;
    private DcMotor rightDrive;
    private boolean motorsAvailable = false;

    @Override
    public void runOpMode() throws InterruptedException {
        // Initialize Lemonlight
        telemetry.addLine("Initializing Lemonlight...");
        telemetry.update();

        lemonlight = GroveVisionI2CHelper.bindLemonlight(hardwareMap, telemetry);
        if (lemonlight == null) {
            telemetry.addLine("ERROR: Failed to bind Lemonlight");
            telemetry.addLine("Check Robot Configuration!");
            telemetry.update();
            sleep(5000);
            return;
        }

        // Verify communication
        if (!lemonlight.ping()) {
            telemetry.addLine("ERROR: Lemonlight ping failed");
            telemetry.addData("Last Error", lemonlight.getLastError());
            telemetry.update();
            sleep(5000);
            return;
        }

        // Display device info
        String fwVersion = lemonlight.getFirmwareVersion();
        telemetry.addData("Firmware", fwVersion);
        telemetry.update();

        // Initialize drive motors (optional)
        try {
            leftDrive = hardwareMap.get(DcMotor.class, "leftDrive");
            rightDrive = hardwareMap.get(DcMotor.class, "rightDrive");
            leftDrive.setDirection(DcMotor.Direction.REVERSE);
            rightDrive.setDirection(DcMotor.Direction.FORWARD);
            motorsAvailable = true;
            telemetry.addLine("Drive motors initialized");
        } catch (Exception e) {
            telemetry.addLine("Drive motors not configured (demo mode)");
            motorsAvailable = false;
        }

        // Create sensor wrapper
        sensor = new LemonlightSensor(lemonlight);

        telemetry.addLine();
        telemetry.addLine("Lemonlight ready!");
        telemetry.addLine("Press START to begin autonomous");
        telemetry.update();

        waitForStart();

        // Main autonomous loop
        while (opModeIsActive()) {
            try {
                // Update vision data (may throw LemonlightException)
                sensor.update();
                LemonlightResult result = sensor.getLastResult();

                if (result != null && result.isValid()) {
                    processVisionData(result);
                } else {
                    // No valid detection - search behavior
                    telemetry.addLine("No valid detections");
                    if (motorsAvailable) {
                        rotateInPlace(TURN_SPEED);
                    }
                }

            } catch (LemonlightException e) {
                // Handle vision errors gracefully
                telemetry.addLine("=== Vision Error ===");
                telemetry.addData("Error", e.getShortCode());
                telemetry.addData("Message", e.getUserMessage());

                // Stop motors on error
                if (motorsAvailable) {
                    stopMotors();
                }

                // If error is fatal, exit
                if (!e.isRecoverable()) {
                    telemetry.addLine("FATAL ERROR - Stopping");
                    telemetry.update();
                    break;
                }
            }

            telemetry.update();
            sleep(50);  // Update at ~20 Hz
        }

        // Stop motors when done
        if (motorsAvailable) {
            stopMotors();
        }
    }

    /**
     * Process vision detection data and control robot accordingly
     */
    private void processVisionData(LemonlightResult result) {
        telemetry.addLine("=== Vision Data ===");
        telemetry.addData("Total Detections", result.getDetectionCount());

        // Process bounding boxes (object detection)
        if (!result.getDetections().isEmpty()) {
            processDetections(result);
        }

        // Process classifications
        if (!result.getClassifications().isEmpty()) {
            processClassifications(result);
        }

        // Process keypoints
        if (!result.getKeypoints().isEmpty()) {
            processKeypoints(result);
        }
    }

    /**
     * Process bounding box detections and navigate towards target
     */
    private void processDetections(LemonlightResult result) {
        // Find highest confidence detection
        LemonlightResult.Detection bestDetection = null;
        int highestScore = MIN_CONFIDENCE;

        for (LemonlightResult.Detection det : result.getDetections()) {
            if (det.score > highestScore) {
                highestScore = det.score;
                bestDetection = det;
            }
        }

        if (bestDetection != null) {
            telemetry.addLine("--- Best Detection ---");
            telemetry.addData("Position", "x=%d y=%d", bestDetection.x, bestDetection.y);
            telemetry.addData("Size", "w=%d h=%d", bestDetection.w, bestDetection.h);
            telemetry.addData("Confidence", "%d%%", bestDetection.score);
            telemetry.addData("Target ID", bestDetection.targetId);

            // Calculate center of detected object
            int detectionCenterX = bestDetection.x + bestDetection.w / 2;
            int offsetFromCenter = detectionCenterX - IMAGE_CENTER_X;

            telemetry.addData("Center Offset", "%d px", offsetFromCenter);

            if (motorsAvailable) {
                // Navigate based on detection position
                if (Math.abs(offsetFromCenter) < CENTER_TOLERANCE) {
                    // Object is centered - drive forward
                    telemetry.addLine("ACTION: Centered - Moving forward");
                    driveForward(DRIVE_SPEED);
                } else if (offsetFromCenter > 0) {
                    // Object is to the right - turn right
                    telemetry.addLine("ACTION: Turning right");
                    turnRight(TURN_SPEED);
                } else {
                    // Object is to the left - turn left
                    telemetry.addLine("ACTION: Turning left");
                    turnLeft(TURN_SPEED);
                }
            }
        } else {
            telemetry.addData("Boxes", "%d (all below confidence threshold)",
                result.getDetections().size());
            if (motorsAvailable) {
                stopMotors();
            }
        }
    }

    /**
     * Process classification results
     */
    private void processClassifications(LemonlightResult result) {
        telemetry.addLine("--- Classifications ---");
        for (LemonlightResult.Classification cls : result.getClassifications()) {
            telemetry.addData("Class", "ID=%d Score=%d%%", cls.targetId, cls.score);
        }
    }

    /**
     * Process keypoint detections
     */
    private void processKeypoints(LemonlightResult result) {
        telemetry.addLine("--- Keypoints ---");
        telemetry.addData("Count", result.getKeypoints().size());

        // Example: Find center of all keypoints
        if (!result.getKeypoints().isEmpty()) {
            int avgX = 0, avgY = 0;
            for (LemonlightResult.KeyPoint kp : result.getKeypoints()) {
                avgX += kp.x;
                avgY += kp.y;
            }
            avgX /= result.getKeypoints().size();
            avgY /= result.getKeypoints().size();
            telemetry.addData("Keypoint Center", "x=%d y=%d", avgX, avgY);
        }
    }

    // Motor control helper methods
    private void driveForward(double speed) {
        if (!motorsAvailable) return;
        leftDrive.setPower(speed);
        rightDrive.setPower(speed);
    }

    private void turnLeft(double speed) {
        if (!motorsAvailable) return;
        leftDrive.setPower(-speed);
        rightDrive.setPower(speed);
    }

    private void turnRight(double speed) {
        if (!motorsAvailable) return;
        leftDrive.setPower(speed);
        rightDrive.setPower(-speed);
    }

    private void rotateInPlace(double speed) {
        if (!motorsAvailable) return;
        leftDrive.setPower(-speed * 0.5);
        rightDrive.setPower(speed * 0.5);
    }

    private void stopMotors() {
        if (!motorsAvailable) return;
        leftDrive.setPower(0);
        rightDrive.setPower(0);
    }
}
