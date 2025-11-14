package com.teamcode.util;

public final class Angle {
    private Angle() {}

    public static double norm(double a) {
        while (a <= -Math.PI) a += 2*Math.PI;
        while (a >  Math.PI)  a -= 2*Math.PI;
        return a;
    }

    public static double shortestDiff(double to, double from) {
        return norm(to - from);
    }

    public static double lerpAngle(double from, double to, double t) {
        double d = shortestDiff(to, from);
        return norm(from + d * t);
    }
}
