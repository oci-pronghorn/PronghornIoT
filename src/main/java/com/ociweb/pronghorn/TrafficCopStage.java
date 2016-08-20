package com.ociweb.pronghorn;

import com.ociweb.pronghorn.iot.schema.TrafficAckSchema;
import com.ociweb.pronghorn.iot.schema.TrafficOrderSchema;
import com.ociweb.pronghorn.iot.schema.TrafficReleaseSchema;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeReader;
import com.ociweb.pronghorn.pipe.PipeWriter;
import com.ociweb.pronghorn.stage.PronghornStage;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;

//TODO: this class and its schemas will be moved to the general Pronghorn project, 
//TODO: once moved over to pronghorn add unit tests arround it.


/**
 * Supports a single primary input pipe that defines which  output pipes should be processed and in which order.
 * 
 * @author Nathan Tippy
 *
 */
public class TrafficCopStage extends PronghornStage {
    
    private Pipe<TrafficOrderSchema> primaryIn; 
    private Pipe<TrafficAckSchema>[] ackIn;
    private Pipe<TrafficReleaseSchema>[] goOut;
    private int ackExpectedOn = -1;   
    private GraphManager graphManager;
    private final long msAckTimeout;
    private long ackExpectedTime;
    
    private int goPendingOnPipe = -1;
    private int goPendingOnPipeCount = 0;
    
    
    public TrafficCopStage(GraphManager graphManager, long msAckTimeout, Pipe<TrafficOrderSchema> primaryIn, Pipe<TrafficAckSchema>[] ackIn,  Pipe<TrafficReleaseSchema>[] goOut) {
    	super(graphManager, join(ackIn, primaryIn), goOut);
    	
    	this.msAckTimeout = msAckTimeout;
        this.primaryIn = primaryIn;
        this.ackIn = ackIn;
        this.goOut = goOut;
        this.graphManager = graphManager;//for toString
        
        //force all commands to happen upon publish and release
        this.supportsBatchedPublish = false;
        this.supportsBatchedRelease = false;
    }    
    
    public String toString() {
        String result = super.toString();
        
        return result+ ( (ackExpectedOn>=0) ? " AckExpectedOn:"+ackExpectedOn+" "+GraphManager.getRingProducer(graphManager, +ackIn[ackExpectedOn].id) : "" );
    }
    
    
    @Override
    public void run() {
        do {
            ////////////////////////////////////////////////
            //check first if we are waiting for an ack back
            ////////////////////////////////////////////////
            if (ackExpectedOn>=0) {                              
                
                if (!PipeReader.tryReadFragment(ackIn[ackExpectedOn])) {
                    
                    if (System.currentTimeMillis() > ackExpectedTime) {
                        throw new RuntimeException("Expected to get ack back from "+GraphManager.getRingProducer(graphManager, +ackIn[ackExpectedOn].id)+" within "+msAckTimeout+"ms");
                    }
                    
                    return;//we are still waiting for requested operation to complete
                } else {
                    PipeReader.releaseReadLock(ackIn[ackExpectedOn]);
                    ackExpectedOn = -1;//clear value we are no longer waiting
                }
            }
            
            ////////////////////////////////////////////////////////
            //check second to roll up release messages for new stages from primaryIn
            ////////////////////////////////////////////////////////
            
            if (-1==goPendingOnPipe) {
            	if (!PipeReader.tryReadFragment(primaryIn)) {
            		return;//there is nothing todo
            	} else {             		
            		if (TrafficOrderSchema.MSG_GO_10 == PipeReader.getMsgIdx(primaryIn)) {
            			
            			goPendingOnPipe = ackExpectedOn = PipeReader.readInt(primaryIn, TrafficOrderSchema.MSG_GO_10_FIELD_PIPEIDX_11);
            			goPendingOnPipeCount = PipeReader.readInt(primaryIn, TrafficOrderSchema.MSG_GO_10_FIELD_COUNT_12);
            			//NOTE: since we might not send release right  away and we have released the input pipe the outgoing release
            			//      may be dropped upon clean shutdown.  This is OK since dropping work is what we want during a shutdown request.
            			PipeReader.releaseReadLock(primaryIn); 
            			ackExpectedTime = msAckTimeout>0 ? msAckTimeout+System.currentTimeMillis() : Long.MAX_VALUE; 
            			
            		} else {
            			//this may be shutting down or an unsupported message
            			assert(-1 == PipeReader.getMsgIdx(primaryIn)) : "Expected end of stream however got unsupported message: "+PipeReader.getMsgIdx(primaryIn);
            			requestShutdown();
            			PipeReader.releaseReadLock(primaryIn);  
            			return;//reached end of stream
            		}
            	}            	
            }
            assert(goPendingOnPipe!=-1);
            
            //TODO: fix this is stopping metronome in releaes code.
//            //check if following messages can be merged to the current release message, if its a release for the same pipe as the current active
//            while ((Pipe.peekInt(primaryIn, 2)==goPendingOnPipe) && PipeReader.tryReadFragment(primaryIn)) {
//            	assert(PipeReader.readInt(primaryIn, TrafficOrderSchema.MSG_GO_10_FIELD_PIPEIDX_11) == ackExpectedOn);
//            	goPendingOnPipeCount += PipeReader.readInt(primaryIn, TrafficOrderSchema.MSG_GO_10_FIELD_COUNT_12);
//            	PipeReader.releaseReadLock(primaryIn); 
//            }
            
            /////////////////////////////////////////////////////////
            //check third for room to send the pending go release message
            /////////////////////////////////////////////////////////            
        	Pipe<TrafficReleaseSchema> releasePipe = goOut[goPendingOnPipe];
            if (PipeWriter.tryWriteFragment(releasePipe, TrafficReleaseSchema.MSG_RELEASE_20)) { 
            	PipeWriter.writeInt(releasePipe, TrafficReleaseSchema.MSG_RELEASE_20_FIELD_COUNT_22, goPendingOnPipeCount);                	
            	PipeWriter.publishWrites(releasePipe);
            	goPendingOnPipe = -1;
            } else {
            	return;//try again later
            }            

        } while(true);
    }

}
