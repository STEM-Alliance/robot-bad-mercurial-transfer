package org.wfrobotics.robot.commands;

import org.wfrobotics.Utilities;
import org.wfrobotics.reuse.commands.drive.DriveSwerve;
import org.wfrobotics.robot.Robot;
import org.wfrobotics.robot.config.Commands;
import org.wfrobotics.robot.config.IO;

import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.command.CommandGroup;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;

public class SteamworksDrive extends CommandGroup 
{  
    private IntakeSetup intake;

    private double intakeLastOn;

    public SteamworksDrive()
    {   
        intake = new IntakeSetup(false);

        addParallel(intake);
        addSequential(new DriveSwerve(DriveSwerve.MODE.FUSION));
    }
    
    protected void initialized()
    {
        Robot.driveSubsystem.setGearHopper(true);
    }

    protected void execute()
    {
        double angleDifference = Robot.driveSubsystem.getLastVector().getAngle();
        double vectorMag = Robot.driveSubsystem.getLastVector().getMag();
        boolean intakeOn;

        angleDifference = -(angleDifference - 90);
        angleDifference = Utilities.wrapToRange(angleDifference, -180, 180);

        // Restart the intake timers whenever we move in that direction
        if(Math.abs(vectorMag) > .1 &&
                angleDifference > Commands.INTAKE_OFF_ANGLE &&
                angleDifference  < (180 - Commands.INTAKE_OFF_ANGLE))
        {
            intakeLastOn = Timer.getFPGATimestamp();
        }

        if(IO.buttonPanelBlackTop.get() || IO.buttonManY.get())
        {
            intakeOn = true;
        }
        else
        {
            // Keep the intakes for a while after we stop moving in that direction
            intakeOn = (Timer.getFPGATimestamp() - intakeLastOn) < Commands.INTAKE_OFF_TIMEOUT;
        }
        
        SmartDashboard.putNumber("angleDifference", angleDifference);
        SmartDashboard.putBoolean("intakeOn", intakeOn);

        intake.set(intakeOn);
    }

    protected void end() 
    {
        Robot.intakeSubsystem.setSpeed(0);
    }

    protected void interrupted()
    {
        end();
    }
}