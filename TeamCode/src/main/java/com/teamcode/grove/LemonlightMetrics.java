package com.teamcode.grove;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Metrics collector for Lemonlight driver performance and health.
 * Thread-safe, low-overhead metrics suitable for competition use.
 *
 * <p>Example usage:
 * <pre>{@code
 * LemonlightMetrics metrics = lemonlight.getMetrics();
 *
 * // Display in telemetry
 * telemetry.addData("Success Rate", "%.1f%%", metrics.getSuccessRate() * 100);
 * telemetry.addData("Avg Latency", "%.0fms", metrics.getAverageLatencyMs());
 * telemetry.addData("Detections/Read", "%.1f", metrics.getAvgDetectionsPerRead());
 *
 * // Check health
 * if (metrics.getSuccessRate() < 0.8) {
 *     telemetry.addLine("WARNING: Low success rate");
 * }
 * }</pre>
 */
public class LemonlightMetrics {
    private final AtomicLong totalReads;
    private final AtomicLong successfulReads;
    private final AtomicLong failedReads;
    private final AtomicLong totalDetections;
    private final AtomicLong totalLatencyMs;

    // Latency tracking (histogram)
    private final ConcurrentHashMap<LatencyBucket, AtomicLong> latencyHistogram;

    // Health indicators
    private final AtomicLong lastSuccessfulReadMs;
    private final AtomicReference<String> lastErrorMessage;

    // Counters for specific operations
    private final AtomicLong i2cWriteSuccess;
    private final AtomicLong i2cWriteFailure;
    private final AtomicLong i2cReadSuccess;
    private final AtomicLong i2cReadFailure;
    private final AtomicLong parseErrors;

    /**
     * Latency buckets for histogram tracking.
     */
    public enum LatencyBucket {
        UNDER_100MS(0, 100, "< 100ms"),
        _100_TO_200MS(100, 200, "100-200ms"),
        _200_TO_300MS(200, 300, "200-300ms"),
        _300_TO_500MS(300, 500, "300-500ms"),
        OVER_500MS(500, Integer.MAX_VALUE, "> 500ms");

        private final int min;
        private final int max;
        private final String label;

        LatencyBucket(int min, int max, String label) {
            this.min = min;
            this.max = max;
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        static LatencyBucket forLatency(long latencyMs) {
            for (LatencyBucket bucket : values()) {
                if (latencyMs >= bucket.min && latencyMs < bucket.max) {
                    return bucket;
                }
            }
            return OVER_500MS;
        }
    }

    /**
     * Creates a new metrics collector with all counters initialized to zero.
     */
    public LemonlightMetrics() {
        this.totalReads = new AtomicLong(0);
        this.successfulReads = new AtomicLong(0);
        this.failedReads = new AtomicLong(0);
        this.totalDetections = new AtomicLong(0);
        this.totalLatencyMs = new AtomicLong(0);
        this.lastSuccessfulReadMs = new AtomicLong(0);
        this.lastErrorMessage = new AtomicReference<>("");

        this.i2cWriteSuccess = new AtomicLong(0);
        this.i2cWriteFailure = new AtomicLong(0);
        this.i2cReadSuccess = new AtomicLong(0);
        this.i2cReadFailure = new AtomicLong(0);
        this.parseErrors = new AtomicLong(0);

        this.latencyHistogram = new ConcurrentHashMap<>();
        for (LatencyBucket bucket : LatencyBucket.values()) {
            latencyHistogram.put(bucket, new AtomicLong(0));
        }
    }

    /**
     * Records a read operation with latency and success status.
     *
     * @param latencyMs Latency in milliseconds
     * @param success true if read was successful
     */
    public void recordReadLatency(long latencyMs, boolean success) {
        totalReads.incrementAndGet();
        totalLatencyMs.addAndGet(latencyMs);

        if (success) {
            successfulReads.incrementAndGet();
            lastSuccessfulReadMs.set(System.currentTimeMillis());
        } else {
            failedReads.incrementAndGet();
        }

        LatencyBucket bucket = LatencyBucket.forLatency(latencyMs);
        latencyHistogram.get(bucket).incrementAndGet();
    }

    /**
     * Records the number of detections in a result.
     *
     * @param count Number of detections
     */
    public void recordDetectionCount(int count) {
        totalDetections.addAndGet(count);
    }

    /**
     * Records an error message.
     *
     * @param errorMessage Error message text
     */
    public void recordError(String errorMessage) {
        lastErrorMessage.set(errorMessage);
    }

    /**
     * Increments I2C write success counter.
     */
    public void incrementI2cWriteSuccess() {
        i2cWriteSuccess.incrementAndGet();
    }

    /**
     * Increments I2C write failure counter.
     */
    public void incrementI2cWriteFailure() {
        i2cWriteFailure.incrementAndGet();
    }

    /**
     * Increments I2C read success counter.
     */
    public void incrementI2cReadSuccess() {
        i2cReadSuccess.incrementAndGet();
    }

    /**
     * Increments I2C read failure counter.
     */
    public void incrementI2cReadFailure() {
        i2cReadFailure.incrementAndGet();
    }

    /**
     * Increments parse error counter.
     */
    public void incrementParseError() {
        parseErrors.incrementAndGet();
    }

    /**
     * Gets the success rate (0.0 to 1.0).
     *
     * @return Success rate as a fraction
     */
    public double getSuccessRate() {
        long total = totalReads.get();
        if (total == 0) return 1.0;
        return successfulReads.get() / (double) total;
    }

    /**
     * Gets the average latency in milliseconds.
     *
     * @return Average latency, or 0.0 if no reads
     */
    public double getAverageLatencyMs() {
        long reads = totalReads.get();
        if (reads == 0) return 0.0;
        return totalLatencyMs.get() / (double) reads;
    }

    /**
     * Gets the average detections per successful read.
     *
     * @return Average detections per read
     */
    public double getAvgDetectionsPerRead() {
        long reads = successfulReads.get();
        if (reads == 0) return 0.0;
        return totalDetections.get() / (double) reads;
    }

    /**
     * Gets time since last successful read in milliseconds.
     *
     * @return Time since last success, or -1 if never successful
     */
    public long getTimeSinceLastSuccessMs() {
        long last = lastSuccessfulReadMs.get();
        if (last == 0) return -1;
        return System.currentTimeMillis() - last;
    }

    /**
     * Gets the last error message.
     *
     * @return Last error message, or empty string if none
     */
    public String getLastErrorMessage() {
        return lastErrorMessage.get();
    }

    /**
     * Gets total number of reads attempted.
     *
     * @return Total reads
     */
    public long getTotalReads() {
        return totalReads.get();
    }

    /**
     * Gets number of successful reads.
     *
     * @return Successful reads
     */
    public long getSuccessfulReads() {
        return successfulReads.get();
    }

    /**
     * Gets number of failed reads.
     *
     * @return Failed reads
     */
    public long getFailedReads() {
        return failedReads.get();
    }

    /**
     * Gets total detections across all successful reads.
     *
     * @return Total detections
     */
    public long getTotalDetections() {
        return totalDetections.get();
    }

    /**
     * Gets count for a specific latency bucket.
     *
     * @param bucket Latency bucket
     * @return Count of reads in that bucket
     */
    public long getLatencyBucketCount(LatencyBucket bucket) {
        return latencyHistogram.get(bucket).get();
    }

    /**
     * Gets I2C write success count.
     *
     * @return Write success count
     */
    public long getI2cWriteSuccess() {
        return i2cWriteSuccess.get();
    }

    /**
     * Gets I2C write failure count.
     *
     * @return Write failure count
     */
    public long getI2cWriteFailure() {
        return i2cWriteFailure.get();
    }

    /**
     * Gets I2C read success count.
     *
     * @return Read success count
     */
    public long getI2cReadSuccess() {
        return i2cReadSuccess.get();
    }

    /**
     * Gets I2C read failure count.
     *
     * @return Read failure count
     */
    public long getI2cReadFailure() {
        return i2cReadFailure.get();
    }

    /**
     * Gets parse error count.
     *
     * @return Parse error count
     */
    public long getParseErrors() {
        return parseErrors.get();
    }

    /**
     * Checks if metrics indicate healthy operation.
     *
     * @return true if success rate > 80% and last success within 1 second
     */
    public boolean isHealthy() {
        long timeSinceSuccess = getTimeSinceLastSuccessMs();
        return getSuccessRate() > 0.8 &&
               (timeSinceSuccess < 1000 || timeSinceSuccess == -1);
    }

    /**
     * Exports metrics as formatted string for telemetry display.
     *
     * @return Formatted metrics string
     */
    public String toTelemetryString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Reads: %d (✓%d ✗%d)\n",
            totalReads.get(), successfulReads.get(), failedReads.get()));
        sb.append(String.format("Success Rate: %.1f%%\n", getSuccessRate() * 100));
        sb.append(String.format("Avg Latency: %.0fms\n", getAverageLatencyMs()));
        sb.append(String.format("Avg Detections: %.1f\n", getAvgDetectionsPerRead()));

        long timeSinceSuccess = getTimeSinceLastSuccessMs();
        if (timeSinceSuccess >= 0) {
            sb.append(String.format("Last Success: %dms ago\n", timeSinceSuccess));
        }

        String lastError = lastErrorMessage.get();
        if (lastError != null && !lastError.isEmpty()) {
            sb.append(String.format("Last Error: %s\n", lastError));
        }

        return sb.toString();
    }

    /**
     * Exports detailed metrics including latency histogram.
     *
     * @return Detailed metrics string
     */
    public String toDetailedString() {
        StringBuilder sb = new StringBuilder();
        sb.append(toTelemetryString());

        sb.append("\nLatency Distribution:\n");
        for (LatencyBucket bucket : LatencyBucket.values()) {
            long count = latencyHistogram.get(bucket).get();
            if (count > 0) {
                sb.append(String.format("  %s: %d\n", bucket.getLabel(), count));
            }
        }

        sb.append("\nI2C Operations:\n");
        sb.append(String.format("  Write: %d success, %d failed\n",
            i2cWriteSuccess.get(), i2cWriteFailure.get()));
        sb.append(String.format("  Read: %d success, %d failed\n",
            i2cReadSuccess.get(), i2cReadFailure.get()));
        sb.append(String.format("  Parse Errors: %d\n", parseErrors.get()));

        return sb.toString();
    }

    /**
     * Resets all metrics to zero.
     * Useful for starting fresh at beginning of match.
     */
    public void reset() {
        totalReads.set(0);
        successfulReads.set(0);
        failedReads.set(0);
        totalDetections.set(0);
        totalLatencyMs.set(0);
        lastSuccessfulReadMs.set(0);
        lastErrorMessage.set("");

        i2cWriteSuccess.set(0);
        i2cWriteFailure.set(0);
        i2cReadSuccess.set(0);
        i2cReadFailure.set(0);
        parseErrors.set(0);

        for (AtomicLong counter : latencyHistogram.values()) {
            counter.set(0);
        }
    }

    @Override
    public String toString() {
        return String.format("LemonlightMetrics{reads=%d, successRate=%.1f%%, avgLatency=%.0fms}",
            totalReads.get(), getSuccessRate() * 100, getAverageLatencyMs());
    }
}
