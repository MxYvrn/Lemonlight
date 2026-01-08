package com.teamcode.subsystems;

import org.junit.Assert;
import org.junit.Test;

import com.teamcode.TeamCodeTestUtils;

public class OdometryTest {
    @Test
    public void testTicksToInches_halfRevolution() {
        try {
            java.lang.reflect.Method m = Odometry.class.getDeclaredMethod("ticksToInches", int.class);
            m.setAccessible(true);
            int halfTicks = (int) (TeamCodeTestUtils.getTicksPerRev() / 2.0);
            double inches = (double) m.invoke(null, halfTicks);
            double expected = Math.PI * TeamCodeTestUtils.getWheelDiameterIn() * TeamCodeTestUtils.getGearRatio() / 2.0;
            Assert.assertEquals(expected, inches, 1e-6);
        } catch (Exception ex) {
            Assert.fail("Reflection call failed: " + ex.getMessage());
        }
    }
}
