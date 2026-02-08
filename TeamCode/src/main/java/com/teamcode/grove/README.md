# Lemonlight Driver (Grove Vision AI V2)

Complete FTC I2C driver for Grove Vision AI V2 vision sensor, enabling AI-powered object detection, classification, and keypoint tracking for autonomous robot control.

---

## 📋 Table of Contents

- [Overview](#overview)
- [Hardware Setup](#hardware-setup)
- [Robot Configuration](#robot-configuration)
- [Quick Start](#quick-start)
- [API Reference](#api-reference)
- [Example OpModes](#example-opmodes)
- [Troubleshooting](#troubleshooting)
- [Technical Details](#technical-details)

---

## Overview

**Lemonlight** is a custom I2C driver that enables FTC robots to communicate with the Grove Vision AI V2 sensor. The driver implements the SSCMA (Seeed SenseCraft Model Assistant) protocol over I2C, providing:

- **Object Detection**: Bounding boxes with position, size, and confidence scores
- **Classification**: Image classification results with class IDs and scores
- **Keypoint Detection**: Landmark/pose detection with coordinate positions
- **Model Management**: Load and switch between AI models on the device
- **Real-time Inference**: Trigger vision inference and retrieve results

### Key Features

✅ Full FTC I2C framework integration
✅ Low-level packet protocol (READ, WRITE, AVAIL, RESET)
✅ AT command interface with JSON response parsing
✅ Thread-safe operation with error handling
✅ Multiple test and example OpModes
✅ Support for boxes, classes, and keypoints

---

## Hardware Setup

### Required Hardware

1. **Grove Vision AI V2** sensor
   - Default I2C Address: `0x62`
   - Supports 400 kHz I2C clock
   - Pre-loaded with SSCMA firmware

2. **FTC Control Hub or Expansion Hub**
   - I2C port for sensor connection

### Wiring

Connect the Grove Vision AI V2 to the Control Hub I2C port:

```
Grove Vision AI V2          Control Hub I2C Port
-------------------         --------------------
VCC (Red)          →        3.3V or 5V
GND (Black)        →        GND
SDA (White)        →        SDA
SCL (Yellow)       →        SCL
```

**Important**: Ensure proper I2C pull-up resistors are present (usually built into the Control Hub).

---

## Robot Configuration

### Step 1: Add I2C Device

1. Open the **Robot Controller app**
2. Go to **Configure Robot** → Select your configuration
3. Select the **I2C Bus** where Lemonlight is connected
4. Tap **Add** → Choose **I2C Device**
5. Select device type: **"Lemonlight (Grove Vision AI V2)"**
6. Name the device: `lemonlight` (or custom name)
7. **Save** the configuration

### Step 2: Verify Configuration

The device should appear in your configuration as:
```
I2C Bus 0
  └─ lemonlight (Lemonlight (Grove Vision AI V2)) @ 0x62
```

---

## Quick Start

### Basic Example

```java
import com.teamcode.grove.*;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

@TeleOp(name = "Lemonlight Quick Test")
public class QuickTest extends LinearOpMode {
    @Override
    public void runOpMode() {
        // Initialize Lemonlight
        Lemonlight lemonlight = GroveVisionI2CHelper.bindLemonlight(
            hardwareMap, telemetry, "lemonlight"
        );

        if (lemonlight == null) {
            telemetry.addLine("Lemonlight not found!");
            telemetry.update();
            return;
        }

        // Verify connection
        telemetry.addData("Connected", lemonlight.ping());
        telemetry.addData("Firmware", lemonlight.getFirmwareVersion());
        telemetry.update();

        waitForStart();

        // Main loop - read vision data
        while (opModeIsActive()) {
            LemonlightResult result = lemonlight.readInference();

            if (result.isValid()) {
                telemetry.addData("Detections", result.getDetectionCount());

                for (LemonlightResult.Detection det : result.getDetections()) {
                    telemetry.addData("Box", "x=%d y=%d conf=%d%%",
                        det.x, det.y, det.score);
                }
            }

            telemetry.update();
            sleep(100);  // 10 Hz update rate
        }
    }
}
```

---

## API Reference

### Lemonlight Class

Main I2C driver class extending `I2cDeviceSynchDevice<I2cDeviceSynch>`.

#### Initialization Methods

```java
// Check if device is responding
boolean ping()

// Get firmware version string
String getFirmwareVersion()

// Get device ID and info (JSON)
String getDeviceInfo()
```

#### Vision Methods

```java
// Trigger inference and read result
LemonlightResult readInference()

// Set custom invoke command (default: "AT+INVOKE=1,0,1\r")
void setInvokeCommand(String cmd)
```

#### Configuration Methods

```java
// List available AI models (JSON response)
String listModels()

// Load specific model by ID
boolean setModel(int modelId)

// Configure sensor input
boolean setSensor(int sensorId)
```

#### Low-Level Methods

```java
// Check available bytes in device buffer
int availBytes()

// Read exact number of bytes
byte[] readExact(int length)

// Read with timeout
byte[] readMessageWithTimeout(int maxLen, long timeoutMs)

// Reset device buffer
void resetBuffer()

// Get last error message
String getLastError()
```

### LemonlightResult Class

Immutable result container for vision inference data.

#### Properties

```java
boolean isValid()                          // Whether result is valid
int getDetectionCount()                    // Total detections (boxes + classes + points)
long getTimestampMs()                      // Result timestamp
byte[] getRaw()                            // Raw response data
String getFirmwareVersion()                // Firmware version

List<Detection> getDetections()            // Bounding boxes
List<Classification> getClassifications()  // Classifications
List<KeyPoint> getKeypoints()              // Keypoints

int getTopScorePercent()                   // Highest detection confidence
```

#### Detection Class

Bounding box detection result.

```java
public final class Detection {
    public final int x;         // Top-left X coordinate
    public final int y;         // Top-left Y coordinate
    public final int w;         // Width in pixels
    public final int h;         // Height in pixels
    public final int score;     // Confidence (0-100)
    public final int targetId;  // Class/target ID
}
```

#### Classification Class

Image classification result.

```java
public final class Classification {
    public final int targetId;  // Class ID
    public final int score;     // Confidence (0-100)
}
```

#### KeyPoint Class

Keypoint/landmark detection result.

```java
public final class KeyPoint {
    public final int x;         // X coordinate
    public final int y;         // Y coordinate
    public final int score;     // Confidence (0-100)
    public final int targetId;  // Keypoint ID
}
```

### LemonlightSensor Class

Thin wrapper for caching inference results.

```java
// Constructor
LemonlightSensor(Lemonlight driver)

// Update cached result
void update()

// Get cached result
LemonlightResult getLastResult()

// Get last update timestamp
long getLastUpdateMs()

// Access underlying driver
Lemonlight getDriver()
```

### GroveVisionI2CHelper Class

Utility class for device initialization.

```java
// Bind with default device name "lemonlight"
static Lemonlight bindLemonlight(HardwareMap hardwareMap, Telemetry telemetry)

// Bind with custom device name
static Lemonlight bindLemonlight(HardwareMap hardwareMap, Telemetry telemetry, String deviceName)
```

---

## Example OpModes

### 1. GroveVisionI2C_OneShotTest

Single inference test - verifies basic communication and displays parsed results.

**Use Case**: Initial hardware testing, connection verification
**Location**: [GroveVisionI2C_OneShotTest.java](GroveVisionI2C_OneShotTest.java)

### 2. Lemonlight_StreamTelemetry

Continuous streaming test - polls at 10 Hz and displays live detection data.

**Use Case**: Real-time monitoring, model testing, debugging
**Location**: [Lemonlight_StreamTelemetry.java](Lemonlight_StreamTelemetry.java)

### 3. Lemonlight_AutonomousExample

Complete autonomous navigation example using vision-guided control.

**Use Case**: Autonomous programming reference, vision-based navigation
**Location**: [Lemonlight_AutonomousExample.java](Lemonlight_AutonomousExample.java)

**Features**:
- Object detection and tracking
- Center-targeting navigation
- Classification and keypoint processing
- Drive motor control integration

---

## Troubleshooting

### Problem: "Lemonlight not found" or binding fails

**Solutions**:
1. Check Robot Configuration has device named correctly
2. Verify I2C wiring (SDA, SCL, power, ground)
3. Check I2C address is `0x62` (default)
4. Restart Robot Controller app
5. Power cycle the Control Hub

### Problem: `ping()` returns false

**Solutions**:
1. Verify Grove Vision AI V2 has power (LED indicator)
2. Check I2C connections are secure
3. Confirm device firmware is loaded (use `getFirmwareVersion()`)
4. Test with I2C scanner to verify address
5. Check for I2C bus conflicts with other devices

### Problem: `readInference()` returns invalid results

**Solutions**:
1. Check `getLastError()` for error message
2. Verify model is loaded on device (`listModels()`, `setModel()`)
3. Increase timeout values in `LemonlightConstants`
4. Check device has valid AI model flashed
5. Verify camera is connected and working

### Problem: Low detection confidence or no detections

**Solutions**:
1. Ensure proper lighting conditions
2. Verify model matches target objects
3. Train custom model for your use case
4. Adjust `MIN_CONFIDENCE` threshold in OpMode
5. Check camera focus and field of view

### Problem: Slow or inconsistent performance

**Solutions**:
1. Reduce polling frequency (increase `sleep()` duration)
2. Use `LemonlightSensor` wrapper for caching
3. Check for I2C bus contention with other devices
4. Verify adequate power supply to sensor
5. Monitor loop times in telemetry

---

## Technical Details

### I2C Protocol

**Address**: `0x62` (7-bit)
**Clock Speed**: 400 kHz
**Protocol**: SSCMA Local Device Framing

#### Packet Format

```
[0x10][CMD][LEN_HI][LEN_LO][...payload...]
```

#### Commands

| Command | Code | Description |
|---------|------|-------------|
| READ    | 0x01 | Read data from device buffer |
| WRITE   | 0x02 | Write data to device buffer |
| AVAIL   | 0x03 | Check available bytes |
| RESET   | 0x06 | Clear device buffer |

### AT Command Protocol

Commands follow format: `AT+<COMMAND>=<args>\r`

#### Common Commands

| Command | Format | Description |
|---------|--------|-------------|
| INVOKE  | `AT+INVOKE=<iterations>,<feedback>,<mode>\r` | Trigger inference |
| VER     | `AT+VER?\r` | Get firmware version |
| STAT    | `AT+STAT?\r` | Get device status |
| MODEL   | `AT+MODEL=<id>!\r` | Load model by ID |
| MODELS  | `AT+MODELS?\r` | List available models |
| SENSOR  | `AT+SENSOR=<id>!\r` | Configure sensor |
| ID      | `AT+ID?\r` | Get device information |

#### Response Format

```json
{
  "type": 0,
  "name": "COMMAND_NAME",
  "code": 0,
  "data": {...}
}
```

### Performance Characteristics

| Metric | Value |
|--------|-------|
| Inference Time | ~100-300ms (model dependent) |
| I2C Transfer | ~20-50ms |
| Recommended Poll Rate | 5-10 Hz |
| Max Frame Buffer | 512 bytes |
| Max Read Length | 256 bytes |
| Default Timeout | 3 seconds |

### Constants Reference

All constants are defined in [LemonlightConstants.java](LemonlightConstants.java):

```java
I2C_ADDRESS_7BIT = 0x62
CMD_HEADER = 0x10
MAX_FRAME_LEN = 512
MAX_READ_LEN = 256
READ_TIMEOUT_MS = 2000
INVOKE_TIMEOUT_MS = 3000
AVAIL_POLL_MS = 15
```

---

## Advanced Usage

### Custom Invoke Command

```java
// Set custom invoke parameters (iterations, feedback, mode)
lemonlight.setInvokeCommand("AT+INVOKE=3,1,0\r");
```

### Model Management

```java
// List all available models
String models = lemonlight.listModels();
telemetry.addData("Models", models);

// Load specific model (e.g., person detection)
boolean success = lemonlight.setModel(1);
if (success) {
    telemetry.addLine("Model loaded successfully");
}
```

### Error Handling

```java
LemonlightResult result = lemonlight.readInference();

if (!result.isValid()) {
    String error = lemonlight.getLastError();
    if (error != null) {
        telemetry.addData("Error", error);

        if (error.contains("timeout")) {
            // Handle timeout - retry or reset
            lemonlight.resetBuffer();
        }
    }
}
```

### Multi-Threading

```java
// Use LemonlightSensor wrapper for thread-safe caching
LemonlightSensor sensor = new LemonlightSensor(lemonlight);

// Background thread updates sensor
Thread updateThread = new Thread(() -> {
    while (opModeIsActive()) {
        sensor.update();
        Thread.sleep(100);  // 10 Hz
    }
});
updateThread.start();

// Main loop reads cached result
while (opModeIsActive()) {
    LemonlightResult result = sensor.getLastResult();
    // Process result...
}
```

---

## Resources

- **SSCMA Documentation**: https://github.com/Seeed-Studio/SSCMA-Micro
- **Grove Vision AI V2 Wiki**: https://wiki.seeedstudio.com/grove_vision_ai_v2/
- **FTC I2C Driver Guide**: https://github.com/FIRST-Tech-Challenge/FtcRobotController/wiki/Writing-an-I2C-Driver
- **AT Protocol Specification**: https://github.com/Seeed-Studio/SSCMA-Micro/blob/main/docs/protocol/at-protocol-en_US.md

---

## Contributing

Found a bug or want to improve the driver? Contributions are welcome!

---

## License

This driver is part of the Lemonlight FTC project and follows the project's license terms.

---

**Version**: 1.0
**Last Updated**: 2026-02-08
**Maintainer**: MxYvrn
**Repository**: https://github.com/MxYvrn/Lemonlight
