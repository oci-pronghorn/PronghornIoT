package com.ociweb.device.impl.graph;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.ociweb.device.config.GroveConnectionConfiguration;
import com.ociweb.device.config.GroveShieldV2EdisonConfiguration;
import com.ociweb.device.config.grovepi.GrovePiConfiguration;
import com.ociweb.device.grove.GroveShieldV2RequestStage;
import com.ociweb.device.grove.GroveShieldV2ResponseStage;
import com.ociweb.device.grove.schema.GroveRequestSchema;
import com.ociweb.device.grove.schema.GroveResponseSchema;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeConfig;
import com.ociweb.pronghorn.stage.route.SplitterStage;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;
import com.ociweb.pronghorn.stage.scheduling.ThreadPerStageScheduler;

public class IOTDeviceRuntime {

    /*
     * Caution: in order to make good use of ProGuard we need to make an effort to avoid using static so 
     * dependencies can be traced and kept in the jar.
     *  
     */
    
    protected static GroveConnectionConfiguration config;
    
    private GraphManager gm = new GraphManager();
    private List<Pipe<GroveRequestSchema>> collectedRequestPipes = new ArrayList<Pipe<GroveRequestSchema>>();
    private List<Pipe<GroveResponseSchema>> collectedResponsePipes = new ArrayList<Pipe<GroveResponseSchema>>();
    
    
    private PipeConfig<GroveRequestSchema> requestPipeConfig = new PipeConfig<GroveRequestSchema>(GroveRequestSchema.instance, 100);
    private PipeConfig<GroveResponseSchema> responsePipeConfig = new PipeConfig<GroveResponseSchema>(GroveResponseSchema.instance, 100);
    
    
    protected IOTDeviceRuntime() {
        
        
    }

    
    protected static GroveConnectionConfiguration getHarwareConfig() {
        if (null==config) {
            
            //TODO: use the name of the JVM in the system properties to determine which one we are on
            System.out.println("PLEASE REVIEW AND FIND A WAY TO CHOOSE:\n");                                                                                                        
            System.out.println(System.getProperty("os.version"));
            String osversion  =System.getProperty("os.version");
            String version = "edison";
            boolean isEdison = false;
            boolean isPi     = true;
            if ( osversion.toLowerCase().indexOf(version.toLowerCase()) != -1 ) {
             isEdison = true;
             isPi     = false;
            }
            
            if (isEdison) {
                config = new GroveShieldV2EdisonConfiguration();
            }
            
            if (isPi) {
                config = new GrovePiConfiguration();
            }
            
        }
        
        return config;
    }
    
    
    public RequestAdapter requestAdapterInstance() {
               
        Pipe<GroveRequestSchema> pipe = new Pipe<GroveRequestSchema>(requestPipeConfig );
        collectedRequestPipes.add(pipe);
        return new RequestAdapter(pipe);//TODO: do I need to make this an unschedlued stage? or can we make graph support this??
        
    }
    

    
    public void addRESTSignature(int i, String string) {

        // TODO accumulate all thse rest processor, when start is called then configure the server to take them all. 
        
    }
    
    protected void registerListener(AnalogListener listener) {
       
        Pipe<GroveResponseSchema> pipe = new Pipe<GroveResponseSchema>(responsePipeConfig.grow2x());
        collectedResponsePipes.add(pipe);
        
        ReactiveListenerStage stage = new ReactiveListenerStage(gm, listener, pipe);

    }
    
    protected void registerListener(Object listener) {
        
        Pipe<GroveResponseSchema> pipe = new Pipe<GroveResponseSchema>(responsePipeConfig.grow2x());
        collectedResponsePipes.add(pipe);
        
        if (listener instanceof RestListener) {
            
            Pipe restPipe = null; //this holds rest requests detected from the webserver, these can come directly from router.
            
            //create new pipe
            
            //add pipe to collection for starting the server ??
            
            
            ReactiveListenerStage stage = new ReactiveListenerStage(gm, listener, pipe, restPipe);
            
        } else {
        
        
            ReactiveListenerStage stage = new ReactiveListenerStage(gm, listener, pipe);
        }
    }

    protected void start() {
       buildGraph(); 
        
        //NOTE: need to consider different schedulers in the future.
       ThreadPerStageScheduler scheduler = new ThreadPerStageScheduler(gm);
       scheduler.startup();
       
       Runtime.getRuntime().addShutdownHook(new Thread() {
           public void run() {
             scheduler.shutdown();
             scheduler.awaitTermination(60, TimeUnit.MINUTES);
           }
       });
       
       
    }

    private void buildGraph() {

        //all the request pipes are passed into this single stage for modification of the hardware
        int s = collectedRequestPipes.size();        
        new GroveShieldV2RequestStage(gm, collectedRequestPipes.toArray(new Pipe[s]), config);
        
        //all the registered listers are managed here.
        s = collectedResponsePipes.size();           
        Pipe<GroveResponseSchema> responsePipe = new Pipe<GroveResponseSchema>(responsePipeConfig);
        new SplitterStage<>(gm, responsePipe, collectedResponsePipes.toArray(new Pipe[s]));
        new GroveShieldV2ResponseStage(gm,responsePipe,config);
        
    }





    
    
}
