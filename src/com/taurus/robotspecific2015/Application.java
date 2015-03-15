package com.taurus.robotspecific2015;

import java.util.ArrayList;

import com.taurus.controller.Controller;
import com.taurus.controller.ControllerChooser;
import com.taurus.led.Color;
import com.taurus.led.Effect;
import com.taurus.led.LEDs;
import com.taurus.robotspecific2015.Constants.*;
import com.taurus.swerve.SwerveChassis;

import edu.wpi.first.wpilibj.AnalogInput;
import edu.wpi.first.wpilibj.PowerDistributionPanel;
import edu.wpi.first.wpilibj.Preferences;
import edu.wpi.first.wpilibj.GenericHID.Hand;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.smartdashboard.SendableChooser;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;

public class Application implements com.taurus.Application {
    // App generic
    private final double TIME_RATE_DASH = .2;
    private final double TIME_RATE_SWERVE = .01;
    private double TimeLastDash = 0;
    private double TimeLastSwerve = 0;

    protected SwerveChassis drive;
    protected Controller controller;
    private ControllerChooser controllerChooser;
    private PowerDistributionPanel PDP;
    public static Preferences prefs;
    
    // App specific
    private boolean endOfMatchEffectSent;
    
    private Vision vision = new Vision();
    private Lift lift;
    private AnalogInput distance_sensor_left;
    private AnalogInput distance_sensor_right;

    private STATE_LIFT_ACTION CurrentLiftAction = STATE_LIFT_ACTION.NO_ACTION;

    private SendableChooser autoChooser;
    private SendableChooser testChooser;
    private Autonomous autonomous;

    private boolean StartTeleInChute = false;
    private boolean StartTeleGyroCal = true;
    public double StartMatchTime = 0;

    protected static LEDs leds;
    private final Effect effectEndOfMatch;

    public Application()
    {
        // App generic
        prefs = Preferences.getInstance();        
        PDP = new PowerDistributionPanel();
        controllerChooser = new ControllerChooser();
        controller = controllerChooser.GetController();        
        drive = new SwerveChassis(controller);
        
        // App specific
        lift = new Lift(drive, controller);
        
        distance_sensor_left =
                new AnalogInput(Constants.DISTANCE_SENSOR_LEFT_PIN);
        distance_sensor_right =
                new AnalogInput(Constants.DISTANCE_SENSOR_RIGHT_PIN);

        vision.Start();

        autoChooser = new SendableChooser();
        autoChooser.addDefault("Do nothing", AUTO_MODE.DO_NOTHING);
      //autoChooser.addObject("Go to zone", AUTO_MODE.GO_TO_ZONE);
        autoChooser.addObject("Container", AUTO_MODE.GRAB_CONTAINER);
        autoChooser.addObject("Container No Move", AUTO_MODE.GRAB_CONTAINER_NO_MOVE);
        autoChooser.addObject("Container + tote",
                AUTO_MODE.GRAB_CONTAINER_RIGHT_CHUTE);
        autoChooser.addObject("Container + 2 totes",
                AUTO_MODE.GRAB_CONTAINER_LEFT_CHUTE);

        SmartDashboard.putData("Autonomous mode", autoChooser);

        testChooser = new SendableChooser();
        testChooser.addDefault("Pneumatics/Motors", Integer.valueOf(Constants.TEST_MODE_PNEUMATIC));
        testChooser.addObject("Actuator", Integer.valueOf(Constants.TEST_MODE_ACTUATOR));
        SmartDashboard.putData("Test", testChooser);
        
        // LEDs
        LEDController ledHardware = new LEDController();
        leds = new LEDs(ledHardware, ledHardware.NumOfColors, Thread.NORM_PRIORITY - 2);  // Below normal priority
//        leds.start();  // Comment this line to disable LEDs
        endOfMatchEffectSent = false;
        ArrayList<Color[]> colors = new ArrayList<Color[]>();
        colors.add(new Color[]{Color.Red, Color.Red, Color.Red, Color.Red});
        colors.add(new Color[]{Color.Black, Color.Black, Color.Black, Color.Black});
        effectEndOfMatch = new Effect(colors, Effect.EFFECT.FLASH, 5, .5);
    }

    public void TeleopInit()
    {
        StartMatchTime = Timer.getFPGATimestamp();
        
        ArrayList<Color[]> colors = new ArrayList<Color[]>();
        
        if(StartTeleInChute)
        {
            CurrentLiftAction = STATE_LIFT_ACTION.ADD_CHUTE_TOTE;
        }
        else
        {
            CurrentLiftAction = STATE_LIFT_ACTION.ZERO_LIFT;
        }
        
        lift.init();
        
        if(StartTeleGyroCal)
        {
            drive.ZeroGyro();
        }
        
//        CompressorChargedOnce = false;
//        CompressorTimer = Timer.getFPGATimestamp();

        // Set LEDs
        colors.add(new Color[]{Color.Random(), Color.White, Color.Blue, Color.Red});
        colors.add(new Color[]{Color.Random(), Color.Black, Color.Cyan, Color.Green});
        colors.add(new Color[]{Color.Random(), Color.Green, Color.White, Color.Yellow});
        //leds.AddEffect(new LEDEffect(colors, LEDEffect.EFFECT.FLASH, Double.MAX_VALUE, 2), true);
    }

    public void TeleopPeriodic()
    {
        // Service drive
        if ((Timer.getFPGATimestamp() - TimeLastDash) > TIME_RATE_DASH)
        {
            TimeLastDash = Timer.getFPGATimestamp();
            
            UpdateDashboard();
            
            //SmartDashboard.putNumber("Dash Task Length", Timer.getFPGATimestamp() - TimeLastDash);
        }

        if ((Timer.getFPGATimestamp() - TimeLastSwerve) > TIME_RATE_SWERVE)
        {
            TimeLastSwerve = Timer.getFPGATimestamp();
            
            drive.run();
            
            SmartDashboard.putNumber("Swerve Task Length", Timer.getFPGATimestamp() - TimeLastSwerve);
        }
        
        // Service Lift
        UpdateDashboard();
        
        if(StartMatchTime > 105.0 && StartMatchTime < 110.0)
        {
            controller.setRumble(Hand.kLeft , 1);
        }
        else
        {
            controller.setRumble(Hand.kLeft , 0);    
        }
        
        
        if (controller.getCarHome())
        {
            lift.GetCar().GoToZero(6);
        }
        else if (controller.getCarTop())
        {
            lift.GetCar().GoUp();
        }
        else if (controller.getReleaseEverything())
        {
            lift.GetCylindersContainerFixed().Contract();
            lift.GetCylindersStackHolder().Extend();
            lift.GetCylindersRails().Contract();
            lift.GetCylindersContainerCar().Contract();
            lift.GetEjector().StopIn();
            
        }
        else if(controller.getLiftShake())
        {
            lift.ShakeCar();
        }
        else
        {
            if (controller.getStopAction())
            {
                CurrentLiftAction = STATE_LIFT_ACTION.NO_ACTION;
                lift.SetContainerInStack(false);
                lift.init();
            }

            lift.GetCar().ZeroIfNeeded();

            // if not currently running anything, try and find a button
            // do this first so we can run the action this run rather than
            // waiting until the next time this task is ran

            // find if a button is pressed, then execute
            if (controller.getAddChuteTote())
            {
                CurrentLiftAction = STATE_LIFT_ACTION.ADD_CHUTE_TOTE;
            }
            else if (controller.getAddFloorTote())
            {
                CurrentLiftAction = STATE_LIFT_ACTION.ADD_FLOOR_TOTE;
            }
            else if (controller.getAddContainer())
            {
                CurrentLiftAction = STATE_LIFT_ACTION.ADD_CONTAINER;
            }
            else if (controller.getEjectStack())
            {
                CurrentLiftAction = STATE_LIFT_ACTION.EJECT_STACK;
            }
            else if (controller.getCarryStack())
            {
                lift.SetToteOnRails(true);
                CurrentLiftAction = STATE_LIFT_ACTION.CARRY_STACK;
            }
            else if (controller.getDropStack())
            {
                CurrentLiftAction = STATE_LIFT_ACTION.DROP_STACK;
            }
            else if (controller.getCarryStackNoTote())
            {
                lift.SetToteOnRails(false);
                CurrentLiftAction = STATE_LIFT_ACTION.CARRY_STACK;
            }

            switch (CurrentLiftAction)
            {
                case ADD_CHUTE_TOTE:
                    if (lift.AddChuteToteToStack(5))
                    {
                        CurrentLiftAction = STATE_LIFT_ACTION.NO_ACTION;
                    }
                    break;

                case ADD_FLOOR_TOTE:
                    if (lift.AddFloorToteToStack(5))
                    {
                        CurrentLiftAction = STATE_LIFT_ACTION.NO_ACTION;
                    }
                    break;

                case ADD_CONTAINER:
                    if (lift.AddContainerToStack())
                    {
                        CurrentLiftAction = STATE_LIFT_ACTION.NO_ACTION;
                    }
                    break;

                case EJECT_STACK:
                    if (lift.EjectStack())
                    {
                        CurrentLiftAction = STATE_LIFT_ACTION.NO_ACTION;
                    }
                    break;

                case CARRY_STACK:
                    if (lift.LowerStackToCarryHeight())
                    {
                        // automatically drop the stack
                        //CurrentLiftAction = STATE_LIFT_ACTION.DROP_STACK;
                        CurrentLiftAction = STATE_LIFT_ACTION.NO_ACTION;
                    }
                    break;

                case DROP_STACK:
                    if (lift.DropStack())
                    {
                        // automatically go into chute tote
                        //CurrentLiftAction = STATE_LIFT_ACTION.ADD_CHUTE_TOTE;
                        CurrentLiftAction = STATE_LIFT_ACTION.NO_ACTION;
                    }
                    break;

                case ZERO_LIFT:
                    if (lift.GetCar().GoToZero(6))
                    {
                        CurrentLiftAction = STATE_LIFT_ACTION.NO_ACTION;
                    }
                    break;
                    
                case NO_ACTION:
                default:
                    lift.GetCar().GetActuator().SetSpeedRaw(0);
                    break;
            }
            
            if (endOfMatchEffectSent || Timer.getMatchTime() > 120)
            {
                //leds.AddEffect(effectEndOfMatch, true);
            }
        }

    }

    public void TeleopDeInit()
    {

    }

    public void AutonomousInit()
    {
        ArrayList<Color[]> colors = new ArrayList<Color[]>();
        
        controller = controllerChooser.GetController();
        
        lift.init();
        lift.SetContainerInStack(false);
        
        drive.ZeroGyro();
        AUTO_MODE automode = (AUTO_MODE) autoChooser.getSelected();
        autonomous = new Autonomous(drive, lift, vision, automode);
        
        // Set LEDs
        colors.add(new Color[]{Color.Green, Color.White, Color.Blue, Color.Red});
        //leds.AddEffect(new LEDEffect(colors, LEDEffect.EFFECT.SPIN, 15, 2), true);
    }

    public void AutonomousPeriodic()
    {
        lift.GetCar().ZeroIfNeeded();
        UpdateDashboard();
        autonomous.Run();
        
        // TODO set these
        StartTeleInChute = false;
        StartTeleGyroCal = false;
    }

    public void AutonomousDeInit()
    {
    }

    public void TestModeInit()
    {
        controller = controllerChooser.GetController();
    }

    public void TestModePeriodic()
    {
        boolean button1 = controller.getRawButton(1);
        boolean button2 = controller.getRawButton(2);
        boolean button3 = controller.getRawButton(3);
        boolean button4 = controller.getRawButton(4);
        boolean button5 = controller.getRawButton(5);
        boolean button6 = controller.getRawButton(6);
        boolean button15 = controller.getRawButton(15);
        boolean button16 = controller.getRawButton(16);
        lift.GetCar().ZeroIfNeeded();
        

        // test modes for cylinders and motors and features.
        switch (((Integer) testChooser.getSelected()).intValue())
        {
            case Constants.TEST_MODE_PNEUMATIC:
                PneumaticSubsystem testCylinders;
                boolean action = true;

                if (button1)
                {
                    testCylinders = lift.GetCylindersRails();
                }
                else if (button2)
                {
                    testCylinders = lift.GetCylindersContainerCar();
                }
                else if (button3)
                {
                    testCylinders = lift.GetCylindersContainerFixed();
                }
                else if (button4)
                {
                    testCylinders = lift.GetCylindersStackHolder();
                }
                else if (button5)
                {
                    testCylinders = lift.GetEjector().GetCylindersStop();
                }
                else if (button6)
                {
                    testCylinders = lift.GetEjector().GetCylindersPusher();
                }
                else
                {
                    testCylinders = lift.GetCylindersRails();
                    action = false;
                }

                if (action)
                {
                    // Toggle selected cylinders to opposite position
                    if (testCylinders.IsExtended())
                    {
                        testCylinders.Contract();
                    }
                    else
                    {
                        testCylinders.Extend();
                    }
                }
                if (button15)
                {
                    lift.GetCar().GetActuator().SetSpeedRaw(.7);
                }
                else if (button16)
                {
                    lift.GetCar().GetActuator().SetSpeedRaw(-.7);
                }
                else
                {
                    lift.GetCar().GetActuator().SetSpeedRaw(0);
                }
                break;
            case Constants.TEST_MODE_ACTUATOR:
                if (button1)
                {
                    lift.GetCar().SetPosition(LIFT_POSITIONS_E.ZERO);
                }
                else if (button2)
                {
                    lift.GetCar().SetPosition(LIFT_POSITIONS_E.CHUTE);
                }
                else if (button3)
                {
                    lift.GetCar().SetPosition(LIFT_POSITIONS_E.DESTACK);
                }
                else if (button4)
                {
                    lift.GetCar().SetPosition(LIFT_POSITIONS_E.STACK);
                }
                else if (button5)
                {
                    lift.GetCar().SetPosition(LIFT_POSITIONS_E.CHUTE);
                }
                else if (button6)
                {
                    lift.GetCar().SetPosition(LIFT_POSITIONS_E.CONTAINER_STACK);
                }
                else
                {
                    lift.GetCar().GetActuator().SetSpeedRaw(0);
                    // lift.GetEjector().SetMotors(0);
                }
                break;
            default:
                break;
        }

        lift.GetCar().ZeroIfNeeded();
        UpdateDashboard();
    }

    public void TestModeDeInit()
    {

    }

    public void DisabledInit()
    {

    }

    public void DisabledPeriodic()
    {
        lift.GetCar().ZeroIfNeeded();
        UpdateDashboard();
    }

    public void DisabledDeInit()
    {

    }
    
    private void UpdateDashboard()
    {
//      for (int i = 0; i < 16; i++)
//      {
//          SmartDashboard.putNumber("PDP " + i, PDP.getCurrent(i));
//      }
//
//      SmartDashboard.putNumber("PDP Total Current", PDP.getTotalCurrent());
//      SmartDashboard.putNumber("PDP Total Power", PDP.getTotalPower());
//      SmartDashboard.putNumber("PDP Total Energy", PDP.getTotalEnergy());
      SmartDashboard.putNumber("Voltage", PDP.getVoltage());

      // display the joysticks on smart dashboard
//      SmartDashboard.putNumber("Left Mag",
//              controller.getMagnitude(Hand.kLeft));
//      SmartDashboard.putNumber("Left Angle",
//              controller.getDirectionDegrees(Hand.kLeft));
//      SmartDashboard.putNumber("Right Mag",
//              controller.getMagnitude(Hand.kRight));
//      SmartDashboard.putNumber("Right Angle",
//              controller.getDirectionDegrees(Hand.kRight));

//      if (driveScheme.get() == DriveScheme.ANGLE_DRIVE)
//      {
//          SmartDashboard.putNumber("Angle heading",
//                  controller.getAngleDrive_Heading());
//      }
//
//      // display each wheel's mag and angle in SmartDashboard
//      for (int i = 0; i < SwerveConstants.WheelCount; i++)
//      {
//          SmartDashboard.putNumber("Wheel " + Integer.toString(i) + " Mag",
//                  drive.getWheelActual(i).getMag());
//          SmartDashboard.putNumber("Wheel " + Integer.toString(i) + " Angle",
//                  drive.getWheelActual(i).getAngle());
//      }

      SmartDashboard.putNumber("Gyro Angle", drive.getGyro().getYaw());
      SmartDashboard.putNumber("Last Heading", drive.getLastHeading());
      SmartDashboard.putBoolean("Field Relative", drive.getFieldRelative());

      SmartDashboard.putBoolean("Brake", drive.getBrake());
      
      
      
      
      
      
//        SmartDashboard.putBoolean("ToteIntakeSensor", lift.GetToteIntakeSensor().IsOn());
        
        SmartDashboard.putNumber("Car Height", lift.GetCar().GetHeight());
        SmartDashboard.putBoolean("Zero Sensor", lift.GetCar().GetZeroSensor().IsOn());
        SmartDashboard.putNumber("Actuator Raw", lift.GetCar().GetActuator().GetRaw());
        SmartDashboard.putNumber("Actuator Position", lift.GetCar().GetActuator().GetPositionRaw());
        
//        SmartDashboard
//                .putNumber("Distance Left", 
//                        12.402 
//                        * Math.pow(distance_sensor_left.getVoltage(), -1.074) 
//                        / 2.54);        
//        SmartDashboard
//                .putNumber("Distance Right", 
//                        12.402 
//                        * Math.pow(distance_sensor_right.getVoltage(), -1.074) 
//                        / 2.54);

        SmartDashboard.putNumber("TotesInStack", lift.GetTotesInStack());
        SmartDashboard.putString("RailContents", lift.GetRailContents().toString());
        SmartDashboard.putBoolean("ContainerInStack", lift.GetContainerInStack());
        
        SmartDashboard.putString("CylindersRails.State", lift.GetCylindersRails().GetState().toString());
        SmartDashboard.putString("CylindersPusher.State", lift.GetEjector().GetCylindersPusher().GetState().toString());

        SmartDashboard.putString("CurrentLiftAction_", CurrentLiftAction.toString());
        
        SmartDashboard.putString("StateAddChuteToteToStack", lift.GetStateAddChuteToteToStack().toString());
        SmartDashboard.putString("StateAddContainerToStack", lift.GetStateAddContainerToStack().toString());
        SmartDashboard.putString("StateAddFloorToteToStack", lift.GetStateAddFloorToteToStack().toString());
        SmartDashboard.putString("StateCarryStack", lift.GetStateCarryStack().toString());
        SmartDashboard.putString("StateDropStack", lift.GetStateDropStack().toString());
        SmartDashboard.putString("StateEjectStack", lift.GetStateEjectStack().toString());
    }
}
