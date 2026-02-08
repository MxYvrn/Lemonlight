package com.teamcode.grove;

import android.util.Log;

/**
 * Logging wrapper for Lemonlight driver.
 * Provides structured logging compatible with Android's Log system.
 *
 * <p>Usage:
 * <pre>{@code
 * private static final LemonlightLogger logger = new LemonlightLogger("Lemonlight");
 *
 * logger.debug("Starting inference");
 * logger.info("Model loaded: id={}", modelId);
 * logger.warn("Timeout occurred: duration={}ms", duration);
 * logger.error("I2C communication failed", exception);
 * }</pre>
 */
public class LemonlightLogger {
    private final String tag;
    private static final String PREFIX = "Lemonlight";
    private static boolean globalEnabled = true;

    /**
     * Creates a new logger with the specified tag.
     *
     * @param tag Log tag (will be prefixed with "Lemonlight:")
     */
    public LemonlightLogger(String tag) {
        this.tag = PREFIX + ":" + tag;
    }

    /**
     * Creates a new logger for a class.
     *
     * @param clazz Class to log for
     */
    public LemonlightLogger(Class<?> clazz) {
        this(clazz.getSimpleName());
    }

    /**
     * Enables or disables all Lemonlight logging globally.
     *
     * @param enabled true to enable logging, false to disable
     */
    public static void setGlobalEnabled(boolean enabled) {
        globalEnabled = enabled;
    }

    /**
     * Logs a debug message.
     *
     * @param message Message with optional {} placeholders
     * @param args Arguments to substitute
     */
    public void debug(String message, Object... args) {
        if (globalEnabled && Log.isLoggable(tag, Log.DEBUG)) {
            Log.d(tag, format(message, args));
        }
    }

    /**
     * Logs an info message.
     *
     * @param message Message with optional {} placeholders
     * @param args Arguments to substitute
     */
    public void info(String message, Object... args) {
        if (globalEnabled && Log.isLoggable(tag, Log.INFO)) {
            Log.i(tag, format(message, args));
        }
    }

    /**
     * Logs a warning message.
     *
     * @param message Message with optional {} placeholders
     * @param args Arguments to substitute
     */
    public void warn(String message, Object... args) {
        if (globalEnabled) {
            Log.w(tag, format(message, args));
        }
    }

    /**
     * Logs an error message.
     *
     * @param message Message with optional {} placeholders
     * @param args Arguments to substitute
     */
    public void error(String message, Object... args) {
        if (globalEnabled) {
            Log.e(tag, format(message, args));
        }
    }

    /**
     * Logs an error message with exception.
     *
     * @param message Message with optional {} placeholders
     * @param throwable Exception to log
     */
    public void error(String message, Throwable throwable) {
        if (globalEnabled) {
            Log.e(tag, message, throwable);
        }
    }

    /**
     * Logs an error message with exception and arguments.
     *
     * @param message Message with optional {} placeholders
     * @param args Arguments (last arg should be Throwable if exception logging is desired)
     */
    public void error(String message, Object... args) {
        if (!globalEnabled) return;

        // Check if last argument is Throwable
        if (args.length > 0 && args[args.length - 1] instanceof Throwable) {
            Object[] msgArgs = new Object[args.length - 1];
            System.arraycopy(args, 0, msgArgs, 0, msgArgs.length);
            Log.e(tag, format(message, msgArgs), (Throwable) args[args.length - 1]);
        } else {
            Log.e(tag, format(message, args));
        }
    }

    /**
     * Formats a message with {} placeholders.
     * Simple implementation for Android (no SLF4J dependency).
     *
     * @param message Message template
     * @param args Arguments to substitute
     * @return Formatted message
     */
    private String format(String message, Object... args) {
        if (args == null || args.length == 0) {
            return message;
        }

        StringBuilder result = new StringBuilder();
        int argIndex = 0;
        int length = message.length();
        int i = 0;

        while (i < length) {
            if (i < length - 1 && message.charAt(i) == '{' && message.charAt(i + 1) == '}') {
                // Found placeholder
                if (argIndex < args.length) {
                    result.append(args[argIndex++]);
                } else {
                    result.append("{}");
                }
                i += 2;
            } else {
                result.append(message.charAt(i));
                i++;
            }
        }

        return result.toString();
    }

    /**
     * Checks if debug logging is enabled for this logger.
     *
     * @return true if debug logging is enabled
     */
    public boolean isDebugEnabled() {
        return globalEnabled && Log.isLoggable(tag, Log.DEBUG);
    }

    /**
     * Gets the tag for this logger.
     *
     * @return Log tag
     */
    public String getTag() {
        return tag;
    }
}
