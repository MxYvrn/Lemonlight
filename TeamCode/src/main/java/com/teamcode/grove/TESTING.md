# Lemonlight Driver Testing Guide

Complete testing checklist for validating your Lemonlight (Grove Vision AI V2) driver installation and functionality.

---

## 🔧 Pre-Testing Setup

### Hardware Checklist

- [ ] Grove Vision AI V2 connected to Control Hub I2C port
- [ ] Proper wiring verified (VCC, GND, SDA, SCL)
- [ ] Device powered on (LED indicator check)
- [ ] Camera lens is clean and unobstructed
- [ ] I2C address set to 0x62 (default)

### Software Checklist

- [ ] FTC SDK version 10.1 or later
- [ ] Robot Controller app installed and updated
- [ ] Driver Station app connected to Robot Controller
- [ ] Code synced to Robot Controller (Build → Make Project in Android Studio)

### Robot Configuration

- [ ] Configuration created with I2C device
- [ ] Device type: "Lemonlight (Grove Vision AI V2)"
- [ ] Device name: "lemonlight"
- [ ] Configuration saved and active

---

## 🧪 Test Sequence

Follow these tests in order. Each test builds on the previous one.

### Test 1: Hardware Connection (GroveVisionI2C_OneShotTest)

**Goal**: Verify basic I2C communication and device recognition.

**Steps**:
1. Select OpMode: `GroveVisionI2C_OneShotTest` (TeleOp → Test group)
2. Press **INIT**
3. Observe telemetry display:
   - [ ] "Driver initialized" (not "Driver init failed")
   - [ ] Address shows: `0x62`
   - [ ] Firmware version displayed (not "n/a")
   - [ ] No error messages
4. Press **START**
5. Observe telemetry after read:
   - [ ] "Read completed" (not "Read failed")
   - [ ] Hex data displayed (confirms data transfer)
   - [ ] Parsed count shown

**Expected Output**:
```
Driver initialized
Address: 0x62
Firmware: 1.x.x (or version number)

=== One-shot read ===
Hex: 10 01 XX XX ... (raw data)
Parsed: count=X topScore=XX%

Read completed
```

**If Test Fails**:
- Check wiring connections
- Verify Robot Configuration
- Check I2C bus for conflicts
- Power cycle Control Hub
- See [Troubleshooting](#troubleshooting)

---

### Test 2: Continuous Streaming (Lemonlight_StreamTelemetry)

**Goal**: Verify continuous data acquisition and parsing.

**Steps**:
1. Select OpMode: `Lemonlight_StreamTelemetry` (TeleOp → Test group)
2. Press **INIT**
3. Observe initialization:
   - [ ] "Ready. Press START for stream." displayed
   - [ ] No binding errors
4. Press **START**
5. Monitor streaming telemetry (10 Hz updates):
   - [ ] Loop time < 150ms (good performance)
   - [ ] Inference age updates regularly
   - [ ] Detection count varies with scene
   - [ ] Top score changes with objects
   - [ ] Raw hex data refreshes

**Expected Output** (with object in view):
```
=== Lemonlight stream ===
Loop ms: 85
Inference age ms: 120
Detections: 2
Top score %: 87
Raw (first 32): 10 01 A3 12 7B 22 74 79 ...
```

**Expected Output** (no objects):
```
=== Lemonlight stream ===
Loop ms: 75
Inference age ms: 580
Detections: 0
Top score %: 0
Raw (first 32): 10 01 XX XX ...
```

**Performance Benchmarks**:
- Loop time: 50-150ms (acceptable)
- Inference age: < 500ms (fresh data)
- Update rate: ~10 Hz

**If Test Fails**:
- If loop time > 200ms: reduce polling frequency or check for I2C contention
- If no detections: verify camera has clear view and proper lighting
- If inference age increases continuously: model not running (check firmware)

---

### Test 3: Detection Parsing

**Goal**: Verify all three data types (boxes, classes, keypoints) are parsed correctly.

**Steps**:
1. Continue with `Lemonlight_StreamTelemetry` OpMode
2. Test different scenarios:

#### Box Detection Test
- [ ] Place known object in camera view
- [ ] Verify detection count > 0
- [ ] Verify top score % > 50 (for good detections)
- [ ] Move object → observe detection count/score changes

#### Classification Test
- [ ] Use model with classification output
- [ ] Verify classes are parsed (check raw JSON if needed)

#### Keypoint Test
- [ ] Use model with keypoint output (e.g., pose detection)
- [ ] Verify keypoints are detected

**Verification**:
```java
// In your test OpMode, check result contents:
if (result.getDetections().size() > 0) {
    telemetry.addLine("✓ Box detection working");
}
if (result.getClassifications().size() > 0) {
    telemetry.addLine("✓ Classification working");
}
if (result.getKeypoints().size() > 0) {
    telemetry.addLine("✓ Keypoint detection working");
}
```

---

### Test 4: Configuration Commands

**Goal**: Verify AT command configuration methods work.

**Steps**:
1. Create test OpMode or use Driver Station telemetry
2. Test model listing:
   ```java
   String models = lemonlight.listModels();
   telemetry.addData("Models", models);
   ```
   - [ ] JSON response received
   - [ ] Model IDs listed

3. Test model loading:
   ```java
   boolean success = lemonlight.setModel(1);
   telemetry.addData("Model Load", success ? "Success" : "Failed");
   ```
   - [ ] Returns true for valid model ID
   - [ ] Returns false for invalid model ID

4. Test device info:
   ```java
   String info = lemonlight.getDeviceInfo();
   telemetry.addData("Device Info", info);
   ```
   - [ ] JSON response with device details

**Expected Model Response**:
```json
{"type":1,"name":"MODELS","code":0,"data":{"models":[{"id":1,"type":"YOLO","name":"Object Detection"}]}}
```

---

### Test 5: Error Handling

**Goal**: Verify error conditions are handled gracefully.

**Test Scenarios**:

#### Scenario A: Device Disconnected
1. Start streaming OpMode
2. Disconnect I2C cable
3. Observe telemetry:
   - [ ] Error message displayed
   - [ ] No crashes or freezes
   - [ ] `getLastError()` provides diagnostic info

#### Scenario B: Invalid Commands
```java
boolean result = lemonlight.setModel(999);  // Invalid model ID
// Should return false, no crash
```
- [ ] Returns false for invalid model
- [ ] Error message available

#### Scenario C: Timeout Conditions
1. Unplug camera from Grove Vision AI V2
2. Run inference
3. Observe:
   - [ ] Timeout error reported
   - [ ] Operation doesn't hang indefinitely

---

### Test 6: Autonomous Integration (Lemonlight_AutonomousExample)

**Goal**: Verify driver works in autonomous mode with motor control.

**Steps**:
1. Configure drive motors: `leftDrive`, `rightDrive` (optional)
2. Select OpMode: `Lemonlight_AutonomousExample` (Autonomous → Example group)
3. Press **INIT**
   - [ ] Lemonlight initializes
   - [ ] Firmware version displayed
   - [ ] Motors initialized (if configured)
4. Press **START**
5. Observe autonomous behavior:
   - [ ] Robot detects objects
   - [ ] Telemetry shows detection data
   - [ ] Motors respond to detections (if configured):
     - Object centered → drives forward
     - Object left → turns left
     - Object right → turns right
     - No object → rotates in place

**Expected Telemetry**:
```
=== Vision Data ===
Total Detections: 1
--- Best Detection ---
Position: x=120 y=80
Size: w=50 h=60
Confidence: 85%
Target ID: 0
Center Offset: -10 px
ACTION: Turning left
```

**Safety Notes**:
- Test in open area with clear space
- Be ready to emergency stop
- Start with motors disconnected for dry run

---

### Test 7: Performance Testing

**Goal**: Measure and optimize performance metrics.

**Measurements**:
1. **Inference Latency**
   ```java
   long start = System.currentTimeMillis();
   LemonlightResult result = lemonlight.readInference();
   long elapsed = System.currentTimeMillis() - start;
   telemetry.addData("Inference Time", "%d ms", elapsed);
   ```
   - [ ] Target: < 300ms
   - [ ] Acceptable: < 500ms

2. **Loop Rate**
   ```java
   long loopStart = System.currentTimeMillis();
   // ... your loop code ...
   long loopTime = System.currentTimeMillis() - loopStart;
   telemetry.addData("Hz", "%.1f", 1000.0 / loopTime);
   ```
   - [ ] Target: 10 Hz (100ms loop)
   - [ ] Acceptable: 5-20 Hz

3. **Detection Consistency**
   - Place static object in view
   - Monitor detection stability over 30 seconds
   - [ ] Detection count stable (±1)
   - [ ] Confidence score stable (±10%)
   - [ ] Position stable (±5 pixels)

**Performance Targets**:
| Metric | Target | Acceptable | Action if Below |
|--------|--------|------------|-----------------|
| Inference | <300ms | <500ms | Check model complexity |
| Loop time | <100ms | <200ms | Reduce polling rate |
| Update rate | 10 Hz | 5-20 Hz | Adjust sleep timing |
| Detection stability | ±5px | ±10px | Improve lighting |

---

## 🐛 Troubleshooting

### Issue: "Lemonlight not found" on INIT

**Causes & Solutions**:
1. Robot Configuration
   - [ ] Verify device added to config
   - [ ] Check device name matches code
   - [ ] Confirm device type is correct
2. Hardware
   - [ ] Check I2C wiring
   - [ ] Verify power to sensor (LED on?)
   - [ ] Try different I2C port
3. Software
   - [ ] Restart Robot Controller app
   - [ ] Re-deploy code
   - [ ] Check for multiple configurations

### Issue: ping() returns false

**Diagnosis Steps**:
1. Check device power: LED indicator on Grove Vision AI V2
2. Verify I2C communication:
   ```java
   int avail = lemonlight.availBytes();
   telemetry.addData("AVAIL response", avail);
   ```
3. Test with I2C scanner (separate tool/OpMode)
4. Check for address conflicts with other I2C devices

**Solutions**:
- Ensure firmware is loaded on Grove Vision AI V2
- Power cycle the sensor
- Check for loose connections
- Verify pull-up resistors on I2C bus

### Issue: No detections or low confidence

**Environmental Factors**:
- [ ] Adequate lighting (not too bright or dark)
- [ ] Clear view of objects
- [ ] Objects within detection range (typically 0.5-3m)
- [ ] Camera lens is clean

**Model Factors**:
- [ ] Correct model loaded for target objects
- [ ] Model is trained on similar objects
- [ ] Objects match training data characteristics

**Solutions**:
- Train custom model for your specific objects
- Adjust lighting conditions
- Move objects closer to camera
- Use higher-quality trained model

### Issue: Slow performance or timeouts

**Optimization Checklist**:
- [ ] Reduce polling frequency (increase sleep time)
- [ ] Use `LemonlightSensor` wrapper for caching
- [ ] Check for other I2C devices causing contention
- [ ] Verify power supply is stable
- [ ] Consider using simpler/faster model

**Code Optimization**:
```java
// BAD: Polling too fast
while (opModeIsActive()) {
    sensor.update();  // No delay - hammers I2C bus
}

// GOOD: Reasonable polling rate
while (opModeIsActive()) {
    sensor.update();
    sleep(100);  // 10 Hz - balanced performance
}
```

### Issue: Inconsistent or corrupt data

**Symptoms**:
- Random detection count spikes
- Unparseable JSON
- Negative coordinates
- Score > 100

**Solutions**:
1. Check I2C signal integrity:
   - Shorten I2C cables
   - Add/check pull-up resistors
   - Reduce I2C clock speed (edit constants)
2. Verify firmware version:
   ```java
   String fw = lemonlight.getFirmwareVersion();
   // Should be 1.x.x or later
   ```
3. Reset buffer between reads:
   ```java
   lemonlight.resetBuffer();
   LemonlightResult result = lemonlight.readInference();
   ```

---

## 📊 Test Results Template

Copy and fill out this template for your testing session:

```
LEMONLIGHT DRIVER TEST RESULTS
Date: _______________
Firmware Version: _______________
FTC SDK Version: _______________

[ ] Test 1: Hardware Connection - PASS / FAIL
    Notes: _________________________________

[ ] Test 2: Continuous Streaming - PASS / FAIL
    Loop Time: _______ ms
    Update Rate: _______ Hz

[ ] Test 3: Detection Parsing - PASS / FAIL
    Box Detection: PASS / FAIL
    Classification: PASS / FAIL
    Keypoint Detection: PASS / FAIL

[ ] Test 4: Configuration Commands - PASS / FAIL
    Models Listed: _____________________________

[ ] Test 5: Error Handling - PASS / FAIL
    Disconnect: PASS / FAIL
    Invalid Command: PASS / FAIL
    Timeout: PASS / FAIL

[ ] Test 6: Autonomous Integration - PASS / FAIL
    Motor Control: TESTED / NOT TESTED

[ ] Test 7: Performance Testing - PASS / FAIL
    Inference Latency: _______ ms
    Detection Stability: GOOD / ACCEPTABLE / POOR

Overall Assessment: READY FOR COMPETITION / NEEDS WORK

Issues Found:
1. _____________________________________________
2. _____________________________________________
3. _____________________________________________

Next Steps:
1. _____________________________________________
2. _____________________________________________
```

---

## 🚀 Ready for Competition Checklist

Before using Lemonlight in competition:

- [ ] All tests passed successfully
- [ ] Performance meets targets (< 300ms inference, 10 Hz updates)
- [ ] Error handling tested and working
- [ ] Autonomous OpMode tested with drive motors
- [ ] Detection consistency verified (stable results)
- [ ] Team familiar with telemetry interpretation
- [ ] Backup OpMode without vision available
- [ ] Documentation reviewed by team
- [ ] Driver practice with vision-guided navigation
- [ ] Emergency stop procedures established

---

## 📝 Additional Testing Ideas

### Advanced Tests

1. **Multi-Object Tracking**: Test with multiple objects in view
2. **Occlusion Handling**: Partially hide objects, verify detection
3. **Distance Testing**: Measure detection range limits
4. **Angle Testing**: Test at various camera angles
5. **Lighting Variations**: Test under field lighting conditions
6. **Battery Voltage**: Test at different battery levels
7. **Temperature**: Monitor performance over extended operation
8. **Interference**: Test with other devices active

### Custom Model Testing

If using custom trained models:
- [ ] Test with training objects
- [ ] Test with similar but untrained objects
- [ ] Verify confidence thresholds
- [ ] Document minimum detection distance
- [ ] Test under competition-like lighting

---

## 📚 Support Resources

- **Driver README**: [README.md](README.md)
- **Code Examples**: See example OpModes in this directory
- **SSCMA Protocol**: https://github.com/Seeed-Studio/SSCMA-Micro
- **FTC Forums**: https://ftcforum.firstinspires.org
- **Issue Tracker**: https://github.com/MxYvrn/Lemonlight/issues

---

**Happy Testing! 🍋✨**
