package com.teamcode.grove;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

/**
 * Example OpMode demonstrating LemonlightConfig usage.
 *
 * <p>Shows how to:
 * <ul>
 *   <li>Use preset configurations (default, fast, reliable, minimal)</li>
 *   <li>Build custom configurations with fluent API</li>
 *   <li>Apply configurations to Lemonlight driver</li>
 *   <li>Display configuration details in telemetry</li>
 * </ul>
 *
 * <p>This OpMode compares different configurations and shows their impact
 * on driver behavior and performance.
 */
@TeleOp(name = "Lemonlight Config Example", group = "Example")
public class Lemonlight_ConfigExample extends LinearOpMode {

    private Lemonlight lemonlight;

    @Override
    public void runOpMode() throws InterruptedException {
        telemetry.addLine("=== Lemonlight Configuration Example ===");
        telemetry.addLine();
        telemetry.addLine("This example shows different configuration options:");
        telemetry.addLine("1. DEFAULT  - Balanced settings for most use cases");
        telemetry.addLine("2. FAST     - Aggressive retries, shorter timeouts");
        telemetry.addLine("3. RELIABLE - Conservative timeouts, more retries");
        telemetry.addLine("4. MINIMAL  - No retries, no circuit breaker");
        telemetry.addLine("5. CUSTOM   - Build your own configuration");
        telemetry.update();

        sleep(3000);

        // Example 1: Default configuration
        telemetry.clear();
        telemetry.addLine("=== Configuration Examples ===");
        telemetry.addLine();
        demonstrateDefaultConfig();
        telemetry.update();
        sleep(2000);

        // Example 2: Fast configuration
        demonstrateFastConfig();
        telemetry.update();
        sleep(2000);

        // Example 3: Reliable configuration
        demonstrateReliableConfig();
        telemetry.update();
        sleep(2000);

        // Example 4: Minimal configuration
        demonstrateMinimalConfig();
        telemetry.update();
        sleep(2000);

        // Example 5: Custom configuration
        demonstrateCustomConfig();
        telemetry.update();
        sleep(2000);

        // Initialize with DEFAULT configuration for actual use
        telemetry.clear();
        telemetry.addLine("Initializing with DEFAULT configuration...");
        telemetry.update();

        lemonlight = GroveVisionI2CHelper.bindLemonlight(hardwareMap, telemetry);
        if (lemonlight == null) {
            telemetry.addLine("ERROR: Failed to bind Lemonlight");
            telemetry.update();
            sleep(5000);
            return;
        }

        // Display active configuration
        telemetry.clear();
        telemetry.addLine("=== Active Configuration ===");
        telemetry.addLine(LemonlightConfig.defaultConfig().toString());
        telemetry.addLine();
        telemetry.addLine("Press START to test configuration");
        telemetry.update();

        waitForStart();

        int successCount = 0;
        int errorCount = 0;
        long totalLatency = 0;

        // Main loop - test the configuration
        while (opModeIsActive()) {
            long loopStart = System.currentTimeMillis();

            try {
                LemonlightResult result = lemonlight.readInference();
                long latency = System.currentTimeMillis() - loopStart;

                if (result != null && result.isValid()) {
                    successCount++;
                    totalLatency += latency;
                    displaySuccess(result, latency);
                } else {
                    errorCount++;
                    displayError(latency);
                }

            } catch (LemonlightException e) {
                errorCount++;
                displayException(e);
            }

            // Display statistics
            displayStatistics(successCount, errorCount, totalLatency, successCount);

            telemetry.update();
            sleep(500);
        }
    }

    private void demonstrateDefaultConfig() {
        LemonlightConfig config = LemonlightConfig.defaultConfig();

        telemetry.addLine("1. DEFAULT Configuration:");
        telemetry.addData("  Read Timeout", "%dms", config.getReadTimeoutMs());
        telemetry.addData("  Invoke Timeout", "%dms", config.getInvokeTimeoutMs());
        telemetry.addData("  Max Retries", config.getMaxRetries());
        telemetry.addData("  Initial Retry Delay", "%dms", config.getInitialRetryDelayMs());
        telemetry.addData("  Circuit Breaker", config.isCircuitBreakerEnabled() ? "Enabled" : "Disabled");
        telemetry.addData("  CB Threshold", config.getCircuitBreakerThreshold());
        telemetry.addData("  CB Cooldown", "%dms", config.getCircuitBreakerCooldownMs());
        telemetry.addLine();
    }

    private void demonstrateFastConfig() {
        LemonlightConfig config = LemonlightConfig.fastConfig();

        telemetry.addLine("2. FAST Configuration:");
        telemetry.addData("  Read Timeout", "%dms (shorter)", config.getReadTimeoutMs());
        telemetry.addData("  Invoke Timeout", "%dms (shorter)", config.getInvokeTimeoutMs());
        telemetry.addData("  Max Retries", "%d (more)", config.getMaxRetries());
        telemetry.addData("  Initial Retry Delay", "%dms (faster)", config.getInitialRetryDelayMs());
        telemetry.addData("  Retry Backoff", "%.1fx (gentler)", config.getRetryBackoffMultiplier());
        telemetry.addData("  CB Threshold", "%d (lower)", config.getCircuitBreakerThreshold());
        telemetry.addData("  CB Cooldown", "%dms (shorter)", config.getCircuitBreakerCooldownMs());
        telemetry.addLine();
    }

    private void demonstrateReliableConfig() {
        LemonlightConfig config = LemonlightConfig.reliableConfig();

        telemetry.addLine("3. RELIABLE Configuration:");
        telemetry.addData("  Read Timeout", "%dms (longer)", config.getReadTimeoutMs());
        telemetry.addData("  Invoke Timeout", "%dms (longer)", config.getInvokeTimeoutMs());
        telemetry.addData("  Max Retries", "%d (more)", config.getMaxRetries());
        telemetry.addData("  Max Retry Delay", "%dms (longer)", config.getMaxRetryDelayMs());
        telemetry.addData("  Retry Backoff", "%.1fx (exponential)", config.getRetryBackoffMultiplier());
        telemetry.addData("  CB Threshold", "%d (higher)", config.getCircuitBreakerThreshold());
        telemetry.addData("  CB Cooldown", "%dms (longer)", config.getCircuitBreakerCooldownMs());
        telemetry.addLine();
    }

    private void demonstrateMinimalConfig() {
        LemonlightConfig config = LemonlightConfig.minimalConfig();

        telemetry.addLine("4. MINIMAL Configuration:");
        telemetry.addData("  Max Retries", "%d (none)", config.getMaxRetries());
        telemetry.addData("  Circuit Breaker", config.isCircuitBreakerEnabled() ? "Enabled" : "Disabled");
        telemetry.addData("  Metrics", config.isMetricsEnabled() ? "Enabled" : "Disabled");
        telemetry.addData("  Logging", config.isLoggingEnabled() ? "Enabled" : "Disabled");
        telemetry.addLine("  (Use for testing or custom resilience)");
        telemetry.addLine();
    }

    private void demonstrateCustomConfig() {
        // Build a custom configuration with specific requirements
        LemonlightConfig config = LemonlightConfig.builder()
            .readTimeout(4000)              // Longer timeout for slow I2C bus
            .invokeTimeout(5000)            // Allow more time for inference
            .maxRetries(7)                  // Very aggressive retries
            .initialRetryDelay(30)          // Start with short delay
            .maxRetryDelay(2000)            // Cap at 2 seconds
            .retryBackoff(2.5)              // Moderate backoff
            .circuitBreaker(true, 4, 2500)  // Open after 4 failures, 2.5s cooldown
            .metrics(true)                  // Enable metrics
            .logging(true)                  // Enable logging
            .maxImageSize(1280, 720)        // Custom image dimensions
            .build();

        telemetry.addLine("5. CUSTOM Configuration (Builder Pattern):");
        telemetry.addData("  Read Timeout", "%dms", config.getReadTimeoutMs());
        telemetry.addData("  Invoke Timeout", "%dms", config.getInvokeTimeoutMs());
        telemetry.addData("  Max Retries", config.getMaxRetries());
        telemetry.addData("  Retry Delays", "%d-%dms",
            config.getInitialRetryDelayMs(), config.getMaxRetryDelayMs());
        telemetry.addData("  CB Settings", "%d failures / %dms cooldown",
            config.getCircuitBreakerThreshold(), config.getCircuitBreakerCooldownMs());
        telemetry.addData("  Max Image Size", "%dx%d",
            config.getMaxImageWidth(), config.getMaxImageHeight());
        telemetry.addLine();
        telemetry.addLine("Code:");
        telemetry.addLine("  LemonlightConfig.builder()");
        telemetry.addLine("    .readTimeout(4000)");
        telemetry.addLine("    .maxRetries(7)");
        telemetry.addLine("    .circuitBreaker(true, 4, 2500)");
        telemetry.addLine("    .build()");
        telemetry.addLine();
    }

    private void displaySuccess(LemonlightResult result, long latency) {
        telemetry.addLine("=== ✓ SUCCESS ===");
        telemetry.addData("Latency", "%dms", latency);
        telemetry.addData("Detections", result.getDetectionCount());
        telemetry.addData("Boxes", result.getDetections().size());
        telemetry.addData("Classes", result.getClassifications().size());
        telemetry.addData("Keypoints", result.getKeypoints().size());
        telemetry.addData("Top Score", "%d%%", result.getTopScorePercent());
    }

    private void displayError(long latency) {
        telemetry.addLine("=== ✗ ERROR ===");
        telemetry.addData("Latency", "%dms", latency);
        telemetry.addLine("Invalid result after retries");
    }

    private void displayException(LemonlightException e) {
        telemetry.addLine("=== ✗ EXCEPTION ===");
        telemetry.addData("Error Code", e.getShortCode());
        telemetry.addData("Message", e.getUserMessage());
        telemetry.addData("Severity", e.getSeverity().toString());
        telemetry.addData("Recoverable", e.isRecoverable() ? "Yes" : "No");
    }

    private void displayStatistics(int success, int errors, long totalLatency, int successCount) {
        telemetry.addLine();
        telemetry.addLine("=== Statistics ===");
        telemetry.addData("Successful Reads", success);
        telemetry.addData("Failed Reads", errors);

        if (success + errors > 0) {
            double successRate = success / (double) (success + errors) * 100;
            telemetry.addData("Success Rate", "%.1f%%", successRate);
        }

        if (successCount > 0) {
            double avgLatency = totalLatency / (double) successCount;
            telemetry.addData("Avg Latency", "%.0fms", avgLatency);
        }

        // Display metrics if available
        if (lemonlight != null) {
            LemonlightMetrics metrics = lemonlight.getMetrics();
            telemetry.addLine();
            telemetry.addData("Total Reads", metrics.getTotalReads());
            telemetry.addData("Metrics Success Rate", "%.1f%%", metrics.getSuccessRate() * 100);
        }
    }
}
