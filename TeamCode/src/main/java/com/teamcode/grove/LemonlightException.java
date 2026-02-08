package com.teamcode.grove;

/**
 * Custom exception for Lemonlight driver operations.
 * Provides structured error handling with error codes, severity levels, and detailed context.
 *
 * <p>Example usage:
 * <pre>{@code
 * try {
 *     lemonlight.readInference();
 * } catch (LemonlightException e) {
 *     if (e.getSeverity() == ErrorSeverity.FATAL) {
 *         // Reinitialize device
 *     } else if (e.isRecoverable()) {
 *         // Retry operation
 *     }
 *     telemetry.addData("Error", e.getErrorCode().getMessage());
 * }
 * }</pre>
 */
public class LemonlightException extends RuntimeException {
    private final ErrorCode code;
    private final ErrorSeverity severity;
    private final long timestamp;

    /**
     * Error codes for Lemonlight operations.
     */
    public enum ErrorCode {
        // Device communication errors (1000-1099)
        PING_FAILED(1000, "Device ping failed"),
        TIMEOUT(1001, "Operation timeout"),
        INVALID_RESPONSE(1002, "Invalid device response"),
        I2C_COMMUNICATION_ERROR(1003, "I2C communication error"),
        BUFFER_OVERFLOW(1004, "Buffer size exceeded"),
        INTERRUPTED(1005, "Operation interrupted"),

        // JSON parsing errors (1100-1199)
        JSON_PARSE_ERROR(1100, "JSON parsing failed"),
        INVALID_JSON_FORMAT(1101, "Invalid JSON format"),
        MISSING_JSON_FIELD(1102, "Required JSON field missing"),

        // Configuration errors (2000-2099)
        INVALID_MODEL_ID(2000, "Invalid model ID"),
        INVALID_SENSOR_ID(2001, "Invalid sensor ID"),
        INVALID_CONFIGURATION(2002, "Invalid configuration"),

        // State errors (3000-3099)
        DEVICE_NOT_READY(3000, "Device not ready"),
        DEVICE_NOT_INITIALIZED(3001, "Device not initialized"),
        INVALID_STATE_TRANSITION(3002, "Invalid state transition"),

        // Resilience errors (4000-4099)
        CIRCUIT_BREAKER_OPEN(4000, "Circuit breaker is open"),
        MAX_RETRIES_EXCEEDED(4001, "Maximum retries exceeded");

        private final int code;
        private final String message;

        ErrorCode(int code, String message) {
            this.code = code;
            this.message = message;
        }

        public int getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }
    }

    /**
     * Error severity levels indicating recoverability.
     */
    public enum ErrorSeverity {
        /**
         * Warning - Operation completed with degraded quality.
         * Action: Log and continue.
         */
        WARNING,

        /**
         * Error - Operation failed but can be retried.
         * Action: Retry with backoff or fall back to alternative.
         */
        ERROR,

        /**
         * Fatal - Unrecoverable error requiring device reset.
         * Action: Reinitialize device or fail OpMode.
         */
        FATAL
    }

    /**
     * Creates a new LemonlightException with error code, severity, and details.
     *
     * @param code Error code identifying the specific error
     * @param severity Severity level indicating recoverability
     * @param details Additional context about the error
     */
    public LemonlightException(ErrorCode code, ErrorSeverity severity, String details) {
        super(formatMessage(code, severity, details));
        this.code = code;
        this.severity = severity;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Creates a new LemonlightException wrapping another exception.
     *
     * @param code Error code identifying the specific error
     * @param severity Severity level indicating recoverability
     * @param cause The underlying exception
     */
    public LemonlightException(ErrorCode code, ErrorSeverity severity, Throwable cause) {
        super(formatMessage(code, severity, null), cause);
        this.code = code;
        this.severity = severity;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Creates a new LemonlightException with details and cause.
     *
     * @param code Error code identifying the specific error
     * @param severity Severity level indicating recoverability
     * @param details Additional context about the error
     * @param cause The underlying exception
     */
    public LemonlightException(ErrorCode code, ErrorSeverity severity, String details, Throwable cause) {
        super(formatMessage(code, severity, details), cause);
        this.code = code;
        this.severity = severity;
        this.timestamp = System.currentTimeMillis();
    }

    private static String formatMessage(ErrorCode code, ErrorSeverity severity, String details) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("[%s-%04d] %s", severity, code.getCode(), code.getMessage()));
        if (details != null && !details.isEmpty()) {
            sb.append(": ").append(details);
        }
        return sb.toString();
    }

    /**
     * Gets the error code.
     *
     * @return Error code
     */
    public ErrorCode getErrorCode() {
        return code;
    }

    /**
     * Gets the error severity.
     *
     * @return Severity level
     */
    public ErrorSeverity getSeverity() {
        return severity;
    }

    /**
     * Gets the timestamp when the error occurred.
     *
     * @return Timestamp in milliseconds
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Checks if the error is recoverable (not FATAL).
     *
     * @return true if the error can be recovered from
     */
    public boolean isRecoverable() {
        return severity != ErrorSeverity.FATAL;
    }

    /**
     * Gets a short error code string for telemetry display.
     *
     * @return Error code formatted as "E1001" or "W1000"
     */
    public String getShortCode() {
        char prefix = severity == ErrorSeverity.WARNING ? 'W' :
                     severity == ErrorSeverity.ERROR ? 'E' : 'F';
        return String.format("%c%04d", prefix, code.getCode());
    }

    /**
     * Gets a user-friendly error message suitable for telemetry.
     *
     * @return User-friendly error message
     */
    public String getUserMessage() {
        return code.getMessage();
    }

    @Override
    public String toString() {
        return String.format("LemonlightException{code=%s, severity=%s, message='%s', timestamp=%d}",
            code, severity, getMessage(), timestamp);
    }
}
