package com.ociweb.pronghorn.iot.schema;

import com.ociweb.pronghorn.pipe.*;

import java.nio.ByteBuffer;

public class ImageSchema extends MessageSchema<ImageSchema> {

    public final static FieldReferenceOffsetManager FROM = new FieldReferenceOffsetManager(
            new int[]{0xc0400002,0xb8000000,0xc0200002},
            (short)0,
            new String[]{"ChunkedStream","ByteArray",null},
            new long[]{1, 2, 0},
            new String[]{"global",null,null},
            "ImageSchema.xml",
            new long[]{2, 2, 0},
            new int[]{2, 2, 0});


    protected ImageSchema() {
        super(FROM);
    }

    public static final ImageSchema instance = new ImageSchema();

    public static final int MSG_CHUNKEDSTREAM_1 = 0x00000000;
    public static final int MSG_CHUNKEDSTREAM_1_FIELD_BYTEARRAY_2 = 0x01c00001;


    public static void consume(Pipe<ImageSchema> input) {
        while (PipeReader.tryReadFragment(input)) {
            int msgIdx = PipeReader.getMsgIdx(input);
            switch(msgIdx) {
                case MSG_CHUNKEDSTREAM_1:
                    consumeChunkedStream(input);
                    break;
                case -1:
                    //requestShutdown();
                    break;
            }
            PipeReader.releaseReadLock(input);
        }
    }

    public static void consumeChunkedStream(Pipe<ImageSchema> input) {
        ByteBuffer fieldByteArray = PipeReader.readBytes(input, MSG_CHUNKEDSTREAM_1_FIELD_BYTEARRAY_2, ByteBuffer.allocate(
                PipeReader.readBytesLength(input, MSG_CHUNKEDSTREAM_1_FIELD_BYTEARRAY_2)));
    }

    public static void publishChunkedStream(Pipe<ImageSchema> output, byte[] fieldByteArrayBacking, int fieldByteArrayPosition, int fieldByteArrayLength) {
        PipeWriter.presumeWriteFragment(output, MSG_CHUNKEDSTREAM_1);
        PipeWriter.writeBytes(output,MSG_CHUNKEDSTREAM_1_FIELD_BYTEARRAY_2, fieldByteArrayBacking, fieldByteArrayPosition, fieldByteArrayLength);
        PipeWriter.publishWrites(output);
    }
}
