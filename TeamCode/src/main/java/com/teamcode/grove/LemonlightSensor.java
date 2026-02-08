package com.teamcode.grove;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe wrapper around Lemonlight driver for caching inference results.
 *
 * <p>This class provides thread-safe access to the most recent vision data,
 * allowing multiple threads to read cached results without blocking. Update
 * operations are also thread-safe.
 *
 * <p><b>Thread Safety:</b> All methods are thread-safe and non-blocking.
 * Multiple threads can safely call {@link #update()} and {@link #getLastResult()}
 * concurrently.
 *
 * <p>Example usage:
 * <pre>{@code
 * LemonlightSensor sensor = new LemonlightSensor(lemonlight);
 *
 * // Background thread updates sensor
 * Thread updateThread = new Thread(() -> {
 *     while (opModeIsActive()) {
 *         sensor.update();
 *         Thread.sleep(100);  // 10 Hz
 *     }
 * });
 * updateThread.start();
 *
 * // Main loop reads cached result
 * while (opModeIsActive()) {
 *     LemonlightResult result = sensor.getLastResult();
 *     if (result != null && result.isValid()) {
 *         // Use vision data...
 *     }
 * }
 * }</pre>
 */
public class LemonlightSensor {
    private final Lemonlight driver;
    private final AtomicReference<LemonlightResult> lastResult;
    private final AtomicLong lastUpdateMs;
    private final AtomicLong totalUpdates;
    private final AtomicLong failedUpdates;
    private final LemonlightLogger logger;

    /**
     * Creates a new LemonlightSensor wrapping the specified driver.
     *
     * @param driver Lemonlight driver instance to wrap
     * @throws IllegalArgumentException if driver is null
     */
    public LemonlightSensor(Lemonlight driver) {
        if (driver == null) {
            throw new IllegalArgumentException("Driver cannot be null");
        }

        this.driver = driver;
        this.lastResult = new AtomicReference<>(null);
        this.lastUpdateMs = new AtomicLong(0);
        this.totalUpdates = new AtomicLong(0);
        this.failedUpdates = new AtomicLong(0);
        this.logger = new LemonlightLogger(LemonlightSensor.class);
    }

    /**
     * Updates the cached result by reading new inference data from the device.
     *
     * <p>This method is thread-safe and can be called from multiple threads
     * concurrently, though typically it's called from a single background thread.
     *
     * <p>If the read fails, the cached result is not updated and the failure
     * is counted in statistics.
     *
     * @throws LemonlightException if a critical error occurs (propagated from driver)
     */
    public void update() {
        long startTime = System.currentTimeMillis();
        totalUpdates.incrementAndGet();

        try {
            LemonlightResult result = driver.readInference();
            long timestamp = System.currentTimeMillis();

            if (result != null && result.isValid()) {
                lastResult.set(result);
                lastUpdateMs.set(timestamp);

                if (logger.isDebugEnabled()) {
                    logger.debug("Sensor updated: detections={}, latency={}ms",
                        result.getDetectionCount(),
                        timestamp - startTime);
                }
            } else {
                failedUpdates.incrementAndGet();
                logger.warn("Sensor update returned invalid result");
            }

        } catch (LemonlightException e) {
            failedUpdates.incrementAndGet();
            logger.error("Sensor update failed: {}", e.getMessage());
            throw e;  // Propagate critical errors

        } catch (Exception e) {
            failedUpdates.incrementAndGet();
            logger.error("Unexpected error during sensor update", e);
            throw new LemonlightException(
                LemonlightException.ErrorCode.DEVICE_NOT_READY,
                LemonlightException.ErrorSeverity.ERROR,
                "Sensor update failed",
                e
            );
        }
    }

    /**
     * Gets the most recent cached result.
     *
     * <p>This method is non-blocking and thread-safe. Returns the result
     * from the most recent successful {@link #update()} call, or null if
     * no successful update has occurred yet.
     *
     * @return Latest cached result, or null if never updated
     */
    public LemonlightResult getLastResult() {
        return lastResult.get();
    }

    /**
     * Gets the timestamp of the last successful update.
     *
     * @return Timestamp in milliseconds, or 0 if never updated
     */
    public long getLastUpdateMs() {
        return lastUpdateMs.get();
    }

    /**
     * Gets the age of the cached result in milliseconds.
     *
     * @return Age in milliseconds, or -1 if never updated
     */
    public long getResultAgeMs() {
        long lastUpdate = lastUpdateMs.get();
        if (lastUpdate == 0) return -1;
        return System.currentTimeMillis() - lastUpdate;
    }

    /**
     * Gets the underlying Lemonlight driver.
     *
     * @return Driver instance
     */
    public Lemonlight getDriver() {
        return driver;
    }

    /**
     * Gets the total number of update attempts.
     *
     * @return Total update count
     */
    public long getTotalUpdates() {
        return totalUpdates.get();
    }

    /**
     * Gets the number of failed update attempts.
     *
     * @return Failed update count
     */
    public long getFailedUpdates() {
        return failedUpdates.get();
    }

    /**
     * Gets the success rate of updates.
     *
     * @return Success rate as a fraction (0.0 to 1.0)
     */
    public double getSuccessRate() {
        long total = totalUpdates.get();
        if (total == 0) return 1.0;
        long failed = failedUpdates.get();
        return (total - failed) / (double) total;
    }

    /**
     * Checks if the cached result is fresh (less than 1 second old).
     *
     * @return true if result is fresh, false if stale or never updated
     */
    public boolean isFresh() {
        long age = getResultAgeMs();
        return age >= 0 && age < 1000;
    }

    /**
     * Checks if the sensor is healthy (good success rate and fresh data).
     *
     * @return true if sensor is healthy
     */
    public boolean isHealthy() {
        return getSuccessRate() > 0.8 && isFresh();
    }

    /**
     * Gets health statistics for telemetry display.
     *
     * @return Health statistics object
     */
    public SensorHealth getHealth() {
        long total = totalUpdates.get();
        long failed = failedUpdates.get();
        double successRate = getSuccessRate();
        long ageMs = getResultAgeMs();
        boolean isHealthy = isHealthy();

        return new SensorHealth(total, failed, successRate, ageMs, isHealthy);
    }

    /**
     * Resets all statistics counters.
     * Does not clear the cached result.
     */
    public void resetStatistics() {
        totalUpdates.set(0);
        failedUpdates.set(0);
        logger.info("Sensor statistics reset");
    }

    /**
     * Clears the cached result and resets statistics.
     */
    public void reset() {
        lastResult.set(null);
        lastUpdateMs.set(0);
        resetStatistics();
        logger.info("Sensor fully reset");
    }

    /**
     * Health statistics container.
     */
    public static class SensorHealth {
        public final long totalUpdates;
        public final long failedUpdates;
        public final double successRate;
        public final long resultAgeMs;
        public final boolean isHealthy;

        SensorHealth(long total, long failed, double successRate, long ageMs, boolean healthy) {
            this.totalUpdates = total;
            this.failedUpdates = failed;
            this.successRate = successRate;
            this.resultAgeMs = ageMs;
            this.isHealthy = healthy;
        }

        /**
         * Gets a formatted string for telemetry display.
         *
         * @return Formatted health string
         */
        public String toTelemetryString() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Updates: %d (✓%d ✗%d)\n",
                totalUpdates, totalUpdates - failedUpdates, failedUpdates));
            sb.append(String.format("Success Rate: %.1f%%\n", successRate * 100));

            if (resultAgeMs >= 0) {
                sb.append(String.format("Result Age: %dms\n", resultAgeMs));
            } else {
                sb.append("Result Age: Never updated\n");
            }

            sb.append(String.format("Status: %s", isHealthy ? "✓ Healthy" : "✗ Unhealthy"));

            return sb.toString();
        }

        @Override
        public String toString() {
            return String.format("SensorHealth{updates=%d, success=%.1f%%, age=%dms, healthy=%s}",
                totalUpdates, successRate * 100, resultAgeMs, isHealthy);
        }
    }

    @Override
    public String toString() {
        return String.format("LemonlightSensor{updates=%d, success=%.1f%%, age=%dms}",
            totalUpdates.get(), getSuccessRate() * 100, getResultAgeMs());
    }
}
