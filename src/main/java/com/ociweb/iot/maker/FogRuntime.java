package com.ociweb.iot.maker;

import java.util.ArrayList;

import com.ociweb.pronghorn.iot.schema.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.gl.api.Behavior;
import com.ociweb.gl.api.MsgCommandChannel;
import com.ociweb.gl.api.MsgRuntime;
import com.ociweb.gl.impl.ChildClassScanner;
import com.ociweb.gl.impl.schema.MessagePubSub;
import com.ociweb.gl.impl.schema.MessageSubscription;
import com.ociweb.gl.impl.schema.TrafficOrderSchema;
import com.ociweb.gl.impl.stage.ReactiveManagerPipeConsumer;
import com.ociweb.iot.hardware.HardwareImpl;
import com.ociweb.iot.hardware.impl.SerialInputSchema;
import com.ociweb.iot.hardware.impl.edison.GroveV3EdisonImpl;
import com.ociweb.iot.hardware.impl.grovepi.GrovePiHardwareImpl;
import com.ociweb.iot.hardware.impl.grovepi.LinuxModel;
import com.ociweb.iot.hardware.impl.grovepi.MacModel;
import com.ociweb.iot.hardware.impl.grovepi.PiModel;
import com.ociweb.iot.hardware.impl.grovepi.WindowsModel;
import com.ociweb.iot.hardware.impl.test.TestHardware;
import com.ociweb.pronghorn.iot.ReactiveListenerStageIOT;
import com.ociweb.pronghorn.iot.i2c.I2CBacking;
import com.ociweb.pronghorn.pipe.DataInputBlobReader;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeConfig;
import com.ociweb.pronghorn.pipe.PipeConfigManager;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;
import com.ociweb.pronghorn.stage.scheduling.NonThreadScheduler;

public class FogRuntime extends MsgRuntime<HardwareImpl, ListenerFilterIoT>  {

	private static boolean isRunning = false;
	public static final int I2C_WRITER      = FogCommandChannel.I2C_WRITER;
	public static final int PIN_WRITER      = FogCommandChannel.PIN_WRITER;
	public static final int SERIAL_WRITER   = FogCommandChannel.SERIAL_WRITER;
	public static final int BT_WRITER       = FogCommandChannel.BT_WRITER;

	private static final Logger logger = LoggerFactory.getLogger(FogRuntime.class);

	private static final int i2cDefaultLength = 300;
	private static final int i2cDefaultMaxPayload = 16;

	private static final byte edI2C = 6;

	private int imageTriggerRateMillis = 1250;

	static final String PROVIDED_HARDWARE_IMPL_NAME = "com.ociweb.iot.hardware.impl.ProvidedHardwareImpl";


	public FogRuntime() {
		this(new String[0]);
	}

	public FogRuntime(String[] args) {
		super(args);

        //adds all the operators for the FogRuntime
		ReactiveListenerStageIOT.initOperators();

	}


	public Hardware getHardware(){
		if(this.builder==null){

			///////////////
			//setup system for binary binding in case Zulu is found on Arm
			//must populate os.arch as "arm" instead of "aarch32" or "aarch64" in that case, JIFFI is dependent on this value.
			if (System.getProperty("os.arch", "unknown").contains("aarch")) {
				System.setProperty("os.arch", "arm"); //TODO: investigate if this a bug against jiffi or zulu and inform them
			}

			long startTime = System.currentTimeMillis();

			// Detect provided hardware implementation.
			// TODO: Should this ONLY occur on Android devices?
			try {
				Class.forName("android.app.Activity");
				logger.trace("Detected Android environment. Searching for {}.", PROVIDED_HARDWARE_IMPL_NAME);

				try {
					Class<?> clazz = Class.forName(PROVIDED_HARDWARE_IMPL_NAME);
					logger.trace("Detected {}.", PROVIDED_HARDWARE_IMPL_NAME);
					try {
						this.builder = (HardwareImpl) clazz.getConstructor(GraphManager.class).newInstance(gm);
						return this.builder;
					} catch (NoSuchMethodException e) {
						logger.warn(
								"{} does not provide a single argument constructor that accepts a GraphManager. Continuing native hardware detection.", PROVIDED_HARDWARE_IMPL_NAME);
					} catch (Throwable e) {
						logger.warn(
								"Unable to instantiate {}. Continuing native hardware detection.", PROVIDED_HARDWARE_IMPL_NAME, e);
					}
				} catch (ClassNotFoundException e) {
					logger.trace("No {} is present.", PROVIDED_HARDWARE_IMPL_NAME);
				}
			} catch (ClassNotFoundException ignored) { }

			logger.info("android duration {} ",System.currentTimeMillis()-startTime);

			////////////////////////
			//The best way to detect the pi or edison is to first check for the expected matching i2c implmentation
			///////////////////////
			PiModel pm = null;
			I2CBacking i2cBacking = null;
			if ((pm = PiModel.detect()) != PiModel.Unknown){
				logger.info("Detected running on " + pm);
				this.builder = new GrovePiHardwareImpl(gm, args, pm.i2cBus());
			}			else if(WindowsModel.detect() != WindowsModel.Unknown) {
				this.builder = new TestHardware(gm, args);
				logger.info("Detected running on Windows, test mock hardware will be used");
			}

			else if(MacModel.detect() != MacModel.Unknown) {
				this.builder = new TestHardware(gm, args);
				logger.info("Detected running on Mac, test mock hardware will be used");

			}
			else if(LinuxModel.detect() != LinuxModel.Unknown) {
				this.builder = new TestHardware(gm, args);
				logger.info("Detected Running on Linux, test mock hardware will be used");

			}
			else if (null != (this.builder = new GroveV3EdisonImpl(gm, args, edI2C)).getI2CBacking() ) {
				logger.info("Detected running on Edison");
				System.out.println("You are running on the Edison hardware.");
			}
			else {
				this.builder = new TestHardware(gm, args);
				logger.info("Unrecognized hardware, test mock hardware will be used");
			}
		}
		return this.builder;
	}


	public FogCommandChannel newCommandChannel() {

		int instance = -1;

		PipeConfigManager pcm = buildPipeManager();

		return this.builder.newCommandChannel(instance, pcm);

	}

	public FogCommandChannel newCommandChannel(int features) {

		int instance = -1;

		PipeConfigManager pcm = buildPipeManager();

		return this.builder.newCommandChannel(features, instance, pcm);

	}

	protected PipeConfigManager buildPipeManager() {
		PipeConfigManager pcm = super.buildPipeManager();
		pcm.addConfig(new PipeConfig<GroveRequestSchema>(GroveRequestSchema.instance, defaultCommandChannelLength));
		pcm.addConfig(new PipeConfig<I2CCommandSchema>(I2CCommandSchema.instance, i2cDefaultLength,i2cDefaultMaxPayload));
		pcm.addConfig(defaultCommandChannelLength,0,TrafficOrderSchema.class );
		return pcm;
	}

	public FogCommandChannel newCommandChannel(int features, int customChannelLength) {

		int instance = -1;

		PipeConfigManager pcm = new PipeConfigManager();
		pcm.addConfig(customChannelLength,0,GroveRequestSchema.class);
		pcm.addConfig(customChannelLength, defaultCommandChannelMaxPayload, I2CCommandSchema.class);
		pcm.addConfig(customChannelLength, defaultCommandChannelMaxPayload, MessagePubSub.class );
		pcm.addConfig(customChannelLength,0,TrafficOrderSchema.class);

		return this.builder.newCommandChannel(features, instance, pcm);

	}

	public ListenerFilterIoT addRotaryListener(RotaryListener listener) {
		return registerListener(listener);
	}

	public ListenerFilterIoT addAnalogListener(AnalogListener listener) {
		return registerListener(listener);
	}

	public ListenerFilterIoT addDigitalListener(DigitalListener listener) {
		return registerListener(listener);
	}

	public ListenerFilterIoT addSerialListener(SerialListener listener) {
		return registerListener(listener);
	}

	public ListenerFilterIoT registerListener(Behavior listener) {
		return registerListenerImpl(listener);
	}

	public void setImageTriggerRate(int triggerRateMillis) {
		if (triggerRateMillis < 1250) {
			throw new RuntimeException("Image listeners cannot be used with trigger rates of less than 1250 MS.");
		}

		this.imageTriggerRateMillis = triggerRateMillis;
	}

	public ListenerFilterIoT addImageListener(ImageListener listener) {
		switch (builder.getPlatformType()) {
			case GROVE_PI:
				return registerListener(listener);
			default:
				throw new UnsupportedOperationException("Image listeners are not supported for [" +
						builder.getPlatformType() +
						"] hardware");
		}
	}

	public ListenerFilterIoT addI2CListener(I2CListener listener) {
		return registerListenerImpl(listener);
	}

	private ListenerFilterIoT registerListenerImpl(Behavior listener) {

		outputPipes = new Pipe<?>[0];
		ChildClassScanner.visitUsedByClass(listener, gatherPipesVisitor, MsgCommandChannel.class);//populates OutputPipes

		/////////
		//pre-count how many pipes will be needed so the array can be built to the right size
		/////////
		int pipesCount = 0;
		if (this.builder.isListeningToI2C(listener) && this.builder.hasI2CInputs()) {
			pipesCount++;
		}
		if (this.builder.isListeningToPins(listener) && this.builder.hasDigitalOrAnalogInputs()) {
			pipesCount++;
		}

		if (this.builder.isListeningToSerial(listener)) {
			pipesCount++;
		}

		if (this.builder.isListeningToCamera(listener)) {
			pipesCount++;
		}

		pipesCount = addGreenPipesCount(listener, pipesCount);

		Pipe<?>[] inputPipes = new Pipe<?>[pipesCount];


		if (this.builder.isListeningToI2C(listener) && this.builder.hasI2CInputs()) {
			inputPipes[--pipesCount] = new Pipe<I2CResponseSchema>(new PipeConfig<I2CResponseSchema>(I2CResponseSchema.instance, defaultCommandChannelLength, defaultCommandChannelMaxPayload).grow2x());
		}
		if (this.builder.isListeningToPins(listener) && this.builder.hasDigitalOrAnalogInputs()) {
			inputPipes[--pipesCount] = new Pipe<GroveResponseSchema>(new PipeConfig<GroveResponseSchema>(GroveResponseSchema.instance, defaultCommandChannelLength).grow2x());
		}
		if (this.builder.isListeningToSerial(listener) ) {
			inputPipes[--pipesCount] = newSerialInputPipe(new PipeConfig<SerialInputSchema>(SerialInputSchema.instance, defaultCommandChannelLength, defaultCommandChannelMaxPayload).grow2x());
		}
		if (this.builder.isListeningToCamera(listener)) {
			inputPipes[--pipesCount] = new Pipe<ImageSchema>(new PipeConfig<ImageSchema>(ImageSchema.instance, defaultCommandChannelLength, defaultCommandChannelMaxPayload).grow2x());
		}

		populateGreenPipes(listener, pipesCount, inputPipes);

		/////////////////////
		//StartupListener is not driven by any response data and is called when the stage is started up. no pipe needed.
		/////////////////////
		//TimeListener, time rate signals are sent from the stages its self and therefore does not need a pipe to consume.
		/////////////////////
        //this is empty when transducerAutowiring is off
        final ArrayList<ReactiveManagerPipeConsumer> consumers = new ArrayList<ReactiveManagerPipeConsumer>();


        //extract this into common method to be called in GL and FL
		if (transducerAutowiring) {
			inputPipes = autoWireTransducers(listener, inputPipes, consumers);
		}

		ReactiveListenerStageIOT reactiveListener = builder.createReactiveListener(gm, listener,
													inputPipes, outputPipes, consumers,
													parallelInstanceUnderActiveConstruction);
		configureStageRate(listener,reactiveListener);

		//////////
		///only for assert, TODO: remove upon assert disabled
		///////////
		int testId = -1;
		int i = inputPipes.length;
		while (--i>=0) {
			if (inputPipes[i]!=null && Pipe.isForSchema((Pipe<MessageSubscription>)inputPipes[i], MessageSubscription.class)) {
				testId = inputPipes[i].id;
			}
		}
		assert(-1==testId || GraphManager.allPipesOfType(gm, MessageSubscription.instance)[subscriptionPipeIdx-1].id==testId) : "GraphManager has returned the pipes out of the expected order";
		//////////////////

		return reactiveListener;

	}

	private static Pipe<SerialInputSchema> newSerialInputPipe(PipeConfig<SerialInputSchema> config) {
		return new Pipe<SerialInputSchema>(config) {
			@SuppressWarnings("unchecked")
			@Override
			protected DataInputBlobReader<SerialInputSchema> createNewBlobReader() {
				return new SerialReader(this);
			}    		
		};
	}



	public static FogRuntime test(FogApp app) {
		FogRuntime runtime = new FogRuntime();
		//force hardware to TestHardware regardless of where or what platform its run on.
		//this is done because this is the test() method and must behave the same everywhere.
		runtime.builder = new TestHardware(runtime.gm, runtime.args);
		TestHardware hardware = (TestHardware)runtime.getHardware();
		hardware.isInUnitTest = true;

		app.declareConfiguration(runtime.builder);
		GraphManager.addDefaultNota(runtime.gm, GraphManager.SCHEDULE_RATE, runtime.builder.getDefaultSleepRateNS());

		runtime.declareBehavior(app);

		runtime.builder.coldSetup(); //TODO: should we add LCD init in the PI hardware code? How do we know when its used?

		runtime.builder.setImageTriggerRateMillis(runtime.imageTriggerRateMillis);
		runtime.builder.buildStages(runtime.subscriptionPipeLookup, runtime.netPipeLookup, runtime.gm);

		runtime.logStageScheduleRates();

		if ( runtime.builder.isTelemetryEnabled()) {
			runtime.gm.enableTelemetry(8098);
		}
		//exportGraphDotFile();

		runtime.scheduler  = new NonThreadScheduler(runtime.gm);
		//= runtime.builder.createScheduler(runtime);
		//for test we do not call startup and wait instead for this to be done by test.

		return runtime;
	}

	public static FogRuntime run(FogApp app) {
		return run(app,new String[0]);
	}
	public static FogRuntime run(FogApp app, String[] args) throws UnsupportedOperationException {
		if (FogRuntime.isRunning){
			throw new UnsupportedOperationException("An FogApp is already running!");
		}

		long lastTime;
		long nowTime;

		FogRuntime.isRunning = true;
		FogRuntime runtime = new FogRuntime(args);

		logger.info("{} ms startup", lastTime = System.currentTimeMillis());
		Hardware hardware = runtime.getHardware();
		//this default for Fog is slower due to the expected minimum hardware of iot devices
		hardware.setDefaultRate(20_000); // 1/50 of 1 ms

		app.declareConfiguration(hardware);
		GraphManager.addDefaultNota(runtime.gm, GraphManager.SCHEDULE_RATE, runtime.builder.getDefaultSleepRateNS());
		logger.info("{} ms duration {} ms finished declare configuration", nowTime = System.currentTimeMillis(), nowTime-lastTime);
		lastTime = nowTime;

		runtime.declareBehavior(app);
		logger.info("{} ms duration {} ms finished declare behavior", nowTime = System.currentTimeMillis(), nowTime-lastTime);
		lastTime = nowTime;

		//TODO: at this point realize the stages in declare behavior
		//      all updates are done so create the reactors with the right pipes and names
		//      this change will let us move routes to part of the fluent API plus other benifits..
		//      move all reactor fields into object created early, shell is created here.
		//      register must hold list of all temp objects (linked list to preserve order?)

		System.out.println("To exit app press Ctrl-C");
		runtime.builder.coldSetup(); //TODO: should we add LCD init in the PI hardware code? How do we know when its used?

		runtime.builder.buildStages(runtime.subscriptionPipeLookup, runtime.netPipeLookup, runtime.gm);
		runtime.logStageScheduleRates();

		logger.info("{} ms duration {} ms finished building internal graph", nowTime = System.currentTimeMillis(), nowTime-lastTime);
		lastTime = nowTime;

		if ( runtime.builder.isTelemetryEnabled()) {
			runtime.gm.enableTelemetry(8098);
			logger.info("{} ms duration {} ms finished building telemetry", lastTime = nowTime = System.currentTimeMillis(), nowTime-lastTime);
		}
		//exportGraphDotFile();

		runtime.scheduler = runtime.builder.createScheduler(runtime);
		runtime.scheduler.startup();
		logger.info("{} ms duration {} ms finished graph startup", nowTime = System.currentTimeMillis(), nowTime-lastTime);
		lastTime = nowTime;

		return runtime;
	}


}
