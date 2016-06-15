package com.ociweb.device.impl.graph;

import com.ociweb.device.grove.schema.GroveResponseSchema;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeReader;
import com.ociweb.pronghorn.stage.PronghornStage;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;

public class ReactiveListenerStage extends PronghornStage {

    private final Object listener;
    private Pipe<GroveResponseSchema> groveResponsePipe;
    private Pipe<?>                   restResponsePipes;
    
    
    protected ReactiveListenerStage(GraphManager graphManager, Object listener, Pipe<GroveResponseSchema> groveResponsePipes) {
        super(graphManager, groveResponsePipes, NONE);
        this.listener = listener;
        this.groveResponsePipe = groveResponsePipes;    
    }
    
    protected ReactiveListenerStage(GraphManager graphManager, Object listener, Pipe<GroveResponseSchema> groveResponsePipes, Pipe restResponsePipes) {
        super(graphManager, new Pipe[]{groveResponsePipes, restResponsePipes}, NONE);
        this.listener = listener;
        this.groveResponsePipe = groveResponsePipes;    
        assert(null!=restResponsePipes);
        this.restResponsePipes = restResponsePipes;
        assert(listener instanceof RestListener);
        
    }

    @Override
    public void run() {
        
        //TODO: replace with linked list of processors?, NOTE each one also needs a length bound so it does not starve the rest.
        consumeResponseMessage(listener, groveResponsePipe);
        consumeRestMessage(listener, restResponsePipes);
        
        
        //if additional array sources are added then processors will go here for those pipe arrays
         
        
    }

    private void consumeRestMessage(Object listener2, Pipe<?> p) {
        if (null!= p) {
            
            while (PipeReader.tryReadFragment(p)) {                
                
                int msgIdx = PipeReader.getMsgIdx(p);
                
                //no need to check instance of since this was registered and we have a pipe
                ((RestListener)listener).restRequest(1, null, null);
                
                //done reading message off pipe
                PipeReader.releaseReadLock(p);
            }
            
        }
    }

    private void consumeResponseMessage(Object listener, Pipe<GroveResponseSchema> p) {
        while (PipeReader.tryReadFragment(p)) {                
            
            int msgIdx = PipeReader.getMsgIdx(p);
            switch (msgIdx) {   //Just 4 methods??  TODO: must remove specifc field times and use the general types here as well.
                case GroveResponseSchema.MSG_TIME_10:                         
                    if (listener instanceof TimeListener) {                 
                    
                        long time = PipeReader.readLong(p, GroveResponseSchema.MSG_TIME_10_FIELD_VALUE_11);
                        //TODO: for multiple clock rates we need to add a second identifier for which one this event belongs to.

                        ((TimeListener)listener).timeEvent(time);  
                    
                    }   
                break;
                case GroveResponseSchema.MSG_ANALOGSAMPLE_30:
                    if (listener instanceof AnalogListener) {
                        
                        int average = PipeReader.readInt(p, GroveResponseSchema.MSG_ANALOGSAMPLE_30_FIELD_AVERAGE_33);
                        int connector = PipeReader.readInt(p, GroveResponseSchema.MSG_ANALOGSAMPLE_30_FIELD_CONNECTOR_31);
                        int value = PipeReader.readInt(p, GroveResponseSchema.MSG_ANALOGSAMPLE_30_FIELD_VALUE_32);
                        
                        ((AnalogListener)listener).analogEvent(connector, average, value);
                        
                    }   
                break;               
                case GroveResponseSchema.MSG_DIGITALSAMPLE_20:
                    if (listener instanceof DigitalListener) {
                        int connector = PipeReader.readInt(p, GroveResponseSchema.MSG_DIGITALSAMPLE_20_FIELD_CONNECTOR_21);
                        int value = PipeReader.readInt(p, GroveResponseSchema.MSG_DIGITALSAMPLE_20_FIELD_VALUE_22);
                            
                        ((DigitalListener)listener).digitalEvent(connector, value);
                        
                    }   
                break; 
                case GroveResponseSchema.MSG_ENCODER_70:
                    if (listener instanceof RotaryListener) {    
                        int connector = PipeReader.readInt(p, GroveResponseSchema.MSG_ENCODER_70_FIELD_CONNECTOR_71);
                        int value = PipeReader.readInt(p, GroveResponseSchema.MSG_ENCODER_70_FIELD_VALUE_72);
                        int delta = PipeReader.readInt(p, GroveResponseSchema.MSG_ENCODER_70_FIELD_DELTA_73);
                        int speed = PipeReader.readInt(p, GroveResponseSchema.MSG_ENCODER_70_FIELD_SPEED_74);
                        
                        ((RotaryListener)listener).rotaryEvent(connector, value, delta, speed);
                                            
                    }   
                break;
                case -1:
                {    
                    requestShutdown();
                    PipeReader.releaseReadLock(p);
                    return;
                }   
                default:
                    throw new UnsupportedOperationException("Unknown id: "+msgIdx);
            }               
            
            //done reading message off pipe
            PipeReader.releaseReadLock(p);
        }
    }
    
    
    
    
}
