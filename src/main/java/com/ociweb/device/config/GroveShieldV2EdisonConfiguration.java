package com.ociweb.device.config;

import java.util.Arrays;

import com.ociweb.device.grove.GroveConnect;
import com.ociweb.device.impl.EdisonConstants;
import com.ociweb.device.impl.EdisonGPIO;
import com.ociweb.device.impl.EdisonPinManager;


public class GroveShieldV2EdisonConfiguration extends GroveConnectionConfiguration {

    private GroveConnect[] usedLines;
    
    public GroveShieldV2EdisonConfiguration() {
        super();
    }
    
    public GroveShieldV2EdisonConfiguration(boolean publishTime, boolean configI2C, GroveConnect[] encoderInputs,
            GroveConnect[] digitalInputs, GroveConnect[] digitalOutputs, GroveConnect[] pwmOutputs, GroveConnect[] analogInputs) {
        super(publishTime, configI2C, encoderInputs, digitalInputs, digitalOutputs, pwmOutputs, analogInputs);
    }

    public void coldSetup() {
        usedLines = buildUsedLines();
        System.out.println("CHECK HERE THIS IS USEDLINE");
        System.out.println(Arrays.toString(usedLines));
        EdisonGPIO.ensureAllLinuxDevices(usedLines);
    }
    
    public void cleanup() {
        EdisonGPIO.removeAllLinuxDevices(usedLines);
    }

    
    public void setToKnownStateFromColdStart() {
        EdisonGPIO.gpioOutputEnablePins.setDirectionHigh(10);
        EdisonGPIO.gpioOutputEnablePins.setValueHigh(10);
        EdisonGPIO.gpioOutputEnablePins.setDirectionHigh(11);
        EdisonGPIO.gpioOutputEnablePins.setValueHigh(11);
        EdisonGPIO.gpioOutputEnablePins.setDirectionHigh(12);
        EdisonGPIO.gpioOutputEnablePins.setValueHigh(12);
        EdisonGPIO.gpioOutputEnablePins.setDirectionHigh(13);
        EdisonGPIO.gpioOutputEnablePins.setValueHigh(13);
    }

    public void configurePinsForDigitalInput(byte connection) {
        EdisonGPIO.configDigitalInput(connection); //readBit
    }

    public void configurePinsForAnalogInput(byte connection) {
        EdisonGPIO.configAnalogInput(connection);  //readInt
    }
    
    public void configurePinsForDigitalOutput(byte connection){
    	EdisonGPIO.configDigitalOutput(connection); //writeBit
    }
    
    public void configurePinsForI2C() {
        EdisonGPIO.configI2C();
    }
    
    public void i2cDataIn() {
        EdisonGPIO.configI2CDataIn();
    }
    
    public void i2cDataOut() {
        EdisonGPIO.configI2CDataOut();
    }
    
    public void i2cClockIn() {
        EdisonGPIO.configI2CClockIn();
    }
    
    public void i2cClockOut() {
        EdisonGPIO.configI2CClockOut();
    }

    public boolean i2cReadAck() {
        return EdisonPinManager.readInt(EdisonConstants.DATA_RAW_VOLTAGE) < EdisonConstants.HIGH_LINE_VOLTAGE_MARK;

    }
    public boolean i2cReadDataBool() {
        return EdisonPinManager.readInt(EdisonConstants.DATA_RAW_VOLTAGE) > EdisonConstants.HIGH_LINE_VOLTAGE_MARK;
    }
    
    public boolean i2cReadClockBool() {
        return EdisonPinManager.readInt(EdisonConstants.CLOCK_RAW_VOLTAGE) > EdisonConstants.HIGH_LINE_VOLTAGE_MARK;
    }

    public void beginPinConfiguration() {
        super.beginPinConfiguration();        
        EdisonGPIO.shieldControl.setDirectionLow(0);
    }
    
    public void endPinConfiguration() {
        EdisonGPIO.shieldControl.setDirectionHigh(0);
        super.endPinConfiguration();
    }

    public int readBit(int connector) {        
        return EdisonPinManager.readBit(connector);
    }

    public int readInt(int connector) {
        return EdisonPinManager.readInt(connector);
    }
    
	public void writeBit(int connector, int value) {
	EdisonPinManager.writeBit(connector, value, EdisonGPIO.gpioLinuxPins);//maybe gpioOutputEnablePins or others (pick one)
	}
	
    public void i2cSetClockLow() {
        EdisonPinManager.writeValue(EdisonPinManager.I2C_CLOCK, EdisonPinManager.I2C_LOW, EdisonGPIO.gpioLinuxPins);
    }

    public void i2cSetClockHigh() {
        EdisonPinManager.writeValue(EdisonPinManager.I2C_CLOCK, EdisonPinManager.I2C_HIGH, EdisonGPIO.gpioLinuxPins);
    }

    public void i2cSetDataLow() {
        EdisonPinManager.writeValue(EdisonPinManager.I2C_DATA, EdisonPinManager.I2C_LOW, EdisonGPIO.gpioLinuxPins);
    }

    public void i2cSetDataHigh() {
        EdisonPinManager.writeValue(EdisonPinManager.I2C_DATA, EdisonPinManager.I2C_HIGH, EdisonGPIO.gpioLinuxPins);
    }

    public int i2cReadData() {
        return readBit(EdisonPinManager.I2C_DATA);
    }

    public int i2cReadClock() {
        return readBit(EdisonPinManager.I2C_CLOCK);
    }
    
    static void findDup(GroveConnect[] base, int baseLimit, GroveConnect[] items, boolean mapAnalogs) {
        int i = items.length;
        while (--i>=0) {
            int j = baseLimit;
            while (--j>=0) {
                if (mapAnalogs ? base[j].connection ==  EdisonConstants.ANALOG_CONNECTOR_TO_PIN[items[i].connection] :  base[j]==items[i]) {
                    throw new UnsupportedOperationException("Connector "+items[i]+" is assigned more than once.");
                }
            }
        }     
    }

    public GroveConnect[] buildUsedLines() {
        
        GroveConnect[] result = new GroveConnect[digitalInputs.length+
                                 encoderInputs.length+
                                 digitalOutputs.length+
                                 pwmOutputs.length+
                                 analogInputs.length+
                                 (configI2C?2:0)];
        
        int pos = 0;
        System.arraycopy(digitalInputs, 0, result, pos, digitalInputs.length);
        pos+=digitalInputs.length;
        
        if (0!=(encoderInputs.length&0x1)) {
            throw new UnsupportedOperationException("Rotery encoder requires two neighboring digital inputs.");
        }
        findDup(result,pos,encoderInputs, false);
        System.arraycopy(encoderInputs, 0, result, pos, encoderInputs.length);
        pos+=encoderInputs.length;
                
        findDup(result,pos,digitalOutputs, false);
        System.arraycopy(digitalOutputs, 0, result, pos, digitalOutputs.length);
        pos+=digitalOutputs.length;
        
        findDup(result,pos,pwmOutputs, false);
        System.arraycopy(pwmOutputs, 0, result, pos, pwmOutputs.length);
        pos+=pwmOutputs.length;        
        
        findDup(result,pos,analogInputs, true);
        int j = analogInputs.length;
        while (--j>=0) {
            result[pos++] = new GroveConnect(analogInputs[j].twig,EdisonConstants.ANALOG_CONNECTOR_TO_PIN[analogInputs[j].connection]);
        }
        
        if (configI2C) {
            findDup(result,pos,EdisonConstants.i2cPins, false);
            System.arraycopy(EdisonConstants.i2cPins, 0, result, pos, EdisonConstants.i2cPins.length);
            pos+=EdisonConstants.i2cPins.length;
        }
    
        return result;
    }


}