package org.wfrobotics.robot;

import org.wfrobotics.reuse.subsystems.vision.CameraServer;
import org.wfrobotics.reuse.utilities.DashboardView;
import org.wfrobotics.reuse.utilities.HerdLogger;
import org.wfrobotics.robot.config.Autonomous;
import org.wfrobotics.robot.config.IO;
import org.wfrobotics.robot.config.VisionMode;
import org.wfrobotics.robot.subsystems.Auger;
import org.wfrobotics.robot.subsystems.Climber;
import org.wfrobotics.robot.subsystems.Intake;
import org.wfrobotics.robot.subsystems.LED;
import org.wfrobotics.robot.subsystems.Lifter;
import org.wfrobotics.robot.subsystems.Shooter;
import org.wfrobotics.robot.subsystems.SwerveDriveSteamworks;
import org.wfrobotics.robot.vision.messages.CameraMode;

import edu.wpi.first.wpilibj.DriverStation.Alliance;
import edu.wpi.first.wpilibj.SampleRobot;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.command.Command;
import edu.wpi.first.wpilibj.command.Scheduler;

public class Robot extends SampleRobot
{
    private final HerdLogger log = new HerdLogger(Robot.class);
    private final Scheduler scheduler = Scheduler.getInstance();
    private final RobotState state = RobotState.getInstance();
    private LED leds;
    public static SwerveDriveSteamworks driveSubsystem;
    public static Auger augerSubsystem;
    public static Climber climberSubsystem;
    public static DashboardView dashboardView;
    public static Intake intakeSubsystem;
    public static Lifter lifterSubsystem;
    public static Shooter shooterSubsystem;
    public static IO controls;

    Command autonomousCommand;
    double lastPeriodicTime = 0;

    public void robotInit()
    {
        driveSubsystem = new SwerveDriveSteamworks();
        augerSubsystem = new Auger();
        climberSubsystem = new Climber();
        dashboardView = new DashboardView();
        intakeSubsystem = new Intake();
        lifterSubsystem = new Lifter(true);
        shooterSubsystem = new Shooter();
        leds = LED.getInstance();

        controls = IO.getInstance();  // IMPORTANT: Initialize IO after subsystems, so all subsystem parameters passed to commands are initialized

        CameraServer.getInstance().send(new CameraMode(VisionMode.robotDefault().getValue()));
    }

    public void operatorControl()
    {
        if (autonomousCommand != null) autonomousCommand.cancel();
        leds.set(LED.defaultLEDEffect);

        while (isOperatorControl() && isEnabled())
        {
            allPeriodic();
        }
    }

    public void autonomous()
    {
        leds.set(leds.getAllianceEffect());
        autonomousCommand =  Autonomous.setupSelectedMode();
        if (autonomousCommand != null) autonomousCommand.start();

        while (isAutonomous() && isEnabled())
        {
            allPeriodic();
        }
    }

    public void disabled()
    {
        leds.set(LED.defaultLEDEffect);

        while (isDisabled())
        {
            lifterSubsystem.reset();
            driveSubsystem.zeroGyro();
            log.info("TeamColor", (m_ds.getAlliance() == Alliance.Red) ? "Red" : "Blue");

            allPeriodic();
        }
    }

    public void test()
    {
        while (isTest() && isEnabled())
        {
            allPeriodic();
        }
    }

    private void allPeriodic()
    {
        log.debug("Periodic Time", getPeriodicTime());
        log.info("Drive", driveSubsystem);
        log.info("Battery", m_ds.getBatteryVoltage());
        state.logState();

        scheduler.run();
    }

    /** Should be <= 20ms, the rate the driver station pings with IO updates. This assumes using closed loop CANTalon's or sensors/PID are all on our fast service thread to prevent latency */
    private String getPeriodicTime()
    {
        double now = Timer.getFPGATimestamp();
        String periodicTime = String.format("%.0f ms", (now - lastPeriodicTime) * 1000);

        lastPeriodicTime = now;
        return periodicTime;
    }
}
