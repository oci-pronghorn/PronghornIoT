package com.ociweb.device.config;

import java.util.concurrent.ThreadLocalRandom;

import com.ociweb.device.grove.GroveConnect;
import com.ociweb.device.grove.schema.I2CBusSchema;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeWriter;

public class GroveShieldV2MockConfiguration extends GroveConnectionConfiguration {
    
    private final static int HIGH = 1;
    private final static int LOW  = 0;
    
    private final static int IN  = 1;
    private final static int OUT = 0;

    private Pipe<I2CBusSchema> pipe;    
    
    private int clockValue = HIGH; //must be pulled down to low
    private int dataValue = HIGH; //must be pulled down to low
    
    private int dataMode = IN;
    private int clockMode = IN;
        
    
    ThreadLocalRandom r = ThreadLocalRandom.current();
    public GroveShieldV2MockConfiguration(boolean publishTime, boolean configI2C, GroveConnect[] encoderInputs,
            GroveConnect[] digitalInputs, GroveConnect[] digitalOutputs, GroveConnect[] pwmOutputs,
            GroveConnect[] analogInputs) {
        super(publishTime, configI2C, encoderInputs, digitalInputs, digitalOutputs, pwmOutputs, analogInputs);        
    }    
    
    public void addOptionalI2CBusSimulationPipe(Pipe<I2CBusSchema> pipe) {
        this.pipe = pipe;
    }
    
    public void progressLog(int taskAtHand, int stepAtHand, int byteToSend) {
        if (null!=pipe) {
            
            while (! Pipe.isInit(pipe)) {
                //this pipe is used inside the startup of another stage TODO: Need a more elegant way to solve this corner case.
                Thread.yield();
            }
            
            if (PipeWriter.tryWriteFragment(pipe,I2CBusSchema.MSG_STATE_200)) {
                
                PipeWriter.writeInt(pipe, I2CBusSchema.MSG_STATE_200_FIELD_TASK_201, taskAtHand);
                PipeWriter.writeInt(pipe, I2CBusSchema.MSG_STATE_200_FIELD_STEP_202, stepAtHand);
                PipeWriter.writeInt(pipe, I2CBusSchema.MSG_STATE_200_FIELD_BYTE_202, byteToSend);                
                PipeWriter.writeLong(pipe, I2CBusSchema.MSG_STATE_200_FIELD_TIME_103, System.nanoTime());
                PipeWriter.publishWrites(pipe);
                
            } else {
                System.err.println("error pipe is not keeping up with feed ***** ");
                System.err.println("");
                System.exit(0);
            }
        }
        
    }

    private void sendDataPoint() {
        
        if (null!=pipe) {
            
            while (! Pipe.isInit(pipe)) {
                //this pipe is used inside the startup of another stage TODO: Need a more elegant way to solve this corner case.
                Thread.yield();
            }
            
            if (PipeWriter.tryWriteFragment(pipe,I2CBusSchema.MSG_POINT_100)) {
                
                PipeWriter.writeInt(pipe, I2CBusSchema.MSG_POINT_100_FIELD_CLOCK_101, clockValue);
                PipeWriter.writeInt(pipe, I2CBusSchema.MSG_POINT_100_FIELD_DATA_102, dataValue);
                PipeWriter.writeLong(pipe, I2CBusSchema.MSG_POINT_100_FIELD_TIME_103, System.nanoTime());
                PipeWriter.publishWrites(pipe);
                
            } else {
                System.err.println("error pipe is not keeping up with feed");
            }
        }
    }
    
     
    
    @Override
    public int readBit(int connector) {
       return r.nextInt(100) > 80 ? 1 : 0;
    }

    @Override
    public int readInt(int connector) {
        return Math.abs(r.nextInt());
    }

    @Override
    public void i2cSetClockLow() {
        if (clockMode != OUT) {
            throw new UnsupportedOperationException();
        }
        if (clockValue!=LOW) {
            clockValue = LOW;
    
            sendDataPoint();
        }
            
    }

    @Override
    public void i2cSetClockHigh() {
        if (clockMode != OUT) {
            throw new UnsupportedOperationException();
        }
        if (clockValue!=HIGH) {
            clockValue = HIGH;

            sendDataPoint();
        }
            
    }

    @Override
    public void i2cSetDataLow() {
        if (dataMode != OUT) {
            throw new UnsupportedOperationException();
        }
        if (dataValue!=LOW) {
            dataValue = LOW;

           sendDataPoint();
        }
           
    }

    @Override
    public void i2cSetDataHigh() {
        if (dataMode != OUT) {
            throw new UnsupportedOperationException();
        }
        
        if (dataValue!=HIGH) {
            dataValue = HIGH;

           sendDataPoint();
        }
           
    }
    
    @Override
    public int i2cReadData() {
        return dataValue;
    }

    @Override
    public int i2cReadClock() {
        return clockValue;
    }
    
    @Override
    public boolean i2cReadClockBool() {
        return clockValue!=0;
    }
    
    @Override
    public boolean i2cReadDataBool() {
        return dataValue!=0;
    }

    @Override
    public void i2cDataIn() {
        dataMode = IN;
    }

    @Override
    public void i2cDataOut() {
       dataMode = OUT;
    }

    @Override
    public void i2cClockIn() {
        clockMode = IN;
    }

    @Override
    public void i2cClockOut() {
        clockMode = OUT;
    }
    
    
    @Override
    public void configurePinsForDigitalInput(byte connection) {
        // TODO Auto-generated method stub

    }
    
    @Override
	public void configurePinsForDigitalOutput(byte connection) {
		// TODO Auto-generated method stub
		
	}


    @Override
    public void configurePinsForAnalogInput(byte connection) {
        // TODO Auto-generated method stub

    }

    @Override
    public void configurePinsForI2C() {
        // TODO Auto-generated method stub

    }

    
    @Override
    public void coldSetup() {
        // TODO Auto-generated method stub

    }

    @Override
    public void cleanup() {
        // TODO Auto-generated method stub

    }

    @Override
    public boolean i2cReadAck() {
        return true;
    }

	@Override
	public void writeBit(int connector, int value) {
		//TODO:System.out.println();
		//TODO:Log show the log to show the mock process 
		
	}

}
