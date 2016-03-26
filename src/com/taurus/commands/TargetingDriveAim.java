package com.taurus.commands;

import com.taurus.PIDController;
import com.taurus.Utilities;
import com.taurus.robot.Robot;
import com.taurus.vision.Target;
import com.taurus.vision.Vision;

import edu.wpi.first.wpilibj.Preferences;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.command.Command;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;

public class TargetingDriveAim extends Command
{
    private final double YAW_INTENTIONAL_OVERSHOOT = 1;  // Overshoot angle to compensate for snapback when motors stop
    public final static double DRIVE_ANGLE_TOLERANCE = .8;
    public final int HOLD_COUNT = 6;

    private int shooterAimed;
    private int driveAimed;

    private Vision vision;
    private PIDController drivePID;

    private double startTime;
    
    private double desiredYaw;
    private double desiredPitch;
    private double currentDistance;
    private boolean YawFound;
    private boolean PitchFound;
    
    public TargetingDriveAim() 
    {
        requires(Robot.aimerSubsystem);
        requires(Robot.rockerDriveSubsystem);
        
        vision = Vision.getInstance();        
        //drivePID = new PIDController(.2, 0.1, 0.1, .75); //TODO change max output 
    }

    protected void initialize() 
    {
        shooterAimed = 0;
        driveAimed = 0;
        desiredYaw = 0;
        YawFound = false;
        currentDistance = 0;

        startTime = Timer.getFPGATimestamp();
        SmartDashboard.putBoolean("TargetFound", false);
        SmartDashboard.putBoolean("TargetAimPitch", false);
        SmartDashboard.putBoolean("TargetAimYaw", false);     
    }

    protected void execute()
    {
        Utilities.PrintCommand("Aimer", this);
        Utilities.PrintCommand("Drive", this);
        
        Target target = vision.getTarget();

        SmartDashboard.putBoolean("TargetFound", target != null);
        
        if(target == null)
        {
            shooterAimed = 0;
            driveAimed = 0;
            return;
        }
        
        // do the up/down aiming
        if(PitchFound)
        {
            if(shooterAimed <= HOLD_COUNT)
            {
                if(Robot.aimerSubsystem.aim(desiredPitch))
                    shooterAimed++;
                else
                    shooterAimed = 0;
            }
            else
            {
                Robot.aimerSubsystem.setSpeed(0);
            }
        }
        
        SmartDashboard.putBoolean("YawFound", YawFound);
        SmartDashboard.putNumber("desiredYaw", desiredYaw);
        // do the left/right aiming
        if (YawFound)
        {
            // Use Gyro fine angle
            // Use image for initial angle
            if(driveAimed <= HOLD_COUNT)
            {
                if(Robot.rockerDriveSubsystem.turnToAngle(desiredYaw, false, currentDistance < 90))
                    driveAimed++;
                else
                    driveAimed = 0;
            }
            else
            {
                Robot.rockerDriveSubsystem.driveRaw(0, 0);
            }
        }
        else
        {
            // we only want to change our desired heading/yaw if the target info is new
            // this should help with using old data from the image
            if(target.NewData())
            {
                desiredYaw = target.Yaw() + Robot.rockerDriveSubsystem.getYaw();
                desiredYaw = desiredYaw + Math.signum(target.Yaw()) * YAW_INTENTIONAL_OVERSHOOT;
                desiredPitch = target.Pitch() + Robot.aimerSubsystem.getCurrentAngle();
                currentDistance = target.DistanceToTarget();
                target.NewDataClear();
                YawFound = true;
                PitchFound = true;
            }
        }
        
        SmartDashboard.putBoolean("TargetAimPitch", shooterAimed > HOLD_COUNT);
        SmartDashboard.putBoolean("TargetAimYaw", driveAimed > HOLD_COUNT);
        //SmartDashboard.putString("Targeting", "" + (target != null) + driveAimed + shooterAimed);
    }

    protected boolean isFinished() 
    {
        return shooterAimed > HOLD_COUNT;// && driveAimed > HOLD_COUNT;
    }

    protected void end()
    {
        Utilities.PrintCommand("Aimer", null);
        Utilities.PrintCommand("Drive", null);
//        Robot.rockerDriveSubsystem.driveRaw(0.0, 0.0);
//        Robot.aimerSubsystem.setSpeed(0);
    }

    protected void interrupted() 
    {
        end();
    }
    
    private boolean aim(double changeInAngle) 
    {
        updatedPIDConstants();
        double motorOutput = -drivePID.update(changeInAngle);  //TODO add limits for angle

        if(Math.abs(changeInAngle) <= DRIVE_ANGLE_TOLERANCE)
        {
            Robot.rockerDriveSubsystem.driveRaw(0.0, 0.0);
            return true;
        } 
        else
        {
            Robot.rockerDriveSubsystem.driveRaw(-motorOutput, motorOutput);
            return false;
        }
    }

    private void updatedPIDConstants()
    {

        drivePID.setP(Preferences.getInstance().getDouble("DrivePID_P", .2));
        drivePID.setI(Preferences.getInstance().getDouble("DrivePID_I", 0));
        drivePID.setD(Preferences.getInstance().getDouble("DrivePID_D", 0));
        drivePID.setMin(Preferences.getInstance().getDouble("DrivePID_Min", .1));
        drivePID.setMax(Preferences.getInstance().getDouble("DrivePID_Max", 0.7));
    }
}
