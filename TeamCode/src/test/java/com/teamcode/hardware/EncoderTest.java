package com.teamcode.hardware;

import org.junit.Assert;
import org.junit.Test;

public class EncoderTest {
    @Test
    public void testMotorReaderSeam_getRawAndDelta() {
        // MotorReader returns increasing positions
        final int[] pos = {1000};
        Encoder.MotorReader reader = new Encoder.MotorReader() {
            @Override
            public int getCurrentPosition() { return pos[0]; }
        };

        // Create Encoder with null HW and null name; constructor handles null name via testReader
        Encoder e = new Encoder(null, null, 1, reader);
        Assert.assertTrue(e.isPresent());
        int raw = e.getRaw();
        Assert.assertEquals(1000, raw);

        // simulate movement
        pos[0] = 1100;
        int delta = e.getDeltaTicks();
        Assert.assertEquals(100, delta);
    }
}
