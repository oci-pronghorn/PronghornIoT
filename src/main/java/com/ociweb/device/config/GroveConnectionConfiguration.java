package com.ociweb.device.config;

import java.nio.IntBuffer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import com.ociweb.device.grove.GroveConnect;
import com.ociweb.device.grove.GroveShieldV2I2CStage;
import com.ociweb.device.grove.Twig;
import com.ociweb.device.impl.EdisonGPIO;
import com.ociweb.device.impl.EdisonPinManager;
import com.ociweb.device.testApps.GroveShieldTestApp;

public abstract class GroveConnectionConfiguration {
    
    private static final int PIN_SETUP_TIMEOUT = 3; //in seconds
    public static final int MAX_MOVING_AVERAGE_SUPPORTED = 101;
    
    private static final GroveConnect[] EMPTY = new GroveConnect[0];
    
    public boolean configI2C;       //Humidity, LCD need I2C address so..
    public GroveConnect[] encoderInputs; //Rotary Encoder
    
    public GroveConnect[] digitalInputs; //Button, Motion
    public GroveConnect[] digitalOutputs;//Relay Buzzer
    
    public GroveConnect[] analogInputs;  //Light, UV, Moisture
    public GroveConnect[] pwmOutputs;    //Servo   //(only 3, 5, 6, 9, 10, 11 when on edison)
    
    public boolean publishTime;
    public long lastTime;
    //TODO: ma per field with max defined here., 
    //TODO: publish with or with out ma??
    
    //only publish when the moving average changes
    
    private ReentrantLock lock = new ReentrantLock();
    
    public GroveConnectionConfiguration() {
        this(false,false,EMPTY,EMPTY,EMPTY,EMPTY,EMPTY);
    }
    
    public GroveConnectionConfiguration(boolean publishTime, boolean configI2C, GroveConnect[] encoderInputs,
            GroveConnect[] digitalInputs, GroveConnect[] digitalOutputs, GroveConnect[] pwmOutputs, GroveConnect[] analogInputs) {
        
        this.publishTime = publishTime;
        this.configI2C = configI2C;
        this.encoderInputs = encoderInputs; //TODO: rename as multi digital
        
        
        this.digitalInputs = digitalInputs;
        this.digitalOutputs = digitalOutputs;
        this.pwmOutputs = pwmOutputs;
        this.analogInputs = analogInputs;
    }

    /////
    /////
    
    GroveConnect[] growConnections(GroveConnect[] original, GroveConnect toAdd) {
        int l = original.length;
        GroveConnect[] result = new GroveConnect[l+1];
        System.arraycopy(original, 0, result, 0, l);
        result[l] = toAdd;
        return result;
    }
    
    public GroveConnectionConfiguration useConnectA(Twig t, int connection) {
        return useConnectA(t,connection,-1);
    }
    
    public GroveConnectionConfiguration useConnectA(Twig t, int connection, int customRate) {
        GroveConnect gc = new GroveConnect(t,connection);
        if (t.isInput()) {
            assert(!t.isOutput());
            analogInputs = growConnections(analogInputs, gc);
        } else {
            assert(t.isOutput());
            pwmOutputs = growConnections(pwmOutputs, gc);
        }
        return this;
    }
    
    public GroveConnectionConfiguration useConnectD(Twig t, int connection) {
        return useConnectD(t,connection,-1);
    }
    
    public GroveConnectionConfiguration useConnectD(Twig t, int connection, int customRate) {
        GroveConnect gc = new GroveConnect(t,connection);
        if (t.isInput()) {
            assert(!t.isOutput());
            digitalInputs = growConnections(digitalInputs, gc);
        } else {
            assert(t.isOutput());
            digitalOutputs = growConnections(digitalOutputs, gc);
        }
        return this;
    }  
    

    public GroveConnectionConfiguration useConnectDs(Twig t, int ... connections) {

        if (t.isInput()) {
            assert(!t.isOutput());
            for(int con:connections) {
                encoderInputs = growConnections(encoderInputs, new GroveConnect(t,con));
            }
        } else {
            throw new UnsupportedOperationException("TODO: finish this implementation");
//            assert(t.isOutput());
//            for(int con:connections) {
//                encoderOutputs = growConnections(encoderOutputs, new GroveConnect(t,con));
//            }
        }
        return this;
        
    }  
    
    public GroveConnectionConfiguration useTime(int rate) {
        this.publishTime = true; //TODO: add support for time rate.
        return this;
    }
    
    public GroveConnectionConfiguration useI2C() {
        this.publishTime = true; //TODO: add support for time rate.
        return this;
    }
    
    /////
    /////
    
    public void setToKnownStateFromColdStart() {
        //Not always needed
    }

    public void beginPinConfiguration() {
        try {
            if (!lock.tryLock(PIN_SETUP_TIMEOUT, TimeUnit.SECONDS)) {
                throw new RuntimeException("One of the stages was not able to complete startup due to pin configuration issues.");
            }
        } catch (InterruptedException e) {
           Thread.currentThread().interrupt();
        }
    }
    
    public void endPinConfiguration() {
       lock.unlock();
    }
    
    public abstract int readBit(int connector); //Platform specific
    public abstract int readInt(int connector); //Platform specific
    
    
    public abstract void writeBit(int connector, int value);             //Platform specific
    public abstract void configurePinsForDigitalInput(byte connection); //Platform specific
    public abstract void configurePinsForAnalogInput(byte connection); //Platform specific
    public abstract void configurePinsForI2C();
    
    public int maxAnalogMovingAverage() {
        return MAX_MOVING_AVERAGE_SUPPORTED;
    }

    public abstract void i2cSetClockLow();
    public abstract void i2cSetClockHigh();
    public abstract void i2cSetDataLow();
    public abstract void i2cSetDataHigh();
    public abstract int i2cReadData();
    public abstract int i2cReadClock();
    
    public abstract void i2cDataIn();
    public abstract void i2cDataOut();
    public abstract void i2cClockIn();
    public abstract void i2cClockOut();
    public abstract boolean i2cReadAck();
    public abstract boolean i2cReadClockBool();
    public abstract boolean i2cReadDataBool();
    
    public abstract void coldSetup();
    public abstract void cleanup();

    static final boolean debug = false;
    public void progressLog(int taskAtHand, int stepAtHand, int byteToSend) {
        
        if (debug) {
        
            long now = System.nanoTime();
    
            long duration = now-lastTime;
            if (duration<GroveShieldV2I2CStage.NS_PAUSE) {
                System.err.println("calling I2C too fast");
            }
            if (duration >= 35_000_000) {
                System.err.println("calling I2C too slow "+duration+" devices may have now timed out. next is "+taskAtHand+":"+stepAtHand);
            } else if (duration> 1_000_000 /*20_000_000*/) {
                System.err.println("warning calling I2C too slow "+duration+". next is "+taskAtHand+":"+stepAtHand);
            }
            lastTime = System.nanoTime();//use new time because we must avoid recording our log message in the duration time.
        }
    }

	



    
}