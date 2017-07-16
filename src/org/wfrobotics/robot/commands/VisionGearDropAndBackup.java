package org.wfrobotics.robot.commands;

import org.wfrobotics.reuse.commands.drive.AutoDrive;
import org.wfrobotics.reuse.commands.driveconfig.FieldRelative;
import org.wfrobotics.robot.Robot;

import edu.wpi.first.wpilibj.command.CommandGroup;

public class VisionGearDropAndBackup extends CommandGroup
{
    public VisionGearDropAndBackup()
    {
        boolean fieldRelative = Robot.driveSubsystem.getFieldRelative();
        
        addSequential(new VisionGear());  // In front of the gear; score it with vision
        
        // wait a half sec
        addSequential(new AutoDrive(0, 0, 0, -1, .5));
        
        // now drop it and back up
        addSequential(new FieldRelative(false));

        addParallel(new Lift(Lift.MODE.DOWN));
        addSequential(new AutoDrive(0.2, 0, 0, -1, .75));
        
        addParallel(new Lift(Lift.MODE.DOWN));
        addSequential(new AutoDrive(.4, 0, 0, -1, .5));
        
        addSequential(new FieldRelative(fieldRelative));
        
        addSequential(new AutoDrive(0, 0, 0, -1, .1));  // Don't coast GOOD        
    }
}