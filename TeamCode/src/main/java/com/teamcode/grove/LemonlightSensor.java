package com.teamcode.grove;

/**
 * Thin wrapper around Lemonlight driver: holds driver reference, update() runs readInference() and caches last result.
 */

public class LemonlightSensor {

    private final Lemonlight driver;
    private LemonlightResult lastResult;
    private long lastUpdateMs;

    public LemonlightSensor(Lemonlight driver) {
        this.driver = driver;
        this.lastResult = null;
        this.lastUpdateMs = 0;
    }

    public void update() {
        if (driver == null) return;
        lastResult = driver.readInference();
        lastUpdateMs = System.currentTimeMillis();
    }

    public LemonlightResult getLastResult() {
        return lastResult;
    }

    public long getLastUpdateMs() {
        return lastUpdateMs;
    }

    public Lemonlight getDriver() {
        return driver;
    }
}
