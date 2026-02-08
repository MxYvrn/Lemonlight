package com.teamcode.grove;

/**
 * Configuration settings for Lemonlight driver.
 *
 * <p>Provides flexible configuration of timeouts, retry behavior, circuit breaker settings,
 * and performance parameters. Use the builder pattern for fluent configuration.
 *
 * <p>Example usage:
 * <pre>{@code
 * LemonlightConfig config = LemonlightConfig.builder()
 *     .readTimeout(5000)
 *     .maxRetries(5)
 *     .circuitBreaker(true, 3, 2000)
 *     .build();
 *
 * Lemonlight lemonlight = new Lemonlight(deviceClient, true, config);
 * }</pre>
 */
public class LemonlightConfig {

    // I2C settings
    private final int i2cAddress;
    private final int maxFrameLength;
    private final int maxReadLength;

    // Timeout settings
    private final long readTimeoutMs;
    private final long invokeTimeoutMs;
    private final int availPollMs;

    // Retry settings
    private final int maxRetries;
    private final long initialRetryDelayMs;
    private final long maxRetryDelayMs;
    private final double retryBackoffMultiplier;

    // Circuit breaker settings
    private final boolean circuitBreakerEnabled;
    private final int circuitBreakerThreshold;
    private final long circuitBreakerCooldownMs;

    // Performance settings
    private final boolean metricsEnabled;
    private final boolean loggingEnabled;

    // Validation settings
    private final int maxImageWidth;
    private final int maxImageHeight;
    private final int minConfidenceScore;
    private final int maxConfidenceScore;

    /**
     * Private constructor - use builder() to create instances.
     */
    private LemonlightConfig(Builder builder) {
        this.i2cAddress = builder.i2cAddress;
        this.maxFrameLength = builder.maxFrameLength;
        this.maxReadLength = builder.maxReadLength;
        this.readTimeoutMs = builder.readTimeoutMs;
        this.invokeTimeoutMs = builder.invokeTimeoutMs;
        this.availPollMs = builder.availPollMs;
        this.maxRetries = builder.maxRetries;
        this.initialRetryDelayMs = builder.initialRetryDelayMs;
        this.maxRetryDelayMs = builder.maxRetryDelayMs;
        this.retryBackoffMultiplier = builder.retryBackoffMultiplier;
        this.circuitBreakerEnabled = builder.circuitBreakerEnabled;
        this.circuitBreakerThreshold = builder.circuitBreakerThreshold;
        this.circuitBreakerCooldownMs = builder.circuitBreakerCooldownMs;
        this.metricsEnabled = builder.metricsEnabled;
        this.loggingEnabled = builder.loggingEnabled;
        this.maxImageWidth = builder.maxImageWidth;
        this.maxImageHeight = builder.maxImageHeight;
        this.minConfidenceScore = builder.minConfidenceScore;
        this.maxConfidenceScore = builder.maxConfidenceScore;
    }

    /**
     * Creates a builder for fluent configuration.
     *
     * @return New builder with default values
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates default configuration suitable for most use cases.
     *
     * @return Default configuration
     */
    public static LemonlightConfig defaultConfig() {
        return new Builder().build();
    }

    /**
     * Creates configuration optimized for fast response times.
     * Uses aggressive retries and shorter timeouts.
     *
     * @return Performance-optimized configuration
     */
    public static LemonlightConfig fastConfig() {
        return new Builder()
            .readTimeout(1000)
            .invokeTimeout(2000)
            .maxRetries(5)
            .initialRetryDelay(25)
            .retryBackoff(1.5)
            .circuitBreaker(true, 3, 1500)
            .build();
    }

    /**
     * Creates configuration optimized for reliability.
     * Uses conservative timeouts and multiple retries.
     *
     * @return Reliability-optimized configuration
     */
    public static LemonlightConfig reliableConfig() {
        return new Builder()
            .readTimeout(5000)
            .invokeTimeout(6000)
            .maxRetries(5)
            .initialRetryDelay(100)
            .maxRetryDelay(3000)
            .retryBackoff(3.0)
            .circuitBreaker(true, 8, 5000)
            .build();
    }

    /**
     * Creates minimal configuration with no retries or circuit breaker.
     * Useful for testing or when implementing custom resilience.
     *
     * @return Minimal configuration
     */
    public static LemonlightConfig minimalConfig() {
        return new Builder()
            .maxRetries(0)
            .circuitBreaker(false, 0, 0)
            .metrics(false)
            .logging(false)
            .build();
    }

    // Getters

    public int getI2cAddress() {
        return i2cAddress;
    }

    public int getMaxFrameLength() {
        return maxFrameLength;
    }

    public int getMaxReadLength() {
        return maxReadLength;
    }

    public long getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public long getInvokeTimeoutMs() {
        return invokeTimeoutMs;
    }

    public int getAvailPollMs() {
        return availPollMs;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public long getInitialRetryDelayMs() {
        return initialRetryDelayMs;
    }

    public long getMaxRetryDelayMs() {
        return maxRetryDelayMs;
    }

    public double getRetryBackoffMultiplier() {
        return retryBackoffMultiplier;
    }

    public boolean isCircuitBreakerEnabled() {
        return circuitBreakerEnabled;
    }

    public int getCircuitBreakerThreshold() {
        return circuitBreakerThreshold;
    }

    public long getCircuitBreakerCooldownMs() {
        return circuitBreakerCooldownMs;
    }

    public boolean isMetricsEnabled() {
        return metricsEnabled;
    }

    public boolean isLoggingEnabled() {
        return loggingEnabled;
    }

    public int getMaxImageWidth() {
        return maxImageWidth;
    }

    public int getMaxImageHeight() {
        return maxImageHeight;
    }

    public int getMinConfidenceScore() {
        return minConfidenceScore;
    }

    public int getMaxConfidenceScore() {
        return maxConfidenceScore;
    }

    /**
     * Creates a RetryPolicy from this configuration.
     *
     * @return Configured retry policy
     */
    public RetryPolicy createRetryPolicy() {
        if (maxRetries <= 0) {
            return RetryPolicy.noRetry();
        }
        return new RetryPolicy(maxRetries, initialRetryDelayMs,
            maxRetryDelayMs, retryBackoffMultiplier);
    }

    /**
     * Creates a CircuitBreaker from this configuration.
     *
     * @return Configured circuit breaker, or null if disabled
     */
    public CircuitBreaker createCircuitBreaker() {
        if (!circuitBreakerEnabled) {
            return null;
        }
        return new CircuitBreaker(circuitBreakerThreshold, circuitBreakerCooldownMs);
    }

    @Override
    public String toString() {
        return String.format(
            "LemonlightConfig{i2c=0x%02X, readTimeout=%dms, invokeTimeout=%dms, " +
            "retries=%d, circuitBreaker=%s(%d/%dms), metrics=%s, logging=%s}",
            i2cAddress, readTimeoutMs, invokeTimeoutMs, maxRetries,
            circuitBreakerEnabled ? "enabled" : "disabled",
            circuitBreakerThreshold, circuitBreakerCooldownMs,
            metricsEnabled, loggingEnabled
        );
    }

    /**
     * Builder for LemonlightConfig with fluent API and validation.
     */
    public static class Builder {
        // Defaults from LemonlightConstants
        private int i2cAddress = 0x62;
        private int maxFrameLength = 512;
        private int maxReadLength = 256;
        private long readTimeoutMs = 2000;
        private long invokeTimeoutMs = 3000;
        private int availPollMs = 15;

        // Default retry settings
        private int maxRetries = 3;
        private long initialRetryDelayMs = 50;
        private long maxRetryDelayMs = 1000;
        private double retryBackoffMultiplier = 2.0;

        // Default circuit breaker settings
        private boolean circuitBreakerEnabled = true;
        private int circuitBreakerThreshold = 5;
        private long circuitBreakerCooldownMs = 3000;

        // Default performance settings
        private boolean metricsEnabled = true;
        private boolean loggingEnabled = true;

        // Default validation settings
        private int maxImageWidth = 1920;
        private int maxImageHeight = 1080;
        private int minConfidenceScore = 0;
        private int maxConfidenceScore = 100;

        /**
         * Sets the I2C address.
         *
         * @param address I2C address (valid range: 0x08-0x77)
         * @return this builder
         * @throws IllegalArgumentException if address is out of range
         */
        public Builder i2cAddress(int address) {
            if (address < 0x08 || address > 0x77) {
                throw new IllegalArgumentException(
                    String.format("I2C address must be in range [0x08, 0x77], got: 0x%02X", address)
                );
            }
            this.i2cAddress = address;
            return this;
        }

        /**
         * Sets the maximum frame length for I2C packets.
         *
         * @param length Maximum frame length in bytes (range: 64-1024)
         * @return this builder
         * @throws IllegalArgumentException if length is out of range
         */
        public Builder maxFrameLength(int length) {
            if (length < 64 || length > 1024) {
                throw new IllegalArgumentException(
                    String.format("Max frame length must be in range [64, 1024], got: %d", length)
                );
            }
            this.maxFrameLength = length;
            return this;
        }

        /**
         * Sets the maximum read length for responses.
         *
         * @param length Maximum read length in bytes (range: 32-512)
         * @return this builder
         * @throws IllegalArgumentException if length is out of range
         */
        public Builder maxReadLength(int length) {
            if (length < 32 || length > 512) {
                throw new IllegalArgumentException(
                    String.format("Max read length must be in range [32, 512], got: %d", length)
                );
            }
            this.maxReadLength = length;
            return this;
        }

        /**
         * Sets the read timeout.
         *
         * @param timeoutMs Timeout in milliseconds (range: 100-30000)
         * @return this builder
         * @throws IllegalArgumentException if timeout is out of range
         */
        public Builder readTimeout(long timeoutMs) {
            if (timeoutMs < 100 || timeoutMs > 30000) {
                throw new IllegalArgumentException(
                    String.format("Read timeout must be in range [100, 30000]ms, got: %d", timeoutMs)
                );
            }
            this.readTimeoutMs = timeoutMs;
            return this;
        }

        /**
         * Sets the invoke timeout for AI inference operations.
         *
         * @param timeoutMs Timeout in milliseconds (range: 100-30000)
         * @return this builder
         * @throws IllegalArgumentException if timeout is out of range
         */
        public Builder invokeTimeout(long timeoutMs) {
            if (timeoutMs < 100 || timeoutMs > 30000) {
                throw new IllegalArgumentException(
                    String.format("Invoke timeout must be in range [100, 30000]ms, got: %d", timeoutMs)
                );
            }
            this.invokeTimeoutMs = timeoutMs;
            return this;
        }

        /**
         * Sets the polling interval for checking available data.
         *
         * @param pollMs Poll interval in milliseconds (range: 5-100)
         * @return this builder
         * @throws IllegalArgumentException if interval is out of range
         */
        public Builder availPoll(int pollMs) {
            if (pollMs < 5 || pollMs > 100) {
                throw new IllegalArgumentException(
                    String.format("Avail poll must be in range [5, 100]ms, got: %d", pollMs)
                );
            }
            this.availPollMs = pollMs;
            return this;
        }

        /**
         * Sets the maximum number of retry attempts.
         *
         * @param retries Maximum retries (range: 0-10)
         * @return this builder
         * @throws IllegalArgumentException if retries is out of range
         */
        public Builder maxRetries(int retries) {
            if (retries < 0 || retries > 10) {
                throw new IllegalArgumentException(
                    String.format("Max retries must be in range [0, 10], got: %d", retries)
                );
            }
            this.maxRetries = retries;
            return this;
        }

        /**
         * Sets the initial retry delay.
         *
         * @param delayMs Initial delay in milliseconds (range: 10-1000)
         * @return this builder
         * @throws IllegalArgumentException if delay is out of range
         */
        public Builder initialRetryDelay(long delayMs) {
            if (delayMs < 10 || delayMs > 1000) {
                throw new IllegalArgumentException(
                    String.format("Initial retry delay must be in range [10, 1000]ms, got: %d", delayMs)
                );
            }
            this.initialRetryDelayMs = delayMs;
            return this;
        }

        /**
         * Sets the maximum retry delay.
         *
         * @param delayMs Maximum delay in milliseconds (range: 100-10000)
         * @return this builder
         * @throws IllegalArgumentException if delay is out of range
         */
        public Builder maxRetryDelay(long delayMs) {
            if (delayMs < 100 || delayMs > 10000) {
                throw new IllegalArgumentException(
                    String.format("Max retry delay must be in range [100, 10000]ms, got: %d", delayMs)
                );
            }
            this.maxRetryDelayMs = delayMs;
            return this;
        }

        /**
         * Sets the retry backoff multiplier.
         *
         * @param multiplier Backoff multiplier (range: 1.0-5.0)
         * @return this builder
         * @throws IllegalArgumentException if multiplier is out of range
         */
        public Builder retryBackoff(double multiplier) {
            if (multiplier < 1.0 || multiplier > 5.0) {
                throw new IllegalArgumentException(
                    String.format("Retry backoff must be in range [1.0, 5.0], got: %.2f", multiplier)
                );
            }
            this.retryBackoffMultiplier = multiplier;
            return this;
        }

        /**
         * Configures circuit breaker settings.
         *
         * @param enabled Whether circuit breaker is enabled
         * @param threshold Number of failures before opening circuit (range: 1-20)
         * @param cooldownMs Cooldown period in milliseconds (range: 500-30000)
         * @return this builder
         * @throws IllegalArgumentException if parameters are out of range
         */
        public Builder circuitBreaker(boolean enabled, int threshold, long cooldownMs) {
            if (enabled) {
                if (threshold < 1 || threshold > 20) {
                    throw new IllegalArgumentException(
                        String.format("Circuit breaker threshold must be in range [1, 20], got: %d", threshold)
                    );
                }
                if (cooldownMs < 500 || cooldownMs > 30000) {
                    throw new IllegalArgumentException(
                        String.format("Circuit breaker cooldown must be in range [500, 30000]ms, got: %d", cooldownMs)
                    );
                }
            }
            this.circuitBreakerEnabled = enabled;
            this.circuitBreakerThreshold = threshold;
            this.circuitBreakerCooldownMs = cooldownMs;
            return this;
        }

        /**
         * Enables or disables metrics collection.
         *
         * @param enabled Whether metrics are enabled
         * @return this builder
         */
        public Builder metrics(boolean enabled) {
            this.metricsEnabled = enabled;
            return this;
        }

        /**
         * Enables or disables logging.
         *
         * @param enabled Whether logging is enabled
         * @return this builder
         */
        public Builder logging(boolean enabled) {
            this.loggingEnabled = enabled;
            return this;
        }

        /**
         * Sets the maximum expected image dimensions for validation.
         *
         * @param width Maximum width in pixels (range: 320-3840)
         * @param height Maximum height in pixels (range: 240-2160)
         * @return this builder
         * @throws IllegalArgumentException if dimensions are out of range
         */
        public Builder maxImageSize(int width, int height) {
            if (width < 320 || width > 3840) {
                throw new IllegalArgumentException(
                    String.format("Max image width must be in range [320, 3840], got: %d", width)
                );
            }
            if (height < 240 || height > 2160) {
                throw new IllegalArgumentException(
                    String.format("Max image height must be in range [240, 2160], got: %d", height)
                );
            }
            this.maxImageWidth = width;
            this.maxImageHeight = height;
            return this;
        }

        /**
         * Sets the valid confidence score range.
         *
         * @param min Minimum score (range: 0-99)
         * @param max Maximum score (range: 1-100)
         * @return this builder
         * @throws IllegalArgumentException if range is invalid
         */
        public Builder confidenceRange(int min, int max) {
            if (min < 0 || min > 99) {
                throw new IllegalArgumentException(
                    String.format("Min confidence must be in range [0, 99], got: %d", min)
                );
            }
            if (max < 1 || max > 100) {
                throw new IllegalArgumentException(
                    String.format("Max confidence must be in range [1, 100], got: %d", max)
                );
            }
            if (min >= max) {
                throw new IllegalArgumentException(
                    String.format("Min confidence (%d) must be less than max confidence (%d)", min, max)
                );
            }
            this.minConfidenceScore = min;
            this.maxConfidenceScore = max;
            return this;
        }

        /**
         * Builds the configuration.
         *
         * @return Immutable configuration object
         */
        public LemonlightConfig build() {
            // Validate cross-field constraints
            if (initialRetryDelayMs > maxRetryDelayMs) {
                throw new IllegalStateException(
                    String.format("Initial retry delay (%dms) cannot exceed max retry delay (%dms)",
                        initialRetryDelayMs, maxRetryDelayMs)
                );
            }

            return new LemonlightConfig(this);
        }
    }
}
