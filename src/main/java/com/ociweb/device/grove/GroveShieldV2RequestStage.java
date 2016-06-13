package com.ociweb.device.grove;

import com.ociweb.device.config.GroveConnectionConfiguration;
import com.ociweb.device.grove.schema.GroveRequestSchema;
import com.ociweb.device.impl.EdisonPinManager;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.stage.PronghornStage;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;

//TODO: should be Grove specific NOT edision specific
public class GroveShieldV2RequestStage extends PronghornStage {

    private final Pipe<GroveRequestSchema>[] requestPipes;
    private final GroveConnectionConfiguration config;
    
    public GroveShieldV2RequestStage(GraphManager gm, Pipe<GroveRequestSchema> requestPipe, GroveConnectionConfiguration config) {
        super(gm, requestPipe, NONE);
        
        this.requestPipes = new Pipe[]{requestPipe};
        this.config = config;
        
    }
    
    public GroveShieldV2RequestStage(GraphManager gm, Pipe<GroveRequestSchema>[] requestPipes, GroveConnectionConfiguration config) {
        super(gm, requestPipes, NONE);
        
        this.requestPipes = requestPipes;
        this.config = config;
        
    }
        
    
    @Override
    public void startup() {
    //  i = config.pwmOutputs.length;
    //  while (--i>=0) {
//          configPWM(config.pwmOutputs[i]); //take from pipe and write, get type and field from pipe
//          
//          script[reverseBits(sliceCount++)] = ((MASK_DO_PORT&config.pwmOutputs[i])<<SHIFT_DO_PORT) |
//                                              ((MASK_DO_JOB&DO_DATA_WRITE)<<SHIFT_DO_JOB );
    //  }
    }
    
    
    @Override
    public void run() {
      
        int j = requestPipes.length;
        while (--j>=0) {
            processPipe(requestPipes[j]);
            
        }
        
    }

    private void processPipe(Pipe<GroveRequestSchema> requestPipe) {
        while (Pipe.hasContentToRead(requestPipe)) {
            
            //read the messages.
            int msg = Pipe.takeMsgIdx(requestPipe);
            
            switch (msg) {
                case GroveRequestSchema.MSG_DIGITALSET_110:
                {
                    int connector = Pipe.takeValue(requestPipe);
                    int value = Pipe.takeValue(requestPipe);     
                    
                    
                    config.writeBit(connector,value);
                    
       
                }   
                break;
                case GroveRequestSchema.MSG_DIGITALSET_120:
                {
                    int connector = Pipe.takeValue(requestPipe);
                    int duration = Pipe.takeValue(requestPipe); 
                    
                    //TODO write something to device
                    
                }   
                break;
                case GroveRequestSchema.MSG_ANALOGSET_140:
                { 
                    int connector = Pipe.takeValue(requestPipe);
                    int position = Pipe.takeValue(requestPipe); 
            
                    //TODO write something to device
                    
                }   
                break;    
                //shutodown? needs all pipes to agree???
                
                        
            }
            
            Pipe.confirmLowLevelRead(requestPipe, Pipe.sizeOf(requestPipe, msg));
            Pipe.releaseReadLock(requestPipe);
            
            
        }
    }

    @Override
    public void shutdown() {
        
        
    }
    
    


    
    
}
