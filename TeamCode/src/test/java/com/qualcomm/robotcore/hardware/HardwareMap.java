package com.qualcomm.robotcore.hardware;

/**
 * Minimal fake HardwareMap for unit tests.
 */
public class HardwareMap {
    private final DcMotorEx motor;

    public HardwareMap(DcMotorEx motor) {
        this.motor = motor;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> cls, String name) {
        return (T) motor;
    }
}
