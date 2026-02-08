package com.teamcode.grove;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

/**
 * Example OpMode demonstrating LemonlightResult fluent query API.
 *
 * <p>Shows how to:
 * <ul>
 *   <li>Filter detections by confidence, target ID, and location</li>
 *   <li>Find best, largest, or closest detections</li>
 *   <li>Query classifications and keypoints</li>
 *   <li>Chain multiple filter criteria</li>
 *   <li>Handle empty results gracefully</li>
 * </ul>
 *
 * <p>This OpMode demonstrates practical use cases for vision-based
 * autonomous navigation and object tracking.
 */
@TeleOp(name = "Lemonlight Query Example", group = "Example")
public class Lemonlight_QueryExample extends LinearOpMode {

    private Lemonlight lemonlight;

    @Override
    public void runOpMode() throws InterruptedException {
        telemetry.addLine("=== Lemonlight Query API Example ===");
        telemetry.addLine();
        telemetry.addLine("Demonstrates fluent query API for filtering");
        telemetry.addLine("vision results by confidence, location, and type.");
        telemetry.update();

        // Initialize device
        lemonlight = GroveVisionI2CHelper.bindLemonlight(hardwareMap, telemetry);
        if (lemonlight == null) {
            telemetry.addLine("ERROR: Failed to bind Lemonlight");
            telemetry.update();
            sleep(5000);
            return;
        }

        telemetry.addLine();
        telemetry.addLine("Device initialized. Press START to begin.");
        telemetry.update();

        waitForStart();

        // Main loop - demonstrate different query patterns
        while (opModeIsActive()) {
            try {
                LemonlightResult result = lemonlight.readInference();

                if (result == null || !result.isValid()) {
                    telemetry.addLine("No valid result available");
                    telemetry.update();
                    sleep(500);
                    continue;
                }

                telemetry.clear();
                telemetry.addLine("=== Query API Examples ===");
                telemetry.addLine();

                // Example 1: Find best high-confidence detection
                demonstrateBestDetection(result);

                // Example 2: Filter by location
                demonstrateLocationFiltering(result);

                // Example 3: Find largest object
                demonstrateLargestObject(result);

                // Example 4: Find closest object to center
                demonstrateClosestObject(result);

                // Example 5: Count objects by confidence
                demonstrateConfidenceCounting(result);

                // Example 6: Query classifications
                demonstrateClassificationQuery(result);

                // Example 7: Query keypoints
                demonstrateKeypointQuery(result);

                telemetry.update();

            } catch (LemonlightException e) {
                telemetry.addLine("Error: " + e.getUserMessage());
                telemetry.update();
            }

            sleep(500);
        }
    }

    /**
     * Example 1: Find best high-confidence detection of specific target.
     */
    private void demonstrateBestDetection(LemonlightResult result) {
        telemetry.addLine("1. Best High-Confidence Detection:");

        try {
            // Find best detection of target 0 with at least 80% confidence
            LemonlightResult.Detection best = result.query()
                .targetId(0)
                .minConfidence(80)
                .bestOrThrow();

            telemetry.addData("  Found", "Target %d at (%d, %d)", best.targetId, best.x, best.y);
            telemetry.addData("  Confidence", "%d%%", best.score);
            telemetry.addData("  Size", "%dx%d", best.w, best.h);

        } catch (IllegalStateException e) {
            telemetry.addData("  Result", "No high-confidence target 0 found");
        }

        telemetry.addLine();
    }

    /**
     * Example 2: Filter detections by location (left/right half of image).
     */
    private void demonstrateLocationFiltering(LemonlightResult result) {
        telemetry.addLine("2. Location Filtering (Image Halves):");

        // Assuming 640x480 image
        int imageWidth = 640;
        int imageHeight = 480;

        // Count objects in left half
        int leftCount = result.query()
            .withinBounds(0, 0, imageWidth / 2, imageHeight)
            .minConfidence(50)
            .count();

        // Count objects in right half
        int rightCount = result.query()
            .withinBounds(imageWidth / 2, 0, imageWidth / 2, imageHeight)
            .minConfidence(50)
            .count();

        telemetry.addData("  Left Half", "%d objects", leftCount);
        telemetry.addData("  Right Half", "%d objects", rightCount);

        if (leftCount > rightCount) {
            telemetry.addData("  Recommendation", "Turn right");
        } else if (rightCount > leftCount) {
            telemetry.addData("  Recommendation", "Turn left");
        } else {
            telemetry.addData("  Recommendation", "Go straight");
        }

        telemetry.addLine();
    }

    /**
     * Example 3: Find largest object (useful for tracking closest object).
     */
    private void demonstrateLargestObject(LemonlightResult result) {
        telemetry.addLine("3. Largest Object:");

        result.query()
            .minConfidence(60)
            .largest()
            .ifPresentOrElse(
                largest -> {
                    int area = largest.w * largest.h;
                    telemetry.addData("  Found", "Target %d (area: %d px²)", largest.targetId, area);
                    telemetry.addData("  Size", "%dx%d", largest.w, largest.h);
                    telemetry.addData("  Confidence", "%d%%", largest.score);
                },
                () -> telemetry.addData("  Result", "No objects above 60% confidence")
            );

        telemetry.addLine();
    }

    /**
     * Example 4: Find object closest to center (for centering robot).
     */
    private void demonstrateClosestObject(LemonlightResult result) {
        telemetry.addLine("4. Closest to Center:");

        int centerX = 320;  // Image center
        int centerY = 240;

        result.query()
            .targetId(0)
            .minConfidence(70)
            .closestTo(centerX, centerY)
            .ifPresentOrElse(
                closest -> {
                    int objCenterX = closest.x + closest.w / 2;
                    int objCenterY = closest.y + closest.h / 2;
                    int offsetX = objCenterX - centerX;
                    int offsetY = objCenterY - centerY;

                    telemetry.addData("  Object Center", "(%d, %d)", objCenterX, objCenterY);
                    telemetry.addData("  Offset from Center", "X: %d, Y: %d", offsetX, offsetY);

                    // Provide steering recommendation
                    if (Math.abs(offsetX) < 20) {
                        telemetry.addData("  Alignment", "✓ Centered");
                    } else if (offsetX > 0) {
                        telemetry.addData("  Alignment", "← Turn left %d px", offsetX);
                    } else {
                        telemetry.addData("  Alignment", "→ Turn right %d px", -offsetX);
                    }
                },
                () -> telemetry.addData("  Result", "No target 0 found")
            );

        telemetry.addLine();
    }

    /**
     * Example 5: Count objects by confidence ranges.
     */
    private void demonstrateConfidenceCounting(LemonlightResult result) {
        telemetry.addLine("5. Confidence Distribution:");

        int highConf = result.query().minConfidence(80).count();
        int medConf = result.query().confidenceRange(50, 79).count();
        int lowConf = result.query().maxConfidence(49).count();

        telemetry.addData("  High (80-100%%)", "%d objects", highConf);
        telemetry.addData("  Medium (50-79%%)", "%d objects", medConf);
        telemetry.addData("  Low (0-49%%)", "%d objects", lowConf);

        telemetry.addLine();
    }

    /**
     * Example 6: Query classifications.
     */
    private void demonstrateClassificationQuery(LemonlightResult result) {
        telemetry.addLine("6. Classification Query:");

        // Find best classification
        result.queryClassifications()
            .minConfidence(70)
            .best()
            .ifPresentOrElse(
                best -> {
                    telemetry.addData("  Best Class", "ID: %d", best.targetId);
                    telemetry.addData("  Confidence", "%d%%", best.score);
                },
                () -> telemetry.addData("  Result", "No classifications > 70%")
            );

        // Count high-confidence classifications
        int count = result.queryClassifications()
            .minConfidence(80)
            .count();

        telemetry.addData("  High Confidence", "%d classifications", count);
        telemetry.addLine();
    }

    /**
     * Example 7: Query keypoints by location.
     */
    private void demonstrateKeypointQuery(LemonlightResult result) {
        telemetry.addLine("7. Keypoint Query:");

        // Find keypoints in top-left quadrant
        int count = result.queryKeypoints()
            .withinBounds(0, 0, 320, 240)
            .minConfidence(60)
            .count();

        telemetry.addData("  Top-Left Quadrant", "%d keypoints", count);

        // Find best keypoint overall
        result.queryKeypoints()
            .minConfidence(70)
            .best()
            .ifPresentOrElse(
                best -> {
                    telemetry.addData("  Best Keypoint", "(%d, %d) - %d%%",
                        best.x, best.y, best.score);
                },
                () -> telemetry.addData("  Best Keypoint", "None found")
            );

        telemetry.addLine();
    }
}
