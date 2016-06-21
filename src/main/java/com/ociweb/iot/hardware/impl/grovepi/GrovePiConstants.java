package com.ociweb.iot.hardware.impl.grovepi;

import com.ociweb.iot.grove.GroveTwig;
import com.ociweb.iot.hardware.HardConnection;
import com.ociweb.iot.hardware.HardConnection.ConnectionType;

/**
 * TODO: GPIO 2 is SDA on an RPi
 *       GPIO 3 is SCL on an RPi
 *
 * TODO: Only pins we're actually using/care about are 2 and 3.
 *
 * @author Brandon Sanders [brandon@alicorn.io]
 */
public class GrovePiConstants {
    
    public static final short[] GPIO_PINS = new short[] {
        2,
        3,
//        5,
        7,
        11,
        12,
        13,
        15,
        16,
        18,
        22,
        29,
        31,
        32,
        33,
        35,
        36,
        37,
        38,
        40
    };

    public static final short[] GPIO_PIN_MODES = new short[] {
        2,
        3,
//        5,
        7,
        11,
        12,
        13,
        15,
        16,
        18,
        22,
        29,
        31,
        32,
        33,
        35,
        36,
        37,
        38,
        40
    };

    public static final short[] OUTPUT_ENABLE = new short[] {
        2,
        3,
//        5,
        7,
        11,
        12,
        13,
        15,
        16,
        18,
        22,
        29,
        31,
        32,
        33,
        35,
        36,
        37,
        38,
        40
    };
    
    public static final short[] PULL_UP_ENABLE = new short[] {
        2,
        3,
//        5,
        7,
        11,
        12,
        13,
        15,
        16,
        18,
        22,
        29,
        31,
        32,
        33,
        35,
        36,
        37,
        38,
        40
    };
    
    public static final HardConnection[] i2cPins = new HardConnection[] {
    new HardConnection(GroveTwig.I2C,0,ConnectionType.Grove),
    new HardConnection(GroveTwig.I2C,1,ConnectionType.Grove)};
    
    public final static int DATA_RAW_VOLTAGE = 0;
    public final static int CLOCK_RAW_VOLTAGE = 1;
    public final static int HIGH_LINE_VOLTAGE_MARK = 1 << 8; //This is a number needing 9 or more full bits.
}