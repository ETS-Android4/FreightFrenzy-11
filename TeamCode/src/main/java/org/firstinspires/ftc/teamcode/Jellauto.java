package org.firstinspires.ftc.teamcode;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import org.firstinspires.ftc.teamcode.framework.BaseOpMode;
import org.firstinspires.ftc.teamcode.framework.Task;
import org.firstinspires.ftc.teamcode.framework.components.Auto;

@Autonomous(name = "Jellauto")
public class Jellauto extends BaseOpMode {
    protected Auto dt;
    
    @Override
    public void runOpMode() throws InterruptedException {
        initHardware();
        dt = new Auto(motors, imu);
        
        waitForStart();
        
        Task.run(dt.move(40.0, 0.5), this);
    }
}
