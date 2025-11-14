package com.teamcode.hardware;

import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.HardwareMap;

public class Encoder {
    private final DcMotorEx motor;
    private final int dir; // +1 or -1
    private int lastPos;
    private boolean initialized = false;

    public Encoder(HardwareMap hw, String name, int directionMultiplier) {
        this.motor = name == null || name.isEmpty() ? null : hw.get(DcMotorEx.class, name);
        this.dir = directionMultiplier;
        if (motor != null) {
            this.lastPos = motor.getCurrentPosition();
            this.initialized = true;
        }
    }

    public boolean isPresent() { return motor != null; }

    public int getRaw() { return isPresent() ? motor.getCurrentPosition() * dir : 0; }

    public int getDeltaTicks() {
        if (!isPresent()) return 0;
        int cur = motor.getCurrentPosition() * dir;
        int dt = cur - lastPos;
        lastPos = cur;
        return dt;
    }

    public void reset() {
        if (!isPresent()) return;
        lastPos = motor.getCurrentPosition() * dir;
    }
}
