# Bug List - Prioritized by Severity

## CRITICAL (Blocks Core Functionality)

### [BUG-001] Shooter RPM calculation incorrect - 60x too fast
- **Severity**: CRITICAL
- **File**: `Constants.java:219`
- **Summary**: `SHOOTER_RPM_TO_TPS` is missing division by 60, causing velocity setpoint to be 60x too high
- **Impact**: Shooter cannot reach target RPMs (motor controller saturates or fails)
- **Repro Test**: `ShooterSubsystemStressTest.testRPMToTPSConversion()`
- **Fix**: Change to `SHOOTER_TICKS_PER_REV / 60.0`

### [BUG-002] ShooterSubsystem uses negative velocity but motor direction not reversed
- **Severity**: CRITICAL  
- **File**: `ShooterSubsystem.java:77`
- **Summary**: Code sets `-targetVelocityTPS` but motor direction was reverted (not REVERSE), causing wrong rotation
- **Impact**: Shooter spins backward or fails to spin
- **Repro Test**: `ShooterSubsystemStressTest.testVelocitySignConsistency()`
- **Fix**: Either remove negative OR restore REVERSE direction setting

## HIGH (Degrades Performance/Accuracy)

### [BUG-003] Odometry division by zero risk in arc correction
- **Severity**: HIGH
- **File**: `Odometry.java:167`
- **Summary**: Division by `dTheta_enc` after checking `< 1e-6`, but edge case handling could fail with NaN/Inf
- **Impact**: Robot pose becomes NaN/Inf, crashes autonomous
- **Repro Test**: `OdometryStressTest.testDivisionByZeroEdgeCases()`
- **Fix**: Add explicit NaN checks and clamping

### [BUG-004] Voltage compensation can cause power > 1.0 after clamping
- **Severity**: HIGH
- **File**: `DriveSubsystem.java:149-162`
- **Summary**: Voltage compensation scales power, then clamps. But if voltage < 12V, scale > 1.0, can exceed limits
- **Impact**: Motors may exceed safe power limits when battery is low
- **Repro Test**: `DriveSubsystemStressTest.testVoltageCompensationBounds()`
- **Fix**: Clamp voltage scale to reasonable range (e.g., 0.8 to 1.2)

### [BUG-005] No null check for HardwareMap.get() in subsystems
- **Severity**: HIGH
- **File**: Multiple subsystem constructors
- **Summary**: If hardware missing, `hw.get()` throws exception, crashes OpMode init
- **Impact**: Robot fails to initialize if hardware misconfigured
- **Repro Test**: `SubsystemInitializationTest.testMissingHardwareGracefulDegradation()`
- **Fix**: Add try-catch and null checks with graceful degradation

## MEDIUM (Edge Cases/Corner Cases)

### [BUG-006] Telemetry rate limiting uses nanoTime() - can overflow ✅ FIXED
- **Severity**: MEDIUM
- **File**: `TeleOpMain.java:119-124`
- **Summary**: `now - lastTelemetryNs` can be negative if System.nanoTime() overflows (rare but possible)
- **Impact**: Telemetry stops updating until overflow cycles
- **Repro Test**: `TeleOpStressTest.testTelemetryNanoTimeOverflow()`
- **Fix**: Check for negative delta and reset lastTelemetryNs if overflow detected

### [BUG-007] FeederSubsystem ramp timer never resets on repeated activations
- **Severity**: MEDIUM
- **File**: `FeederSubsystem.java:31-40`
- **Summary**: Ramp timer only resets on rising edge, but if shoot command toggles quickly, ramp may not reset properly
- **Impact**: Feeder may start at wrong power level
- **Repro Test**: `FeederSubsystemStressTest.testRampTimerResetSequence()`
- **Fix**: Reset timer whenever feedCommandActive becomes true

### [BUG-008] IntakeSubsystem.getDirectionSign() returns 0.0 if motor null but lastPower cached ✅ FIXED
- **Severity**: MEDIUM
- **File**: `IntakeSubsystem.java:91-101`
- **Summary**: If motor becomes null after initialization, direction sign returns 0 even if lastPower was non-zero
- **Impact**: Feeder coupling breaks if intake motor disconnects mid-match
- **Repro Test**: `IntakeSubsystemStressTest.testDirectionSignAfterMotorDisconnect()`
- **Fix**: Check lastPower even if motor is null (use cached value). Also update lastPower in update() even when motor is null.

## LOW (Code Quality/Minor Issues)

### [BUG-009] Constants.SHOOTER_RPM_TO_TPS comment says "/60.0" but code doesn't divide
- **Severity**: LOW
- **File**: `Constants.java:219`
- **Summary**: Documentation mismatch - comment implies division by 60 but code doesn't
- **Impact**: Confusion for future developers
- **Repro Test**: Documentation test
- **Fix**: Fix the constant (matches BUG-001)

### [BUG-010] Pose2d.copy() allocates new object - potential GC pressure
- **Severity**: LOW
- **File**: `Pose2d.java:14`
- **Summary**: High-frequency calls to getPose() create GC pressure in tight loops
- **Impact**: Performance degradation over long matches
- **Repro Test**: `Pose2dStressTest.testCopyAllocations()`
- **Fix**: Already have getPoseInto() - document preference for high-frequency code

---

## Summary
- **Critical**: 2 bugs (shooter completely broken) - ✅ ALL FIXED
- **High**: 3 bugs (accuracy/initialization issues) - ✅ 1 FIXED (BUG-004), 2 deferred
- **Medium**: 3 bugs (edge cases) - ✅ ALL FIXED (BUG-006, BUG-007, BUG-008)
- **Low**: 2 bugs (documentation/performance) - Deferred

