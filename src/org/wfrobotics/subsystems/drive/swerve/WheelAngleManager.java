package org.wfrobotics.subsystems.drive.swerve;

import org.wfrobotics.Utilities;

import com.ctre.CANTalon;
import com.ctre.CANTalon.FeedbackDevice;
import com.ctre.CANTalon.TalonControlMode;

import edu.wpi.first.wpilibj.Preferences;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;

public class WheelAngleManager 
{
    private static final double HalfCircleCounts = 4096/2;
    private static final double HalfCircleDegrees = 180;
    private static final double CountsPerDegree = 4096/360;
    /** While not moving, wheels angle error small enough to save and use a cached angle**/
    private final double ANGLE_SW_DEADBAND = 4096 * .02;
    
    private final CANTalon talon;
    /** Invert the angle motor and sensor to swap left/right */
    private final boolean angleInverted = true;
    private final String name;
    private static final boolean DEBUG = true;

    private double offset = 0;
    private boolean reverseMotor = false;
    private boolean usingCachedAngle = false;
    private double cachedAngle = 0;
    
    public WheelAngleManager(String name, int talonAddress, boolean invert)
    {
        this.name = name;
        talon = new CANTalon(talonAddress);
        //talon.setVoltageRampRate(60);
        talon.ConfigFwdLimitSwitchNormallyOpen(true);
        talon.ConfigRevLimitSwitchNormallyOpen(true);
        talon.enableForwardSoftLimit(false);
        talon.enableReverseSoftLimit(false);
        talon.enableBrakeMode(false);
        talon.configNominalOutputVoltage(0, 0);
        talon.configPeakOutputVoltage(11, -11);  // Almost as good as setVoltageRampRate, transcients are brief - mainly when switching 180 degrees
        //angleMotor.SetVelocityMeasurementPeriod(CANTalon.VelocityMeasurementPeriod.Period_50Ms);
        //angleMotor.SetVelocityMeasurementWindow(32);type name = new type();
        
        talon.setFeedbackDevice(FeedbackDevice.CtreMagEncoder_Absolute);
        talon.changeControlMode(TalonControlMode.Position);
        talon.setPID(SwerveConstants.ANGLE_PID_P, SwerveConstants.ANGLE_PID_I, SwerveConstants.ANGLE_PID_D,
                          0, 0, 10, 0);
        
        talon.enableZeroSensorPositionOnForwardLimit(false);
        talon.enableZeroSensorPositionOnIndex(false, true);
        talon.enableZeroSensorPositionOnIndex(false, false);
        talon.enableZeroSensorPositionOnReverseLimit(false);
        
        //talon.reverseSensor(invert);
        talon.setInverted(invert);
        talon.setAllowableClosedLoopErr(0);
        talon.enableControl();
        talon.set(talon.getPosition());
    }
    
    public void setPosition(double desiredDegrees, boolean maintainCurrentAngle)
    {        
      double desiredCounts = Utilities.wrapToRange(desiredDegrees * CountsPerDegree + offset, 0, 2*HalfCircleCounts);
      double error;
      double commanded = -1;
      double countsCurrent = Utilities.wrapToRange(talon.getPosition(), -HalfCircleCounts, HalfCircleCounts);
      
      error = Utilities.wrapToRange(desiredCounts - countsCurrent, -HalfCircleCounts, HalfCircleCounts);
      
      if (Math.abs(error) < ANGLE_SW_DEADBAND ||
          (maintainCurrentAngle && !usingCachedAngle))
      {
          cachedAngle = talon.getPosition();
          usingCachedAngle = true;
      }
      else if (!maintainCurrentAngle)
      {
          usingCachedAngle = false;
      }
      
      if (usingCachedAngle)
      {
          commanded = cachedAngle;
          //applySmartReverse(cachedAngle, error, countsCurrent);  // Must always update reverse boolean, even while using cache 
      }
      else
      {
          //commanded = applySmartReverse(desiredCounts, error, countsCurrent);'
          commanded = desiredCounts;
      }
        
      talon.set(commanded);
      
      if(DEBUG)
      {
          SmartDashboard.putNumber(name + ".get", talon.getPosition());
          SmartDashboard.putNumber(name + ".Desired", desiredCounts);
//          SmartDashboard.putNumber(name + ".SensorValue", sensorValue);
//          SmartDashboard.putNumber(name + ".Error", error);
          SmartDashboard.putNumber(name + ".Cached", cachedAngle);
          SmartDashboard.putNumber(name + ".Commanded", commanded / CountsPerDegree);
          SmartDashboard.putNumber(name + ".CommandedCounts", commanded);
          SmartDashboard.putBoolean(name + ".Reverse", reverseMotor);
          SmartDashboard.putNumber(name + ".angle.err", talon.getClosedLoopError() & 0xFFF);
      }
    }
    
    public double getCurrentAngle()
    {
        //return Utilities.wrapToRange(talon.getEncPosition() / CountsPerDegree, 360);
        return Utilities.wrapToRange((talon.getPosition() + offset) / CountsPerDegree, 360);
    }
    
    public double getAnglePotAdjusted()
    {
        double invert = reverseMotor ? -1 : 1;
        double position = getCurrentAngle();
        
        return Utilities.round(Utilities.wrapToRange(invert * position, -HalfCircleDegrees, HalfCircleDegrees), 2);
    }
    
    public void setOffset(double degrees)
    {        
        int absolute = talon.getPulseWidthPosition() & 0xFFF;
        double offset = Utilities.wrapToRange(absolute + degrees * CountsPerDegree, -HalfCircleCounts, HalfCircleCounts);
        
        talon.setPosition(offset);
    }

    public void resetIntegral()
    {
        talon.clearIAccum();
    }
    
    public void updatePID()
    {
        talon.setP(Preferences.getInstance().getDouble("WheelAnglePID_P", SwerveConstants.ANGLE_PID_P));
        talon.setI(Preferences.getInstance().getDouble("WheelAnglePID_I", SwerveConstants.ANGLE_PID_I));
        talon.setD(Preferences.getInstance().getDouble("WheelAnglePID_D", SwerveConstants.ANGLE_PID_D));
    }
    
    /**
     * Should the motor be driving in reverse? (180 vs 360 turning)
     * @return true if motor should be reversed
     */
    public boolean isReverseMotor()
    {
        return reverseMotor;
    }
    
    /**
     * Calculate the error from the current reading and desired position, determine if motor reversal is needed
     * @param desired angle you want
     * @param error 
     * @param current angle you are at
     * @return Offset to add to desired angle
     */
    private double applySmartReverse(double desired, double error, double current)
    {
        // error = Utilities.wrapToRange(desiredCounts - countsCurrent, -HalfCircleCounts, HalfCircleCounts);
        double reverseAngle = Utilities.wrapToRange(desired + HalfCircleCounts, -HalfCircleCounts, HalfCircleCounts);
        double reverseError = Utilities.wrapToRange(reverseAngle - current, -HalfCircleCounts, HalfCircleCounts);
        double offsetNoSignChange = 0;
        double bestAngle = desired;
        
//        reverseMotor = Math.abs(reverseError) < Math.abs(error);
        reverseMotor = Math.abs(error) > HalfCircleCounts / 2;
        bestAngle = (reverseMotor) ? reverseAngle : desired;
        
//        // Fix issue where Mag Encoder doesn't command on shortest path when the angle's sign flips
//        if (bestAngle < 0 || bestAngle > HalfCircleCounts)
//        {
//            SmartDashboard.putBoolean(name + ".WheelApplyOffset", true);
//            offsetNoSignChange = HalfCircleCounts + bestAngle;
//            bestAngle = Utilities.wrapToRange(desired + offsetNoSignChange, -HalfCircleCounts, HalfCircleCounts);
//        }
//        else
//        {
//            SmartDashboard.putBoolean(name + ".WheelApplyOffset", false);
//        }
        
        return Utilities.wrapToRange(bestAngle, 0, 2*HalfCircleCounts);
        //return offsetNoSignChange;
        //return (reverseMotor) ? reverseAngle : desired;
        
    }
}