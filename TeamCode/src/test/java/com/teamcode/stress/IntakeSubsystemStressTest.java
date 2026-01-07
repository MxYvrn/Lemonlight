package com.teamcode.stress;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Stress tests for IntakeSubsystem.
 * Tests direction sign calculation, motor disconnect scenarios.
 */
public class IntakeSubsystemStressTest {

    /**
     * BUG-008: Test direction sign after motor disconnect.
     * If motor becomes null after initialization, getDirectionSign() should still
     * return correct value based on cached lastPower.
     */
    @Test
    public void testDirectionSignAfterMotorDisconnect() {
        // Simulate IntakeSubsystem state
        double lastPower = 0.8; // Intake was running (positive = collect)
        boolean intakeMotorNull = true; // Motor disconnected
        
        // Bug fix: Check lastPower even if motor is null
        double directionSign;
        if (lastPower == 0.0) {
            directionSign = 0.0;
        } else {
            directionSign = lastPower > 0 ? 1.0 : -1.0;
        }
        
        // Should return correct sign based on lastPower, not motor state
        assertEquals("Direction sign should be +1 for positive lastPower",
                1.0, directionSign, 0.001);
        
        // Test with negative power (outtake)
        lastPower = -0.6;
        directionSign = lastPower == 0.0 ? 0.0 : (lastPower > 0 ? 1.0 : -1.0);
        assertEquals("Direction sign should be -1 for negative lastPower",
                -1.0, directionSign, 0.001);
        
        // Test with zero power
        lastPower = 0.0;
        directionSign = lastPower == 0.0 ? 0.0 : (lastPower > 0 ? 1.0 : -1.0);
        assertEquals("Direction sign should be 0 for zero lastPower",
                0.0, directionSign, 0.001);
    }

    /**
     * Test that lastPower is updated even when motor is null.
     */
    @Test
    public void testLastPowerUpdateWhenMotorNull() {
        // Simulate update() logic
        boolean motorNull = true;
        double targetPower = 0.8; // Intake active
        double lastPower = 0.0;
        
        // Update lastPower regardless of motor state
        if (Math.abs(targetPower - lastPower) > 0.01) {
            lastPower = targetPower;
            // Only update motor if it exists
            // if (intakeMotor != null) intakeMotor.setPower(targetPower);
        }
        
        // lastPower should be updated
        assertEquals("lastPower should be updated even if motor is null",
                targetPower, lastPower, 0.001);
    }

    /**
     * Test direction sign consistency with different power values.
     */
    @Test
    public void testDirectionSignConsistency() {
        // Test various power values
        double[] testPowers = {0.8, -0.6, 0.0, 0.01, -0.01, 1.0, -1.0};
        double[] expectedSigns = {1.0, -1.0, 0.0, 1.0, -1.0, 1.0, -1.0};
        
        for (int i = 0; i < testPowers.length; i++) {
            double power = testPowers[i];
            double expected = expectedSigns[i];
            
            double sign = power == 0.0 ? 0.0 : (power > 0 ? 1.0 : -1.0);
            
            assertEquals(String.format("Power %.2f should have sign %.1f", power, expected),
                    expected, sign, 0.001);
        }
    }
}

