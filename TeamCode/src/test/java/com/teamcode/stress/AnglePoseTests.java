package com.teamcode.stress;

import com.teamcode.util.Angle;
import com.teamcode.util.Pose2d;
import org.junit.Test;
import static org.junit.Assert.*;

public class AnglePoseTests {

    @Test
    public void testAngleNormRange() {
        for (double a = -20*Math.PI; a <= 20*Math.PI; a += 0.37) {
            double n = Angle.norm(a);
            assertTrue("Angle.norm out of range: " + n, n >= -Math.PI && n < Math.PI);
        }
    }

    @Test
    public void testShortestDiffSymmetry() {
        double a = 3.0;
        double b = -2.2;
        double d1 = Angle.shortestDiff(a, b);
        double d2 = -Angle.shortestDiff(b, a);
        assertEquals(d1, d2, 1e-9);
    }

    @Test
    public void testLerpAngleBounds() {
        double from = 2.5;
        double to = -2.5;
        for (double t = 0; t <= 1.0; t += 0.05) {
            double v = Angle.lerpAngle(from, to, t);
            assertTrue(v >= -Math.PI && v < Math.PI);
        }
    }

    @Test
    public void testPoseHeadingNormalizedOnConstruct() {
        Pose2d p = new Pose2d(1.0, 2.0, 10*Math.PI);
        assertTrue(p.heading >= -Math.PI && p.heading < Math.PI);
    }
}
