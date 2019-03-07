package org.wfrobotics.robot.config;

import org.wfrobotics.reuse.config.EnhancedRobotConfig;
import java.util.Optional;

import org.wfrobotics.reuse.config.RobotConfigPicker;
import org.wfrobotics.reuse.config.TalonConfig.ClosedLoopConfig;
import org.wfrobotics.reuse.config.TalonConfig.FollowerConfig;
import org.wfrobotics.reuse.config.TalonConfig.Gains;
import org.wfrobotics.reuse.config.TalonConfig.MasterConfig;
import org.wfrobotics.reuse.config.TankConfig;
import org.wfrobotics.reuse.config.VisionConfig;
import org.wfrobotics.reuse.subsystems.PositionBasedSubsystem.PositionConfig;

public class RobotConfig extends EnhancedRobotConfig
{
    private static RobotConfig instance = null;

    public PnuaticConfig getPnumaticConfig()
    {
        final PnuaticConfig config = new PnuaticConfig();
               // Hardware
               config.kAddressPCMGrippers = 0;
               config.kAddressPCMShifter = 0;
               config.kAddressPCMPoppers = 0;
               // intake
                config.kAddressSolenoidPoppersF = 0;
                config.kAddressSolenoidPoppersB = 1;
                // drive
                config.kAddressSolenoidShifterF = 6;
                config.kAddressSolenoidShifterB = 7;
   //climb
                config.kAddressSolenoidGrippersF = 4;
                config.kAddressSolenoidGrippersB = 5;
      //elevator

                config.kAddressSolenoidLockersF = 0;
                config.kAddressSolenoidLockersB = 1;
                config.KAddressSolenoidPushUpF = 2;
                config.KAddressSolenoidPushUpB = 3;
                return config;
    }
    //                      Tank
    // _________________________________________________________________________________

    // Hardware
    public TankConfig getTankConfig()
    {
        final TankConfig config = new DeepSpaceTankConfig();

        config.VELOCITY_MAX = 6250.0;
        config.VELOCITY_PATH = (int) (config.VELOCITY_MAX * 0.8);
        config.ACCELERATION = config.VELOCITY_PATH;
        config.STEERING_DRIVE_DISTANCE_P = 0.000022;
        config.STEERING_DRIVE_DISTANCE_I = 0.000005;
        config.OPEN_LOOP_RAMP = 0.30; // how fast do you acellerate

        config.CLOSED_LOOP = new ClosedLoopConfig("Tank", new MasterConfig[] {
            // Right
            new MasterConfig(18, false, true, new FollowerConfig(17, false), new FollowerConfig(16, false)),
            // Left
            new MasterConfig(13, false, true, new FollowerConfig(14, false), new FollowerConfig(15, false)),
        }, new Gains[] {
            new Gains("Velocity", 1, 0.0, 0.0, 0.0, 1023.0 / config.VELOCITY_MAX, 0),
            new Gains("Turn", 0, 0.175, 0.0004, 0.175 * 4.5 , 1023.0 / config.VELOCITY_MAX, 0, (int) (config.VELOCITY_MAX * 0.95), (int) (config.VELOCITY_MAX * 0.95)),
        });

        config.GEAR_RATIO_LOW = (54.0 / 32.0);
        config.SCRUB = 0.98;
        config.WHEEL_DIAMETER = 6 + 3/8;
        config.WIDTH = 27.0;

        return config;
    }

    public class DeepSpaceTankConfig extends TankConfig
    {
        // @Override
        // public Command getTeleopCommand()
        // {
        //     return new DriveCheesy();  // TODO DriveCarefully, accelerates slower when elevator is up
        // }
    }
    //                       Elevator
    // _________________________________________________________________________________

    // Hardware
    public PositionConfig getElevatorConfig()
    {
        int kTicksToTop = Integer.MAX_VALUE;
        double kLiftVelocityMaxUp = 2200.0;
        int kLiftCruiseUp = (int) (kLiftVelocityMaxUp * 0.975);
        int kLiftAccelerationUp = (int) (kLiftCruiseUp * 6.0);

        final PositionConfig c = new PositionConfig();

        c.kClosedLoop = new ClosedLoopConfig("Lift", new MasterConfig[] {
            new MasterConfig(10, false, false, new FollowerConfig(11, true, true))
        }, new Gains[] {
            new Gains("Motion Magic", 0, 0.0, 0.000, 0.0, 1023.0 / kLiftVelocityMaxUp, 0, kLiftCruiseUp, kLiftAccelerationUp),
        });
        c.kHardwareLimitNormallyOpenB = true;
        c.kHardwareLimitNormallyOpenT = true;
        c.kTicksToTop = kTicksToTop;
        c.kFullRangeInchesOrDegrees = 38.0;
        //        c.kSoftwareLimitT = Optional.of(kTicksToTop);
        //        c.kTuning = Optional.of(false);

        return c;
    }
    // // Subsystem
    // public static double kElevatorFeedForwardHasCube = 0.25;
    // public static double kElevatorFeedForwardNoCube = 0.20;
    // public static final int kElevatorTicksStartup = -1500;
    // public static int kElevatorTickRateSlowVelocityObserved = 500;
    // public static int kElevatorTickRateSlowEnough = kElevatorTickRateSlowVelocityObserved + 200;

    //                      Intake
    // _________________________________________________________________________________

    // Hardware
    public final int kAddressTalonCargo = 8;
    public final boolean kInvertTalonCargo = true;
    public final int kAddressDigitalHatchSensor = 0;

    //                      Link
    // _________________________________________________________________________________
    public PositionConfig getLinkConfig()
    {
        final PositionConfig c = new PositionConfig();

        int kTicksToTop = 7000;
        int kWristVelocityMax = 540;
        int kWristVelocityCruise = (int) (kWristVelocityMax * 0.975);
        int kWristAcceleration = (int) (kWristVelocityCruise * 6.0);

        c.kClosedLoop = new ClosedLoopConfig("Link", new MasterConfig[] {
            new MasterConfig(8, true, true)
        }, new Gains[] {
            new Gains("Motion Magic", 0, 1.0, 0.0000, 0.0, 1023.0 / kWristVelocityMax, 0, kWristVelocityCruise, kWristAcceleration),
        });
        c.kHardwareLimitNormallyOpenB = true;
        c.kHardwareLimitNormallyOpenT = true;
        c.kTicksToTop = kTicksToTop;
        c.kFullRangeInchesOrDegrees = 90.0;
        c.kSoftwareLimitT = Optional.of(kTicksToTop);
        //        c.kTuning = Optional.of(true);

        return c;
    }

    // Constructor
    protected RobotConfig()
    {
        this.vision = Optional.of(new VisionConfig(69.0));
    }

    //                      Helper Methods
    // _________________________________________________________________________________

    public static RobotConfig getInstance()
    {
        if (instance == null)
        {
            instance = (RobotConfig) RobotConfigPicker.get(new EnhancedRobotConfig[] {
                new PracticeConfig(),  // Practice robot differences
                new RobotConfig(),     // Competition robot
            });
        }
        return instance;
    }
}