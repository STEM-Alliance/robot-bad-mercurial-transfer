package org.wfrobotics.commands;

import org.wfrobotics.robot.Robot;

import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.command.Command;

/**
 * This command rev's the shooters motor. 
 * This may be useful by itself in situations when you anticipate the need to shoot, reducing the setup time.
 * If another command tries to use the shooter subsystem, I envision this command ending.
 * @author drlindne
 *
 */
public class Rev extends Command
{
    public enum MODE {SHOOT, OFF};  // TODO DRL Unjam?
    
    private final MODE mode;
    
    public Rev(MODE mode)
    {
        requires(Robot.shooterSubsystem);
        
        this.mode = mode;
    }
    
    public Rev(MODE mode, double timeout)
    {
        requires(Robot.shooterSubsystem);
        
        this.mode = mode;
        setTimeout(timeout);
    }

    @Override
    protected void initialize()
    {
        
    }

    @Override
    protected void execute()
    {
        if (mode == MODE.SHOOT)
        {
            Robot.shooterSubsystem.setSpeed(Constants.SHOOTER_READY_SHOOT_SPEED);
        }
        else if (mode == MODE.OFF)
        {
            Robot.shooterSubsystem.setSpeed(0);
        }
        else
        {
            DriverStation.reportError("Rev mode not supported", true);
        }
    }

    @Override
    protected boolean isFinished()
    {
        return isTimedOut();
    }

    @Override
    protected void end()
    {
        // If you need to shut off the motors, probably create a new command or set the subsystem in your group's end()???
    }

    @Override
    protected void interrupted()
    {
        end();
    }
}