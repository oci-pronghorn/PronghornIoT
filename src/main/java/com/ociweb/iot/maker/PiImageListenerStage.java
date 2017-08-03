package com.ociweb.iot.maker;

import com.ociweb.pronghorn.iot.schema.ImageSchema;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.stage.PronghornStage;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;

/**
 * Time-based image listener backing for Raspberry Pi hardware.
 *
 * TODO: Does this stage take pictures and pass a file path
 *       or does it actually convert the pictures to bytes and pass
 *       them on the pipe?
 *
 * TODO: For total integration
 * - Build a new schema. --Done
 * - As user declares things, the system builds dangling pipes.
 *  - The other end of the pipe is not defined yet.
 *  - When you make the stage, the graph will register it.
 *  - If you run the graph, it'll complain because the stage has dangling pipes.
 *  - BuildGraph asks the graph for the dangling pipes by schema.
 *  - Those dangling pipes are then tied into something.
 *  - This design means we need a unique schema for each kind of special resource.
 * - ImageListener registration creates an instance of the reactor stage.
 *  - That stage needs attached to it an image listener pipe.
 *  - When the graph is built, we track all of the listeners and attach the pipe.
 *  - Use the replicator stage to take one pipe (from picture taker stage) and feed it to the listeners if there
 *    are more than one listener.
 *
 * @author Brandon Sanders [brandon@alicorn.io]
 */
public class PiImageListenerStage extends PronghornStage {

    private static final Logger logger = LoggerFactory.getLogger(PiImageListenerStage.class);

    // File name prefix.
    private final String fileNamePrefix = new File("").getAbsolutePath() + File.separator;

    // Output pipe for image data.
    private final Pipe<ImageSchema> output;

    // Image buffer information; we only process one image at a time.
    private byte[] fileNameBytes = null;
    private int fileBytesReadIndex = -1;

    /**
     * Takes a picture from the currently connected Raspberry Pi camera and
     * saves it to a file.
     *
     * TODO: This method is primitive in that it relies on Pi command line tools
     *       and unknown delays in order to capture images. It would be preferable
     *       to leverage the native APIs in the Raspberry Pi, but those are
     *       extremely complex and undocumented...
     *
     * @param fileName Name of the file (without extensions) to save
     *                 the image to.
     *
     * @return The full path name to the created file.
     */
    private String takePicture(String fileName) {
        try {
            Runtime.getRuntime().exec("raspistill --nopreview --timeout 1 --shutter 2500 --width 1280 --height 960 --quality 75 --output " + fileName + ".jpg");
        } catch (IOException e) {
            logger.error("Unable to take picture from Raspberry Pi Camera due to error [{}].", e.getMessage(), e);
        }

        return fileNamePrefix + fileName + ".jpg"; //TODO: rewrite to be GC free
    }

    public PiImageListenerStage(GraphManager graphManager, Pipe<ImageSchema> output, int triggerRateMilliseconds) {
        super(graphManager, NONE, output);

        // Attach to our output pipe.
        this.output = output;

        // Add this listener to the graph.
        GraphManager.addNota(graphManager, GraphManager.SCHEDULE_RATE, triggerRateMilliseconds * 1000000, this);
    }

    @Override
    public void shutdown() {
        // Wait for room on the pipe to write any remaining data.
        Pipe.spinBlockForRoom(output, Pipe.EOF_SIZE);

        // Publish remaining data.
        Pipe.publishEOF(output);
    }

    @Override
    public void run() {

        // Only execute while we have room to write our output.
        if (Pipe.hasRoomForWrite(output)) {

            // Do we have image data? If not, get some!
            if (fileBytesReadIndex == -1) {
                // TODO: rewrite to be GC free
                // TODO: RandomAccessFile may be the best choice once it's available to us...
                // Take a picture and load the byte information.
                fileNameBytes = (takePicture(
                        "Pronghorn-Image-Capture-" + System.currentTimeMillis()) + "\n").getBytes(StandardCharsets.UTF_16);
                fileBytesReadIndex = 0;
            }

            // Load byte buffers from the pipe so we have somewhere to put the image data.
            ByteBuffer[] buffers = Pipe.wrappedWritingBuffers(output);

            // Determine maximum write size.
            final int maximumToWrite = Math.min(buffers[0].remaining(), fileNameBytes.length - fileBytesReadIndex);

            // Fill the buffers as much as possible.
            System.arraycopy(fileNameBytes, fileBytesReadIndex,
                             buffers[0].array(), buffers[0].position(), maximumToWrite);

            // Progress index by the number of bytes written.
            fileBytesReadIndex += maximumToWrite;

            // If the index exceeds our bounds, we're done writing.
            if (fileBytesReadIndex >= fileNameBytes.length) {
                fileBytesReadIndex = -1;
            }

            // Publish our changes.
            final int size = Pipe.addMsgIdx(output, ImageSchema.MSG_CHUNKEDSTREAM_1);
            Pipe.moveBlobPointerAndRecordPosAndLength(maximumToWrite, output);
            Pipe.confirmLowLevelWrite(output, size);
            Pipe.publishWrites(output);
        }
    }
}
