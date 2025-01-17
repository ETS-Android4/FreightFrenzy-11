package org.firstinspires.ftc.teamcode.framework.components;

import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.hardware.bosch.BNO055IMU;

import org.firstinspires.ftc.teamcode.framework.Task;
import org.firstinspires.ftc.teamcode.framework.Motors;
import org.firstinspires.ftc.teamcode.framework.BaseOpMode;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;
import android.os.Environment;

public class Auto {
    public final static long ENC_PER_IN = 1075;

    protected DcMotor[] motors;
    public Auto(DcMotor[] motors) {
        this.motors = motors;
        for (DcMotor motor: motors) {
            motor.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
            motor.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        }
    }

    public Pose pose = null;
    public PositioningTask position() {
        return new PositioningTask(motors, this.pose);
    }

    public Task moveTo(Pose target, double speed, double Pm, double Pa) {
        return new MoveTask(motors, target, this, speed, Pm, Pa);
    }

    public String debugEncoders() {
        return String.format("L:%d R:%d H:%d", motors[Motors.E_L].getCurrentPosition(), +motors[Motors.E_R].getCurrentPosition(), motors[Motors.E_H].getCurrentPosition());
    }

    public static class Pose {
        public Pose(double x, double y, double angle) {
            this.x = x;
            this.y = y;
            this.angle = angle;
        }

        public Pose clone() {
            return new Pose(x, y, angle);
        }

        public double x;
        public double y;
        public double angle;

        /** Forward offset; positive if horizontal encoder in front, negative if in back */
        private static double F = 0;

        /** The lateral distance, distance between the two vertical encoders */
        private static double L = 0;

        static {
            // Initialize the F and L constants to the values provided in ~/.freight-frenzy/odo.txt
            try {
                File rfile = new File(Environment.getExternalStorageDirectory() + "/.freight-frenzy/odo.txt");
                Scanner file = new Scanner(rfile);
                while (file.hasNextLine()) {
                    String data = file.nextLine();
                    if (data.startsWith("F")) {
                        F = Double.parseDouble(data.substring(2));
                    } else if (data.startsWith("L")) {
                        L = Double.parseDouble(data.substring(2));
                    }
                }
            } catch (FileNotFoundException e) {
                // fail silently, dont want crash
                F = 0;
                L = 0;
            }
        }

        /** Updates `this` to match new encoder readings*/
        public void update(double d_left, double d_right, double d_horiz) {
            double phi = (d_left - d_right) / L;
            double dxc = (d_left + d_right) / 2,
                dxh = d_horiz - (F * phi);

            double[] diff = calculateDiff(dxc, dxh, phi, angle);
            x += diff[0];
            y += diff[1];
            angle += phi;

            angle = angle % (2*Math.PI);
        }

        /** Returns `this` - `other` */
        public double[] minus(Pose other) {
            return new double[] {x - other.x, y - other.y, angle - other.angle};
        }

        /** Given dxc (forward/back), dxh (horizontal), and phi (angle), return dx, dy */
        private static double[] calculateDiff(double dxc, double dxh, double phi, double prev_angle) {
            // i think i did this wrong
            // need to double check
            double dxc2, dxh2;
            if (phi != 0) {
                dxc2 = dxc * Math.sin(phi)/phi + dxh * ((Math.cos(phi) - 1) / phi);
                dxh2 = dxc * ((1 - Math.cos(phi)) / phi) + dxh * Math.sin(phi)/phi;
            } else {
                dxc2 = dxc;
                dxh2 = dxh;
            }

            double dx = dxc2 * Math.cos(prev_angle) + dxh2 * -Math.sin(prev_angle),
                dy = dxc2 * Math.sin(prev_angle) + dxh2 * Math.cos(prev_angle);

            return new double[] {dx, dy};
        }

        /** For debugging purposes */
        public String toString() {
            return String.format("[%.2f %.2f; %.2fπ] (%.2f deg)", x, y, angle / Math.PI, angle * 180/Math.PI);
        }
    }
}

class MoveTask implements Task { 
    private Auto.Pose target;
    private DcMotor[] motors;
    private Auto auto;

    private double speed; // 0.5
    private double Pm; // 0.0007
    private double Pa; // 1.1

    public MoveTask(DcMotor[] motors, Auto.Pose target, Auto auto, double speed, double Pm, double Pa) {
        this.target = target;
        this.motors = motors;
        this.auto = auto;
        this.speed = speed;
        this.Pm = Pm;
        this.Pa = Pa;
    }

    private double[] prevError;
    private long prevTime;
    public boolean step() {
        // calculate error e(t) with respect to time
        double[] diff = target.minus(auto.pose);
        double diffX_g = diff[0], diffY_g = diff[1], diffTheta = diff[2];

        // if difference is small finish
        if (Math.abs(diffX_g) <= 300 && Math.abs(diffY_g) <= 300 && Math.abs(diffTheta) <= 0.08 * Math.PI) {
            for (DcMotor motor: motors) {
                motor.setPower(0);
            }
            return true;
        }

        // transform global x and y into relative x and y
        double diffX = diffX_g * Math.cos(auto.pose.angle) + diffY_g * Math.sin(auto.pose.angle);
        double diffY = diffX_g * -Math.sin(auto.pose.angle) + diffY_g * Math.cos(auto.pose.angle);

        // transform x and y difference into a/b (diagonal) difference
        double diffA = diffX * 1 + diffY * -1, // [1 1] motors: BR, FL
            diffB = diffX * 1 + diffY * 1; // [-1 1] motors: BL, FR

        final double Dm = 0.0002;
        final double Da = 0;

        // calculate derivative de/dt
        double[] deriv = new double[3];
        long currTime = System.currentTimeMillis();
        if (prevError != null) {
            for (int i = 0; i < 3; i++) {
                deriv[i] = (diff[i] - prevError[i]) / (currTime - prevTime);
            }
        }
        prevTime = currTime;
        double derivX = deriv[0], derivY = deriv[1], derivTheta = deriv[2];

        // transform dx/dt and dy/dt into da/dt and db/dt
        double derivA = derivX * 1 + derivY * 1,
            derivB = derivX * -1 + derivY * 1;

        // apply PID: u(t) = Pe(t) + Dde/dt
        double pa = diffA * Pm + derivA * Dm, pb = diffB * Pm  + derivB * Dm, pTheta = diffTheta * Pa + derivTheta * Da;

        if (Math.abs(pa + pTheta) > speed || Math.abs(pa - pTheta) > speed || Math.abs(pb + pTheta) > speed || Math.abs(pb - pTheta) > speed) {
            double max = Math.max(Math.max(
                    Math.abs(pa + pTheta), Math.abs(pa - pTheta)),
                Math.max(
                    Math.abs(pb + pTheta), Math.abs(pb - pTheta)));
            pa = pa/max * speed;
            pb = pb/max * speed;
        }

        // set powers
        motors[Motors.BR].setPower(pa + pTheta);
        motors[Motors.FL].setPower(pa - pTheta);
        motors[Motors.BL].setPower(pb - pTheta);
        motors[Motors.FR].setPower(pb + pTheta);

        return false;
    }
}

class PositioningTask implements Task {
    protected DcMotor[] motors;
    protected Auto.Pose pose_;

    public PositioningTask(DcMotor[] motors, Auto.Pose poseToUpdate) {
        this.motors = motors;
        pose_ = poseToUpdate;
    }

    /** Previous encoder positions */
    protected double[] prevEncoderPosition = new double[4];
    protected double[] currEncoderPosition = new double[4];
    protected boolean initialized = false;
    public boolean step() {
        for (int i = 0; i < 4; i++) {
            currEncoderPosition[i] = motors[i].getCurrentPosition();
        }

        if (initialized) {
            pose_.update(currEncoderPosition[Motors.E_L] - prevEncoderPosition[Motors.E_L],
                +(currEncoderPosition[Motors.E_R] - prevEncoderPosition[Motors.E_R]),
                currEncoderPosition[Motors.E_H] - prevEncoderPosition[Motors.E_H]);

            for (int i = 0; i < 4; i++) {
                prevEncoderPosition[i] = currEncoderPosition[i];
            }
        }

        initialized = true;

        return false;      
    }
}


/* OLD AUTO CLASS
/** Represents a drivetrain, for use during autonomous *
public class Auto {
    // measurements
    // 1000 ticks / 19 in
    // 500 ticks / 9.5 in
    // new
    // 500 ticks / 14 in

    public static final double ENCODERS_PER_IN = 500 / 14; // experimental testing needed

    protected DcMotor[] motors;
    protected BNO055IMU imu;
    public Auto(DcMotor[] motors, BNO055IMU imu) {
        this.motors = motors;
        this.imu = imu;
    }

    // positive angle = right
    public Task move(double distance, double angle, double maxSpeed) {
        return new MoveTask(motors, imu, distance, angle, maxSpeed);
    }

    private static class PivotState {
        boolean initialized;
        double targetAngle;
        double initialAngle;
    }

    // angle in degrees
    public Task pivot(double angle, double maxSpeed) {
        final PivotState state = new PivotState();
        state.initialized = false;

        final double direction = angle > 0 ? 1 : -1;

        final double P = 1.0 / 10.0; // at an angle of 10 and above, P*angle >= 1
        // 0.2 power <=> 2 deg *P

        return () -> {
            if (!state.initialized) {
                state.initialAngle = ((imu.getAngularOrientation().firstAngle % 360) + 360) % 360;
                state.targetAngle = (state.initialAngle + angle + 360) % 360;
                for (DcMotor motor : motors) {
                    motor.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
                }
                state.initialized = true;
            }

            double currentAngle = ((imu.getAngularOrientation().firstAngle % 360) + 360) % 360;
            double diff = (state.targetAngle - currentAngle + 360) % 360; // positive number, [0, 360]

            if (diff <= 2 || Math.abs(diff-360) <= 2) {
                stop();
                return true;
            }

            double pow;
            if (direction == 1) {
                pow = diff * P;
            } else { // direction == -1
                pow = (diff-360)*P;
            }
            if (pow > maxSpeed) pow = maxSpeed;
            if (pow < -maxSpeed) pow = -maxSpeed;

            BaseOpMode.tele.addData("initial", state.initialAngle);
            BaseOpMode.tele.addData("target", state.targetAngle);
            BaseOpMode.tele.addData("current", currentAngle);
            BaseOpMode.tele.addData("diff", diff);
            BaseOpMode.tele.addData("pow", pow);
            BaseOpMode.tele.update();

            motors[Motors.FR].setPower(pow);
            motors[Motors.BR].setPower(pow);
            motors[Motors.FL].setPower(-pow);
            motors[Motors.BL].setPower(-pow);
            return false;
        };
    }

    public void stop() {
        for (DcMotor motor : motors) {
            motor.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
            motor.setPower(0);
        }
    }
}

class MoveTask implements Task {
    private double encoderDist;
    private double angle;
    private double maxSpeed;

    protected DcMotor[] motors;
    protected BNO055IMU imu;

    public MoveTask(DcMotor[] motors, BNO055IMU imu, double distance, double angle, double maxSpeed) {
        this.encoderDist = distance * Auto.ENCODERS_PER_IN;
        this.angle = angle;
        this.maxSpeed = maxSpeed;
        this.motors = motors;
        this.imu = imu;
    }

    private static double encoderAverage(double value1, double value2) {
        if (value1 == 0) return value2;
        if (value2 == 0) return value1;
        return (value1 + value2)/2;
    }

    private final static double KP = 0.003;
    private final static double KD = 0.001;

    private boolean initialized;
    private double previousDist;
    private long previousTime;
    private double currentAngle;
    private double targetAngle;
    public boolean step() {
        if (!initialized) {
            for (DcMotor motor: motors) {
                motor.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
                motor.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
            }
            targetAngle = ((imu.getAngularOrientation().firstAngle % 360) + 360) % 360;

            initialized = true;
        }

        // a = [1,1]
        // b = [-1,1]
        double aDistTarget = encoderDist * Math.cos((angle + 45) * Math.PI / 180);
        double bDistTarget = encoderDist * Math.sin((angle + 45) * Math.PI / 180);

        double aDistCurrent = encoderAverage(motors[Motors.BL].getCurrentPosition(), motors[Motors.FR].getCurrentPosition());
        double bDistCurrent = encoderAverage(motors[Motors.FL].getCurrentPosition(), motors[Motors.BR].getCurrentPosition());

        BaseOpMode.tele.addData("target", aDistTarget);
        BaseOpMode.tele.addData("current", aDistCurrent);
        BaseOpMode.tele.update();

        double dist = Math.sqrt((aDistCurrent - aDistTarget)*(aDistCurrent - aDistTarget) + (bDistCurrent - bDistTarget)*(bDistCurrent - bDistTarget)) * Math.signum(aDistTarget*(aDistTarget - aDistCurrent));
        if (Math.abs(dist) < 1 * Auto.ENCODERS_PER_IN) {
            for (DcMotor motor: motors) {
                motor.setPower(0);
            }
            return true;
        }

        // calculate distance in angle
        double currentAngle = ((imu.getAngularOrientation().firstAngle % 360) + 360) % 360;
        double angleDiff = (targetAngle - currentAngle + 360) % 360; // positive number, [0, 360]
        if (angleDiff > 180) {
            angleDiff -= 360;
        }
        double anglePow = angleDiff * 0.001;

        // calculate power
        double pow;
        if (previousTime == 0) {
            pow = dist*KP;
        } else {
            long time = System.currentTimeMillis();
            pow = dist*KP + (dist-previousDist)/(time-previousTime)*KD;
            previousDist = dist;
            previousTime = time;
        }

        if (pow > maxSpeed) {
            pow = maxSpeed;
        }
        if (pow < -maxSpeed) {
            pow = -maxSpeed;
        }

        double aVel = pow * Math.cos((angle + 45) * Math.PI / 180);
        double bVel = pow * Math.sin((angle + 45) * Math.PI / 180);
        motors[Motors.BL].setPower(aVel - anglePow);
        motors[Motors.FR].setPower(aVel + anglePow);
        motors[Motors.BR].setPower(bVel + anglePow);
        motors[Motors.FL].setPower(bVel - anglePow);
        return false;
    }
} */