package com.ociweb.device.config.grovepi;

import com.ociweb.device.config.GroveConnectionConfiguration;
import com.ociweb.device.grove.GroveConnect;
import com.ociweb.device.impl.grovepi.GrovePiConstants;
import com.ociweb.device.impl.grovepi.GrovePiGPIO;
import com.ociweb.device.impl.grovepi.GrovePiPinManager;

public class GrovePiConfiguration extends GroveConnectionConfiguration {

    private GroveConnect[] usedLines;
    
    public GrovePiConfiguration() {
        super();
    }
    
    public GrovePiConfiguration(boolean publishTime, boolean configI2C, GroveConnect[] encoderInputs,
            GroveConnect[] digitalInputs, GroveConnect[] digitalOutputs, GroveConnect[] pwmOutputs, GroveConnect[] analogInputs) {
        super(publishTime, configI2C, encoderInputs, digitalInputs, digitalOutputs, pwmOutputs, analogInputs);
    }

    public void coldSetup() {
        usedLines = buildUsedLines();
        GrovePiGPIO.ensureAllLinuxDevices(usedLines);
    }
    
    public void cleanup() {
        GrovePiGPIO.removeAllLinuxDevices(usedLines);
    }

    public void configurePinsForDigitalInput(byte connection) {
        GrovePiGPIO.configDigitalInput(connection); //readBit
    }

    public void configurePinsForAnalogInput(byte connection) {
        System.err.println("Analog pins are not supported.");
    }
    
    public void configurePinsForI2C() {
        GrovePiGPIO.configI2C();
    }
    
    public void i2cDataIn() {
        GrovePiGPIO.configI2CDataIn();
    }
    
    public void i2cDataOut() {
        GrovePiGPIO.configI2CDataOut();
    }
    
    public void i2cClockIn() {
        GrovePiGPIO.configI2CClockIn();
    }
    
    public void i2cClockOut() {
        GrovePiGPIO.configI2CClockOut();
    }

    public boolean i2cReadAck() {
        boolean ack;
        int voltage = readInt(GrovePiConstants.DATA_RAW_VOLTAGE);
        ack = voltage<GrovePiConstants.HIGH_LINE_VOLTAGE_MARK;
        if (!ack) {    
            System.err.println("ack value "+ack+" "+Integer.toBinaryString(voltage));
        }
        return ack;
    }

    @Override
    public boolean i2cReadClockBool() {
        return i2cReadClock() == 0; //TODO: 0 or 1 for HI?
    }

    public void beginPinConfiguration() {
        super.beginPinConfiguration();        
    }
    
    public void endPinConfiguration() {
        super.endPinConfiguration();
    }

    public int readBit(int connector) {   
        return GrovePiPinManager.readBit(connector);
    }
    
    //TODO: Since there's no ADC built into the Pi, we can only read HI or LO.
    public int readInt(int connector) {
        return GrovePiPinManager.readBit(connector);
    }
    
    public void writeBit(int connector, int value) {   
         GrovePiPinManager.writeBit(connector,value,GrovePiGPIO.gpioLinuxPins);
    }
    
    //TODO: Is it right to config them as outputs before writing?
    public void i2cSetClockLow() {
        GrovePiGPIO.configDigitalOutput(GrovePiPinManager.I2C_CLOCK);
        GrovePiPinManager.writeValue(GrovePiPinManager.I2C_CLOCK, GrovePiPinManager.I2C_LOW, GrovePiGPIO.gpioLinuxPins);
    }

    public void i2cSetClockHigh() {
        GrovePiGPIO.configDigitalOutput(GrovePiPinManager.I2C_CLOCK);
        GrovePiPinManager.writeValue(GrovePiPinManager.I2C_CLOCK, GrovePiPinManager.I2C_HIGH, GrovePiGPIO.gpioLinuxPins);
    }

    public void i2cSetDataLow() {
        GrovePiGPIO.configDigitalOutput(GrovePiPinManager.I2C_DATA);
        GrovePiPinManager.writeValue(GrovePiPinManager.I2C_DATA, GrovePiPinManager.I2C_LOW, GrovePiGPIO.gpioLinuxPins);
    }

    public void i2cSetDataHigh() {
        GrovePiGPIO.configDigitalOutput(GrovePiPinManager.I2C_DATA);
        GrovePiPinManager.writeValue(GrovePiPinManager.I2C_DATA, GrovePiPinManager.I2C_HIGH, GrovePiGPIO.gpioLinuxPins);
    }

    public int i2cReadData() {
        return readBit(GrovePiPinManager.I2C_DATA);
    }
    public boolean i2cReadDataBool() { return false; } //TODO:

    public int i2cReadClock() {
        return readBit(GrovePiPinManager.I2C_CLOCK);
    }
    
    static void findDup(GroveConnect[] base, int baseLimit, GroveConnect[] items, boolean mapAnalogs) {
        int i = items.length;
        while (--i>=0) {
            int j = baseLimit;
            while (--j>=0) {
                //TODO: Will probably have undesired side effects.
//                if (mapAnalogs ? base[j].connection == GrovePiConstants.ANALOG_CONNECTOR_TO_PIN[items[i].connection] :  base[j]==items[i]) {
                if (mapAnalogs ? false : base[j] == items[i]) {
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
            throw new UnsupportedOperationException("Rotary encoder requires two neighboring digital inputs.");
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
        
        if (configI2C) {
            findDup(result,pos,GrovePiConstants.i2cPins, false);
            System.arraycopy(GrovePiConstants.i2cPins, 0, result, pos, GrovePiConstants.i2cPins.length);
            pos+=GrovePiConstants.i2cPins.length;
        }
    
        return result;
    }
}