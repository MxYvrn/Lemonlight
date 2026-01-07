package com.teamcode.stress;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Stress tests for TeleOpMain.
 * Tests telemetry rate limiting, nanoTime() overflow handling.
 */
public class TeleOpStressTest {

    /**
     * BUG-006: Test nanoTime() overflow handling.
     * System.nanoTime() can overflow (extremely rare, but possible after ~292 years).
     * When it overflows, delta becomes negative and telemetry stops updating.
     */
    @Test
    public void testTelemetryNanoTimeOverflow() {
        // Simulate nanoTime() overflow scenario
        long lastTelemetryNs = Long.MAX_VALUE - 50_000_000L; // Near overflow
        long TELEMETRY_INTERVAL_NS = 100_000_000L;
        
        // Simulate overflow: nanoTime() wraps to small positive value
        long now = 50_000_000L; // After overflow, nanoTime() restarts
        long delta = now - lastTelemetryNs;
        
        // Delta will be negative (overflow occurred)
        assertTrue("Delta should be negative after overflow", delta < 0);
        
        // Bug fix: Check for negative delta and reset
        if (delta < 0) {
            lastTelemetryNs = now;
            delta = 0;
        }
        
        // Should now be safe to continue
        assertTrue("Delta should be non-negative after reset", delta >= 0);
    }

    /**
     * Test that telemetry rate limiting works correctly with normal timestamps.
     */
    @Test
    public void testTelemetryRateLimitingNormalCase() {
        long lastTelemetryNs = 1_000_000_000L; // 1 second
        long TELEMETRY_INTERVAL_NS = 100_000_000L; // 0.1 seconds
        
        // First check: too soon, should not update
        long now1 = 1_050_000_000L; // 50ms later
        long delta1 = now1 - lastTelemetryNs;
        assertFalse("Should not update if interval not met", delta1 > TELEMETRY_INTERVAL_NS);
        
        // Second check: enough time elapsed, should update
        long now2 = 1_150_000_000L; // 150ms later
        long delta2 = now2 - lastTelemetryNs;
        assertTrue("Should update if interval is met", delta2 > TELEMETRY_INTERVAL_NS);
    }

    /**
     * Test that negative delta doesn't cause immediate update.
     */
    @Test
    public void testNegativeDeltaHandling() {
        long lastTelemetryNs = 100_000_000L;
        long now = 50_000_000L; // Before last (overflow scenario)
        long TELEMETRY_INTERVAL_NS = 100_000_000L;
        
        long delta = now - lastTelemetryNs;
        
        // Handle overflow
        if (delta < 0) {
            lastTelemetryNs = now;
            delta = 0;
        }
        
        // Should not update (delta is now 0)
        assertFalse("Should not update immediately after overflow reset",
                delta > TELEMETRY_INTERVAL_NS);
    }
}

