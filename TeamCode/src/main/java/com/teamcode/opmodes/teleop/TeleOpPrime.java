package com.teamcode.opmodes.teleop;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.Servo;
import com.teamcode.Constants;
import com.teamcode.subsystems.FeederSubsystem;
import com.teamcode.subsystems.IntakeSubsystem;
import com.teamcode.subsystems.ShooterSubsystem;

/**
 * TeleOp without odometry dependencies.
 * Use this when odometry pods are disconnected or unavailable.
 * All manipulator subsystems (shooter, feeder, intake) work normally.
 * Drive uses basic mecanum math without pose tracking.
 */
@TeleOp(name = "TeleOp Prime", group = "Main")
public class TeleOpPrime extends LinearOpMode {

    // Drive motors (direct control, no odometry)
    private DcMotorEx frontLeft, frontRight, backLeft, backRight;

    // Manipulator subsystems
    private ShooterSubsystem shooter;
    private FeederSubsystem feeder;
    private IntakeSubsystem intake;
    
    // Servo
    private Servo swingGate;

    // Button edge detection
    private boolean lastDpadUp = false;
    private boolean lastA1 = false;
    private boolean lastA2 = false;
    private boolean lastX = false;
    private boolean lastY = false;
    private boolean lastB = false;

    // Telemetry rate limiting
    private long lastTelemetryNs = 0;
    private static final long TELEMETRY_INTERVAL_NS = 100_000_000L; // 10Hz

    @Override
    public void runOpMode() throws InterruptedException {
        // ========== INITIALIZATION ==========
        telemetry.addLine("Initializing TeleOp Prime (No Odo)...");
        telemetry.update();

        // Initialize drive motors directly (no odometry dependency)
        try {
            frontLeft = hardwareMap.get(DcMotorEx.class, "FLMotor");
            frontRight = hardwareMap.get(DcMotorEx.class, "FRMotor");
            backLeft = hardwareMap.get(DcMotorEx.class, "BLMotor");
            backRight = hardwareMap.get(DcMotorEx.class, "BRMotor");

            // Standard mecanum motor directions
            frontLeft.setDirection(DcMotorSimple.Direction.FORWARD);
            backLeft.setDirection(DcMotorSimple.Direction.FORWARD);
            frontRight.setDirection(DcMotorSimple.Direction.REVERSE);
            backRight.setDirection(DcMotorSimple.Direction.REVERSE);

            frontLeft.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
            frontRight.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
            backLeft.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
            backRight.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);

            frontLeft.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
            frontRight.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
            backLeft.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
            backRight.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        } catch (Exception e) {
            telemetry.addLine("⚠️ Drive motor initialization failed: " + e.getMessage());
            telemetry.update();
        }

        // Initialize manipulator subsystems (no odometry needed)
        shooter = new ShooterSubsystem(hardwareMap);
        feeder = new FeederSubsystem(hardwareMap);
        intake = new IntakeSubsystem(hardwareMap, Constants.INTAKE_MOTOR_NAME);
        
        try {
            swingGate = hardwareMap.get(Servo.class, "gateServo");
        } catch (Exception e) {
            swingGate = null;
        }

        // Set shooter to medium idle speed
        shooter.setSpeedMode(ShooterSubsystem.SpeedMode.MEDIUM);

        telemetry.setMsTransmissionInterval(100); // SDK-level rate limit
        telemetry.addLine("✓ Ready (No Odometry)");
        telemetry.addLine("Controls:");
        telemetry.addLine("  GP1: Drive (left stick + right stick)");
        telemetry.addLine("  GP1/2 A: Shooter off toggle");
        telemetry.addLine("  GP2 LT: Intake");
        telemetry.addLine("  GP2 RT: Shoot");
        telemetry.addLine("  GP2 X/Y/B: Shooter speed");
        telemetry.addLine("  GP2 D-up: Outtake toggle");
        telemetry.addLine("  GP2 D-left/right: Swing gate");
        telemetry.update();

        waitForStart();

        // ========== MAIN LOOP ==========
        while (opModeIsActive()) {
            // Cache all gamepad reads once per loop
            // Gamepad1
            double gp1LeftStickY = gamepad1.left_stick_y;
            double gp1LeftStickX = gamepad1.left_stick_x;
            double gp1RightStickX = gamepad1.right_stick_x;
            
            // Gamepad2
            double gp2LeftTrigger = gamepad2.left_trigger;
            double gp2RightTrigger = gamepad2.right_trigger;
            boolean gp2A = gamepad2.a;
            boolean gp2DpadDown = gamepad2.dpad_down;
            boolean gp2DpadUp = gamepad2.dpad_up;
            boolean gp2DpadLeft = gamepad2.dpad_left;
            boolean gp2DpadRight = gamepad2.dpad_right;

            // 1. READ INPUTS
            readInputs(gp2A, gp2DpadUp, gp2DpadDown, gp2LeftTrigger);

            // 2. DRIVE (basic mecanum, no odometry)
            double forward = -gp1LeftStickY;  // Inverted (up = positive)
            double strafe = gp1LeftStickX;
            double turn = gp1RightStickX;

            // Apply deadzone
            if (Math.abs(forward) < Constants.JOYSTICK_DEADZONE) forward = 0.0;
            if (Math.abs(strafe) < Constants.JOYSTICK_DEADZONE) strafe = 0.0;
            if (Math.abs(turn) < Constants.JOYSTICK_DEADZONE) turn = 0.0;

            // Mecanum kinematics
            double fl = forward + strafe + turn;
            double fr = forward - strafe - turn;
            double bl = forward - strafe + turn;
            double br = forward + strafe - turn;

            // Normalize to prevent exceeding max power
            double max = Math.max(1.0, Math.max(Math.max(Math.abs(fl), Math.abs(fr)),
                                                 Math.max(Math.abs(bl), Math.abs(br))));
            fl /= max;
            fr /= max;
            bl /= max;
            br /= max;

            // Apply speed multiplier (always normal speed in this version)
            double speedMultiplier = Constants.TELEOP_DRIVE_SPEED_NORMAL;
            fl *= speedMultiplier;
            fr *= speedMultiplier;
            bl *= speedMultiplier;
            br *= speedMultiplier;

            // Set motor powers
            if (frontLeft != null) frontLeft.setPower(fl);
            if (frontRight != null) frontRight.setPower(fr);
            if (backLeft != null) backLeft.setPower(bl);
            if (backRight != null) backRight.setPower(br);

            // 3. INTAKE
            intake.update(gp2LeftTrigger);

            // Tell feeder about intake button commands
            double intakeCmd = 0.0;
            if (gp2DpadDown) intakeCmd = Constants.INTAKE_POWER_COLLECT;
            else if (gp2DpadUp) intakeCmd = Constants.INTAKE_POWER_EJECT;
            else intakeCmd = intake.getPower();
            feeder.setIntakeCommandPower(intakeCmd);

            // 4. SHOOTER + FEEDER
            shooter.setShootCommand(gp2A);
            feeder.setFeedCommand(/*shootActive=*/ gp2A, /*rt=*/ gp2RightTrigger);

            shooter.update();
            feeder.update();

            // 5. SWING GATE
            if (swingGate != null) {
                if (gp2DpadLeft) {
                    swingGate.setPosition(1.0);
                } else if (gp2DpadRight) {
                    swingGate.setPosition(0.0);
                }
            }

            // 6. TELEMETRY (rate-limited)
            long now = System.nanoTime();
            long delta = now - lastTelemetryNs;
            if (delta < 0) {
                lastTelemetryNs = now;
                delta = 0;
            }
            if (delta > TELEMETRY_INTERVAL_NS) {
                updateTelemetry();
                telemetry.update();
                lastTelemetryNs = now;
            }

            idle();
        }

        // ========== CLEANUP ==========
        stopDrive();
        shooter.stop();
        feeder.stop();
        intake.intakeOff();
    }

    /**
     * Read gamepad inputs and handle button edge detection.
     */
    private void readInputs(boolean gp2A, boolean gp2DpadUp, 
                           boolean gp2DpadDown, double gp2LeftTrigger) {
        // Shooter off toggle (A button on either controller)
        boolean a1Now = gamepad1.a;
        boolean a2Now = gp2A;
        if ((a1Now && !lastA1) || (a2Now && !lastA2)) {
            shooter.setEnabled(!shooter.isEnabled());
            telemetry.addLine(shooter.isEnabled() ? ">>> Shooter ENABLED" : ">>> Shooter DISABLED");
        }
        lastA1 = a1Now;
        lastA2 = a2Now;

        // D-pad down = intake (hold), D-pad up = outtake (hold)
        double intakePower = 0.0;
        if (gp2DpadDown) {
            intakePower = Constants.INTAKE_POWER_COLLECT;
        } else if (gp2DpadUp) {
            intakePower = Constants.INTAKE_POWER_EJECT;
        } else if (gp2LeftTrigger > Constants.TRIGGER_THRESHOLD) {
            intakePower = Constants.INTAKE_POWER_COLLECT;
        }
        intake.setPower(intakePower);
        lastDpadUp = gp2DpadUp;

        // Shooter speed mode changes (X/Y/B rising edges)
        boolean gp2X = gamepad2.x;
        boolean gp2Y = gamepad2.y;
        boolean gp2B = gamepad2.b;
        
        if (gp2X && !lastX) {
            shooter.setSpeedMode(ShooterSubsystem.SpeedMode.LOW);
            telemetry.addLine(">>> BUTTON X: Set to LOW speed");
        }
        if (gp2Y && !lastY) {
            shooter.setSpeedMode(ShooterSubsystem.SpeedMode.MEDIUM);
            telemetry.addLine(">>> BUTTON Y: Set to MEDIUM speed");
        }
        if (gp2B && !lastB) {
            shooter.setSpeedMode(ShooterSubsystem.SpeedMode.MAX);
            telemetry.addLine(">>> BUTTON B: Set to MAX speed");
        }
        lastX = gp2X;
        lastY = gp2Y;
        lastB = gp2B;
    }

    /**
     * Update telemetry with subsystem status (no odometry data).
     */
    private void updateTelemetry() {
        telemetry.addLine("=== DRIVE ===");
        telemetry.addData("Mode", "NORMAL (No Odo)");
        telemetry.addLine();

        telemetry.addLine("=== SHOOTER ===");
        ShooterSubsystem.SpeedMode speedMode = shooter.getSpeedMode();
        telemetry.addData("Preset", speedMode.name());
        double targetRPM = shooter.getTargetRPM();
        double currentRPM = shooter.getVelocityRPM();
        double errorRPM = targetRPM - currentRPM;
        boolean atSpeed = shooter.isAtSpeed();
        boolean shooting = shooter.isShootCommandActive();
        
        telemetry.addData("Target RPM", "%.0f", targetRPM);
        telemetry.addData("Current RPM", "%.0f", currentRPM);
        telemetry.addData("Error RPM", "%.0f", errorRPM);
        telemetry.addData("At Speed", atSpeed ? "YES" : "NO");
        telemetry.addData("Shooting", shooting ? "ACTIVE" : "idle");
        
        // Diagnostic data for encoder mismatch debugging
        ShooterSubsystem.DiagnosticData diag = shooter.getDiagnosticData();
        telemetry.addLine();
        telemetry.addLine("--- DEBUG: Encoder Diagnostics ---");
        telemetry.addData("Ticks/Rev (config)", "%.1f", Constants.SHOOTER_TICKS_PER_REV);
        telemetry.addData("Target Enc Vel", "%.1f", diag.targetEncoderVelocity);
        telemetry.addData("Actual Enc Vel", "%.1f", diag.actualEncoderVelocity);
        telemetry.addData("Enc Vel Error", "%.1f", diag.encoderVelocityError);
        telemetry.addData("RPM (correct)", "%.0f", diag.calculatedRPM);
        telemetry.addData("RPM (if 5202)", "%.0f", diag.rpmWithOldWrongTicks);
        telemetry.addData("Diff (5202-5203)", "%.0f", diag.rpmWithOldWrongTicks - diag.calculatedRPM);
        telemetry.addLine();

        telemetry.addLine("=== INTAKE/FEEDER ===");
        double intakePower = intake.getPower();
        double feederPower = feeder.getFeederPower();
        
        telemetry.addData("Intake Power", "%.2f", intakePower);
        telemetry.addData("Outtake Mode", intakePower < 0 ? "ON" : "off");
        telemetry.addData("Feeder Power", "%.2f", feederPower);
        telemetry.addLine();
    }

    /**
     * Stop all drive motors.
     */
    private void stopDrive() {
        if (frontLeft != null) frontLeft.setPower(0.0);
        if (frontRight != null) frontRight.setPower(0.0);
        if (backLeft != null) backLeft.setPower(0.0);
        if (backRight != null) backRight.setPower(0.0);
    }
}
