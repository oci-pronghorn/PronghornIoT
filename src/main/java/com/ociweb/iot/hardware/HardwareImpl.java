package com.ociweb.iot.hardware;

import java.util.concurrent.locks.ReentrantLock;

import com.ociweb.iot.impl.*;
import com.ociweb.iot.maker.*;
import com.ociweb.iot.transducer.*;
import com.ociweb.pronghorn.iot.schema.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.gl.api.Behavior;
import com.ociweb.gl.api.MsgCommandChannel;
import com.ociweb.gl.impl.BuilderImpl;
import com.ociweb.gl.impl.ChildClassScanner;
import com.ociweb.gl.impl.schema.IngressMessages;
import com.ociweb.gl.impl.schema.MessagePubSub;
import com.ociweb.gl.impl.schema.MessageSubscription;
import com.ociweb.gl.impl.schema.TrafficAckSchema;
import com.ociweb.gl.impl.schema.TrafficOrderSchema;
import com.ociweb.gl.impl.schema.TrafficReleaseSchema;
import com.ociweb.gl.impl.stage.TrafficCopStage;
import com.ociweb.iot.hardware.impl.DirectHardwareAnalogDigitalOutputStage;
import com.ociweb.iot.hardware.impl.SerialDataReaderStage;
import com.ociweb.iot.hardware.impl.SerialDataSchema;
import com.ociweb.iot.hardware.impl.SerialDataWriterStage;
import com.ociweb.iot.hardware.impl.SerialInputSchema;
import com.ociweb.iot.hardware.impl.SerialOutputSchema;
import com.ociweb.iot.hardware.impl.edison.EdisonConstants;
import com.ociweb.iot.impl.AnalogListenerBase;
import com.ociweb.iot.impl.DigitalListenerBase;
import com.ociweb.iot.impl.I2CListenerBase;
import com.ociweb.iot.impl.RotaryListenerBase;
import com.ociweb.iot.impl.SerialListenerBase;
import com.ociweb.iot.maker.Baud;
import com.ociweb.iot.maker.FogRuntime;
import com.ociweb.iot.maker.Hardware;
import com.ociweb.iot.maker.Port;
import com.ociweb.iot.transducer.AnalogListenerTransducer;
import com.ociweb.iot.transducer.DigitalListenerTransducer;
import com.ociweb.iot.transducer.I2CListenerTransducer;
import com.ociweb.iot.transducer.RotaryListenerTransducer;
import com.ociweb.iot.transducer.SerialListenerTransducer;
import com.ociweb.pronghorn.iot.ReadDeviceInputStage;
import com.ociweb.pronghorn.iot.i2c.I2CBacking;
import com.ociweb.pronghorn.iot.i2c.I2CJFFIStage;
import com.ociweb.pronghorn.iot.i2c.impl.I2CNativeLinuxBacking;
import com.ociweb.pronghorn.iot.rs232.RS232Client;
import com.ociweb.pronghorn.iot.rs232.RS232Clientable;
import com.ociweb.pronghorn.network.schema.ClientHTTPRequestSchema;
import com.ociweb.pronghorn.network.schema.HTTPRequestSchema;
import com.ociweb.pronghorn.network.schema.NetPayloadSchema;
import com.ociweb.pronghorn.network.schema.NetResponseSchema;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeConfig;
import com.ociweb.pronghorn.pipe.util.hash.IntHashTable;
import com.ociweb.pronghorn.stage.PronghornStage;
import com.ociweb.pronghorn.stage.route.ReplicatorStage;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;
import com.ociweb.pronghorn.stage.test.PipeCleanerStage;
import com.ociweb.pronghorn.util.math.PMath;
import com.ociweb.pronghorn.util.math.ScriptedSchedule;

public abstract class HardwareImpl extends BuilderImpl implements Hardware {


	private static final int MAX_MOVING_AVERAGE_SUPPORTED = 101; //TOOD: is this still needed, remove???

	private static final HardwareConnection[] EMPTY = new HardwareConnection[0];

	protected boolean configCamera = false;
	protected boolean configI2C;       //Humidity, LCD need I2C address so..

	protected long debugI2CRateLastTime;

	protected HardwareConnection[] digitalInputs; //Button, Motion
	protected HardwareConnection[] digitalOutputs;//Relay Buzzer

	protected HardwareConnection[] analogInputs;  //Light, UV, Moisture
	protected HardwareConnection[] pwmOutputs;    //Servo   //(only 3, 5, 6, 9, 10, 11 when on edison)

	protected I2CConnection[] i2cInputs;
	protected I2CConnection[] i2cOutputs;

	private static final int DEFAULT_LENGTH = 16;
	private static final int DEFAULT_PAYLOAD_SIZE = 128;


	private int i2cBus;
	protected I2CBacking i2cBackingInternal;

	protected static final long MS_TO_NS = 1_000_000;


	private static final Logger logger = LoggerFactory.getLogger(HardwareImpl.class);

	protected final IODevice[] deviceOnPort= new IODevice[Port.values().length];
	
	/////////////////
	///Pipes for initial startup declared subscriptions. (Not part of graph)
	private final int maxStartupSubs = 64;
	private final int maxTopicLengh  = 128;
	private Pipe<MessagePubSub> tempPipeOfStartupSubscriptions;
	/////////////////
	/////////////////

	protected ReentrantLock devicePinConfigurationLock = new ReentrantLock();

	protected RS232Client rs232Client;
	protected String rs232ClientDevice = "/dev/ttyMFD1";//custom hardware should override this edison value
	protected Baud   rs232ClientBaud = Baud.B_____9600;
	protected String bluetoothDevice = null;
	

	private static final boolean debug = false;

    private int IDX_PIN = -1;
    private int IDX_I2C = -1;
    private int IDX_SER = -1;

	private int imageTriggerRateMillis = 1250;

	public void setImageTriggerRateMillis(int imageTriggerRateMillis) {
		this.imageTriggerRateMillis = imageTriggerRateMillis;
	}

    public IODevice getConnectedDevice(Port p) {    	
    	return deviceOnPort[p.ordinal()];
    }

	public HardwareImpl(GraphManager gm, String[] args, int i2cBus) {
		this(gm, args, i2cBus, false,false,EMPTY,EMPTY,EMPTY,EMPTY,EMPTY);
	}

	protected HardwareImpl(GraphManager gm, String[] args, int i2cBus, boolean publishTime, boolean configI2C, HardwareConnection[] multiDigitalInput,
			HardwareConnection[] digitalInputs, HardwareConnection[] digitalOutputs, HardwareConnection[] pwmOutputs, HardwareConnection[] analogInputs) {

		super(gm, args);

		this.pcm.addConfig(new PipeConfig<HTTPRequestSchema>(HTTPRequestSchema.instance,
									                   		 2, //only a few requests when FogLight
									                         MAXIMUM_INCOMMING_REST_SIZE));

		this.pcm.addConfig(new PipeConfig<NetPayloadSchema>(NetPayloadSchema.instance,
															2, //only a few requests when FogLight 
															MINIMUM_TLS_BLOB_SIZE)); 		this.pcm.addConfig(new PipeConfig<SerialInputSchema>(SerialInputSchema.instance,
				                                            DEFAULT_LENGTH,
				                                            DEFAULT_PAYLOAD_SIZE));
		this.i2cBus = i2cBus;

		this.configI2C = configI2C; //may be removed.

		this.digitalInputs = digitalInputs;
		this.digitalOutputs = digitalOutputs;
		this.pwmOutputs = pwmOutputs;
		this.analogInputs = analogInputs;

		this.getTempPipeOfStartupSubscriptions().initBuffers();
	}

	public I2CBacking getI2CBacking() {
		if (null == i2cBackingInternal) {
			i2cBackingInternal = getI2CBacking((byte)i2cBus, false);
		}
		return i2cBackingInternal;
	}

	private static I2CBacking getI2CBacking(byte deviceNum, boolean reportError) {
		long start = System.currentTimeMillis();
		try {
			return new I2CNativeLinuxBacking().configure(deviceNum);
		} catch (Throwable t) {
			if (reportError) {
				logger.info("warning could not find the i2c bus", t);
			}
			//avoid non error case that is used to detect which hardware is running.
			return null;
		} finally {
			logger.info("duration of getI2CBacking {} ", System.currentTimeMillis()-start);
		}
	}

	protected HardwareConnection[] growHardwareConnections(HardwareConnection[] original, HardwareConnection toAdd) {
		final int len = original.length;
		//Validate that what we are adding is safe
		int i = len;
		while (--i>=0) {
			if (original[i].register == toAdd.register) {
				throw new UnsupportedOperationException("This connection "+toAdd.register+" already has attachment "+original[i].twig+" so the attachment "+toAdd.twig+" can not be added.");
			}
		}

		//Grow the array
		HardwareConnection[] result = new HardwareConnection[len+1];
		System.arraycopy(original, 0, result, 0, len);
		result[len] = toAdd;
		return result;
	}

	protected I2CConnection[] growI2CConnections(I2CConnection[] original, I2CConnection toAdd){
		if (null==original) {
			return new I2CConnection[] {toAdd};
		} else {
			int l = original.length;
			I2CConnection[] result = new I2CConnection[l+1];
			System.arraycopy(original, 0, result, 0, l);
			result[l] = toAdd;
			return result;
		}
	}

	protected Hardware internalConnectAnalog(IODevice t, int connection, int customRate, int customAverageMS, boolean everyValue) {
		if (t.isInput()) {
			assert(!t.isOutput());
			analogInputs = growHardwareConnections(analogInputs, new HardwareConnection(t,connection, customRate, customAverageMS, everyValue));
		} else {
			assert(t.isOutput());
			pwmOutputs = growHardwareConnections(pwmOutputs, new HardwareConnection(t,connection, customRate, customAverageMS, everyValue));
		}
		return this;
	}  

	protected Hardware internalConnectDigital(IODevice t, int connection, int customRate, int customAverageMS, boolean everyValue) {
		if (t.isInput()) {
			assert(!t.isOutput());
			digitalInputs = growHardwareConnections(digitalInputs, new HardwareConnection(t,connection, customRate, customAverageMS, everyValue));
		} else {
			assert(t.isOutput());			
			digitalOutputs = growHardwareConnections(digitalOutputs, new HardwareConnection(t,connection, customRate, customAverageMS, everyValue));
		}
		return this;
	}
	
	@Override
	public Hardware connect(I2CIODevice t){
		logger.debug("Connecting I2C Device "+t.getClass());
		
		if(t.isInput()){
			i2cInputs = growI2CConnections(i2cInputs, t.getI2CConnection());
		}
		
		if(t.isOutput()){
			i2cOutputs = growI2CConnections(i2cOutputs, t.getI2CConnection());
		}
		
		this.useI2C();
		return this;
	}
	@Override
	public Hardware connect(I2CIODevice t, int customRateMS){
		logger.debug("Connecting I2C Device "+t.getClass());
		if(t.isInput()){
			i2cInputs = growI2CConnections(i2cInputs, new I2CConnection(t.getI2CConnection(),customRateMS));
		}
		
		if(t.isOutput()){
			i2cOutputs = growI2CConnections(i2cOutputs, t.getI2CConnection());
		}
		
		this.useI2C();
		return this;
	}

		
	public Hardware useSerial(Baud baud) {
		this.rs232ClientBaud = baud;
		return this;
	}
	
	/**
     *             
	 * @param baud
	 * @param device Name of the port. On UNIX systems this will typically
     *             be of the form /dev/ttyX, where X is a port number. On
     *             Windows systems this will typically of the form COMX,
     *             where X is again a port number.
	 */
	public Hardware useSerial(Baud baud, String device) {
		this.rs232ClientBaud = baud;
		this.rs232ClientDevice = device;
		return this;
	}

	public Hardware useI2C() {
		this.configI2C = true;
		return this;
	}

	public Hardware useCamera() {
		this.configCamera = true;
		return this;
	}

	@Deprecated //would be nice if we did not have to do this.
	public Hardware useI2C(int bus) {
		this.configI2C = true;
		this.i2cBus = bus;
		return this;
	}
	
	public boolean isUseI2C() {
		return this.configI2C;
	}

	public abstract HardwarePlatformType getPlatformType();
	public abstract int read(Port port); //Platform specific
	public abstract void write(Port port, int value); //Platform specific

	public int maxAnalogMovingAverage() {
		return MAX_MOVING_AVERAGE_SUPPORTED;
	}


	public void coldSetup(){
		System.out.println("");
	}

	protected HardwareConnection[] buildUsedLines() {

		HardwareConnection[] result = new HardwareConnection[digitalInputs.length+
		                                                     digitalOutputs.length+
		                                                     pwmOutputs.length+
		                                                     analogInputs.length+
		                                                     (configI2C?2:0)];

		int pos = 0;
		System.arraycopy(digitalInputs, 0, result, pos, digitalInputs.length);
		pos+=digitalInputs.length;

		findDup(result,pos,digitalOutputs, false);
		System.arraycopy(digitalOutputs, 0, result, pos, digitalOutputs.length);
		pos+=digitalOutputs.length;

		findDup(result,pos,pwmOutputs, false);
		System.arraycopy(pwmOutputs, 0, result, pos, pwmOutputs.length);
		pos+=pwmOutputs.length;        

		findDup(result,pos,analogInputs, true);
		int j = analogInputs.length;
		while (--j>=0) {
			result[pos++] = new HardwareConnection(analogInputs[j].twig,(int) EdisonConstants.ANALOG_CONNECTOR_TO_PIN[analogInputs[j].register]);
		}

		if (configI2C) {
			findDup(result,pos,EdisonConstants.i2cPins, false);
			System.arraycopy(EdisonConstants.i2cPins, 0, result, pos, EdisonConstants.i2cPins.length);
			pos+=EdisonConstants.i2cPins.length;
		}

		return result;
	}

	private static void findDup(HardwareConnection[] base, int baseLimit, HardwareConnection[] items, boolean mapAnalogs) {
		int i = items.length;
		while (--i>=0) {
			int j = baseLimit;
			while (--j>=0) {
				if (mapAnalogs ? base[j].register ==  EdisonConstants.ANALOG_CONNECTOR_TO_PIN[items[i].register] :  base[j]==items[i]) {
					throw new UnsupportedOperationException("Connector "+items[i]+" is assigned more than once.");
				}
			}
		}     
	}


	public void shutdown() {
		super.shutdown();
		//can be overridden by specific hardware impl if shutdown is supported.
	}



	private void createUARTInputStage(Pipe<SerialInputSchema> masterUARTPipe) {
		RS232Clientable client = buildSerialClient();
		new SerialDataReaderStage(this.gm, masterUARTPipe, client);
	}


	
	protected RS232Clientable buildSerialClient() {
		if (null==rs232Client) {
			//custom hardware can override this
			rs232Client = new RS232Client(rs232ClientDevice, rs232ClientBaud);
		}
		return rs232Client;
	}
	
	protected void createADInputStage(Pipe<GroveResponseSchema> masterResponsePipe) {
		//NOTE: rate is NOT set since stage sets and configs its own rate based on polling need.
		ReadDeviceInputStage adInputStage = new ReadDeviceInputStage(this.gm, masterResponsePipe, this);
	}

	protected void createI2COutputInputStage(Pipe<I2CCommandSchema>[] i2cPipes,
			Pipe<TrafficReleaseSchema>[] masterI2CgoOut, Pipe<TrafficAckSchema>[] masterI2CackIn, Pipe<I2CResponseSchema> masterI2CResponsePipe) {

		if (hasI2CInputs()) {
			I2CJFFIStage i2cJFFIStage = new I2CJFFIStage(gm, masterI2CgoOut, i2cPipes, masterI2CackIn, masterI2CResponsePipe, this);
		} else {
			//TODO: build an output only version of this stage because there is nothing to read
			I2CJFFIStage i2cJFFIStage = new I2CJFFIStage(gm, masterI2CgoOut, i2cPipes, masterI2CackIn, masterI2CResponsePipe, this);
		}
	}

	protected void createADOutputStage(Pipe<GroveRequestSchema>[] requestPipes, Pipe<TrafficReleaseSchema>[] masterPINgoOut, Pipe<TrafficAckSchema>[] masterPINackIn) {
		DirectHardwareAnalogDigitalOutputStage adOutputStage = new DirectHardwareAnalogDigitalOutputStage(gm, requestPipes, masterPINgoOut, masterPINackIn, this);
	}


	
	public boolean isListeningToSerial(Object listener) {
		return listener instanceof SerialListenerBase
			   || !ChildClassScanner.visitUsedByClass(listener, deepListener, SerialListenerTransducer.class);
	}

	public boolean isListeningToCamera(Object listener) {
		return listener instanceof ImageListenerBase
				|| !ChildClassScanner.visitUsedByClass(listener, deepListener, ImageListenerTransducer.class);
	}

	public boolean isListeningToI2C(Object listener) {
		return listener instanceof I2CListenerBase
				 || !ChildClassScanner.visitUsedByClass(listener, deepListener, I2CListenerTransducer.class);
	}

	public boolean isListeningToPins(Object listener) {
		return listener instanceof DigitalListenerBase || 
			   listener instanceof AnalogListenerBase || 
			   listener instanceof RotaryListenerBase
			   || !ChildClassScanner.visitUsedByClass(listener, deepListener, DigitalListenerTransducer.class)
			   || !ChildClassScanner.visitUsedByClass(listener, deepListener, AnalogListenerTransducer.class)
			   || !ChildClassScanner.visitUsedByClass(listener, deepListener, RotaryListenerTransducer.class);
	}
	
	private Pipe<MessagePubSub> getTempPipeOfStartupSubscriptions() {
		if (null==tempPipeOfStartupSubscriptions) {

			final PipeConfig<MessagePubSub> messagePubSubConfig = new PipeConfig<MessagePubSub>(MessagePubSub.instance, maxStartupSubs,maxTopicLengh);   
			tempPipeOfStartupSubscriptions = new Pipe<MessagePubSub>(messagePubSubConfig);

		}		

		return tempPipeOfStartupSubscriptions;
	}

	
	public boolean hasI2CInputs() {
		return this.i2cInputs!=null && this.i2cInputs.length>0;
	}

	public I2CConnection[] getI2CInputs() {
		return null==i2cInputs?new I2CConnection[0]:i2cInputs;
	}

	public HardwareConnection[] getAnalogInputs() {
		return analogInputs;
	}

	public HardwareConnection[] getDigitalInputs() {
		return digitalInputs;
	}

	public ScriptedSchedule buildI2CPollSchedule() {
		I2CConnection[] localInputs = getI2CInputs();
		
		long[] schedulePeriods = new long[localInputs.length];
		for (int i = 0; i < localInputs.length; i++) {
			schedulePeriods[i] = localInputs[i].responseMS*MS_TO_NS;
		}
		//logger.debug("known I2C rates: {}",Arrays.toString(schedulePeriods));
		return PMath.buildScriptedSchedule(schedulePeriods);

	}

	public boolean hasDigitalOrAnalogInputs() {
		return (analogInputs.length+digitalInputs.length)>0;
	}
	
	public boolean hasDigitalOrAnalogOutputs() {
		return (pwmOutputs.length+digitalOutputs.length)>0;
	}
	
	public HardwareConnection[] combinedADConnections() {
		HardwareConnection[] localAInputs = getAnalogInputs();
		HardwareConnection[] localDInputs = getDigitalInputs();

		int totalCount = localAInputs.length + localDInputs.length;

		HardwareConnection[] results = new HardwareConnection[totalCount];
		System.arraycopy(localAInputs, 0, results, 0,                   localAInputs.length);
		System.arraycopy(localDInputs, 0, results, localAInputs.length, localDInputs.length);

		return results;
	}

	public ScriptedSchedule buildADPollSchedule() {
		HardwareConnection[] localAInputs = getAnalogInputs();
		HardwareConnection[] localDInputs = getDigitalInputs();

		int totalCount = localAInputs.length + localDInputs.length;
		if (0==totalCount) {
			return null;
		}

		long[] schedulePeriods = new long[totalCount];
		int j = 0;
		for (int i = 0; i < localAInputs.length; i++) {
			schedulePeriods[j++] = localAInputs[i].responseMS*MS_TO_NS;
		}
		for (int i = 0; i < localDInputs.length; i++) {
			schedulePeriods[j++] = localDInputs[i].responseMS*MS_TO_NS;
		}
		//analogs then the digitals

		return PMath.buildScriptedSchedule(schedulePeriods);

	}

	public byte convertToPort(byte connection) {
		return connection;
	}
			
	@Override
	public Hardware connect(ADIODevice t, Port port, int customRateMS, int customAvgWindowMS, boolean everyValue) {
		
		deviceOnPort[port.ordinal()] = t;
		
		if (0 != (port.mask&Port.IS_ANALOG)) {
			return internalConnectAnalog(t, port.port, customRateMS, customAvgWindowMS, everyValue);			
		}
		
		if (0 != (port.mask&Port.IS_DIGITAL)) {
			return internalConnectDigital(t, port.port, customRateMS, customAvgWindowMS, everyValue);			
		}
		
		return this;
	}
	
	@Override
	public Hardware connect(ADIODevice t, Port port, int customRateMS, int customAvgWindowMS) {
		
		deviceOnPort[port.ordinal()] = t;
		
		if (0 != (port.mask&Port.IS_ANALOG)) {
			return internalConnectAnalog(t, port.port, customRateMS, customAvgWindowMS, false);			
		}
		
		if (0 != (port.mask&Port.IS_DIGITAL)) {
			return internalConnectDigital(t, port.port, customRateMS, customAvgWindowMS, false);			
		}
		
		return this;
	}

	@Override
	public Hardware connect(ADIODevice t, Port port, int customRateMS) {
		
		deviceOnPort[port.ordinal()] = t;
				
		if (port.isAnalog()) {
			return internalConnectAnalog(t, port.port, customRateMS, -1, false);			
		} else {
			return internalConnectDigital(t, port.port, customRateMS, -1, false);			
		}
		
	}
	
	@Override
	public Hardware connect(ADIODevice t, Port port, int customRateMS, boolean everyValue) {
		
		deviceOnPort[port.ordinal()] = t;
		
		if (port.isAnalog()) {
			return internalConnectAnalog(t, port.port, customRateMS, -1, everyValue);			
		} else {
			return internalConnectDigital(t, port.port, customRateMS, -1, everyValue);			
		}
		
	}
	
	@Override
	public Hardware connect(ADIODevice t, Port port) {
		
		deviceOnPort[port.ordinal()] = t;
		
		if (0 != (port.mask&Port.IS_ANALOG)) {
			return internalConnectAnalog(t, port.port, -1, -1, false);			
		}
		
		if (0 != (port.mask&Port.IS_DIGITAL)) {
			return internalConnectDigital(t, port.port, -1, -1, false);			
		}
		
		return this;
	}


	public void releasePinOutTraffic(int count, MsgCommandChannel<?> gcc) {		
		MsgCommandChannel.publishGo(count, IDX_PIN, gcc);		
	}

	public void releaseI2CTraffic(int count, MsgCommandChannel<?> gcc) {
		MsgCommandChannel.publishGo(count, IDX_I2C, gcc);
	}
	
	@Override
	public void releasePubSubTraffic(int count, MsgCommandChannel<?> gcc) {
		MsgCommandChannel.publishGo(count, IDX_MSG, gcc);
	}

	public void buildStages(IntHashTable subscriptionPipeLookup2, IntHashTable netPipeLookup2, GraphManager gm2) {
		

		Pipe<I2CResponseSchema>[] i2cResponsePipes = GraphManager.allPipesOfTypeWithNoProducer(gm2, I2CResponseSchema.instance);
		Pipe<GroveResponseSchema>[] responsePipes = GraphManager.allPipesOfTypeWithNoProducer(gm2, GroveResponseSchema.instance);

		Pipe<SerialOutputSchema>[] serialOutputPipes = GraphManager.allPipesOfTypeWithNoConsumer(gm2, SerialOutputSchema.instance);
		Pipe<I2CCommandSchema>[] i2cPipes = GraphManager.allPipesOfTypeWithNoConsumer(gm2, I2CCommandSchema.instance);
		Pipe<GroveRequestSchema>[] pinRequestPipes = GraphManager.allPipesOfTypeWithNoConsumer(gm2, GroveRequestSchema.instance);
		Pipe<SerialInputSchema>[] serialInputPipes = GraphManager.allPipesOfTypeWithNoProducer(gm2, SerialInputSchema.instance);
		Pipe<ImageSchema>[] imageInputPipes = GraphManager.allPipesOfTypeWithNoProducer(gm2, ImageSchema.instance);
		
		Pipe<NetResponseSchema>[] httpClientResponsePipes = GraphManager.allPipesOfTypeWithNoProducer(gm2, NetResponseSchema.instance);
		Pipe<MessageSubscription>[] subscriptionPipes = GraphManager.allPipesOfTypeWithNoProducer(gm2, MessageSubscription.instance);
		
		Pipe<TrafficOrderSchema>[] orderPipes = GraphManager.allPipesOfTypeWithNoConsumer(gm2, TrafficOrderSchema.instance);
		Pipe<ClientHTTPRequestSchema>[] httpClientRequestPipes = GraphManager.allPipesOfTypeWithNoConsumer(gm2, ClientHTTPRequestSchema.instance);
		Pipe<MessagePubSub>[] messagePubSub = GraphManager.allPipesOfTypeWithNoConsumer(gm2, MessagePubSub.instance);
		Pipe<IngressMessages>[] ingressMessagePipes = GraphManager.allPipesOfTypeWithNoConsumer(gm2, IngressMessages.instance);


		//TODO: must pull out those pubSub Pipes for direct connections
		//TODO: new MessageSchema for direct messages from point to point
		//      create the pipe instead of pub sub and attach?
		//TODO: declare up front once in connections, direct connect topics
		//      upon seeing these we build a new pipe
		
		
		int commandChannelCount = orderPipes.length;

		int eventSchemas = 0;
		IDX_PIN = pinRequestPipes.length>0 ? eventSchemas++ : -1;
		IDX_I2C = i2cPipes.length>0 || i2cResponsePipes.length > 0 ? eventSchemas++ : -1;  //the 'or' check is to ensure that reading without a cmd channel works
		IDX_MSG = (IntHashTable.isEmpty(subscriptionPipeLookup2) && subscriptionPipes.length==0 && messagePubSub.length==0) ? -1 : eventSchemas++;
		IDX_NET = useNetClient(netPipeLookup2, httpClientRequestPipes) ? eventSchemas++ : -1;
		IDX_SER = serialOutputPipes.length>0 ? eventSchemas++ : -1;

		long timeout = 20_000; //20 seconds
		
		//TODO: can we share this while with the parent BuilderImpl, I think so..
		int maxGoPipeId = 0;
					
		int t = commandChannelCount;
								
		Pipe<TrafficReleaseSchema>[][] masterGoOut = new Pipe[eventSchemas][0];
		Pipe<TrafficAckSchema>[][]     masterAckIn = new Pipe[eventSchemas][0];
		
		if (IDX_PIN >= 0) {	
			masterGoOut[IDX_PIN] = new Pipe[pinRequestPipes.length];
			masterAckIn[IDX_PIN] = new Pipe[pinRequestPipes.length];
		}		
		if (IDX_I2C >= 0) {
			masterGoOut[IDX_I2C] = new Pipe[i2cPipes.length];
			masterAckIn[IDX_I2C] = new Pipe[i2cPipes.length];
		}		
		if (IDX_MSG >= 0) {
			masterGoOut[IDX_MSG] = new Pipe[messagePubSub.length];
			masterAckIn[IDX_MSG] = new Pipe[messagePubSub.length];
		}		
		if (IDX_NET >= 0) {
			masterGoOut[IDX_NET] = new Pipe[httpClientRequestPipes.length];
			masterAckIn[IDX_NET] = new Pipe[httpClientRequestPipes.length];
		}		
		if (IDX_SER >=0) {
			masterGoOut[IDX_SER] = new Pipe[serialOutputPipes.length];
			masterAckIn[IDX_SER] = new Pipe[serialOutputPipes.length];
		}

				
		while (--t>=0) {
		
			int features = getFeatures(gm2, orderPipes[t]);
			
			Pipe<TrafficReleaseSchema>[] goOut = new Pipe[eventSchemas];
			Pipe<TrafficAckSchema>[] ackIn = new Pipe[eventSchemas];
			
			boolean isDynamicMessaging = (features&Behavior.DYNAMIC_MESSAGING) != 0;
			boolean isNetRequester     = (features&Behavior.NET_REQUESTER) != 0;
			boolean isPinWriter        = (features&FogRuntime.PIN_WRITER) != 0;
			boolean isI2CWriter        = (features&FogRuntime.I2C_WRITER) != 0;
			boolean isSerialWriter     = (features&FogRuntime.SERIAL_WRITER) != 0;

			boolean hasConnections = false;
			if (isDynamicMessaging && IDX_MSG>=0) {
				hasConnections = true;		 		
		 		maxGoPipeId = populateGoAckPipes(maxGoPipeId, masterGoOut, masterAckIn, goOut, ackIn, IDX_MSG);
			}
			if (isNetRequester && IDX_NET>=0) {
				hasConnections = true;		 		
		 		maxGoPipeId = populateGoAckPipes(maxGoPipeId, masterGoOut, masterAckIn, goOut, ackIn, IDX_NET);
			}
			if (isPinWriter && IDX_PIN>=0) {
				hasConnections = true;	
		 		maxGoPipeId = populateGoAckPipes(maxGoPipeId, masterGoOut, masterAckIn, goOut, ackIn, IDX_PIN);
			}
			if (isI2CWriter && IDX_I2C>=0) {
				hasConnections = true;		 		
		 		maxGoPipeId = populateGoAckPipes(maxGoPipeId, masterGoOut, masterAckIn, goOut, ackIn, IDX_I2C);
			}
			if (isSerialWriter && IDX_SER>=0) {
				hasConnections = true;		 		
		 		maxGoPipeId = populateGoAckPipes(maxGoPipeId, masterGoOut, masterAckIn, goOut, ackIn, IDX_SER);
			}

			if (hasConnections) {
				TrafficCopStage trafficCopStage = new TrafficCopStage(gm, timeout, orderPipes[t], ackIn, goOut, this);
			} else {
				PipeCleanerStage.newInstance(gm, orderPipes[t]);
			}
		}
		
		initChannelBlocker(maxGoPipeId);
		
		buildHTTPClientGraph(netPipeLookup2, httpClientResponsePipes, httpClientRequestPipes, masterGoOut, masterAckIn);
		
		if (IDX_MSG <0) {
				logger.trace("saved some resources by not starting up the unused pub sub service.");
		} else {
			 	createMessagePubSubStage(subscriptionPipeLookup2, ingressMessagePipes, 
			 			                 messagePubSub, 
			 			                 masterGoOut[IDX_MSG], masterAckIn[IDX_MSG], subscriptionPipes);
		}

		int c = masterGoOut.length;
		while (--c>=0) {
			if (!PronghornStage.noNulls(masterGoOut[c])) {
				throw new UnsupportedOperationException("Flag is missing in command channel for "+featureName(c));
			}
			if (!PronghornStage.noNulls(masterAckIn[c])) {
				throw new UnsupportedOperationException("Flag is missing in command channel for "+featureName(c));
			}
		}		
		
				
		//////////////////
		//only build and connect I2C if it is used for either in or out  
		//////////////////
		Pipe<I2CResponseSchema> masterI2CResponsePipe = null;
		if (i2cResponsePipes.length>0) {
			masterI2CResponsePipe =  I2CResponseSchema.instance.newPipe(DEFAULT_LENGTH, DEFAULT_PAYLOAD_SIZE);
			ReplicatorStage.newInstance(gm, masterI2CResponsePipe, i2cResponsePipes);
		}

		if (i2cPipes.length>0 || (null!=masterI2CResponsePipe)) {
			createI2COutputInputStage(i2cPipes, masterGoOut[IDX_I2C], masterAckIn[IDX_I2C], masterI2CResponsePipe);
		}
		
		//////////////
		//only build and connect gpio input responses if it is used
		//////////////
		if (responsePipes.length>1) {
			Pipe<GroveResponseSchema> masterResponsePipe = GroveResponseSchema.instance.newPipe(DEFAULT_LENGTH, DEFAULT_PAYLOAD_SIZE);
			ReplicatorStage.newInstance(gm, masterResponsePipe, responsePipes);
			createADInputStage(masterResponsePipe);
		} else {
			if (responsePipes.length==1) {
				createADInputStage(responsePipes[0]);
			}
		}
		
		/////////////
		//only build serial output if data is sent
		/////////////
		if (serialOutputPipes.length>0) {	
			assert(null!=masterGoOut[IDX_SER]);
			assert(serialOutputPipes.length == masterGoOut[IDX_SER].length) : serialOutputPipes.length+" == "+masterGoOut[IDX_SER].length;
			createSerialOutputStage(serialOutputPipes, masterGoOut[IDX_SER], masterAckIn[IDX_SER]);			
		}

		//////////////
		//only build serial input if the data is consumed
		//////////////
		if (serialInputPipes.length>1) {
			Pipe<SerialInputSchema> masterUARTPipe = new Pipe<SerialInputSchema>(pcm.getConfig(SerialInputSchema.class));

			new ReplicatorStage<SerialInputSchema>(gm, masterUARTPipe, serialInputPipes);   
			createUARTInputStage(masterUARTPipe);
		} else {
			if (serialInputPipes.length==1) {
				createUARTInputStage(serialInputPipes[0]);
			} else {


			}
		}

		///////////////
		//only build image input if the data is consumed
		///////////////
		// TODO: Is this where we determine what kind of platform to listen on (e.g., Edison, Pi)?
		if (imageInputPipes.length > 1) {
			Pipe<ImageSchema> masterImagePipe = ImageSchema.instance.newPipe(DEFAULT_LENGTH, DEFAULT_PAYLOAD_SIZE);
			new ReplicatorStage<ImageSchema>(gm, masterImagePipe, imageInputPipes);
			new PiImageListenerStage(gm, masterImagePipe, imageTriggerRateMillis);
		} else if (imageInputPipes.length == 1){
			new PiImageListenerStage(gm, imageInputPipes[0], imageTriggerRateMillis);
		}
				
		///////////////
		//only build direct pin output when we detected its use
		///////////////
		if (IDX_PIN>=0) {
			assert(PronghornStage.noNulls(masterGoOut[IDX_PIN])) : "Go Pipe must not contain nulls";
			assert(PronghornStage.noNulls(masterAckIn[IDX_PIN])) : "Ack Pipe must not contain nulls";
			       
			createADOutputStage(pinRequestPipes, masterGoOut[IDX_PIN], masterAckIn[IDX_PIN]);
		}
	}

	private String featureName(final int c) {
		
		if (c == IDX_I2C) {
			//FogRuntime.I2C_WRITER;
			return "I2C_WRITER";
		}
		if (c == IDX_MSG) {
			//Behavior.DYNAMIC_MESSAGING;
			return "DYNAMIC_MESSAGING";
		}
		if (c == IDX_NET) { //TODO: where is the responder??
			//Behavior.NET_REQUESTER;
			return "NET_REQUESTER";
		}
		if (c == IDX_PIN) {
			//FogRuntime.PIN_WRITER;
			return "PIN_WRITER";
		}
		if (c == IDX_SER) {
			//FogRuntime.SERIAL_WRITER;
			return "SERIAL_WRITER";
		}
		
		return null;
	}

	protected void createSerialOutputStage(Pipe<SerialOutputSchema>[] serialOutputPipes,
			Pipe<TrafficReleaseSchema>[] masterGoOut, Pipe<TrafficAckSchema>[] masterAckIn) {
		new SerialDataWriterStage(gm, serialOutputPipes, masterGoOut, masterAckIn,
				                     this, this.buildSerialClient());
	}

	public static int serialIndex(HardwareImpl hardware) {
		return hardware.IDX_SER;
	}
	
	public static int i2cIndex(HardwareImpl hardware) {
		return hardware.IDX_I2C;
	}
	
	@Override
	public int pubSubIndex() {
		return IDX_MSG;
	}
	
	@Override
	public int netIndex() {
		return IDX_NET;
	}

	public boolean isTestHardware() {
		return false;
	}
	
	
}