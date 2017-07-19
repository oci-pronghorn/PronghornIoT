package com.ociweb.iot.grove.simple_analog;
import com.ociweb.gl.api.Behavior;
import com.ociweb.iot.maker.AnalogListener;
import com.ociweb.iot.maker.Port;

public interface SimpleAnalogListener extends Behavior{
	public void simpleAnalogEvent(Port port, long time, long durationMillis, int value);
}
