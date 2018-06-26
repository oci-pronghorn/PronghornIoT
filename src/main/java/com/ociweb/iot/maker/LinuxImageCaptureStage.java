package com.ociweb.iot.maker;

import com.ociweb.iot.camera.Camera;
import com.ociweb.iot.camera.ProxyCam;
import com.ociweb.iot.camera.RaspiCam;
import com.ociweb.pronghorn.pipe.PipeWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.pronghorn.image.schema.ImageSchema;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.stage.PronghornStage;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Time-based image listener backing for Linux backed by V4L2.
 *
 * This stage passes image frames line-by-line to its consumers.
 *
 * @author Brandon Sanders [brandon@alicorn.io]
 */
public class LinuxImageCaptureStage extends PronghornStage {

    private static final Logger logger = LoggerFactory.getLogger(LinuxImageCaptureStage.class);

    // Frame buffer state constants.
    private static final int FRAME_EMPTY = -1;
    private static final int FRAME_BUFFERED = -2;

    // Output pipe for image data.
    private final Pipe<ImageSchema> output;

    // Camera system.
    private final int width;
    private final int height;
    private final int rowSize;
    private Camera camera;
    private int cameraFd;

    // Image buffer information; we only process one image at a time.
    private ByteBuffer frameBytes = null;
    private long frameBytesTimestamp = -1;
    private int frameBytesHead = FRAME_EMPTY;

    // Default frame size data.
    public static final int DEFAULT_FRAME_WIDTH = 1280;
    public static final int DEFAULT_FRAME_HEIGHT = 720;
    public static final int DEFAULT_ROW_SIZE = DEFAULT_FRAME_WIDTH * 3;

    // Proxy data directory.
    public static final String PROXY_CAMERA_DIRECTORY = "./src/test/images";

    // Output encoding.
    public static final byte[] OUTPUT_ENCODING = "RGB24".getBytes(StandardCharsets.US_ASCII);

    /**
     * Creates a new image listener stage.
     *
     * @param graphManager Graph manager this stage is a part of.
     * @param output Output pipe which images are published to.
     * @param triggerRateMicroseconds Interval in microseconds that this stage will run.
     * @param width Width of images captures from the camera.
     * @param height Height of images captured from the camera.
     */
    public LinuxImageCaptureStage(GraphManager graphManager, Pipe<ImageSchema> output, int triggerRateMicroseconds, int width, int height) {
        this(graphManager, output, triggerRateMicroseconds, width, height, null);
    }

    /**
     * Creates a new image listener stage in test mode that uses a proxy camera.
     *
     * @param graphManager Graph manager this stage is a part of.
     * @param output Output pipe which images are published to.
     * @param triggerRateMicroseconds Interval in microseconds that this stage will run.
     * @param width Width of images captures from the camera.
     * @param height Height of images captured from the camera.
     * @param imageSource Directory or file to read images from using a proxy camera.
     */
    public LinuxImageCaptureStage(GraphManager graphManager, Pipe<ImageSchema> output, int triggerRateMicroseconds, int width, int height, Path imageSource) {
        super(graphManager, NONE, output);

        // Attach to our output pipe.
        this.output = output;

        // Add this listener to the graph.
        GraphManager.addNota(graphManager, GraphManager.SCHEDULE_RATE, triggerRateMicroseconds * 1_000L, this);

        // Configure height data.
        this.width = width;
        this.height = height;
        this.rowSize = width * 3;

        // Configure test camera data if provided.
        if (imageSource != null) {
            this.camera = new ProxyCam();
            this.cameraFd = this.camera.open(imageSource.toString(), width, height);
        }
        
        if (output.maxVarLen<this.rowSize) {
        	throw new UnsupportedOperationException("Input pipe must accept blocks as large as "+rowSize+" but it only takes "+output.maxVarLen);
        }

        GraphManager.addNota(graphManager, GraphManager.PRODUCER, GraphManager.PRODUCER, this);
        
    }

    @Override
    public void startup() {

        // Automatically detect a camera if one is not already set.
        if (camera == null) {

            // Get a file for the default camera device.
            File cameraFile = Paths.get(RaspiCam.DEFAULT_CAMERA_DEVICE).toFile();

            // Open /dev/video0 on Raspberry Pi.
            if (!cameraFile.exists()) {

                // Load V4L2 module.
                try {
                    Runtime.getRuntime().exec("modprobe bcm2835-v4l2").waitFor();
                } catch (IOException | InterruptedException e) {
                    logger.warn("Could not load V4L2 driver via modprobe. Proxy camera will be used.");
                }
            }

            // Open camera interface if the camera is available.
            if (cameraFile.exists()) {
                camera = new RaspiCam();
                cameraFd = camera.open(RaspiCam.DEFAULT_CAMERA_DEVICE, width, height);
                logger.info("Opened camera device {} with FD {}.", RaspiCam.DEFAULT_CAMERA_DEVICE, cameraFd);

                // Otherwise, use a proxy camera.
            } else {
                camera = new ProxyCam();
                cameraFd = camera.open(PROXY_CAMERA_DIRECTORY, width, height);
                logger.info("Opened proxy camera in directory {} with FD {}.", PROXY_CAMERA_DIRECTORY, cameraFd);
            }
        }

        assert cameraFd != -1;

        // Configure byte array for camera frames.
        frameBytes = camera.getFrameBuffer(cameraFd);
    }

    @Override
    public void shutdown() {
		if (Pipe.hasRoomForWrite(output, Pipe.EOF_SIZE)) {
			Pipe.publishEOF(output);
		}

		// Close camera.
        camera.close(cameraFd);
    }

    @Override
    public void run() {

        // Capture a frame if we have no bytes left to transmit.
        if (frameBytesHead == FRAME_EMPTY) {

            // Get the timestamp of the image.
            frameBytesTimestamp = camera.readFrame(cameraFd);

            // If the timestamp was not -1 (valid), we now have a frame buffered.
            if (frameBytesTimestamp != -1) {
                frameBytesHead = FRAME_BUFFERED;
            }
        }

        // Publish a frame start if we have bytes to transmit and room to transmit.
        if (frameBytesHead == FRAME_BUFFERED &&
            PipeWriter.tryWriteFragment(output, ImageSchema.MSG_FRAMESTART_1)) {
            PipeWriter.writeInt(output, ImageSchema.MSG_FRAMESTART_1_FIELD_WIDTH_101, width);
            PipeWriter.writeInt(output, ImageSchema.MSG_FRAMESTART_1_FIELD_HEIGHT_201, height);
            PipeWriter.writeLong(output, ImageSchema.MSG_FRAMESTART_1_FIELD_TIMESTAMP_301, frameBytesTimestamp);
            PipeWriter.writeInt(output, ImageSchema.MSG_FRAMESTART_1_FIELD_FRAMEBYTES_401, frameBytes.capacity());
            PipeWriter.writeInt(output, ImageSchema.MSG_FRAMESTART_1_FIELD_BITSPERPIXEL_501, 24);
            PipeWriter.writeBytes(output, ImageSchema.MSG_FRAMESTART_1_FIELD_ENCODING_601, OUTPUT_ENCODING);
            PipeWriter.publishWrites(output);
            frameBytesHead = 0;
        }

        // Write rows while there are bytes to write and room for those bytes.
        while (frameBytesHead >= 0 &&
               PipeWriter.tryWriteFragment(output, ImageSchema.MSG_FRAMECHUNK_2)) {

            // Write bytes.
            frameBytes.position(frameBytesHead);
            PipeWriter.writeBytes(output, ImageSchema.MSG_FRAMECHUNK_2_FIELD_ROWBYTES_102, frameBytes, rowSize);

            PipeWriter.publishWrites(output);

            // Progress head.
            frameBytesHead += rowSize;

            // If the head exceeds the size of the frame bytes, we're done writing.
            if (frameBytesHead >= frameBytes.capacity()) {
                frameBytesHead = FRAME_EMPTY;
            }
        }
    }
}
