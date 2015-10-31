package com.ociweb.device.config;

import com.ociweb.device.grove.GroveConnect;
import com.ociweb.device.impl.EdisonConstants;
import com.ociweb.device.impl.EdisonGPIO;
import com.ociweb.device.impl.EdisonPinManager;


public class GroveShieldV2EdisonConfiguration extends GroveConnectionConfiguration {

//TODO: //ma per field with max, 
    //TODO: publish with or with out ma.
    
    
    public GroveShieldV2EdisonConfiguration(boolean publishTime, boolean configI2C, GroveConnect[] encoderInputs,
            GroveConnect[] digitalInputs, GroveConnect[] digitalOutputs, GroveConnect[] pwmOutputs, GroveConnect[] analogInputs) {
        super(publishTime, configI2C, encoderInputs, digitalInputs, digitalOutputs, pwmOutputs, analogInputs);
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


    public void beginPinConfiguration() {
        super.beginPinConfiguration();        
        EdisonGPIO.shieldControl.setDirectionLow(0);
    }
    
    public void endPinConfiguration() {
        EdisonGPIO.shieldControl.setDirectionHigh(0);
        super.endPinConfiguration();
    }

    public int readBit(int connector) {
        return EdisonPinManager.readBit(connector, EdisonGPIO.gpioLinuxPins);
    }

    public int readInt(int connector) {
        return EdisonPinManager.readInt(connector, EdisonGPIO.gpioLinuxPins);
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