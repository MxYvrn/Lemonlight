package com.teamcode;

public final class Constants {
    private Constants() {}

    // Device names (match Robot Config)
    public static final String ENC_LEFT   = "odo_left";
    public static final String ENC_RIGHT  = "odo_right";
    public static final String ENC_STRAFE = "odo_strafe"; // set null/"" to disable
    public static final String IMU_NAME   = "imu";

    // Encoder configuration
    // For goBILDA 8192 CPR through-bore: 8192 ticks per wheel revolution.
    public static final double TICKS_PER_REV = 8192.0;
    public static final double WHEEL_DIAMETER_IN = 2.0;     // inches
    public static final double GEAR_RATIO = 1.0;            // wheel revs per encoder rev

    // Geometry (inches)
    public static final double TRACK_WIDTH_IN = 13.5;       // distance between left/right odometry wheels
    public static final double LATERAL_WHEEL_OFFSET_IN = 7.5; // strafe wheel offset from robot center

    // Encoder direction multipliers (+1 or -1) to make forward = +X, left wheel increasing forward
    public static final int LEFT_DIR   = +1;
    public static final int RIGHT_DIR  = -1;
    public static final int STRAFE_DIR = +1;

    // IMU fusion
    public static final boolean USE_IMU = true;     // set false to use pure encoder heading
    public static final double IMU_WEIGHT = 0.12;   // 0..1 complementary filter weight toward IMU absolute yaw
    public static final double IMU_YAW_SIGN = +1.0; // flip if yaw sign is inverted

    // Odometry update
    public static final double MAX_DT_S = 0.050; // cap dt if loops stall (50ms)

    // Telemetry
    public static final boolean TELEMETRY_VERBOSE = true;

    // ========== AUTONOMOUS CONFIGURATION ==========
    // Starting pose (right side of field, facing forward)
    public static final double AUTO_START_X = 12.0;             // inches from field origin
    public static final double AUTO_START_Y = -62.0;            // right side (negative y)
    public static final double AUTO_START_HEADING_RAD = Math.toRadians(90); // facing up-field

    // Scoring position (e.g., near backdrop/basket)
    public static final double AUTO_SCORE_X = 36.0;             // forward toward scoring area
    public static final double AUTO_SCORE_Y = -36.0;            // adjusted right side
    public static final double AUTO_SCORE_HEADING_RAD = Math.toRadians(135); // angle toward scoring

    // Parking position (safe zone)
    public static final double AUTO_PARK_X = 48.0;              // further forward
    public static final double AUTO_PARK_Y = -60.0;             // back to edge
    public static final double AUTO_PARK_HEADING_RAD = Math.toRadians(90); // facing forward

    // Autonomous control gains
    public static final double DRIVE_KP_POS = 0.04;             // proportional gain for position error (in/s per in)
    public static final double DRIVE_KP_ANGLE = 2.0;            // proportional gain for heading error (rad/s per rad)
    public static final double AUTO_MAX_SPEED = 0.6;            // max drive power during auto [0, 1]
    public static final double AUTO_POS_TOLERANCE_IN = 2.0;     // position error tolerance (inches)
    public static final double AUTO_ANGLE_TOLERANCE_RAD = Math.toRadians(5); // heading error tolerance

    // Autonomous timing
    public static final double AUTO_STEP_TIMEOUT_S = 5.0;       // max time per state before forcing next
    public static final double AUTO_SCORE_DURATION_S = 1.5;     // time to hold scoring position
}
