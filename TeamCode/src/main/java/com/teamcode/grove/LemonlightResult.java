package com.teamcode.grove;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Result from Lemonlight AI vision inference containing detections, classifications, and keypoints.
 *
 * <p>Provides fluent query API for filtering and finding specific results:
 * <pre>{@code
 * // Find best high-confidence detection
 * Detection best = result.query()
 *     .targetId(0)
 *     .minConfidence(80)
 *     .bestOrThrow();
 *
 * // Count detections in left half of image
 * int leftCount = result.query()
 *     .withinBounds(0, 0, 640, 480)
 *     .count();
 *
 * // Check if any good detections exist
 * boolean hasTarget = result.query()
 *     .targetId(0)
 *     .minConfidence(70)
 *     .any();
 * }</pre>
 */
public final class LemonlightResult {

    private final byte[] raw;
    private final String firmwareVersion;
    private final int detectionCount;
    private final List<Detection> detections;
    private final List<Classification> classifications;
    private final List<KeyPoint> keypoints;
    private final long timestampMs;
    private final boolean valid;

    public LemonlightResult(byte[] raw, String firmwareVersion, int detectionCount,
        List<Detection> detections, List<Classification> classifications,
        List<KeyPoint> keypoints, long timestampMs, boolean valid) {
        this.raw = raw != null ? raw : new byte[0];
        this.firmwareVersion = firmwareVersion != null ? firmwareVersion : "";
        this.detectionCount = detectionCount;
        this.detections = detections != null ? new ArrayList<>(detections) : new ArrayList<>();
        this.classifications = classifications != null ? new ArrayList<>(classifications) : new ArrayList<>();
        this.keypoints = keypoints != null ? new ArrayList<>(keypoints) : new ArrayList<>();
        this.timestampMs = timestampMs;
        this.valid = valid;
    }

    public byte[] getRaw() { return raw; }
    public String getFirmwareVersion() { return firmwareVersion; }
    public int getDetectionCount() { return detectionCount; }
    public List<Detection> getDetections() { return Collections.unmodifiableList(detections); }
    public List<Classification> getClassifications() { return Collections.unmodifiableList(classifications); }
    public List<KeyPoint> getKeypoints() { return Collections.unmodifiableList(keypoints); }
    public long getTimestampMs() { return timestampMs; }
    public boolean isValid() { return valid; }

    public int getTopScorePercent() {
        int top = 0;
        for (Detection d : detections) if (d.score > top) top = d.score;
        return top;
    }

    /**
     * Creates a fluent query builder for filtering detections.
     *
     * @return Detection query builder
     */
    public DetectionQuery query() {
        return new DetectionQuery(this);
    }

    /**
     * Creates a fluent query builder for filtering classifications.
     *
     * @return Classification query builder
     */
    public ClassificationQuery queryClassifications() {
        return new ClassificationQuery(this);
    }

    /**
     * Creates a fluent query builder for filtering keypoints.
     *
     * @return KeyPoint query builder
     */
    public KeyPointQuery queryKeypoints() {
        return new KeyPointQuery(this);
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

    public static final class Classification {
        public final int targetId;
        public final int score;

        public Classification(int targetId, int score) {
            this.targetId = targetId;
            this.score = score;
        }
    }

    public static final class KeyPoint {
        public final int x, y, score, targetId;

        public KeyPoint(int x, int y, int score, int targetId) {
            this.x = x;
            this.y = y;
            this.score = score;
            this.targetId = targetId;
        }
    }

    /**
     * Fluent query builder for filtering detections.
     *
     * <p>Allows chaining filter criteria and retrieving results:
     * <pre>{@code
     * Detection best = result.query()
     *     .targetId(0)
     *     .minConfidence(80)
     *     .withinBounds(0, 0, 640, 480)
     *     .bestOrThrow();
     * }</pre>
     */
    public static class DetectionQuery {
        private final LemonlightResult result;
        private Integer minConfidence;
        private Integer maxConfidence;
        private Integer targetId;
        private BoundingBox withinBounds;
        private Integer minArea;
        private Integer maxArea;

        DetectionQuery(LemonlightResult result) {
            this.result = result;
        }

        /**
         * Filters detections by minimum confidence score.
         *
         * @param confidence Minimum score (0-100)
         * @return this query for chaining
         */
        public DetectionQuery minConfidence(int confidence) {
            this.minConfidence = confidence;
            return this;
        }

        /**
         * Filters detections by maximum confidence score.
         *
         * @param confidence Maximum score (0-100)
         * @return this query for chaining
         */
        public DetectionQuery maxConfidence(int confidence) {
            this.maxConfidence = confidence;
            return this;
        }

        /**
         * Filters detections by confidence range.
         *
         * @param min Minimum score (0-100)
         * @param max Maximum score (0-100)
         * @return this query for chaining
         */
        public DetectionQuery confidenceRange(int min, int max) {
            this.minConfidence = min;
            this.maxConfidence = max;
            return this;
        }

        /**
         * Filters detections by target ID (class label).
         *
         * @param id Target ID
         * @return this query for chaining
         */
        public DetectionQuery targetId(int id) {
            this.targetId = id;
            return this;
        }

        /**
         * Filters detections to those within specified bounding box.
         *
         * @param x Left coordinate
         * @param y Top coordinate
         * @param width Width of bounding box
         * @param height Height of bounding box
         * @return this query for chaining
         */
        public DetectionQuery withinBounds(int x, int y, int width, int height) {
            this.withinBounds = new BoundingBox(x, y, width, height);
            return this;
        }

        /**
         * Filters detections by minimum area (width * height).
         *
         * @param area Minimum area in pixels
         * @return this query for chaining
         */
        public DetectionQuery minArea(int area) {
            this.minArea = area;
            return this;
        }

        /**
         * Filters detections by maximum area (width * height).
         *
         * @param area Maximum area in pixels
         * @return this query for chaining
         */
        public DetectionQuery maxArea(int area) {
            this.maxArea = area;
            return this;
        }

        /**
         * Returns all detections matching the filter criteria.
         *
         * @return List of matching detections (may be empty)
         */
        public List<Detection> detections() {
            return result.getDetections().stream()
                .filter(d -> minConfidence == null || d.score >= minConfidence)
                .filter(d -> maxConfidence == null || d.score <= maxConfidence)
                .filter(d -> targetId == null || d.targetId == targetId)
                .filter(d -> withinBounds == null || withinBounds.contains(d))
                .filter(d -> minArea == null || (d.w * d.h) >= minArea)
                .filter(d -> maxArea == null || (d.w * d.h) <= maxArea)
                .collect(Collectors.toList());
        }

        /**
         * Returns the best detection (highest confidence) matching criteria.
         *
         * @return Optional containing best detection, or empty if none match
         */
        public Optional<Detection> best() {
            return detections().stream()
                .max(Comparator.comparingInt(d -> d.score));
        }

        /**
         * Returns the best detection or throws exception if none match.
         *
         * @return Best matching detection
         * @throws IllegalStateException if no detections match criteria
         */
        public Detection bestOrThrow() {
            return best().orElseThrow(() ->
                new IllegalStateException("No detections matching criteria"));
        }

        /**
         * Returns the largest detection (by area) matching criteria.
         *
         * @return Optional containing largest detection, or empty if none match
         */
        public Optional<Detection> largest() {
            return detections().stream()
                .max(Comparator.comparingInt(d -> d.w * d.h));
        }

        /**
         * Returns the closest detection to specified point.
         *
         * @param x X coordinate
         * @param y Y coordinate
         * @return Optional containing closest detection, or empty if none match
         */
        public Optional<Detection> closestTo(int x, int y) {
            return detections().stream()
                .min(Comparator.comparingDouble(d -> {
                    int centerX = d.x + d.w / 2;
                    int centerY = d.y + d.h / 2;
                    int dx = centerX - x;
                    int dy = centerY - y;
                    return Math.sqrt(dx * dx + dy * dy);
                }));
        }

        /**
         * Returns count of matching detections.
         *
         * @return Number of detections matching criteria
         */
        public int count() {
            return detections().size();
        }

        /**
         * Checks if any detections match the criteria.
         *
         * @return true if at least one detection matches
         */
        public boolean any() {
            return count() > 0;
        }

        /**
         * Checks if no detections match the criteria.
         *
         * @return true if no detections match
         */
        public boolean none() {
            return count() == 0;
        }

        /**
         * Returns first detection matching criteria.
         *
         * @return Optional containing first detection, or empty if none match
         */
        public Optional<Detection> first() {
            List<Detection> matches = detections();
            return matches.isEmpty() ? Optional.empty() : Optional.of(matches.get(0));
        }
    }

    /**
     * Fluent query builder for filtering classifications.
     */
    public static class ClassificationQuery {
        private final LemonlightResult result;
        private Integer minConfidence;
        private Integer maxConfidence;
        private Integer targetId;

        ClassificationQuery(LemonlightResult result) {
            this.result = result;
        }

        public ClassificationQuery minConfidence(int confidence) {
            this.minConfidence = confidence;
            return this;
        }

        public ClassificationQuery maxConfidence(int confidence) {
            this.maxConfidence = confidence;
            return this;
        }

        public ClassificationQuery targetId(int id) {
            this.targetId = id;
            return this;
        }

        public List<Classification> classifications() {
            return result.getClassifications().stream()
                .filter(c -> minConfidence == null || c.score >= minConfidence)
                .filter(c -> maxConfidence == null || c.score <= maxConfidence)
                .filter(c -> targetId == null || c.targetId == targetId)
                .collect(Collectors.toList());
        }

        public Optional<Classification> best() {
            return classifications().stream()
                .max(Comparator.comparingInt(c -> c.score));
        }

        public Classification bestOrThrow() {
            return best().orElseThrow(() ->
                new IllegalStateException("No classifications matching criteria"));
        }

        public int count() {
            return classifications().size();
        }

        public boolean any() {
            return count() > 0;
        }

        public boolean none() {
            return count() == 0;
        }
    }

    /**
     * Fluent query builder for filtering keypoints.
     */
    public static class KeyPointQuery {
        private final LemonlightResult result;
        private Integer minConfidence;
        private Integer maxConfidence;
        private Integer targetId;
        private BoundingBox withinBounds;

        KeyPointQuery(LemonlightResult result) {
            this.result = result;
        }

        public KeyPointQuery minConfidence(int confidence) {
            this.minConfidence = confidence;
            return this;
        }

        public KeyPointQuery maxConfidence(int confidence) {
            this.maxConfidence = confidence;
            return this;
        }

        public KeyPointQuery targetId(int id) {
            this.targetId = id;
            return this;
        }

        public KeyPointQuery withinBounds(int x, int y, int width, int height) {
            this.withinBounds = new BoundingBox(x, y, width, height);
            return this;
        }

        public List<KeyPoint> keypoints() {
            return result.getKeypoints().stream()
                .filter(k -> minConfidence == null || k.score >= minConfidence)
                .filter(k -> maxConfidence == null || k.score <= maxConfidence)
                .filter(k -> targetId == null || k.targetId == targetId)
                .filter(k -> withinBounds == null || withinBounds.containsPoint(k.x, k.y))
                .collect(Collectors.toList());
        }

        public Optional<KeyPoint> best() {
            return keypoints().stream()
                .max(Comparator.comparingInt(k -> k.score));
        }

        public KeyPoint bestOrThrow() {
            return best().orElseThrow(() ->
                new IllegalStateException("No keypoints matching criteria"));
        }

        public Optional<KeyPoint> closestTo(int x, int y) {
            return keypoints().stream()
                .min(Comparator.comparingDouble(k -> {
                    int dx = k.x - x;
                    int dy = k.y - y;
                    return Math.sqrt(dx * dx + dy * dy);
                }));
        }

        public int count() {
            return keypoints().size();
        }

        public boolean any() {
            return count() > 0;
        }

        public boolean none() {
            return count() == 0;
        }
    }

    /**
     * Helper class representing a bounding box for spatial filtering.
     */
    private static class BoundingBox {
        final int x, y, width, height;

        BoundingBox(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        /**
         * Checks if detection is completely within this bounding box.
         */
        boolean contains(Detection d) {
            return d.x >= x && d.y >= y &&
                   d.x + d.w <= x + width &&
                   d.y + d.h <= y + height;
        }

        /**
         * Checks if point is within this bounding box.
         */
        boolean containsPoint(int px, int py) {
            return px >= x && px <= x + width &&
                   py >= y && py <= y + height;
        }
    }
}
