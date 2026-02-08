package com.teamcode.grove;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Circuit breaker to prevent hammering a failing device.
 *
 * <p>Implements the Circuit Breaker pattern with three states:
 * <ul>
 *   <li><b>CLOSED</b>: Normal operation, requests pass through</li>
 *   <li><b>OPEN</b>: Too many failures, reject requests immediately</li>
 *   <li><b>HALF_OPEN</b>: Testing if device recovered, allow one test request</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * CircuitBreaker breaker = new CircuitBreaker(5, 3000); // 5 failures, 3s cooldown
 *
 * try {
 *     LemonlightResult result = breaker.execute(() -> {
 *         return lemonlight.readInference();
 *     });
 * } catch (LemonlightException e) {
 *     if (e.getErrorCode() == ErrorCode.CIRCUIT_BREAKER_OPEN) {
 *         // Circuit is open, device is failing
 *         telemetry.addLine("Device unavailable, waiting for recovery");
 *     }
 * }
 * }</pre>
 *
 * <p><b>Thread Safety:</b> All methods are thread-safe and can be called
 * concurrently from multiple threads.
 */
public class CircuitBreaker {

    /**
     * Circuit breaker states.
     */
    public enum State {
        /**
         * Normal operation - requests pass through.
         */
        CLOSED,

        /**
         * Too many failures - rejecting requests.
         */
        OPEN,

        /**
         * Testing recovery - allow one test request.
         */
        HALF_OPEN
    }

    private final AtomicReference<State> state;
    private final AtomicInteger failureCount;
    private final AtomicInteger successCount;
    private final AtomicLong lastFailureTime;
    private final AtomicLong lastStateChangeTime;
    private final int failureThreshold;
    private final long cooldownMs;
    private final LemonlightLogger logger;

    /**
     * Creates a circuit breaker with specified parameters.
     *
     * @param failureThreshold Number of consecutive failures before opening circuit
     * @param cooldownMs Time to wait before attempting recovery (milliseconds)
     */
    public CircuitBreaker(int failureThreshold, long cooldownMs) {
        if (failureThreshold < 1) {
            throw new IllegalArgumentException("Failure threshold must be at least 1");
        }
        if (cooldownMs < 0) {
            throw new IllegalArgumentException("Cooldown must be non-negative");
        }

        this.state = new AtomicReference<>(State.CLOSED);
        this.failureCount = new AtomicInteger(0);
        this.successCount = new AtomicInteger(0);
        this.lastFailureTime = new AtomicLong(0);
        this.lastStateChangeTime = new AtomicLong(System.currentTimeMillis());
        this.failureThreshold = failureThreshold;
        this.cooldownMs = cooldownMs;
        this.logger = new LemonlightLogger(CircuitBreaker.class);
    }

    /**
     * Creates a default circuit breaker suitable for most operations.
     * <ul>
     *   <li>Failure threshold: 5</li>
     *   <li>Cooldown: 3000ms (3 seconds)</li>
     * </ul>
     *
     * @return Default circuit breaker
     */
    public static CircuitBreaker defaultBreaker() {
        return new CircuitBreaker(5, 3000);
    }

    /**
     * Creates an aggressive circuit breaker that opens quickly.
     * <ul>
     *   <li>Failure threshold: 3</li>
     *   <li>Cooldown: 2000ms (2 seconds)</li>
     * </ul>
     *
     * @return Aggressive circuit breaker
     */
    public static CircuitBreaker aggressive() {
        return new CircuitBreaker(3, 2000);
    }

    /**
     * Creates a lenient circuit breaker that tolerates more failures.
     * <ul>
     *   <li>Failure threshold: 10</li>
     *   <li>Cooldown: 5000ms (5 seconds)</li>
     * </ul>
     *
     * @return Lenient circuit breaker
     */
    public static CircuitBreaker lenient() {
        return new CircuitBreaker(10, 5000);
    }

    /**
     * Executes an operation through the circuit breaker.
     *
     * <p>If the circuit is OPEN, immediately throws a {@link LemonlightException}
     * with {@link LemonlightException.ErrorCode#CIRCUIT_BREAKER_OPEN}.
     *
     * <p>If the circuit is CLOSED or HALF_OPEN, executes the operation and
     * updates the circuit state based on success or failure.
     *
     * @param operation Operation to execute
     * @param <T> Return type of the operation
     * @return Result of the successful operation
     * @throws LemonlightException if circuit is open or operation fails
     */
    public <T> T execute(Callable<T> operation) throws LemonlightException {
        State currentState = updateState();

        // Reject immediately if circuit is open
        if (currentState == State.OPEN) {
            long remainingCooldown = getRemainingCooldownMs();
            throw new LemonlightException(
                LemonlightException.ErrorCode.CIRCUIT_BREAKER_OPEN,
                LemonlightException.ErrorSeverity.ERROR,
                String.format("Circuit breaker is OPEN (cooldown: %dms remaining)", remainingCooldown)
            );
        }

        // Execute operation
        try {
            T result = operation.call();
            onSuccess();
            return result;

        } catch (LemonlightException e) {
            onFailure();
            throw e;

        } catch (Exception e) {
            onFailure();

            // Wrap non-LemonlightException
            throw new LemonlightException(
                LemonlightException.ErrorCode.DEVICE_NOT_READY,
                LemonlightException.ErrorSeverity.ERROR,
                "Operation failed",
                e
            );
        }
    }

    /**
     * Updates circuit state based on time and failure count.
     *
     * @return Current state after update
     */
    private State updateState() {
        State current = state.get();

        // If OPEN, check if cooldown has expired
        if (current == State.OPEN) {
            long timeSinceFailure = System.currentTimeMillis() - lastFailureTime.get();

            if (timeSinceFailure >= cooldownMs) {
                // Cooldown expired, transition to HALF_OPEN for testing
                if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    lastStateChangeTime.set(System.currentTimeMillis());
                    logger.info("Circuit breaker transitioning OPEN -> HALF_OPEN (cooldown expired)");
                    return State.HALF_OPEN;
                }
            }
        }

        return state.get();
    }

    /**
     * Handles successful operation execution.
     */
    private void onSuccess() {
        State current = state.get();
        successCount.incrementAndGet();

        if (current == State.HALF_OPEN) {
            // Test call succeeded, transition back to CLOSED
            if (state.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
                failureCount.set(0);
                lastStateChangeTime.set(System.currentTimeMillis());
                logger.info("Circuit breaker transitioning HALF_OPEN -> CLOSED (test call succeeded)");
            }

        } else if (current == State.CLOSED) {
            // Reset failure count on success in CLOSED state
            if (failureCount.get() > 0) {
                failureCount.set(0);
                logger.debug("Circuit breaker reset failure count after success");
            }
        }
    }

    /**
     * Handles failed operation execution.
     */
    private void onFailure() {
        lastFailureTime.set(System.currentTimeMillis());
        int failures = failureCount.incrementAndGet();
        State current = state.get();

        if (current == State.HALF_OPEN) {
            // Test call failed, transition back to OPEN
            if (state.compareAndSet(State.HALF_OPEN, State.OPEN)) {
                lastStateChangeTime.set(System.currentTimeMillis());
                logger.warn("Circuit breaker transitioning HALF_OPEN -> OPEN (test call failed)");
            }

        } else if (current == State.CLOSED && failures >= failureThreshold) {
            // Too many failures, open the circuit
            if (state.compareAndSet(State.CLOSED, State.OPEN)) {
                lastStateChangeTime.set(System.currentTimeMillis());
                logger.warn("Circuit breaker transitioning CLOSED -> OPEN (failure threshold reached: {})",
                    failures);
            }
        }
    }

    /**
     * Gets the current circuit state.
     *
     * @return Current state
     */
    public State getState() {
        updateState();  // Update state before returning
        return state.get();
    }

    /**
     * Gets the number of consecutive failures.
     *
     * @return Failure count
     */
    public int getFailureCount() {
        return failureCount.get();
    }

    /**
     * Gets the total number of successful operations.
     *
     * @return Success count
     */
    public int getSuccessCount() {
        return successCount.get();
    }

    /**
     * Gets the remaining cooldown time in milliseconds.
     *
     * @return Remaining cooldown, or 0 if not in cooldown
     */
    public long getRemainingCooldownMs() {
        if (state.get() != State.OPEN) {
            return 0;
        }

        long elapsed = System.currentTimeMillis() - lastFailureTime.get();
        return Math.max(0, cooldownMs - elapsed);
    }

    /**
     * Gets the time since last state change in milliseconds.
     *
     * @return Time since last state change
     */
    public long getTimeSinceStateChangeMs() {
        return System.currentTimeMillis() - lastStateChangeTime.get();
    }

    /**
     * Checks if the circuit is allowing requests.
     *
     * @return true if circuit is CLOSED or HALF_OPEN
     */
    public boolean isAllowingRequests() {
        State current = getState();
        return current == State.CLOSED || current == State.HALF_OPEN;
    }

    /**
     * Manually resets the circuit breaker to CLOSED state.
     * Use with caution - typically the circuit should recover automatically.
     */
    public void reset() {
        state.set(State.CLOSED);
        failureCount.set(0);
        lastFailureTime.set(0);
        lastStateChangeTime.set(System.currentTimeMillis());
        logger.info("Circuit breaker manually reset to CLOSED");
    }

    /**
     * Gets circuit breaker statistics for monitoring.
     *
     * @return Statistics object
     */
    public Statistics getStatistics() {
        return new Statistics(
            state.get(),
            failureCount.get(),
            successCount.get(),
            getRemainingCooldownMs(),
            getTimeSinceStateChangeMs()
        );
    }

    /**
     * Statistics container for circuit breaker state.
     */
    public static class Statistics {
        public final State state;
        public final int failureCount;
        public final int successCount;
        public final long remainingCooldownMs;
        public final long timeSinceStateChangeMs;

        Statistics(State state, int failureCount, int successCount,
                  long remainingCooldownMs, long timeSinceStateChangeMs) {
            this.state = state;
            this.failureCount = failureCount;
            this.successCount = successCount;
            this.remainingCooldownMs = remainingCooldownMs;
            this.timeSinceStateChangeMs = timeSinceStateChangeMs;
        }

        /**
         * Gets a formatted string for telemetry display.
         *
         * @return Formatted statistics string
         */
        public String toTelemetryString() {
            StringBuilder sb = new StringBuilder();
            sb.append("State: ").append(state).append("\n");
            sb.append("Failures: ").append(failureCount).append("\n");
            sb.append("Successes: ").append(successCount).append("\n");

            if (state == State.OPEN) {
                sb.append("Cooldown: ").append(remainingCooldownMs).append("ms\n");
            }

            sb.append("In State: ").append(timeSinceStateChangeMs).append("ms");

            return sb.toString();
        }

        @Override
        public String toString() {
            return String.format("CircuitBreakerStats{state=%s, failures=%d, successes=%d}",
                state, failureCount, successCount);
        }
    }

    @Override
    public String toString() {
        return String.format("CircuitBreaker{state=%s, failures=%d, threshold=%d, cooldown=%dms}",
            state.get(), failureCount.get(), failureThreshold, cooldownMs);
    }
}
