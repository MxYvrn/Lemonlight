package com.teamcode.stress;

import com.teamcode.hardware.Encoder;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.HardwareMap;
import org.junit.Test;
import static org.junit.Assert.*;

public class EncoderTest {

    @Test
    public void testEncoderInitWithDirectionMultiplier() {
        // Use a MotorReader fake to avoid depending on SDK classes
        final int[] pos = new int[] {100};
        Encoder.MotorReader reader = new Encoder.MotorReader() {
            @Override public int getCurrentPosition() { return pos[0]; }
        };

        // Create encoder using package-private constructor
        Encoder e = new Encoder(null, "", -1, reader);
        int dt = e.getDeltaTicks();
        assertEquals("Initial delta after construction should be 0", 0, dt);
    }

    @Test
    public void testEncoderDeltaAfterMovementWithDirection() {
        final int[] pos2 = new int[] {50};
        Encoder.MotorReader reader2 = new Encoder.MotorReader() {
            @Override public int getCurrentPosition() { return pos2[0]; }
        };
        Encoder e2 = new Encoder(null, "", 1, reader2);
        pos2[0] = 80; // simulate movement
        int dt = e2.getDeltaTicks();
        assertEquals(30, dt);
    }
}
