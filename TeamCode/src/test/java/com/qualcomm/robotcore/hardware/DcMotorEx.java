package com.qualcomm.robotcore.hardware;

/**
 * Minimal fake of DcMotorEx for unit tests.
 */
public class DcMotorEx {
    private int position = 0;

    public DcMotorEx() {}

    public int getCurrentPosition() { return position; }

    public void setCurrentPosition(int p) { this.position = p; }
}
