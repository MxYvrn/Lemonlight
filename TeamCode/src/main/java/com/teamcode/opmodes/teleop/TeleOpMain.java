package com.teamcode.opmodes.teleop;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.teamcode.Constants;
import com.teamcode.subsystems.DriveSubsystem;
import com.teamcode.subsystems.FeederSubsystem;
import com.teamcode.subsystems.IntakeSubsystem;
import com.teamcode.subsystems.Odometry;
import com.teamcode.subsystems.ShooterSubsystem;
import com.teamcode.util.Pose2d;
import com.qualcomm.robotcore.hardware.Servo;

@TeleOp(name = "TeleOp Main", group = "Main")
public class TeleOpMain extends LinearOpMode {

    // Subsystems
    private DriveSubsystem drive;
    private Odometry odometry;
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
    // BUGFIX: Use elapsed time instead of absolute nanoTime to handle overflow safely
    private long lastTelemetryNs = 0;
    private static final long TELEMETRY_INTERVAL_NS = 100_000_000L; // 10Hz

    // Pre-allocated pose for zero-allocation telemetry (Step 1 optimization)
    private final Pose2d cachedPose = new Pose2d();

    @Override
    public void runOpMode() throws InterruptedException {
        // ========== INITIALIZATION ==========
        telemetry.addLine("Initializing TeleOp...");
        telemetry.update();

        odometry = new Odometry(hardwareMap);
        drive = new DriveSubsystem(hardwareMap, odometry);
        shooter = new ShooterSubsystem(hardwareMap);
        feeder = new FeederSubsystem(hardwareMap);
        intake = new IntakeSubsystem(hardwareMap, Constants.INTAKE_MOTOR_NAME);
        swingGate = hardwareMap.get(Servo.class, "gateServo");

        // Set shooter to medium idle speed
        shooter.setSpeedMode(ShooterSubsystem.SpeedMode.MEDIUM);

        telemetry.setMsTransmissionInterval(100); // SDK-level rate limit
        telemetry.addLine("✓ Ready");
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

        // Reset odometry at start (optional - could preserve from auto)
        odometry.reset(new Pose2d(0, 0, 0));

        // ========== MAIN LOOP ==========
        while (opModeIsActive()) {
            // Step 2: Cache all gamepad reads once per loop to avoid repeated field access
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

            // 1. READ INPUTS (uses cached gamepad values)
            readInputs(gp2A, gp2DpadUp, gp2DpadDown, gp2LeftTrigger);

            // 2. UPDATE SUBSYSTEMS
            odometry.update();

            // Drive (gamepad1) - using cached values
            double forward = -gp1LeftStickY;  // Inverted (up = positive)
            double strafe = gp1LeftStickX;
            double turn = gp1RightStickX;

            // Drive at normal speed
            drive.teleopDrive(forward, strafe, turn, Constants.TELEOP_DRIVE_SPEED_NORMAL);

            // Intake (gamepad2 LEFT TRIGGER) - using cached value
            intake.update(gp2LeftTrigger);

            // Tell feeder about intake button commands -> set intake command power
            // If D-pad overrides are present, use them; otherwise use the actual intake motor power.
            double intakeCmd = 0.0;
            if (gp2DpadDown) intakeCmd = Constants.INTAKE_POWER_COLLECT;
            else if (gp2DpadUp) intakeCmd = Constants.INTAKE_POWER_EJECT;
            else intakeCmd = intake.getPower();
            feeder.setIntakeCommandPower(intakeCmd);

            // Shooter + Feeder (gamepad2) - using cached values
            // Note: feed command uses A as explicit shoot trigger and RT as feed intensity
            shooter.setShootCommand(gp2A);
            feeder.setFeedCommand(/*shootActive=*/ gp2A, /*rt=*/ gp2RightTrigger);

            shooter.update();
            feeder.update();

            // Swing gate control (gamepad2 dpad left/right)
            if (swingGate != null) {
                if (gp2DpadLeft) {
                    swingGate.setPosition(1.0);
                } else if (gp2DpadRight) {
                    swingGate.setPosition(0.0);
                }
            }

            // 3. TELEMETRY (rate-limited)
            // BUGFIX: Handle nanoTime() overflow by checking for negative deltas
            long now = System.nanoTime();
            long delta = now - lastTelemetryNs;
            // If delta is negative, nanoTime() overflowed - reset and continue
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
        drive.stop();
        shooter.stop();
        feeder.stop();
        intake.intakeOff();
    }

    /**
     * Read gamepad inputs and handle button edge detection.
     * Step 2: Uses cached gamepad values to avoid repeated field access.
     */
    private void readInputs(boolean gp2A, boolean gp2DpadUp, 
                           boolean gp2DpadDown, double gp2LeftTrigger) {
        // Shooter off toggle (A button on either controller)
        boolean a1Now = gamepad1.a;
        boolean a2Now = gp2A;  // Use cached value
        if ((a1Now && !lastA1) || (a2Now && !lastA2)) {
            shooter.setEnabled(!shooter.isEnabled());
            telemetry.addLine(shooter.isEnabled() ? ">>> Shooter ENABLED" : ">>> Shooter DISABLED");
        }
        lastA1 = a1Now;
        lastA2 = a2Now;

        // No toggle: D-pad down = intake (hold), D-pad up = outtake (hold).
        // Using cached values
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
     * Update telemetry with subsystem status.
     * Step 1: Uses getPoseInto() to avoid allocation.
     * Step 3: Pre-computes Math.toDegrees() once.
     */
    private void updateTelemetry() {
        // Step 1: Zero-allocation pose read
        odometry.getPoseInto(cachedPose);
        
        // Step 3: Pre-compute heading in degrees once
        double headingDeg = Math.toDegrees(cachedPose.heading);

        telemetry.addLine("=== DRIVE ===");
        telemetry.addData("Speed Mode", "NORMAL");
        telemetry.addData("Pose", "X=%.1f Y=%.1f H=%.0f°",
            cachedPose.x, cachedPose.y, headingDeg);
        telemetry.addLine();

        telemetry.addLine("=== SHOOTER ===");
        ShooterSubsystem.SpeedMode speedMode = shooter.getSpeedMode();
        telemetry.addData("Preset", speedMode.name());
        // Step 3: Cache values to avoid multiple method calls
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
        // Step 3: Cache values to avoid repeated method calls
        double intakePower = intake.getPower();
        double feederPower = feeder.getFeederPower();
        
        telemetry.addData("Intake Power", "%.2f", intakePower);
        telemetry.addData("Outtake Mode", intakePower < 0 ? "ON" : "off");
        telemetry.addData("Feeder Power", "%.2f", feederPower);
        telemetry.addLine();

        // Health warnings
        if (!drive.checkMotorHealth()) {
            telemetry.addLine("⚠️ DRIVE MOTOR FAILURE");
        }
        if (odometry.isStrafeEncoderMissing()) {
            telemetry.addLine("⚠️ 2-WHEEL ODO");
        }
        if (odometry.getImuFailureCount() >= 5) {
            telemetry.addLine("⚠️ IMU FAILED");
        }
    }
}
