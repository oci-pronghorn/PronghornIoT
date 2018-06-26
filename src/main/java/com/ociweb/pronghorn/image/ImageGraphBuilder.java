package com.ociweb.pronghorn.image;

import com.ociweb.pronghorn.image.schema.CalibrationStatusSchema;
import com.ociweb.pronghorn.image.schema.LocationModeSchema;
import com.ociweb.pronghorn.image.schema.ImageSchema;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeConfig;
import com.ociweb.pronghorn.pipe.RawDataSchema;
import com.ociweb.pronghorn.stage.file.FileBlobReadStage;
import com.ociweb.pronghorn.stage.file.FileBlobWriteStage;
import com.ociweb.pronghorn.stage.math.HistogramSchema;
import com.ociweb.pronghorn.stage.math.HistogramSelectPeakStage;
import com.ociweb.pronghorn.stage.math.HistogramSumStage;
import com.ociweb.pronghorn.stage.math.ProbabilitySchema;
import com.ociweb.pronghorn.stage.route.RawDataJoinerStage;
import com.ociweb.pronghorn.stage.route.RawDataSplitterStage;
import com.ociweb.pronghorn.stage.route.ReplicatorStage;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;
import com.ociweb.pronghorn.stage.test.PipeCleanerStage;
import com.ociweb.pronghorn.stage.test.PipeNoOp;

public class ImageGraphBuilder {

	private static final int downscaleSize[] = new int[] { 256, 144 }; //factor of 5

	public static void buildLocationDetectionGraph(GraphManager gm, 
			String loadFilePath, String saveFilePath,
			Pipe<ImageSchema> imagePipe, //input pipe for the raw image data
			Pipe<LocationModeSchema> modeSelectionPipe, //input pipe to turn on learning mode or cancel learning mode.
			Pipe<ProbabilitySchema> probLocation, //output pipe sending probable locations
			Pipe<CalibrationStatusSchema> calibrationDone //output pipe sending training is complete
			) {
			
							
		
		if (null == calibrationDone) {					
			calibrationDone = CalibrationStatusSchema.instance.newPipe(8, 0);
			PipeCleanerStage.newInstance(gm, calibrationDone);
		}
		
		
		if (null == probLocation) {					
			probLocation = ProbabilitySchema.instance.newPipe(8, 10);
			PipeCleanerStage.newInstance(gm, probLocation);
		}
		
		//////////////////////////////////
		//Pipe definitions
		/////////////////////////////////
		
		Pipe<RawDataSchema> loadDataRaw   = RawDataSchema.instance.newPipe(4, 1<<10);
		Pipe<RawDataSchema> loadDataRed   = RawDataSchema.instance.newPipe(4, 1<<10);
		Pipe<RawDataSchema> loadDataGreen = RawDataSchema.instance.newPipe(4, 1<<10);
		Pipe<RawDataSchema> loadDataBlue  = RawDataSchema.instance.newPipe(4, 1<<10);
		Pipe<RawDataSchema> loadDataMono  = RawDataSchema.instance.newPipe(4, 1<<10);
		
	
		Pipe<RawDataSchema> saveDataRaw   = RawDataSchema.instance.newPipe(4, 1<<10);
		Pipe<RawDataSchema> saveDataRed   = RawDataSchema.instance.newPipe(4, 1<<10);
		Pipe<RawDataSchema> saveDataGreen = RawDataSchema.instance.newPipe(4, 1<<10);
		Pipe<RawDataSchema> saveDataBlue  = RawDataSchema.instance.newPipe(4, 1<<10);
		Pipe<RawDataSchema> saveDataMono  = RawDataSchema.instance.newPipe(4, 1<<10);

		
		Pipe<ImageSchema> imageR = ImageSchema.instance.newPipe(300, 1<<9); 
		Pipe<ImageSchema> imageG = ImageSchema.instance.newPipe(300, 1<<9);
		Pipe<ImageSchema> imageB = ImageSchema.instance.newPipe(300, 1<<9);
		Pipe<ImageSchema> imageM = ImageSchema.instance.newPipe(300, 1<<9);

		
		Pipe<HistogramSchema> histR = HistogramSchema.instance.newPipe(4, 1<<12);
		Pipe<HistogramSchema> histG = HistogramSchema.instance.newPipe(4, 1<<12);
		Pipe<HistogramSchema> histB = HistogramSchema.instance.newPipe(4, 1<<12);
		Pipe<HistogramSchema> histM = HistogramSchema.instance.newPipe(4, 1<<12);
		
		Pipe<HistogramSchema> histSum = HistogramSchema.instance.newPipe(4, 1<<12);		

		
		Pipe<CalibrationStatusSchema> calibrationDoneRoot = PipeConfig.pipe(calibrationDone.config().shrink2x());			
		
		Pipe<CalibrationStatusSchema> calibrationDoneAckR = PipeConfig.pipe(calibrationDone.config());
		Pipe<CalibrationStatusSchema> calibrationDoneAckG = PipeConfig.pipe(calibrationDone.config());
		Pipe<CalibrationStatusSchema> calibrationDoneAckB = PipeConfig.pipe(calibrationDone.config());
		Pipe<CalibrationStatusSchema> calibrationDoneAckM = PipeConfig.pipe(calibrationDone.config());
		
		
		//build an empty selector if one is not provided
		if (null == modeSelectionPipe) {
			modeSelectionPipe = LocationModeSchema.instance.newPipe(6,0);
			PipeNoOp.newInstance(gm, modeSelectionPipe);
		}
		
		PipeConfig<LocationModeSchema> msConfig = modeSelectionPipe.config().grow2x();		
		Pipe<LocationModeSchema> modeSelectionR = PipeConfig.pipe(msConfig);
		Pipe<LocationModeSchema> modeSelectionG = PipeConfig.pipe(msConfig);
		Pipe<LocationModeSchema> modeSelectionB = PipeConfig.pipe(msConfig);
		Pipe<LocationModeSchema> modeSelectionM = PipeConfig.pipe(msConfig);		
	    
	    Pipe<CalibrationStatusSchema> calibrationDoneR = CalibrationStatusSchema.instance.newPipe(6, 0);
	    Pipe<CalibrationStatusSchema> calibrationDoneG = CalibrationStatusSchema.instance.newPipe(6, 0); 
	    Pipe<CalibrationStatusSchema> calibrationDoneB = CalibrationStatusSchema.instance.newPipe(6, 0);
	    Pipe<CalibrationStatusSchema> calibrationDoneM = CalibrationStatusSchema.instance.newPipe(6, 0); 
		////////////////////////////////////////
		//Stage definitions
		////////////////////////////////////////
		
		System.out.println("Building downscaling stage...");

		ImageDownscaleStage.newInstance(gm, imagePipe, new Pipe[] {imageR, imageG, imageB, imageM}, downscaleSize[0], downscaleSize[1] ) ;
	    
		//data is only read once on startup
		FileBlobReadStage.newInstance(gm, loadDataRaw, loadFilePath, false);		
		
		RawDataSplitterStage.newInstance(gm, loadDataRaw,
				                    loadDataRed, loadDataGreen, loadDataBlue, loadDataMono);

		
		ReplicatorStage.newInstance(gm, modeSelectionPipe, modeSelectionR, modeSelectionG, modeSelectionB, modeSelectionM);
				
		
		ReplicatorStage.newInstance(gm, calibrationDoneRoot, calibrationDone, calibrationDoneAckR, calibrationDoneAckG, calibrationDoneAckB, calibrationDoneAckM );
		
	    
	    CalibrationCyclicBarierStage.newInstance(gm, 
	    					calibrationDoneRoot,
	    		            calibrationDoneR, calibrationDoneG, calibrationDoneB, calibrationDoneM);
	    
	    //modeSelectionPipe
		MapImageStage.newInstance(gm, imageR, modeSelectionR, histR, calibrationDoneAckR, calibrationDoneR, loadDataRed,   saveDataRed);
		MapImageStage.newInstance(gm, imageG, modeSelectionG, histG, calibrationDoneAckG, calibrationDoneG, loadDataGreen, saveDataGreen);
		MapImageStage.newInstance(gm, imageB, modeSelectionB, histB, calibrationDoneAckB, calibrationDoneB, loadDataBlue,  saveDataBlue);
		MapImageStage.newInstance(gm, imageM, modeSelectionM, histM, calibrationDoneAckM, calibrationDoneM, loadDataMono,  saveDataMono);
		
		
		HistogramSumStage.newInstance(gm, histSum, histR, histG, histB, histM);
		HistogramSelectPeakStage.newInstance(gm, histSum, probLocation );

		RawDataJoinerStage.newInstance(gm, saveDataRaw, 
				          saveDataRed, saveDataGreen, saveDataBlue, saveDataMono);

		FileBlobWriteStage.newInstance(gm, saveDataRaw, saveFilePath);

	}

	
	
}
