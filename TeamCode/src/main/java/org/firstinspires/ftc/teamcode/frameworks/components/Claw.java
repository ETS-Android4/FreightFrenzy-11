package org.firstinspires.ftc.teamcode.frameworks.components;

import com.qualcomm.robotcore.hardware.Servo;
import com.qualcomm.robotcore.hardware.DcMotor;

public class Claw  {

    private static final double GRAB_POSITION = 1.0;
    private static final double UNGRAB_POSITION = 0.3;   

    private DcMotor pivot;
    private Servo servo;

    public Claw(DcMotor pivot, Servo servo) {
        this.pivot=pivot;
        this.servo=servo;
    }

    public void up() {
        pivot.setPower(0.5);
    }

    public void down() {
        pivot.setPower(-0.5);
    }

    public void off() {
        pivot.setPower(0);
    }

    public void grab() {
        servo.setPosition(GRAB_POSITION);
    }

    public void ungrab() {
        servo.setPosition(UNGRAB_POSITION);
    }
}