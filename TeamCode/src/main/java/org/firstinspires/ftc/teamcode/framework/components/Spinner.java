package org.firstinspires.ftc.teamcode.framework.components;

import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import org.firstinspires.ftc.teamcode.framework.Task;
import org.firstinspires.ftc.teamcode.framework.BaseOpMode;

public class Spinner {
    private DcMotor motor;

    public Spinner(DcMotor motor) {
        this.motor = motor;
    }
    
    public void off(){
        motor.setPower(0);
    }

    public Task run(DcMotorSimple.Direction direction) {
        return new SpinnerTask(motor, direction);
    }
}

class SpinnerTask implements Task {
    DcMotor motor;
    double dir;
    long startTime;
    public SpinnerTask(DcMotor motor, DcMotorSimple.Direction direction) {
        dir = direction == DcMotorSimple.Direction.FORWARD ? 1 : -1;
        this.motor = motor;
    }

    boolean initialized = false;
    public boolean step() {
        if (!initialized) {
            motor.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
            motor.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
            motor.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
            startTime = System.currentTimeMillis();
            initialized = true;
        }

        long currTime = System.currentTimeMillis();
        BaseOpMode.tele.addData("time", currTime);
        BaseOpMode.tele.update();

        if (Math.abs(motor.getCurrentPosition()) < 1575) {
            motor.setPower((((currTime - startTime) * 0.4) + 0.35) * dir); // faster?
        } else if (Math.abs(motor.getCurrentPosition()) < 1575+1050) {
            motor.setPower(1 * dir);
        } else {
            motor.setPower(0);
            return true;
        }

        return false;
    }
}