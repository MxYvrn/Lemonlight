package com.teamcode.grove;

import com.qualcomm.robotcore.hardware.I2cAddr;

/**
 * Constants for Grove Vision AI V2 (lemonlight) I2C protocol.
 * SenseCraft Local Device framing: 0x10 CMD LEN_HI LEN_LO [payload].
 * READ=0x01, WRITE=0x02, AVAIL=0x03, RESET=0x06.
 */

public final class LemonlightConstants {

    public static final String CONFIG_DEVICE_NAME = "lemonlight";

    public static final int I2C_ADDRESS_7BIT = 0x62;
    public static final I2cAddr I2C_ADDR = I2cAddr.create7bit(I2C_ADDRESS_7BIT);

    public static final int CMD_HEADER = 0x10;
    public static final int CMD_READ = 0x01;
    public static final int CMD_WRITE = 0x02;
    public static final int CMD_AVAIL = 0x03;
    public static final int CMD_RESET = 0x06;
    public static final int HEADER_LEN = 4;

    public static final int MAX_FRAME_LEN = 512;
    public static final int MAX_READ_LEN = 256;
    public static final int PING_READ_LEN = 32;

    public static final int AVAIL_POLL_MS = 15;
    public static final long READ_TIMEOUT_MS = 2000;
    public static final long INVOKE_TIMEOUT_MS = 3000;

    public static final String DEFAULT_INVOKE_CMD = "AT+INVOKE=1,0,1\r";

    public static final byte[] AT_STAT = "AT+STAT?\r".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    public static final byte[] AT_VER = "AT+VER?\r".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    public static final byte[] AT_ID = "AT+ID?\r".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    public static final int REG_CMD_RESP = 0;

    private LemonlightConstants() {}
}
