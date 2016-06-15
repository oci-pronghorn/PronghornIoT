package com.ociweb.device.grove;

import com.ociweb.device.grove.schema.GroveResponseSchema;
import com.ociweb.pronghorn.pipe.Pipe;

public enum GroveTwig implements Twig {

    UVSensor(){
        @Override
        public void writeInt(Pipe<GroveResponseSchema> responsePipe, int connector, int intValue, int average) {
            int size = Pipe.addMsgIdx(responsePipe, GroveResponseSchema.MSG_ANALOGSAMPLE_30);
            Pipe.addIntValue(connector, responsePipe);
            Pipe.addIntValue(intValue, responsePipe);
            Pipe.addIntValue(average, responsePipe);
            Pipe.publishWrites(responsePipe);
            Pipe.confirmLowLevelWrite(responsePipe, size);
        }    
        
        @Override
        public boolean isInput() {
            return true;
        }
    },
    LightSensor(){
        @Override
        public void writeInt(Pipe<GroveResponseSchema> responsePipe, int connector, int intValue, int average) {
            int size = Pipe.addMsgIdx(responsePipe, GroveResponseSchema.MSG_ANALOGSAMPLE_30);
            Pipe.addIntValue(connector, responsePipe);
            Pipe.addIntValue(intValue, responsePipe);
            Pipe.addIntValue(average, responsePipe);
            Pipe.publishWrites(responsePipe);
            Pipe.confirmLowLevelWrite(responsePipe, size);
        }   
        
        @Override
        public boolean isInput() {
            return true;
        }
    },
    MoistureSensor(){
        @Override
        public void writeInt(Pipe<GroveResponseSchema> responsePipe, int connector, int intValue, int average) {
            int size = Pipe.addMsgIdx(responsePipe, GroveResponseSchema.MSG_ANALOGSAMPLE_30);
            Pipe.addIntValue(connector, responsePipe);
            Pipe.addIntValue(intValue, responsePipe);
            Pipe.addIntValue(average, responsePipe);
            Pipe.publishWrites(responsePipe);
            Pipe.confirmLowLevelWrite(responsePipe, size);
        }       
        
        @Override
        public boolean isInput() {
            return true;
        }
    },
    Button() {
        @Override
        public void writeBit(Pipe<GroveResponseSchema> responsePipe, int connector, int bitValue) {
            int size = Pipe.addMsgIdx(responsePipe, GroveResponseSchema.MSG_DIGITALSAMPLE_20);
            Pipe.addIntValue(connector, responsePipe);
            Pipe.addIntValue(bitValue, responsePipe);
            Pipe.publishWrites(responsePipe);
            Pipe.confirmLowLevelWrite(responsePipe, size);
        }
        
        @Override
        public boolean isInput() {
            return true;
        }
        
    },
    MotionSensor(){
        @Override
        public void writeBit(Pipe<GroveResponseSchema> responsePipe, int connector, int bitValue) {
            int size = Pipe.addMsgIdx(responsePipe,GroveResponseSchema.MSG_DIGITALSAMPLE_20);
            Pipe.addIntValue(connector, responsePipe);
            Pipe.addIntValue(bitValue, responsePipe);
            Pipe.publishWrites(responsePipe);
            Pipe.confirmLowLevelWrite(responsePipe, size);
        }       
        
        @Override
        public boolean isInput() {
            return true;
        }
    },
    RotaryEncoder() {
        @Override
        public void writeRotation(Pipe<GroveResponseSchema> responsePipe, int connector, int value, int delta, int speed) {            
            int size = Pipe.addMsgIdx(responsePipe, GroveResponseSchema.MSG_ENCODER_70);
            Pipe.addIntValue(connector, responsePipe);
            Pipe.addIntValue(value, responsePipe);
            Pipe.addIntValue(delta, responsePipe);            
            Pipe.addIntValue(speed, responsePipe);
            Pipe.publishWrites(responsePipe);
            Pipe.confirmLowLevelWrite(responsePipe, size);
            
        }
        
        @Override
        public boolean isInput() {
            return true;
        }
        
    },
    Buzzer() {
        
        @Override
        public boolean isOutput() {
            return true;
        }
    },
    Relay() {
        
        @Override
        public boolean isOutput() {
            return true;
        }
    },
    Survo() {
        
        @Override
        public boolean isOutput() {
            return true;
        }
    },
    I2C() {
        
        @Override
        public boolean isInput() {
            return true;
        }
        
        @Override
        public boolean isOutput() {
            return true;
        }
    };
    
    

    public void writeBit(Pipe<GroveResponseSchema> responsePipe, int connector, int bitValue) {
       System.err.println(this);
       throw new UnsupportedOperationException();
    }

    public void writeInt(Pipe<GroveResponseSchema> responsePipe, int connector, int intValue, int average) {
        System.err.println(this);
        throw new UnsupportedOperationException();
    }

    public void writeRotation(Pipe<GroveResponseSchema> responsePipe, int connector, int value, int delta, int speed) {
        System.err.println(this);
        throw new UnsupportedOperationException();
    }
    
    public boolean isInput() {
        return false;
    }
    
    public boolean isOutput() {
        return false;
    }
}
