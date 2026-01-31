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
    private static final int CMD_HEADER_HI = LemonlightConstants.CMD_HEADER_HI;
    private static final int CMD_HEADER_LO = LemonlightConstants.CMD_HEADER_LO;

    private String lastError;

    public Lemonlight(I2cDeviceSynch deviceClient, boolean deviceClientIsOwned) {
        super(deviceClient, deviceClientIsOwned);
        this.deviceClient.setI2cAddress(LemonlightConstants.I2C_ADDR);
        super.registerArmingStateCallback(false);
        this.deviceClient.engage();
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
        byte[] resp = sendCommand(LemonlightConstants.AT_STAT, LemonlightConstants.PING_READ_LEN);
        if (resp == null || resp.length < HEADER_LEN) return false;
        if ((resp[0] & 0xFF) != CMD_HEADER_HI || (resp[1] & 0xFF) != CMD_HEADER_LO) return false;
        return true;
    }

    public String getFirmwareVersion() {
        byte[] resp = sendCommand(LemonlightConstants.AT_VER, LemonlightConstants.MAX_READ_LEN);
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

    public byte[] readFrameRaw(int maxLen) {
        byte[] resp = sendCommand(LemonlightConstants.AT_ID, Math.min(maxLen, LemonlightConstants.MAX_READ_LEN));
        if (resp == null || resp.length == 0) return new byte[0];
        return resp;
    }

    public LemonlightResult readInference() {
        byte[] raw = readFrameRaw(LemonlightConstants.MAX_READ_LEN);
        long ts = System.currentTimeMillis();
        boolean valid = raw != null && raw.length > 0;
        int count = 0;
        List<LemonlightResult.Detection> dets = new ArrayList<>();
        if (valid) {
            int payloadStart = (raw.length >= HEADER_LEN && (raw[0] & 0xFF) == CMD_HEADER_HI && (raw[1] & 0xFF) == CMD_HEADER_LO) ? HEADER_LEN : 0;
            parseBoxesFromJson(raw, payloadStart, raw.length - payloadStart, dets);
            count = dets.size();
        }
        return new LemonlightResult(raw, "", count, dets, ts, valid);
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

    private byte[] sendCommand(byte[] cmd, int maxReadLen) {
        if (cmd == null || cmd.length == 0) return null;
        int len = cmd.length;
        byte[] packet = new byte[HEADER_LEN + len];
        packet[0] = (byte) CMD_HEADER_HI;
        packet[1] = (byte) CMD_HEADER_LO;
        packet[2] = (byte) (len >> 8);
        packet[3] = (byte) (len & 0xFF);
        System.arraycopy(cmd, 0, packet, HEADER_LEN, len);
        try {
            deviceClient.write(REG, packet);
            Thread.sleep(20);
            return deviceClient.read(REG, maxReadLen);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            lastError = e.getMessage();
            return null;
        } catch (Exception e) {
            lastError = e.getMessage();
            return null;
        }
    }
}
