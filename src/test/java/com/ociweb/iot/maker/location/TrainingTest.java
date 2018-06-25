package com.ociweb.iot.maker.location;

import com.ociweb.iot.maker.*;
import org.junit.Test;

public class TrainingTest {
    @Test
    public void calibrationAndTrainingTest() {

        FogRuntime runtime = FogRuntime.run(new FogApp() {

            @Override
            public void declareConnections(Hardware builder) {

            }

            @Override
            public void declareBehavior(FogRuntime runtime) {
                runtime.registerListener(new CalibrationListener() {
                    @Override
                    public boolean finishedCalibration(int startLocation, int units) {
                        return false;
                    }
                });
            }
        });
    }

}
