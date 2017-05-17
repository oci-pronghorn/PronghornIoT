package com.ociweb.iot.trafficlight;


import static com.ociweb.iot.grove.GroveTwig.LED;
import static com.ociweb.iot.maker.Port.D3;
import static com.ociweb.iot.maker.Port.D5;
import static com.ociweb.iot.maker.Port.D6;

import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.iot.grove.Grove_LCD_RGB;
import com.ociweb.iot.maker.CommandChannel;
import com.ociweb.iot.maker.DeviceRuntime;
import com.ociweb.iot.maker.Hardware;
import com.ociweb.iot.maker.IoTSetup;
import com.ociweb.iot.maker.Port;
public class IoTApp implements IoTSetup
{
	private static final Port LED3_PORT = D5;
	private static final Port LED1_PORT = D3;
	private static final Port LED2_PORT = D6;	
	
	public static int RED_MS = 10000;
	public static int GREEN_MS = 8000;
	public static int YELLOW_MS = 2000;
			
	private boolean isWebControlled = false;
	private int webRoute = -1;
	
	private enum State {
		REDLIGHT(RED_MS), 
		GREENLIGHT(GREEN_MS),
		YELLOWLIGHT(YELLOW_MS);
		private int deltaTime;
		State(int deltaTime){this.deltaTime=deltaTime;}
		public int getTime(){return deltaTime;}
	}
	
    public static void main( String[] args ) {
        DeviceRuntime.run(new IoTApp());
    }
    
    
    @Override
    public void declareConnections(Hardware c) {
		c.connect(LED, LED1_PORT);
		c.connect(LED, LED2_PORT);
		c.connect(LED, LED3_PORT);
		c.useI2C();
		
		if (isWebControlled) {
			
			webRoute = c.registerRoute("/trafficLight?color=${color}");
			
		}
		
		
    }


    @Override
    public void declareBehavior(DeviceRuntime runtime) {
    	
    	if (isWebControlled) {
    		
    		//the rest API is missing
    		
    		
    		//runtime.add
    		
    		
    		
    	} else {
    		configureTimeBasedColorChange(runtime);
    	}
       
       
    }


	protected void configureTimeBasedColorChange(DeviceRuntime runtime) {
		final CommandChannel channel0 = runtime.newCommandChannel(GreenCommandChannel.DYNAMIC_MESSAGING);
    	runtime.addPubSubListener((topic, payload)-> {
    		
    		channel0.setValue(LED1_PORT, 1);
			channel0.setValue(LED2_PORT, 0);
			channel0.setValue(LED3_PORT, 0);
			Grove_LCD_RGB.commandForTextAndColor(channel0, "RED", 255, 0, 0);
			channel0.block(State.REDLIGHT.getTime());
			
			channel0.openTopic("GREEN").ifPresent(w->{w.publish();});
			return true;
    	}).addSubscription("RED");

    	final CommandChannel channel1 = runtime.newCommandChannel(GreenCommandChannel.DYNAMIC_MESSAGING);
    	runtime.addPubSubListener((topic, payload)-> {
    		channel1.setValue(LED1_PORT, 0);
			channel1.setValue(LED2_PORT, 0);
			channel1.setValue(LED3_PORT, 1);
			Grove_LCD_RGB.commandForTextAndColor(channel1, "GREEN",0, 255, 0);
			channel1.block(State.GREENLIGHT.getTime());
			
			channel1.openTopic("YELLOW").ifPresent(w->{w.publish();});
			return true;
    	}).addSubscription("GREEN");

    	final CommandChannel channel2 = runtime.newCommandChannel(GreenCommandChannel.DYNAMIC_MESSAGING);
    	runtime.addPubSubListener((topic, payload)-> {
    		channel2.setValue(LED1_PORT, 0);
			channel2.setValue(LED2_PORT, 1);
			channel2.setValue(LED3_PORT, 0);
			Grove_LCD_RGB.commandForTextAndColor(channel2,"YELLOW", 255, 255, 0);
			channel2.block(State.YELLOWLIGHT.getTime());
			
			channel2.openTopic("RED").ifPresent(w->{w.publish();});
			return true;
    	}).addSubscription("YELLOW");
    	
       final CommandChannel channel4 = runtime.newCommandChannel(GreenCommandChannel.DYNAMIC_MESSAGING);
       runtime.addStartupListener(()->{channel4.openTopic("RED").ifPresent(w->{w.publish();});});
	}

     
  
}
