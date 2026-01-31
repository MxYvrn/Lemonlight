package com.teamcode.grove;

import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.I2cAddr;
import com.qualcomm.robotcore.hardware.I2cDevice;

import org.firstinspires.ftc.robotcore.external.Telemetry;

public class GroveVisionI2CHelper {

    public static Lemonlight bindLemonlight(HardwareMap hardwareMap, Telemetry telemetry) {
        return bindLemonlight(hardwareMap, telemetry, LemonlightConstants.CONFIG_DEVICE_NAME);
    }

    public static Lemonlight bindLemonlight(HardwareMap hardwareMap, Telemetry telemetry, String deviceName) {
        telemetry.addLine("Binding I2C device '" + deviceName + "'...");
        telemetry.update();
        
        // init-1 (Tron:Ares)
        Lemonlight lemonlight = null;
        try {
            lemonlight = hardwareMap.get(Lemonlight.class, deviceName);
        } catch (Exception e) {
            lemonlight = null;
        }
        
        // checking whether lemon is there
        if (lemonlight == null) {
            telemetry.addLine("ACTIVE Robot Configuration does not include an I2C device named 'lemonlight'.");
            telemetry.addData("Error", "Add an I2C device, type 'Lemonlight (Grove Vision AI V2)', name: " + deviceName);
            telemetry.update();
            return null;
        }
        // init-2 (Tron:Ares)
        lemonlight.initialize();
        return lemonlight;
    }
    
    
    // Default I2C 7-bit address for Grove Vision AI V2
    private static final int DEFAULT_I2C_ADDRESS = 0x62;
    
    private final I2cDevice device;
    private final I2cAddr i2cAddr;
    private boolean initialized = false;

    //Constructor with default I2C address
    public GroveVisionI2CHelper(HardwareMap hardwareMap, String deviceName) {
        this(hardwareMap, deviceName, DEFAULT_I2C_ADDRESS);
    }

    // Constructor with custom I2C address (unnecssary 4 now)
    public GroveVisionI2CHelper(HardwareMap hardwareMap, String deviceName, int i2cAddress) {
            device = hardwareMap.get(I2cDevice.class, deviceName);
            i2cAddr = I2cAddr.create7bit(i2cAddress);
            initialized = true; }

   
    public boolean isInitialized() {
        return initialized;
    }

    // Get the I2cDevice instance
    public I2cDevice getDevice() {
        return device;
    }

    // write byte to register
    @SuppressWarnings("deprecation")
    public void write8(int register, byte value) {
        device.enableI2cWriteMode(i2cAddr, register, 1);
        byte[] writeBuffer = device.getI2cWriteCache();
        writeBuffer[0] = value;
        device.writeI2cCacheToModule();
    }

    // Overload write8 to write an integer value
    public void write8(int register, int value) {
        write8(register, (byte) (value & 0xFF));
    }

    //reads a byte from register
    @SuppressWarnings("deprecation")
    public byte read8(int register) {
        device.enableI2cReadMode(i2cAddr, register, 1);
        device.readI2cCacheFromModule();
        byte[] readBuffer = device.getCopyOfReadBuffer();
        return readBuffer[0];
    }

    //reads multiple bytes from register
    @SuppressWarnings("deprecation")
    public byte[] read(int register, int length) {

        device.enableI2cReadMode(i2cAddr, register, length);
        device.readI2cCacheFromModule();
        byte[] readBuffer = device.getCopyOfReadBuffer();
        byte[] result = new byte[length];
        System.arraycopy(readBuffer, 0, result, 0, length);
        return result;
    }

    //reads and writes aka combination of above functions above
    public byte[] writeRead(int commandRegister, byte commandByte, int readRegister, int readLength) {
        
        if (!initialized) {
            throw new IllegalStateException("I2C device not initialized");
        }
        
        try {
            write8(commandRegister, commandByte);
            // sleep could be added here, if needed
            return read(readRegister, readLength);
        } catch (Exception e) {
            return null;
        }
    }
}