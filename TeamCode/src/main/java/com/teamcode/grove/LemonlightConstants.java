package com.teamcode.grove;

import com.qualcomm.robotcore.hardware.I2cAddr;

/**
 * Constants for Grove Vision AI V2 (lemonlight) I2C protocol.
 * SenseCraft / SSCMA-Micro binary framing: header 0x10 0x01, then LEN hi/lo (big-endian).
 */

public final class LemonlightConstants {

    public static final String CONFIG_DEVICE_NAME = "lemonlight";

    public static final int I2C_ADDRESS_7BIT = 0x62;
    public static final I2cAddr I2C_ADDR = I2cAddr.create7bit(I2C_ADDRESS_7BIT);

    public static final int CMD_HEADER_HI = 0x10;
    public static final int CMD_HEADER_LO = 0x01;
    public static final int HEADER_LEN = 4;

    public static final int MAX_FRAME_LEN = 512;
    public static final int MAX_READ_LEN = 256;
    public static final int PING_READ_LEN = 32;

    public static final byte[] AT_STAT = "AT+STAT?\r".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    public static final byte[] AT_VER = "AT+VER?\r".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    public static final byte[] AT_ID = "AT+ID?\r".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    public static final int REG_CMD_RESP = 0;

    private LemonlightConstants() {}
}
