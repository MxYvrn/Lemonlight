package com.teamcode.grove;

import com.qualcomm.robotcore.hardware.I2cDeviceSynch;
import com.qualcomm.robotcore.hardware.I2cDeviceSynchDevice;
import com.qualcomm.robotcore.hardware.configuration.annotations.DeviceProperties;
import com.qualcomm.robotcore.hardware.configuration.annotations.I2cDeviceType;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enterprise-grade Grove Vision AI V2 (Lemonlight) I2C driver.
 *
 * <p>This is the enhanced version with:
 * <ul>
 *   <li>Custom exception hierarchy with error codes</li>
 *   <li>Thread-safe buffer management with ThreadLocal</li>
 *   <li>Comprehensive input validation</li>
 *   <li>Structured logging</li>
 *   <li>Performance metrics</li>
 *   <li>Bounds checking on all parsed data</li>
 * </ul>
 *
 * <p><b>Thread Safety:</b> This class is thread-safe for concurrent read operations.
 * Write operations (setModel, setSensor) should be serialized by the caller.
 *
 * @see LemonlightException
 * @see LemonlightMetrics
 * @see LemonlightResult
 */
@I2cDeviceType
@DeviceProperties(name = "Lemonlight (Grove Vision AI V2)", xmlTag = LemonlightConstants.CONFIG_DEVICE_NAME, description = "Grove Vision AI V2 over I2C")
public class Lemonlight extends I2cDeviceSynchDevice<I2cDeviceSynch> {

    private static final int REG = LemonlightConstants.REG_CMD_RESP;
    private static final int HEADER_LEN = LemonlightConstants.HEADER_LEN;
    private static final int CMD_HEADER = LemonlightConstants.CMD_HEADER;

    // ThreadLocal buffer to avoid race conditions
    private static final ThreadLocal<byte[]> packetBuffer = ThreadLocal.withInitial(
        () -> new byte[HEADER_LEN + LemonlightConstants.MAX_FRAME_LEN]
    );

    private String invokeCommand;
    private final LemonlightLogger logger;
    private final LemonlightMetrics metrics;

    // Validation constraints
    private static final int MAX_IMAGE_WIDTH = 1920;
    private static final int MAX_IMAGE_HEIGHT = 1080;
    private static final int MIN_SCORE = 0;
    private static final int MAX_SCORE = 100;
    private static final int MIN_MODEL_ID = 0;
    private static final int MAX_MODEL_ID = 255;
    private static final int MIN_SENSOR_ID = 0;
    private static final int MAX_SENSOR_ID = 255;

    // JSON response code pattern
    private static final Pattern RESPONSE_CODE_PATTERN = Pattern.compile("\"code\":(\\d+)");

    /**
     * Creates a new Lemonlight driver instance.
     *
     * @param deviceClient I2C device client
     * @param deviceClientIsOwned Whether this driver owns the device client
     */
    public Lemonlight(I2cDeviceSynch deviceClient, boolean deviceClientIsOwned) {
        super(deviceClient, deviceClientIsOwned);
        this.deviceClient.setI2cAddress(LemonlightConstants.I2C_ADDR);
        super.registerArmingStateCallback(false);
        this.deviceClient.engage();
        this.invokeCommand = LemonlightConstants.DEFAULT_INVOKE_CMD;
        this.logger = new LemonlightLogger(Lemonlight.class);
        this.metrics = new LemonlightMetrics();

        logger.info("Lemonlight driver created at I2C address 0x{}",
            Integer.toHexString(LemonlightConstants.I2C_ADDRESS_7BIT));
    }

    /**
     * Sets the invoke command for triggering inference.
     *
     * @param cmd Invoke command string (e.g., "AT+INVOKE=1,0,1\r")
     * @throws IllegalArgumentException if cmd is null
     */
    public void setInvokeCommand(String cmd) {
        if (cmd == null) {
            throw new IllegalArgumentException("Invoke command cannot be null");
        }
        this.invokeCommand = cmd;
        logger.info("Invoke command set to: {}", cmd.trim());
    }

    @Override
    public Manufacturer getManufacturer() {
        return Manufacturer.Other;
    }

    @Override
    protected synchronized boolean doInitialize() {
        logger.info("Initializing Lemonlight device...");

        try {
            if (!ping()) {
                logger.error("Initialization failed: ping unsuccessful");
                return false;
            }

            String fwVersion = getFirmwareVersion();
            logger.info("Initialization successful. Firmware: {}",
                fwVersion.isEmpty() ? "unknown" : fwVersion);
            return true;

        } catch (Exception e) {
            logger.error("Initialization failed with exception", e);
            return false;
        }
    }

    @Override
    public String getDeviceName() {
        return "Lemonlight (Grove Vision AI V2)";
    }

    /**
     * Gets the metrics collector for this driver.
     *
     * @return Metrics instance
     */
    public LemonlightMetrics getMetrics() {
        return metrics;
    }

    /**
     * Pings the device to check connectivity.
     *
     * @return true if device responds correctly
     * @throws LemonlightException if communication fails critically
     */
    public boolean ping() {
        logger.debug("Pinging device...");

        try {
            byte[] resp = writePayloadThenRead(
                LemonlightConstants.AT_STAT,
                LemonlightConstants.PING_READ_LEN,
                300
            );

            if (resp == null || resp.length < HEADER_LEN) {
                logger.warn("Ping failed: no response or insufficient data");
                return false;
            }

            if ((resp[0] & 0xFF) != CMD_HEADER) {
                logger.warn("Ping failed: invalid header 0x{}",
                    Integer.toHexString(resp[0] & 0xFF));
                return false;
            }

            logger.debug("Ping successful");
            return true;

        } catch (LemonlightException e) {
            logger.error("Ping failed with exception: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Gets the firmware version from the device.
     *
     * @return Firmware version string, or empty string if unavailable
     */
    public String getFirmwareVersion() {
        logger.debug("Querying firmware version...");

        try {
            byte[] resp = writePayloadThenRead(
                LemonlightConstants.AT_VER,
                LemonlightConstants.MAX_READ_LEN,
                500
            );

            if (resp == null || resp.length <= HEADER_LEN) {
                logger.warn("Failed to get firmware version: no response");
                return "";
            }

            int len = ((resp[2] & 0xFF) << 8) | (resp[3] & 0xFF);
            if (len <= 0 || HEADER_LEN + len > resp.length) {
                logger.warn("Failed to get firmware version: invalid length");
                return "";
            }

            String raw = new String(resp, HEADER_LEN,
                Math.min(len, resp.length - HEADER_LEN), StandardCharsets.US_ASCII);

            // Extract software version from JSON
            if (raw.contains("\"software\"")) {
                int i = raw.indexOf("\"software\"");
                int j = raw.indexOf("\"", i + 12);
                int k = raw.indexOf("\"", j + 1);
                if (k > j) {
                    String version = raw.substring(j + 1, k);
                    logger.debug("Firmware version: {}", version);
                    return version;
                }
            }

            return raw.length() > 0 ? raw : "";

        } catch (Exception e) {
            logger.error("Error querying firmware version", e);
            return "";
        }
    }

    /**
     * Lists all available AI models on the device.
     *
     * @return JSON string with model list, or empty string if unavailable
     */
    public String listModels() {
        logger.debug("Listing available models...");

        try {
            byte[] cmd = "AT+MODELS?\r".getBytes(StandardCharsets.US_ASCII);
            byte[] resp = writePayloadThenRead(cmd, LemonlightConstants.MAX_READ_LEN, 1000);

            if (resp == null || resp.length <= HEADER_LEN) {
                logger.warn("Failed to list models: no response");
                return "";
            }

            int len = ((resp[2] & 0xFF) << 8) | (resp[3] & 0xFF);
            if (len <= 0 || HEADER_LEN + len > resp.length) {
                return "";
            }

            String result = new String(resp, HEADER_LEN,
                Math.min(len, resp.length - HEADER_LEN), StandardCharsets.US_ASCII);

            logger.debug("Models list retrieved: {} bytes", len);
            return result;

        } catch (Exception e) {
            logger.error("Error listing models", e);
            return "";
        }
    }

    /**
     * Loads the specified AI model on the device.
     *
     * @param modelId Model identifier (valid range: 0-255)
     * @return true if model loaded successfully
     * @throws IllegalArgumentException if modelId is out of valid range
     * @throws LemonlightException if device communication fails
     */
    public boolean setModel(int modelId) {
        validateModelId(modelId);

        logger.info("Loading model {}...", modelId);

        try {
            String cmd = String.format("AT+MODEL=%d!\r", modelId);
            byte[] cmdBytes = cmd.getBytes(StandardCharsets.US_ASCII);

            if (cmdBytes.length > LemonlightConstants.MAX_FRAME_LEN) {
                throw new LemonlightException(
                    LemonlightException.ErrorCode.BUFFER_OVERFLOW,
                    LemonlightException.ErrorSeverity.ERROR,
                    "Command exceeds maximum frame length"
                );
            }

            byte[] resp = writePayloadThenRead(cmdBytes,
                LemonlightConstants.MAX_READ_LEN, 1000);

            if (resp == null || resp.length <= HEADER_LEN) {
                throw new LemonlightException(
                    LemonlightException.ErrorCode.TIMEOUT,
                    LemonlightException.ErrorSeverity.ERROR,
                    "No response from setModel command"
                );
            }

            String respStr = new String(resp, HEADER_LEN,
                resp.length - HEADER_LEN, StandardCharsets.US_ASCII);

            boolean success = parseResponseCode(respStr) == 0;

            if (success) {
                logger.info("Model {} loaded successfully", modelId);
            } else {
                logger.warn("Failed to load model {}: {}", modelId, respStr);
            }

            return success;

        } catch (LemonlightException e) {
            logger.error("Error loading model {}", modelId, e);
            throw e;
        }
    }

    /**
     * Configures the sensor input.
     *
     * @param sensorId Sensor identifier (valid range: 0-255)
     * @return true if sensor configured successfully
     * @throws IllegalArgumentException if sensorId is out of valid range
     * @throws LemonlightException if device communication fails
     */
    public boolean setSensor(int sensorId) {
        validateSensorId(sensorId);

        logger.info("Configuring sensor {}...", sensorId);

        try {
            String cmd = String.format("AT+SENSOR=%d!\r", sensorId);
            byte[] cmdBytes = cmd.getBytes(StandardCharsets.US_ASCII);

            byte[] resp = writePayloadThenRead(cmdBytes,
                LemonlightConstants.MAX_READ_LEN, 1000);

            if (resp == null || resp.length <= HEADER_LEN) {
                throw new LemonlightException(
                    LemonlightException.ErrorCode.TIMEOUT,
                    LemonlightException.ErrorSeverity.ERROR,
                    "No response from setSensor command"
                );
            }

            String respStr = new String(resp, HEADER_LEN,
                resp.length - HEADER_LEN, StandardCharsets.US_ASCII);

            boolean success = parseResponseCode(respStr) == 0;

            if (success) {
                logger.info("Sensor {} configured successfully", sensorId);
            } else {
                logger.warn("Failed to configure sensor {}: {}", sensorId, respStr);
            }

            return success;

        } catch (LemonlightException e) {
            logger.error("Error configuring sensor {}", sensorId, e);
            throw e;
        }
    }

    /**
     * Gets device information.
     *
     * @return JSON string with device info, or empty string if unavailable
     */
    public String getDeviceInfo() {
        logger.debug("Querying device info...");

        try {
            byte[] resp = writePayloadThenRead(LemonlightConstants.AT_ID,
                LemonlightConstants.MAX_READ_LEN, 500);

            if (resp == null || resp.length <= HEADER_LEN) {
                return "";
            }

            int len = ((resp[2] & 0xFF) << 8) | (resp[3] & 0xFF);
            if (len <= 0 || HEADER_LEN + len > resp.length) {
                return "";
            }

            return new String(resp, HEADER_LEN,
                Math.min(len, resp.length - HEADER_LEN), StandardCharsets.US_ASCII);

        } catch (Exception e) {
            logger.error("Error querying device info", e);
            return "";
        }
    }

    /**
     * Resets the device buffer.
     */
    public void resetBuffer() {
        logger.debug("Resetting device buffer");

        byte[] scratch = packetBuffer.get();
        scratch[0] = (byte) CMD_HEADER;
        scratch[1] = (byte) LemonlightConstants.CMD_RESET;
        scratch[2] = 0;
        scratch[3] = 0;
        writeRaw(scratch, HEADER_LEN);
    }

    /**
     * Checks how many bytes are available in the device buffer.
     *
     * @return Number of available bytes, or -1 on error
     */
    public int availBytes() {
        byte[] scratch = packetBuffer.get();
        scratch[0] = (byte) CMD_HEADER;
        scratch[1] = (byte) LemonlightConstants.CMD_AVAIL;
        scratch[2] = 0;
        scratch[3] = 0;
        writeRaw(scratch, HEADER_LEN);

        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Interrupted during availBytes");
            return -1;
        }

        byte[] resp = readRaw(8);
        if (resp == null || resp.length < 6) {
            return -1;
        }
        if ((resp[0] & 0xFF) != CMD_HEADER) {
            return -1;
        }

        int avail = ((resp[4] & 0xFF) << 8) | (resp[5] & 0xFF);
        logger.debug("Available bytes: {}", avail);
        return avail;
    }

    /**
     * Reads exactly the specified number of bytes from the device.
     *
     * @param length Number of bytes to read
     * @return Byte array with data, or null on error
     * @throws LemonlightException if read fails
     */
    public byte[] readExact(int length) {
        if (length <= 0 || length > LemonlightConstants.MAX_READ_LEN) {
            throw new IllegalArgumentException(
                String.format("Invalid read length: %d (max: %d)",
                    length, LemonlightConstants.MAX_READ_LEN)
            );
        }

        int readLen = Math.min(length, LemonlightConstants.MAX_READ_LEN);
        byte[] scratch = packetBuffer.get();
        scratch[0] = (byte) CMD_HEADER;
        scratch[1] = (byte) LemonlightConstants.CMD_READ;
        scratch[2] = (byte) (readLen >> 8);
        scratch[3] = (byte) (readLen & 0xFF);
        writeRaw(scratch, HEADER_LEN);

        try {
            Thread.sleep(15);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LemonlightException(
                LemonlightException.ErrorCode.INTERRUPTED,
                LemonlightException.ErrorSeverity.ERROR,
                "Read operation interrupted"
            );
        }

        byte[] resp = readRaw(HEADER_LEN + readLen);
        if (resp == null || resp.length < HEADER_LEN) {
            throw new LemonlightException(
                LemonlightException.ErrorCode.INVALID_RESPONSE,
                LemonlightException.ErrorSeverity.ERROR,
                "Short read response"
            );
        }

        if ((resp[0] & 0xFF) != CMD_HEADER || (resp[1] & 0xFF) != LemonlightConstants.CMD_READ) {
            throw new LemonlightException(
                LemonlightException.ErrorCode.INVALID_RESPONSE,
                LemonlightException.ErrorSeverity.ERROR,
                String.format("Bad header: 0x%02X%02X", resp[0] & 0xFF, resp[1] & 0xFF)
            );
        }

        int declaredLen = ((resp[2] & 0xFF) << 8) | (resp[3] & 0xFF);
        int copy = Math.min(declaredLen, resp.length - HEADER_LEN);

        if (copy <= 0) {
            return new byte[0];
        }

        byte[] out = new byte[copy];
        System.arraycopy(resp, HEADER_LEN, out, 0, copy);
        return out;
    }

    /**
     * Reads a message from the device with timeout.
     *
     * @param maxLen Maximum length to read
     * @param timeoutMs Timeout in milliseconds
     * @return Byte array with message data, or null on timeout
     */
    public byte[] readMessageWithTimeout(int maxLen, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        int avail;

        while ((avail = availBytes()) <= 0 && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(LemonlightConstants.AVAIL_POLL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LemonlightException(
                    LemonlightException.ErrorCode.INTERRUPTED,
                    LemonlightException.ErrorSeverity.ERROR,
                    "Read operation interrupted"
                );
            }
        }

        if (avail <= 0) {
            throw new LemonlightException(
                LemonlightException.ErrorCode.TIMEOUT,
                LemonlightException.ErrorSeverity.ERROR,
                String.format("Timeout waiting for data (%dms)", timeoutMs)
            );
        }

        int readLen = Math.min(avail, Math.min(maxLen, LemonlightConstants.MAX_READ_LEN));
        return readExact(readLen);
    }

    /**
     * Triggers one inference cycle on the device.
     */
    public void invokeOnce() {
        logger.debug("Invoking inference");
        resetBuffer();
        byte[] cmdBytes = invokeCommand.getBytes(StandardCharsets.US_ASCII);
        writePayload(cmdBytes);
    }

    /**
     * Reads raw frame data from the device.
     *
     * @param maxLen Maximum length to read
     * @return Byte array with frame data
     */
    public byte[] readFrameRaw(int maxLen) {
        return readMessageWithTimeout(maxLen, LemonlightConstants.READ_TIMEOUT_MS);
    }

    /**
     * Reads one complete inference result from the device.
     *
     * <p>This method triggers inference, waits for the result, and parses
     * all detection data including bounding boxes, classifications, and keypoints.
     *
     * @return Structured result with all detection data
     * @throws LemonlightException if a critical error occurs
     */
    public LemonlightResult readInference() {
        long startTime = System.nanoTime();
        logger.debug("Starting inference read");

        try {
            invokeOnce();
            byte[] raw = readMessageWithTimeout(
                LemonlightConstants.MAX_READ_LEN,
                LemonlightConstants.INVOKE_TIMEOUT_MS
            );

            long ts = System.currentTimeMillis();
            long latencyMs = (System.nanoTime() - startTime) / 1_000_000;

            boolean valid = raw != null && raw.length > 0;
            int count = 0;
            List<LemonlightResult.Detection> dets = new ArrayList<>();
            List<LemonlightResult.Classification> classes = new ArrayList<>();
            List<LemonlightResult.KeyPoint> points = new ArrayList<>();

            if (valid) {
                try {
                    parseBoxesFromJson(raw, 0, raw.length, dets);
                    parseClassesFromJson(raw, 0, raw.length, classes);
                    parsePointsFromJson(raw, 0, raw.length, points);
                    count = dets.size() + classes.size() + points.size();

                    logger.debug("Inference completed: {} detections, {} classes, {} keypoints, {}ms",
                        dets.size(), classes.size(), points.size(), latencyMs);

                } catch (Exception e) {
                    logger.error("JSON parse failed", e);
                    metrics.incrementParseError();
                    valid = false;
                }
            }

            metrics.recordReadLatency(latencyMs, valid);
            if (valid) {
                metrics.recordDetectionCount(count);
            } else {
                metrics.recordError("Invalid inference result");
            }

            return new LemonlightResult(raw, "", count, dets, classes, points, ts, valid);

        } catch (LemonlightException e) {
            long errorLatencyMs = (System.nanoTime() - startTime) / 1_000_000;
            metrics.recordReadLatency(errorLatencyMs, false);
            metrics.recordError(e.getUserMessage());
            logger.error("Inference read failed after {}ms: {}", errorLatencyMs, e.getMessage());
            throw e;

        } catch (Exception e) {
            long errorLatencyMs = (System.nanoTime() - startTime) / 1_000_000;
            metrics.recordReadLatency(errorLatencyMs, false);
            metrics.recordError(e.getMessage());
            logger.error("Unexpected error during inference", e);

            throw new LemonlightException(
                LemonlightException.ErrorCode.DEVICE_NOT_READY,
                LemonlightException.ErrorSeverity.ERROR,
                "Inference failed",
                e
            );
        }
    }

    // Private helper methods

    private void writePayload(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return;
        }

        int len = Math.min(payload.length, LemonlightConstants.MAX_FRAME_LEN);
        byte[] scratch = packetBuffer.get();
        scratch[0] = (byte) CMD_HEADER;
        scratch[1] = (byte) LemonlightConstants.CMD_WRITE;
        scratch[2] = (byte) (len >> 8);
        scratch[3] = (byte) (len & 0xFF);
        System.arraycopy(payload, 0, scratch, HEADER_LEN, len);
        writeRaw(scratch, HEADER_LEN + len);
    }

    private byte[] writePayloadThenRead(byte[] payload, int maxReadLen, long timeoutMs) {
        writePayload(payload);

        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LemonlightException(
                LemonlightException.ErrorCode.INTERRUPTED,
                LemonlightException.ErrorSeverity.ERROR,
                "Operation interrupted"
            );
        }

        return readMessageWithTimeout(maxReadLen, timeoutMs);
    }

    private void writeRaw(byte[] packet, int length) {
        if (packet == null) {
            throw new IllegalArgumentException("Packet cannot be null");
        }
        if (length <= 0 || length > packet.length) {
            throw new IllegalArgumentException(
                String.format("Invalid length: %d (packet size: %d)", length, packet.length)
            );
        }

        try {
            if (length == packet.length) {
                deviceClient.write(REG, packet);
            } else {
                byte[] slice = new byte[length];
                System.arraycopy(packet, 0, slice, 0, length);
                deviceClient.write(REG, slice);
            }

            metrics.incrementI2cWriteSuccess();

        } catch (Exception e) {
            metrics.incrementI2cWriteFailure();
            logger.error("I2C write failed: length={}", length, e);

            throw new LemonlightException(
                LemonlightException.ErrorCode.I2C_COMMUNICATION_ERROR,
                LemonlightException.ErrorSeverity.ERROR,
                "I2C write failed",
                e
            );
        }
    }

    private byte[] readRaw(int maxLen) {
        if (maxLen <= 0) {
            throw new IllegalArgumentException("Read length must be positive");
        }

        try {
            byte[] result = deviceClient.read(REG, maxLen);
            metrics.incrementI2cReadSuccess();
            return result;

        } catch (Exception e) {
            metrics.incrementI2cReadFailure();
            logger.error("I2C read failed: maxLen={}", maxLen, e);

            throw new LemonlightException(
                LemonlightException.ErrorCode.I2C_COMMUNICATION_ERROR,
                LemonlightException.ErrorSeverity.ERROR,
                "I2C read failed",
                e
            );
        }
    }

    private void parseBoxesFromJson(byte[] raw, int offset, int len,
                                    List<LemonlightResult.Detection> out) {
        if (!validateParseParams(raw, offset, len)) return;

        String s = new String(raw, offset, len, StandardCharsets.US_ASCII);
        int i = s.indexOf("\"boxes\"");
        if (i < 0) return;

        i = s.indexOf("[", i);
        if (i < 0) return;

        int depth = 0;
        int start = -1;

        for (int j = i; j < s.length(); j++) {
            char c = s.charAt(j);
            if (c == '[') {
                depth++;
                if (depth == 2) start = j + 1;
            } else if (c == ']') {
                if (depth == 2 && start > 0) {
                    String inner = s.substring(start, j);
                    parseOneBox(inner, out);
                }
                depth--;
            }
        }
    }

    private void parseOneBox(String inner, List<LemonlightResult.Detection> out) {
        String[] parts = inner.split(",");
        if (parts.length < 6) {
            logger.debug("Invalid box format: expected 6 parts, got {}", parts.length);
            return;
        }

        try {
            int x = parseAndValidateCoordinate(parts[0].trim(), "x", MAX_IMAGE_WIDTH);
            int y = parseAndValidateCoordinate(parts[1].trim(), "y", MAX_IMAGE_HEIGHT);
            int w = parseAndValidateSize(parts[2].trim(), "width", MAX_IMAGE_WIDTH);
            int h = parseAndValidateSize(parts[3].trim(), "height", MAX_IMAGE_HEIGHT);
            int score = parseAndValidateScore(parts[4].trim());
            int targetId = parseAndValidateInt(parts[5].trim(), "targetId", 0, 255);

            // Additional semantic validation
            if (x + w > MAX_IMAGE_WIDTH || y + h > MAX_IMAGE_HEIGHT) {
                logger.warn("Box extends beyond image bounds: x={}, y={}, w={}, h={}", x, y, w, h);
                return;
            }

            out.add(new LemonlightResult.Detection(x, y, w, h, score, targetId));

        } catch (NumberFormatException e) {
            logger.warn("Failed to parse box: {}", inner);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid box values: {}", e.getMessage());
        }
    }

    private void parseClassesFromJson(byte[] raw, int offset, int len,
                                      List<LemonlightResult.Classification> out) {
        if (!validateParseParams(raw, offset, len)) return;

        String s = new String(raw, offset, len, StandardCharsets.US_ASCII);
        int i = s.indexOf("\"classes\"");
        if (i < 0) return;

        i = s.indexOf("[", i);
        if (i < 0) return;

        int depth = 0;
        int start = -1;

        for (int j = i; j < s.length(); j++) {
            char c = s.charAt(j);
            if (c == '[') {
                depth++;
                if (depth == 2) start = j + 1;
            } else if (c == ']') {
                if (depth == 2 && start > 0) {
                    String inner = s.substring(start, j);
                    parseOneClass(inner, out);
                }
                depth--;
            }
        }
    }

    private void parseOneClass(String inner, List<LemonlightResult.Classification> out) {
        String[] parts = inner.split(",");
        if (parts.length < 2) {
            logger.debug("Invalid class format: expected 2 parts, got {}", parts.length);
            return;
        }

        try {
            int targetId = parseAndValidateInt(parts[0].trim(), "targetId", 0, 255);
            int score = parseAndValidateScore(parts[1].trim());

            out.add(new LemonlightResult.Classification(targetId, score));

        } catch (NumberFormatException e) {
            logger.warn("Failed to parse class: {}", inner);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid class values: {}", e.getMessage());
        }
    }

    private void parsePointsFromJson(byte[] raw, int offset, int len,
                                     List<LemonlightResult.KeyPoint> out) {
        if (!validateParseParams(raw, offset, len)) return;

        String s = new String(raw, offset, len, StandardCharsets.US_ASCII);
        int i = s.indexOf("\"points\"");
        if (i < 0) return;

        i = s.indexOf("[", i);
        if (i < 0) return;

        int depth = 0;
        int start = -1;

        for (int j = i; j < s.length(); j++) {
            char c = s.charAt(j);
            if (c == '[') {
                depth++;
                if (depth == 2) start = j + 1;
            } else if (c == ']') {
                if (depth == 2 && start > 0) {
                    String inner = s.substring(start, j);
                    parseOnePoint(inner, out);
                }
                depth--;
            }
        }
    }

    private void parseOnePoint(String inner, List<LemonlightResult.KeyPoint> out) {
        String[] parts = inner.split(",");
        if (parts.length < 4) {
            logger.debug("Invalid point format: expected 4 parts, got {}", parts.length);
            return;
        }

        try {
            int x = parseAndValidateCoordinate(parts[0].trim(), "x", MAX_IMAGE_WIDTH);
            int y = parseAndValidateCoordinate(parts[1].trim(), "y", MAX_IMAGE_HEIGHT);
            int score = parseAndValidateScore(parts[2].trim());
            int targetId = parseAndValidateInt(parts[3].trim(), "targetId", 0, 255);

            out.add(new LemonlightResult.KeyPoint(x, y, score, targetId));

        } catch (NumberFormatException e) {
            logger.warn("Failed to parse keypoint: {}", inner);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid keypoint values: {}", e.getMessage());
        }
    }

    // Validation helper methods

    private boolean validateParseParams(byte[] raw, int offset, int len) {
        if (raw == null || offset < 0 || len <= 0 || offset + len > raw.length) {
            logger.warn("Invalid parse parameters: offset={}, len={}, arrayLen={}",
                offset, len, raw == null ? "null" : raw.length);
            return false;
        }
        return true;
    }

    private void validateModelId(int modelId) {
        if (modelId < MIN_MODEL_ID || modelId > MAX_MODEL_ID) {
            throw new IllegalArgumentException(
                String.format("Model ID must be in range [%d, %d], got: %d",
                    MIN_MODEL_ID, MAX_MODEL_ID, modelId)
            );
        }
    }

    private void validateSensorId(int sensorId) {
        if (sensorId < MIN_SENSOR_ID || sensorId > MAX_SENSOR_ID) {
            throw new IllegalArgumentException(
                String.format("Sensor ID must be in range [%d, %d], got: %d",
                    MIN_SENSOR_ID, MAX_SENSOR_ID, sensorId)
            );
        }
    }

    private int parseAndValidateCoordinate(String value, String name, int max) {
        int val = Integer.parseInt(value);
        if (val < 0 || val > max) {
            throw new IllegalArgumentException(
                String.format("%s coordinate out of range [0, %d]: %d", name, max, val)
            );
        }
        return val;
    }

    private int parseAndValidateSize(String value, String name, int max) {
        int val = Integer.parseInt(value);
        if (val < 1 || val > max) {
            throw new IllegalArgumentException(
                String.format("%s out of range [1, %d]: %d", name, max, val)
            );
        }
        return val;
    }

    private int parseAndValidateScore(String value) {
        int score = Integer.parseInt(value);
        if (score < MIN_SCORE || score > MAX_SCORE) {
            throw new IllegalArgumentException(
                String.format("Score must be in range [%d, %d]: %d", MIN_SCORE, MAX_SCORE, score)
            );
        }
        return score;
    }

    private int parseAndValidateInt(String value, String name, int min, int max) {
        int val = Integer.parseInt(value);
        if (val < min || val > max) {
            throw new IllegalArgumentException(
                String.format("%s must be in range [%d, %d]: %d", name, min, max, val)
            );
        }
        return val;
    }

    private int parseResponseCode(String json) {
        if (json == null || json.isEmpty()) {
            return -1;
        }

        Matcher matcher = RESPONSE_CODE_PATTERN.matcher(json);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                logger.warn("Failed to parse response code from: {}", json);
                return -1;
            }
        }

        return -1;
    }
}
