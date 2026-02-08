package com.teamcode.grove;

/**
 * Retry policy for Lemonlight operations with exponential backoff.
 *
 * <p>Automatically retries failed operations with increasing delays between attempts.
 * Supports configurable retry counts, delays, and backoff multipliers.
 *
 * <p>Example usage:
 * <pre>{@code
 * RetryPolicy retry = RetryPolicy.defaultPolicy();
 *
 * LemonlightResult result = retry.execute(() -> {
 *     return lemonlight.readInference();
 * });
 * }</pre>
 *
 * <p><b>Thread Safety:</b> RetryPolicy instances are immutable and thread-safe.
 * The execute method can be called concurrently from multiple threads.
 */
public class RetryPolicy {
    private final int maxAttempts;
    private final long initialDelayMs;
    private final long maxDelayMs;
    private final double backoffMultiplier;
    private final LemonlightLogger logger;

    /**
     * Creates a retry policy with specified parameters.
     *
     * @param maxAttempts Maximum number of attempts (1 = no retries)
     * @param initialDelayMs Initial delay between retries in milliseconds
     * @param maxDelayMs Maximum delay between retries in milliseconds
     * @param backoffMultiplier Multiplier for exponential backoff (e.g., 2.0 doubles delay)
     */
    public RetryPolicy(int maxAttempts, long initialDelayMs, long maxDelayMs, double backoffMultiplier) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("Max attempts must be at least 1");
        }
        if (initialDelayMs < 0) {
            throw new IllegalArgumentException("Initial delay must be non-negative");
        }
        if (maxDelayMs < initialDelayMs) {
            throw new IllegalArgumentException("Max delay must be >= initial delay");
        }
        if (backoffMultiplier < 1.0) {
            throw new IllegalArgumentException("Backoff multiplier must be >= 1.0");
        }

        this.maxAttempts = maxAttempts;
        this.initialDelayMs = initialDelayMs;
        this.maxDelayMs = maxDelayMs;
        this.backoffMultiplier = backoffMultiplier;
        this.logger = new LemonlightLogger(RetryPolicy.class);
    }

    /**
     * Creates a default retry policy suitable for most operations.
     * <ul>
     *   <li>Max attempts: 3</li>
     *   <li>Initial delay: 50ms</li>
     *   <li>Max delay: 1000ms</li>
     *   <li>Backoff multiplier: 2.0 (exponential)</li>
     * </ul>
     *
     * @return Default retry policy
     */
    public static RetryPolicy defaultPolicy() {
        return new RetryPolicy(3, 50, 1000, 2.0);
    }

    /**
     * Creates an aggressive retry policy for critical operations.
     * <ul>
     *   <li>Max attempts: 5</li>
     *   <li>Initial delay: 25ms</li>
     *   <li>Max delay: 500ms</li>
     *   <li>Backoff multiplier: 1.5</li>
     * </ul>
     *
     * @return Aggressive retry policy
     */
    public static RetryPolicy aggressive() {
        return new RetryPolicy(5, 25, 500, 1.5);
    }

    /**
     * Creates a conservative retry policy for non-critical operations.
     * <ul>
     *   <li>Max attempts: 2</li>
     *   <li>Initial delay: 100ms</li>
     *   <li>Max delay: 2000ms</li>
     *   <li>Backoff multiplier: 3.0</li>
     * </ul>
     *
     * @return Conservative retry policy
     */
    public static RetryPolicy conservative() {
        return new RetryPolicy(2, 100, 2000, 3.0);
    }

    /**
     * Creates a no-retry policy (single attempt only).
     *
     * @return No-retry policy
     */
    public static RetryPolicy noRetry() {
        return new RetryPolicy(1, 0, 0, 1.0);
    }

    /**
     * Executes an operation with retry logic.
     *
     * <p>If the operation throws a {@link LemonlightException}, the retry logic
     * will check if the error is recoverable and retry if attempts remain.
     * Non-recoverable errors (FATAL severity) are immediately rethrown.
     *
     * @param operation Operation to execute
     * @param <T> Return type of the operation
     * @return Result of the successful operation
     * @throws LemonlightException if all retry attempts fail or error is non-recoverable
     */
    public <T> T execute(RetryableOperation<T> operation) throws LemonlightException {
        int attempt = 0;
        long delay = initialDelayMs;
        LemonlightException lastException = null;

        while (attempt < maxAttempts) {
            attempt++;

            try {
                T result = operation.execute();

                // Success - log if this was a retry
                if (attempt > 1) {
                    logger.info("Operation succeeded on attempt {}/{}", attempt, maxAttempts);
                }

                return result;

            } catch (LemonlightException e) {
                lastException = e;

                // Don't retry non-recoverable errors
                if (!e.isRecoverable()) {
                    logger.error("Non-recoverable error on attempt {}, aborting retries",
                        attempt, e);
                    throw e;
                }

                // Don't retry if we've exhausted attempts
                if (attempt >= maxAttempts) {
                    logger.error("Operation failed after {} attempts", attempt, e);
                    throw e;
                }

                // Log retry attempt
                logger.warn("Operation failed on attempt {}/{}, retrying in {}ms: {}",
                    attempt, maxAttempts, delay, e.getUserMessage());

                // Sleep before retry
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new LemonlightException(
                        LemonlightException.ErrorCode.INTERRUPTED,
                        LemonlightException.ErrorSeverity.FATAL,
                        "Retry interrupted",
                        ie
                    );
                }

                // Calculate next delay with exponential backoff
                delay = Math.min((long) (delay * backoffMultiplier), maxDelayMs);
            }
        }

        // Should never reach here, but just in case
        if (lastException != null) {
            throw lastException;
        }

        throw new LemonlightException(
            LemonlightException.ErrorCode.DEVICE_NOT_READY,
            LemonlightException.ErrorSeverity.ERROR,
            "Operation failed after max retries"
        );
    }

    /**
     * Functional interface for retryable operations.
     *
     * @param <T> Return type of the operation
     */
    @FunctionalInterface
    public interface RetryableOperation<T> {
        /**
         * Executes the operation.
         *
         * @return Result of the operation
         * @throws LemonlightException if the operation fails
         */
        T execute() throws LemonlightException;
    }

    // Getters for inspection

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public long getInitialDelayMs() {
        return initialDelayMs;
    }

    public long getMaxDelayMs() {
        return maxDelayMs;
    }

    public double getBackoffMultiplier() {
        return backoffMultiplier;
    }

    @Override
    public String toString() {
        return String.format("RetryPolicy{attempts=%d, initial=%dms, max=%dms, backoff=%.1fx}",
            maxAttempts, initialDelayMs, maxDelayMs, backoffMultiplier);
    }
}
