package org.wfrobotics.robot.commands.lift;

import org.wfrobotics.reuse.commands.wrapper.SeriesCommand;
import org.wfrobotics.robot.config.LiftHeight;

import edu.wpi.first.wpilibj.command.Command;
import edu.wpi.first.wpilibj.command.CommandGroup;

public class AutoLiftToBottom extends CommandGroup
{
    private static final double kLiftRange = LiftHeight.HatchHigh.get() - LiftHeight.HatchLow.get();
    //    private static final double kWristStowHeight = kLiftRange * 0.66 + LiftHeight.Intake.get();
    private static final double kWristUnstowHeight = kLiftRange * 0.66 + LiftHeight.HatchLow.get();

    public AutoLiftToBottom()
    {
        this(0.0);
    }

    public AutoLiftToBottom(double overrideWristDownAngle)
    {
        //        addSequential(new IfLiftIsAbove(new WristToHeight(90.0), kWristStowHeight));
        addParallel(new SeriesCommand(new Command[] {
            new WaitForLiftHeight(kWristUnstowHeight, false),
            //            new WristToHeight(overrideWristDownAngle),  // SLAM SLAM
        }));
        addSequential(new LiftToHeight(LiftHeight.HatchLow.get() + 4.0));  // Gentler slamming
        addSequential(new LiftToHeight(LiftHeight.HatchLow.get()));
        addSequential(new LiftGoHome(-0.25, 2.0));  // Anything the command didn't let the PID finish
    }
}