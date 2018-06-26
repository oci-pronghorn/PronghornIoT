package com.ociweb.iot.maker.location;

import com.ociweb.gl.api.Behavior;
import com.ociweb.iot.maker.FogApp;
import com.ociweb.iot.maker.FogRuntime;
import com.ociweb.iot.maker.Hardware;
import com.ociweb.iot.maker.LocationListener;
import org.junit.Test;

public class LocationListenerTest {

    @Test
    public void correctLocationBehaviorTest() {

        FogRuntime runtime = FogRuntime.run(new FogApp() {
            @Override
            public void declareConnections(Hardware builder) {

            }

            @Override
            public void declareBehavior(FogRuntime runtime) {
                runtime.registerListener(new LocationListener() {
                    @Override
                    public boolean location(int location, long oddsOfRightLocation, long totalSum) {
                        return false;
                    }
                });
            }
        });
    }

}
