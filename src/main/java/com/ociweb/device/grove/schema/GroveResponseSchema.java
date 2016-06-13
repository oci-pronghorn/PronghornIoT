package com.ociweb.device.grove.schema;

import com.ociweb.pronghorn.pipe.FieldReferenceOffsetManager;
import com.ociweb.pronghorn.pipe.MessageSchema;
public class GroveResponseSchema extends MessageSchema {

    public final static FieldReferenceOffsetManager FROM = new FieldReferenceOffsetManager(
            new int[]{0xc0400002,0x90000000,0xc0200002,0xc0400003,0x80000000,0x80000001,0xc0200003,0xc0400004,0x80000002,0x80000003,0x80000004,0xc0200004,0xc1400005,0x80200005,0x88000006,0x88000007,0x80000008,0xc1200005},
            (short)0,
            new String[]{"Time","Value",null,"DigitalSample","Connector","Value",null,"AnalogSample","Connector","Value","Average",null,"Encoder","Connector","Value","Delta","Speed",null},
            new long[]{10, 11, 0, 20, 21, 22, 0, 30, 31, 32, 33, 0, 70, 71, 72, 73, 74, 0},
            new String[]{"global",null,null,"global",null,null,null,"global",null,null,null,null,"global",null,null,null,null,null},
            "GroveResponse.xml",
            new long[]{2, 2, 0},
            new int[]{2, 2, 0});

    
    public static final GroveResponseSchema instance = new GroveResponseSchema();
    
    public static final int MSG_TIME_10 = 0x00000000;
    public static final int MSG_TIME_10_FIELD_VALUE_11 = 0x00800001;
    public static final int MSG_DIGITALSAMPLE_20 = 0x00000003;
    public static final int MSG_DIGITALSAMPLE_20_FIELD_CONNECTOR_21 = 0x00000001;
    public static final int MSG_DIGITALSAMPLE_20_FIELD_VALUE_22 = 0x00000002;
    public static final int MSG_ANALOGSAMPLE_30 = 0x00000007;
    public static final int MSG_ANALOGSAMPLE_30_FIELD_CONNECTOR_31 = 0x00000001;
    public static final int MSG_ANALOGSAMPLE_30_FIELD_VALUE_32 = 0x00000002;
    public static final int MSG_ANALOGSAMPLE_30_FIELD_AVERAGE_33 = 0x00000003;
    public static final int MSG_ENCODER_70 = 0x0000000C;
    public static final int MSG_ENCODER_70_FIELD_CONNECTOR_71 = 0x00000001;
    public static final int MSG_ENCODER_70_FIELD_VALUE_72 = 0x00400002;
    public static final int MSG_ENCODER_70_FIELD_DELTA_73 = 0x00400003;
    public static final int MSG_ENCODER_70_FIELD_SPEED_74 = 0x00000004;
    
    private GroveResponseSchema() {
        super(FROM);
    }
        
}
