package com.teamcode.grove;

import com.qualcomm.robotcore.hardware.I2cDeviceSynch;
import com.qualcomm.robotcore.hardware.I2cDeviceSynchDevice;
import com.qualcomm.robotcore.hardware.configuration.annotations.DeviceProperties;
import com.qualcomm.robotcore.hardware.configuration.annotations.I2cDeviceType;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@I2cDeviceType
@DeviceProperties(name = "Lemonlight (Grove Vision AI V2)", xmlTag = LemonlightConstants.CONFIG_DEVICE_NAME, description = "Grove Vision AI V2 over I2C")
public class Lemonlight extends I2cDeviceSynchDevice<I2cDeviceSynch> {

    private static final int REG = LemonlightConstants.REG_CMD_RESP;
    private static final int HEADER_LEN = LemonlightConstants.HEADER_LEN;
    private static final int CMD_HEADER = LemonlightConstants.CMD_HEADER;

    private String lastError;
    private String invokeCommand;
    private final byte[] packetScratch;

    public Lemonlight(I2cDeviceSynch deviceClient, boolean deviceClientIsOwned) {
        super(deviceClient, deviceClientIsOwned);
        this.deviceClient.setI2cAddress(LemonlightConstants.I2C_ADDR);
        super.registerArmingStateCallback(false);
        this.deviceClient.engage();
        this.invokeCommand = LemonlightConstants.DEFAULT_INVOKE_CMD;
        this.packetScratch = new byte[HEADER_LEN + LemonlightConstants.MAX_FRAME_LEN];
    }

    public void setInvokeCommand(String cmd) {
        this.invokeCommand = cmd != null ? cmd : LemonlightConstants.DEFAULT_INVOKE_CMD;
    }

    @Override
    public Manufacturer getManufacturer() {
        return Manufacturer.Other;
    }

    @Override
    protected synchronized boolean doInitialize() {
        lastError = null;
        if (!ping()) {
            lastError = "ping failed";
            return false;
        }
        return true;
    }

    @Override
    public String getDeviceName() {
        return "Lemonlight (Grove Vision AI V2)";
    }

    public String getLastError() {
        return lastError;
    }

    public boolean ping() {
        byte[] resp = writePayloadThenRead(LemonlightConstants.AT_STAT, LemonlightConstants.PING_READ_LEN, 300);
        if (resp == null || resp.length < HEADER_LEN) return false;
        if ((resp[0] & 0xFF) != CMD_HEADER) return false;
        return true;
    }

    public String getFirmwareVersion() {
        byte[] resp = writePayloadThenRead(LemonlightConstants.AT_VER, LemonlightConstants.MAX_READ_LEN, 500);
        if (resp == null || resp.length <= HEADER_LEN) return "";
        int len = ((resp[2] & 0xFF) << 8) | (resp[3] & 0xFF);
        if (len <= 0 || HEADER_LEN + len > resp.length) return "";
        String raw = new String(resp, HEADER_LEN, Math.min(len, resp.length - HEADER_LEN), StandardCharsets.US_ASCII);
        if (raw.contains("\"software\"")) {
            int i = raw.indexOf("\"software\"");
            int j = raw.indexOf("\"", i + 12);
            int k = raw.indexOf("\"", j + 1);
            if (k > j) return raw.substring(j + 1, k);
        }
        return raw.length() > 0 ? raw : "";
    }

    public void resetBuffer() {
        packetScratch[0] = (byte) CMD_HEADER;
        packetScratch[1] = (byte) LemonlightConstants.CMD_RESET;
        packetScratch[2] = 0;
        packetScratch[3] = 0;
        writeRaw(packetScratch, HEADER_LEN);
    }

    public int availBytes() {
        packetScratch[0] = (byte) CMD_HEADER;
        packetScratch[1] = (byte) LemonlightConstants.CMD_AVAIL;
        packetScratch[2] = 0;
        packetScratch[3] = 0;
        writeRaw(packetScratch, HEADER_LEN);
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            lastError = "interrupted";
            return -1;
        }
        byte[] resp = readRaw(8);
        if (resp == null || resp.length < 6) return -1;
        if ((resp[0] & 0xFF) != CMD_HEADER) return -1;
        return ((resp[4] & 0xFF) << 8) | (resp[5] & 0xFF);
    }

    public byte[] readExact(int length) {
        int readLen = Math.min(length, LemonlightConstants.MAX_READ_LEN);
        packetScratch[0] = (byte) CMD_HEADER;
        packetScratch[1] = (byte) LemonlightConstants.CMD_READ;
        packetScratch[2] = (byte) (readLen >> 8);
        packetScratch[3] = (byte) (readLen & 0xFF);
        writeRaw(packetScratch, HEADER_LEN);
        try {
            Thread.sleep(15);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            lastError = "interrupted";
            return null;
        }
        byte[] resp = readRaw(HEADER_LEN + readLen);
        if (resp == null || resp.length < HEADER_LEN) {
            lastError = "short read";
            return null;
        }
        if ((resp[0] & 0xFF) != CMD_HEADER || (resp[1] & 0xFF) != LemonlightConstants.CMD_READ) {
            lastError = "bad header";
            return null;
        }
        int declaredLen = ((resp[2] & 0xFF) << 8) | (resp[3] & 0xFF);
        int copy = Math.min(declaredLen, resp.length - HEADER_LEN);
        if (copy <= 0) return new byte[0];
        byte[] out = new byte[copy];
        System.arraycopy(resp, HEADER_LEN, out, 0, copy);
        return out;
    }

    public byte[] readMessageWithTimeout(int maxLen, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        int avail;
        while ((avail = availBytes()) <= 0 && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(LemonlightConstants.AVAIL_POLL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                lastError = "interrupted";
                return null;
            }
        }
        if (avail <= 0) {
            lastError = "timeout waiting for AVAIL";
            return null;
        }
        int readLen = Math.min(avail, Math.min(maxLen, LemonlightConstants.MAX_READ_LEN));
        return readExact(readLen);
    }

    public void invokeOnce() {
        resetBuffer();
        byte[] cmdBytes = invokeCommand.getBytes(StandardCharsets.US_ASCII);
        writePayload(cmdBytes);
    }

    public byte[] readFrameRaw(int maxLen) {
        return readMessageWithTimeout(maxLen, LemonlightConstants.READ_TIMEOUT_MS);
    }

    public LemonlightResult readInference() {
        lastError = null;
        invokeOnce();
        byte[] raw = readMessageWithTimeout(LemonlightConstants.MAX_READ_LEN, LemonlightConstants.INVOKE_TIMEOUT_MS);
        long ts = System.currentTimeMillis();
        boolean valid = raw != null && raw.length > 0;
        int count = 0;
        List<LemonlightResult.Detection> dets = new ArrayList<>();
        if (valid) {
            try {
                parseBoxesFromJson(raw, 0, raw.length, dets);
                count = dets.size();
            } catch (Exception e) {
                lastError = "json parse failed";
                valid = false;
            }
        } else if (lastError == null) {
            lastError = "no response";
        }
        return new LemonlightResult(raw, "", count, dets, ts, valid);
    }

    private void writePayload(byte[] payload) {
        if (payload == null || payload.length == 0) return;
        int len = Math.min(payload.length, LemonlightConstants.MAX_FRAME_LEN);
        packetScratch[0] = (byte) CMD_HEADER;
        packetScratch[1] = (byte) LemonlightConstants.CMD_WRITE;
        packetScratch[2] = (byte) (len >> 8);
        packetScratch[3] = (byte) (len & 0xFF);
        System.arraycopy(payload, 0, packetScratch, HEADER_LEN, len);
        writeRaw(packetScratch, HEADER_LEN + len);
    }

    private byte[] writePayloadThenRead(byte[] payload, int maxReadLen, long timeoutMs) {
        writePayload(payload);
        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            lastError = "interrupted";
            return null;
        }
        return readMessageWithTimeout(maxReadLen, timeoutMs);
    }

    private void writeRaw(byte[] packet, int length) {
        try {
            if (length <= 0 || length > packet.length) return;
            if (length == packet.length) {
                deviceClient.write(REG, packet);
            } else {
                byte[] slice = new byte[length];
                System.arraycopy(packet, 0, slice, 0, length);
                deviceClient.write(REG, slice);
            }
        } catch (Exception e) {
            lastError = e.getMessage();
        }
    }

    private byte[] readRaw(int maxLen) {
        try {
            return deviceClient.read(REG, maxLen);
        } catch (Exception e) {
            lastError = e.getMessage();
            return null;
        }
    }

    private void parseBoxesFromJson(byte[] raw, int offset, int len, List<LemonlightResult.Detection> out) {
        if (offset < 0 || len <= 0 || offset + len > raw.length) return;
        String s = new String(raw, offset, len, StandardCharsets.US_ASCII);
        int i = s.indexOf("\"boxes\"");
        if (i < 0) return;
        i = s.indexOf("[", i);
        if (i < 0) return;
        int depth = 0;
        int start = -1;
        for (int j = i; j < s.length(); j++) {
            char c = s.charAt(j);
            if (c == '[') { depth++; if (depth == 2) start = j + 1; }
            else if (c == ']') {
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
        if (parts.length < 6) return;
        try {
            int x = Integer.parseInt(parts[0].trim());
            int y = Integer.parseInt(parts[1].trim());
            int w = Integer.parseInt(parts[2].trim());
            int h = Integer.parseInt(parts[3].trim());
            int score = Integer.parseInt(parts[4].trim());
            int targetId = Integer.parseInt(parts[5].trim());
            out.add(new LemonlightResult.Detection(x, y, w, h, score, targetId));
        } catch (NumberFormatException ignored) {}
    }
}
