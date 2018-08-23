package org.wfrobotics.robot.auto;

import org.wfrobotics.robot.subsystems.IntakeSubsystem;

import edu.wpi.first.wpilibj.command.Command;

/** Set the jaw solenoids open or closed to nom nom nom dat cube */
public class JawsSet extends Command
{
    private final IntakeSubsystem intake = IntakeSubsystem.getInstance();
    private final boolean wantOpen;

    public JawsSet(boolean open, double timeout, boolean blockIntake)
    {
        if (blockIntake)  // Don't allow SmartIntake to override this autonomous command
        {
            requires(intake);
        }
        wantOpen = open;
        setTimeout(timeout);
    }

    protected void initialize()
    {
        intake.setJaws(wantOpen);
    }

    protected boolean isFinished()
    {
        return isTimedOut();
    }
}
