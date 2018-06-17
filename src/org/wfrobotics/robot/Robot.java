package org.wfrobotics.robot;

import org.wfrobotics.reuse.background.BackgroundUpdater;
import org.wfrobotics.reuse.background.RobotStateEstimator;
import org.wfrobotics.reuse.hardware.AutoTune;
import org.wfrobotics.reuse.hardware.led.RevLEDs;
import org.wfrobotics.reuse.hardware.led.RevLEDs.PatternName;
import org.wfrobotics.reuse.subsystems.drive.TankSubsystem;
import org.wfrobotics.reuse.utilities.ConsoleLogger;
import org.wfrobotics.reuse.utilities.DashboardView;
import org.wfrobotics.reuse.utilities.MatchState2018;
import org.wfrobotics.robot.config.Autonomous;
import org.wfrobotics.robot.config.IO;
import org.wfrobotics.robot.subsystems.IntakeSubsystem;
import org.wfrobotics.robot.subsystems.LiftSubsystem;
import org.wfrobotics.robot.subsystems.WinchSubsystem;
import org.wfrobotics.robot.subsystems.Wrist;

import edu.wpi.first.wpilibj.DriverStation.Alliance;
import edu.wpi.first.wpilibj.SampleRobot;
import edu.wpi.first.wpilibj.Spark;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.command.Command;
import edu.wpi.first.wpilibj.command.Scheduler;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;

// TODO 2019 switch to non-deprecated RobotBase
public final class Robot extends SampleRobot
{
    private final BackgroundUpdater backgroundUpdater = new BackgroundUpdater(.005);
    private final Scheduler scheduler = Scheduler.getInstance();
    private final RobotState state = RobotState.getInstance();
    private final MatchState2018 matchState = MatchState2018.getInstance();
    private static TankSubsystem driveSubsystem = TankSubsystem.getInstance();

    public static IntakeSubsystem intakeSubsystem;
    public static LiftSubsystem liftSubsystem;
    public static WinchSubsystem winch;
    public static Wrist wrist;

    public static DashboardView dashboardView = new DashboardView(416, 240, 20);
    public static IO controls;
    Command autonomousCommand;
    double lastPeriodicTime = 0.0;

    public static Spark led = new Spark(9);

    public void robotInit()
    {
        liftSubsystem = new LiftSubsystem();
        intakeSubsystem = new IntakeSubsystem();
        winch = new WinchSubsystem();
        wrist = new Wrist();

        controls = IO.getInstance();  // IMPORTANT: Initialize IO after subsystems, so all subsystem parameters passed to commands are initialized
        Autonomous.setupSelection();

        backgroundUpdater.register(driveSubsystem);
        backgroundUpdater.register(intakeSubsystem);
        backgroundUpdater.register(RobotStateEstimator.getInstance());
    }

    public void operatorControl()
    {
        if (autonomousCommand != null) autonomousCommand.cancel();

        RobotStateEstimator.getInstance().reset();
        backgroundUpdater.start();

        driveSubsystem.setBrake(false);
        led.set(RevLEDs.getValue(PatternName.Yellow));

        while (isOperatorControl() && isEnabled())
        {
            allPeriodic();
        }
    }

    public void autonomous()
    {
        RobotStateEstimator.getInstance().reset();
        backgroundUpdater.start();

        driveSubsystem.setBrake(true);
        led.set(RevLEDs.getValue((m_ds.getAlliance() == Alliance.Red) ? PatternName.Red : PatternName.Blue));

        autonomousCommand =  Autonomous.getConfiguredCommand();
        if (autonomousCommand != null) autonomousCommand.start();

        while (isAutonomous() && isEnabled())
        {
            allPeriodic();
        }
    }

    public void disabled()
    {
        backgroundUpdater.stop();

        driveSubsystem.setBrake(false);
        led.set(RevLEDs.getValue(PatternName.Yellow));

        while (isDisabled())
        {
            matchState.update();

            driveSubsystem.zeroGyro();
            intakeSubsystem.onBackgroundUpdate();  // For cube distance sensor
            //            liftSubsystem.onBackgroundUpdate();  // Zero if possible

            allPeriodic();
        }
    }

    public void test()
    {
        Timer.delay(0.5);

        while (isTest() && isEnabled())
        {
            allPeriodic();
        }
    }

    private void allPeriodic()
    {
        intakeSubsystem.updateSensors();
        liftSubsystem.updateSensors();
        wrist.updateSensors();
        driveSubsystem.updateSensors();

        double schedulerStart = Timer.getFPGATimestamp();
        scheduler.run();

        SmartDashboard.putNumber("Periodic Time ", Timer.getFPGATimestamp() - schedulerStart);
        intakeSubsystem.reportState();
        liftSubsystem.reportState();
        wrist.reportState();
        driveSubsystem.reportState();
        state.reportState();
        backgroundUpdater.reportState();
        ConsoleLogger.reportState();
        AutoTune.getInstance().reportState();
    }
}
