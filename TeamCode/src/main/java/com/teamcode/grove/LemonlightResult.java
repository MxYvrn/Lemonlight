package com.teamcode.grove;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LemonlightResult {

    private final byte[] raw;
    private final String firmwareVersion;
    private final int detectionCount;
    private final List<Detection> detections;
    private final long timestampMs;
    private final boolean valid;

    public LemonlightResult(byte[] raw, String firmwareVersion, int detectionCount, 
        List<Detection> detections, long timestampMs, boolean valid) {
        this.raw = raw != null ? raw : new byte[0];
        this.firmwareVersion = firmwareVersion != null ? firmwareVersion : "";
        this.detectionCount = detectionCount;
        this.detections = detections != null ? new ArrayList<>(detections) : new ArrayList<>();
        this.timestampMs = timestampMs;
        this.valid = valid;
    }

    public byte[] getRaw() { return raw; }
    public String getFirmwareVersion() { return firmwareVersion; }
    public int getDetectionCount() { return detectionCount; }
    public List<Detection> getDetections() { return Collections.unmodifiableList(detections); }
    public long getTimestampMs() { return timestampMs; }
    public boolean isValid() { return valid; }

    public int getTopScorePercent() {
        int top = 0;
        for (Detection d : detections) if (d.score > top) top = d.score;
        return top;
    }

    public static final class Detection {
        public final int x, y, w, h, score, targetId;

        public Detection(int x, int y, int w, int h, int score, int targetId) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.score = score;
            this.targetId = targetId;
        }
    }
}
