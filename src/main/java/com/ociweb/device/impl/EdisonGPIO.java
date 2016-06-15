package com.ociweb.device.impl;

import com.ociweb.device.grove.GroveConnect;

public class EdisonGPIO {

    public static final EdisonPinManager gpioPinMux = new EdisonPinManager(EdisonConstants.GPIO_PIN_MUX);
    public static final EdisonPinManager gpioPinMuxExt = new EdisonPinManager(EdisonConstants.GPIO_PIN_MUX_EXT);
    public static final EdisonPinManager gpioPinModes = new EdisonPinManager(EdisonConstants.GPIO_PIN_MODES);
    public static final EdisonPinManager gpioLinuxPins = new EdisonPinManager(EdisonConstants.GPIO_PINS);
    public static final EdisonPinManager gpioOutputEnablePins = new EdisonPinManager(EdisonConstants.OUTPUT_ENABLE);
    public static final EdisonPinManager gpioPullupEnablePins = new EdisonPinManager(EdisonConstants.PULL_UP_ENABLE);
    public static final EdisonPinManager shieldControl = new EdisonPinManager(EdisonConstants.SHIELD_CONTROL);
    
    public static void ensureAllLinuxDevices(GroveConnect[] usedLines) {
        
        shieldControl.ensureDevice(0); //tri statebyte
        //create the edision later write mode 
        shieldControl.ensureDevice(1); //shield reset
    // shell r
        int j = usedLines.length;
        while (--j>=0) {                
            int i = usedLines[j].connection;
            gpioLinuxPins.ensureDevice(i);
            gpioOutputEnablePins.ensureDevice(i);
            gpioPullupEnablePins.ensureDevice(i);   
            gpioPinMux.ensureDevice(i);
            gpioPinMuxExt.ensureDevice(i);
            gpioPinModes.ensureDevice(i);
        }
        
        //Required for pre-setup of analog pins
        gpioOutputEnablePins.ensureDevice(10);
        gpioOutputEnablePins.ensureDevice(11);
        gpioOutputEnablePins.ensureDevice(12);
        gpioOutputEnablePins.ensureDevice(13);
        
    }
    public static void removeAllLinuxDevices(GroveConnect[] usedLines) {
        shieldControl.removeDevice(0); //tri state
        shieldControl.removeDevice(1); //shield reset
    
        //NOTE: this is overkill to create every single device we may possibly need
        //      TODO: use some flats to reduce this set to only the ones we are using
        
        int j = usedLines.length;
        while (--j>=0) {                
            int i = usedLines[j].connection;       
            gpioLinuxPins.removeDevice(i);
            gpioOutputEnablePins.removeDevice(i);
            gpioPullupEnablePins.removeDevice(i); 
            gpioPinMux.removeDevice(i);
            gpioPinMuxExt.removeDevice(i);
            gpioPinModes.removeDevice(i);
        }
        
      //Required for pre-setup of analog pins
      gpioOutputEnablePins.removeDevice(10);
      gpioOutputEnablePins.removeDevice(11);
      gpioOutputEnablePins.removeDevice(12);
      gpioOutputEnablePins.removeDevice(13);
        
    }
    public static void configDigitalInput(int dPort) {       
        gpioOutputEnablePins.setDirectionLow(dPort);
    
        //no need to map since ports happen to match the digital pins
        gpioPullupEnablePins.setDirectionHigh(dPort);
        gpioLinuxPins.setDirectionIn(dPort);      
    }
    public static void configAnalogInput(int aPort) {
        if (aPort<0 || aPort>5) {
            throw new UnsupportedOperationException("only available on 0, 1, 2, or 3 and only 4 or 5 if I2C is not in use.");
        }
                
        gpioPinMux.setDirectionHigh(EdisonConstants.ANALOG_CONNECTOR_TO_PIN[aPort]);        
        gpioOutputEnablePins.setDirectionLow(EdisonConstants.ANALOG_CONNECTOR_TO_PIN[aPort]);
        gpioPullupEnablePins.setDirectionIn(EdisonConstants.ANALOG_CONNECTOR_TO_PIN[aPort]);  //in      
        
    }
    public static void configPWM(int dPort) {
        if (dPort<3 || 4==dPort || 7==dPort || 8==dPort || dPort>11) {
            //(only 3, 5, 6, 9, 10, 11)
            throw new UnsupportedOperationException("PWM only available on 3, 5, 6, 9, 10 or 11");
        }
        
        gpioOutputEnablePins.setDirectionHigh(dPort);
        gpioPullupEnablePins.setDirectionIn(dPort);
        gpioPinModes.setDebugCurrentPinmuxMode1(dPort);
        gpioPinMuxExt.setDirectionLow(dPort);
        
    }
    public static void configDigitalOutput(int dPort) {       
        gpioPinModes.setDebugCurrentPinmuxMode0(dPort);        
        //no need to map since ports happen to match the digital pins
        gpioPullupEnablePins.setDirectionHigh(dPort); 
        gpioOutputEnablePins.setDirectionHigh(dPort);
        gpioLinuxPins.setDirectionOut(dPort);     
        
        
    }
    //is only supported at 13, Note this disables D10 - D13 
    //Grove does not have any sensors using this at the moment
    public static void configSPI() {
        gpioPinMuxExt.setDirectionHigh(10);
        gpioPinMuxExt.setDirectionHigh(11);
        
        gpioPinMux.setDirectionHigh(10);
        gpioPinMux.setDirectionHigh(11);
        gpioPinMux.setDirectionHigh(12);
        gpioPinMux.setDirectionHigh(13);
        
        gpioOutputEnablePins.setDirectionHigh(10);
        gpioOutputEnablePins.setDirectionHigh(11);
        gpioOutputEnablePins.setDirectionLow(12);
        gpioOutputEnablePins.setDirectionHigh(13);
        
        gpioPullupEnablePins.setDirectionIn(10);
        gpioPullupEnablePins.setDirectionIn(11);
        gpioPullupEnablePins.setDirectionIn(12);
        gpioPullupEnablePins.setDirectionIn(13);
        
        gpioPinModes.setDebugCurrentPinmuxMode1(10);
        gpioPinModes.setDebugCurrentPinmuxMode1(11);
        gpioPinModes.setDebugCurrentPinmuxMode1(12);
        gpioPinModes.setDebugCurrentPinmuxMode1(13);        
        
    }

    public static void configI2CClockOut() {
        shieldControl.setDirectionLow(0); 
    
        //need to read the ack from the data line sent by the slave
        gpioLinuxPins.setDirectionOut(19); //Must be set to allow for read/write
        gpioOutputEnablePins.setDirectionHigh(19); //Must be set to allow values to sick
        
        shieldControl.setDirectionHigh(0);
    }
    public static void configI2CClockIn() {
        shieldControl.setDirectionLow(0); 
    
        //need to read the ack from the data line sent by the slave
       gpioLinuxPins.setDirectionIn(19); //in
       gpioOutputEnablePins.setDirectionLow(19);
       
        shieldControl.setDirectionHigh(0);
    }
    

    public static void configI2CDataOut() {
        shieldControl.setDirectionLow(0); 
    
        //need to read the ack from the data line sent by the slave
        gpioLinuxPins.setDirectionOut(18); //Must be set to allow for read/write        
        gpioOutputEnablePins.setDirectionHigh(18); //Must be set to allow values to sick
       
        shieldControl.setDirectionHigh(0);
    }
    public static void configI2CDataIn() {
        shieldControl.setDirectionLow(0); 
    
        //need to read the ack from the data line sent by the slave
        gpioLinuxPins.setDirectionIn(18); //in        
        gpioOutputEnablePins.setDirectionLow(18); //low
        
        shieldControl.setDirectionHigh(0);
    }
    
    
    
    
    //is only supported at 18/19,  Note this disables use of A4 and A5
    public static void configI2C() {
    
        gpioPinMux.setDirectionLow(18);
        gpioPinMux.setDirectionLow(19);
        
  //      gpioLinuxPins.setDirectionIn(18); //in
  //      gpioLinuxPins.setDirectionIn(19);  //in
        
   //     gpioOutputEnablePins.setDirectionLow(18); //low
    //    gpioOutputEnablePins.setDirectionLow(19); //low
        
        gpioPullupEnablePins.setDirectionIn(18); 
        gpioPullupEnablePins.setDirectionIn(19);
//        gpioPullupEnablePins.setDirectionOut(18); 
  //      gpioPullupEnablePins.setDirectionOut(19);
        
        
        gpioPinModes.setDebugCurrentPinmuxMode1(19);
        gpioPinModes.setDebugCurrentPinmuxMode1(18);
        
        gpioLinuxPins.setDirectionOut(19); //Must be set to allow for read/write
        gpioLinuxPins.setDirectionOut(18); //Must be set to allow for read/write
        
        gpioOutputEnablePins.setDirectionHigh(19); //Must be set to allow values to sick//enable us to be master of the bus not just an observer
        gpioOutputEnablePins.setDirectionHigh(18); //Must be set to allow values to sick
        //NOTE: for Arduino breakout board and Edison only i2c-6  is supported 
        
      //  Path i2cDevice = Paths.get("/sys/class/i2c-dev/i2c-6");
        
        System.out.println("I2C enabled for out");
               
    }
    
    /**
     * Warning every time this is called both clock and data lines will be set to zero
     */
    private static void configI2COut() {
        //    shieldControl.setDirectionLow(0);  //18 data  19 clock
            
            gpioLinuxPins.setDirectionOut(18); //Must be set to allow for read/write
            gpioOutputEnablePins.setDirectionHigh(18); //Must be set to allow values to sick
            
            gpioLinuxPins.setDirectionOut(19); //Must be set to allow for read/write
            gpioOutputEnablePins.setDirectionHigh(19); //Must be set to allow values to sick
            
  
    }
    
    public static void configUART(int dPort) {
          if (dPort<0 || 3==dPort || dPort>4) {
              //only 0, 1, 2 and 4
              throw new UnsupportedOperationException("UART only available on 0, 1, 2 or 4");
          }
            
          //TODO: UART, there are very few Grove sensors using this. To be implemented later  
          throw new UnsupportedOperationException();
          
      }

}
