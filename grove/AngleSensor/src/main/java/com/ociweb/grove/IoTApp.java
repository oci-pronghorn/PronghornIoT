package com.ociweb.grove;


import static com.ociweb.iot.grove.simple_analog.SimpleAnalogTwig.AngleSensor;
import static com.ociweb.iot.grove.simple_digital.SimpleDigitalTwig.LED;
import static com.ociweb.iot.maker.Port.A0;
import static com.ociweb.iot.maker.Port.D3;
import static com.ociweb.iot.maker.Port.D4;

import com.ociweb.iot.maker.FogApp;
import com.ociweb.iot.maker.FogRuntime;
import com.ociweb.iot.maker.Hardware;
import com.ociweb.iot.maker.Port;

public class IoTApp implements FogApp
{
    public static final Port LED1_PORT = D3;
    public static final Port LED2_PORT = D4;
    public static final Port ANGLE_SENSOR = A0;
    
    @Override
    public void declareConnections(Hardware c) {
        
        c.connect(LED, LED1_PORT);
        c.connect(LED,LED2_PORT);
        c.connect(AngleSensor,ANGLE_SENSOR);
        
    }
    
    
    @Override
    public void declareBehavior(FogRuntime runtime) {
        
        runtime.registerListener(new AngleSensorBehavior(runtime));
    }
}
