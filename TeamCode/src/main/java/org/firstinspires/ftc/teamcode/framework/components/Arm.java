package org.firstinspires.ftc.teamcode.framework.components;

import com.qualcomm.robotcore.hardware.DcMotor;
import org.firstinspires.ftc.teamcode.framework.Task;

public class Arm {
    DcMotor[] motors;
    private static final double MAX_SPEED = 0.3;

    public Arm(DcMotor[] motors) {
        this.motors = motors;

        for (DcMotor motor: motors) {
            motor.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
            motor.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);  
        }
    }

    public void setBaseline() {
        for (DcMotor motor: motors) {
            motor.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
            motor.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);  
        }
    }

    public void test(int n) {
        motors[n].setPower(MAX_SPEED);
    }

    public void up() {
        for (DcMotor motor: motors) {
            motor.setPower(MAX_SPEED);
        }
    }

    public void down() {
        for (DcMotor motor: motors) {
            motor.setPower(-MAX_SPEED);
        }
    }

    public Task moveTo(long[] encoderPositions) {
        return new ToTask(encoderPositions);
    }

    private class ToTask implements Task {
        long[] encoderPositions;
        public ToTask(long[] encoderPositions) {
            this.encoderPositions = encoderPositions;
        }

        public boolean step() {
            // if both motors are less than
            // 256 ticks from the target position
            // stop the task
            boolean isFinished = true;
            for (int i = 0; i < 2; i++) {
                long target = encoderPositions[i];
                if (Math.abs(motors[i].getCurrentPosition() - target) > 256) {
                    isFinished = false;
                }
            }
            if (isFinished) return true;

            for (int i = 0; i < 2; i++) {
                long curr = motors[i].getCurrentPosition();
                long target = encoderPositions[i];

                // u(t) = e(t) * P
                double pow = (target - curr) * 0.03;

                // maximum speed :D
                if (pow > MAX_SPEED) pow = MAX_SPEED;
                if (pow < -MAX_SPEED) pow = -MAX_SPEED;

                motors[i].setPower(pow);
            }
            return false;
        }
    }
}

