package com.ociweb.iot.grove.oled.oled2;

import com.ociweb.gl.api.transducer.StartupListenerTransducer;
import com.ociweb.iot.grove.oled.BinaryOLED;
import com.ociweb.iot.maker.FogCommandChannel;
import com.ociweb.iot.maker.IODeviceTransducer;
import com.ociweb.iot.maker.image.*;
import com.ociweb.pronghorn.iot.schema.I2CCommandSchema;
import com.ociweb.pronghorn.pipe.DataOutputBlobWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.ociweb.iot.grove.oled.oled2.OLEDCommands.*;

public class OLED96x96Transducer implements IODeviceTransducer, StartupListenerTransducer, FogBmpDisplayable {
    private Logger logger = LoggerFactory.getLogger((BinaryOLED.class));
    private final FogCommandChannel ch;
    private final int i2cAddress = 0x3c;
    private final int batchLimit = 64;

    public OLED96x96Transducer(FogCommandChannel ch) {
        this.ch = ch;
        this.ch.ensureI2CWriting(10000 * 4, batchLimit);
    }

    @Override
    public void startup() {
        init();
        setOrientation(OLEDOrientation.horizontal);
        //clearDisplay();
        turnScreenOn();
    }

    private boolean init() {
        if (!ch.i2cIsReady(1)) return false;
        DataOutputBlobWriter<I2CCommandSchema> writer = ch.i2cCommandOpen(i2cAddress);
        queueCmd(writer, DISPLAY_OFF);
        queueCmd(writer, SET_D_CLOCK);
        queueCmd(writer, 0x50);
        queueCmd(writer, SET_ROW_ADDRESS);
        queueCmd(writer, SET_CONTRAST);
        queueCmd(writer, 0x70);
        queueCmd(writer, REMAP_SGMT);
        queueCmd(writer, ENTIRE_DISPLAY_ON);
        queueCmd(writer, NORMAL_DISPLAY);
        queueCmd(writer, SET_EXT_VPP);
        queueCmd(writer, 0x80);
        queueCmd(writer, SET_COMMON_SCAN_DIR);
        queueCmd(writer, SET_PHASE_LENGTH);
        queueCmd(writer, 0x1F);
        queueCmd(writer, SET_VCOMH_VOLTAGE);
        queueCmd(writer, 0x20);
        queueCmd(writer, DISPLAY_ON);
        ch.i2cCommandClose();
        ch.i2cFlushBatch();
        return true;
    }

    private boolean turnScreenOn() {
        if (!ch.i2cIsReady(1)) return false;
        DataOutputBlobWriter<I2CCommandSchema> writer = ch.i2cCommandOpen(i2cAddress);
        queueCmd(writer, DISPLAY_ON);
        ch.i2cCommandClose();
        ch.i2cFlushBatch();
        return true;
    }

    public boolean clearDisplay() {
        return injectInitScreen();
    }

    public boolean setContrast(byte contrast) {
        if (!ch.i2cIsReady(1)) return false;
        DataOutputBlobWriter<I2CCommandSchema> writer = ch.i2cCommandOpen(i2cAddress);
        queueCmd(writer, SET_CONTRAST);
        queueCmd(writer, contrast);
        ch.i2cCommandClose();
        ch.i2cFlushBatch();
        return true;
    }

    public boolean setOrientation(OLEDOrientation orientation) {
        if (!ch.i2cIsReady(1)) return false;
        DataOutputBlobWriter<I2CCommandSchema> writer = ch.i2cCommandOpen(i2cAddress);
        queueCmd(writer, REMAP_SGMT);
        queueCmd(writer, orientation.COMMAND);
        ch.i2cCommandClose();
        ch.i2cFlushBatch();
        return true;
    }

    public boolean setScrollActivated(boolean activated) {
        if (!ch.i2cIsReady(1)) return false;
        DataOutputBlobWriter<I2CCommandSchema> writer = ch.i2cCommandOpen(i2cAddress);
        queueCmd(writer, activated ? 0x2F : 0x2E);
        ch.i2cCommandClose();
        ch.i2cFlushBatch();
        return true;
    }

    public boolean setHorizontalScrollProperties(OLEDScrollDirection direction, int startRow, int endRow, int startColumn, int endColumn, OLEDScrollSpeed speed) {
        if (!ch.i2cIsReady(1)) return false;
        DataOutputBlobWriter<I2CCommandSchema> writer = ch.i2cCommandOpen(i2cAddress);
        queueCmd(writer, direction.COMMAND);
        queueCmd(writer, 0x00);
        queueCmd(writer, startRow);
        queueCmd(writer, speed.COMMAND);
        queueCmd(writer, endRow);
        queueCmd(writer, startColumn+8);
        queueCmd(writer, endColumn+8);
        queueCmd(writer, 0x00);
        ch.i2cCommandClose();
        ch.i2cFlushBatch();
        return true;
    }

    public boolean setPresentation(OLEDScreenPresentation presentation) {
        if (!ch.i2cIsReady(1)) return false;
        DataOutputBlobWriter<I2CCommandSchema> writer = ch.i2cCommandOpen(i2cAddress);
        queueCmd(writer, presentation.COMMAND);
        ch.i2cCommandClose();
        ch.i2cFlushBatch();
        return true;
    }

    @Override
    public FogBitmapLayout newBmpLayout() {
        FogBitmapLayout bmpLayout = new FogBitmapLayout(FogColorSpace.gray);
        bmpLayout.setComponentDepth((byte) 4);
        bmpLayout.setWidth(96);
        bmpLayout.setHeight(96);
        return bmpLayout;
    }

    @Override
    public FogBitmap newEmptyBmp() {
        return new FogBitmap(newBmpLayout());
    }

    @Override
    public FogPixelScanner newPreferredBmpScanner(FogBitmap bmp) { return new FogPixelProgressiveScanner(bmp); }

    @Override
    public boolean display(FogPixelScanner scanner) {
        return true;
    }

    private void queueData(DataOutputBlobWriter writer, int data) {
        writer.write(DATA_MODE);
        writer.write(data & 0xFF);
    }

    private void queueCmd(DataOutputBlobWriter writer, int data) {
        writer.write(COMMAND_MODE);
        writer.write(data & 0xFF);
    }

    private boolean injectInitScreen() {

        System.out.print("injectInitScreen2\n");
        setOrientation(OLEDOrientation.horizontal);

        int Row = 0;
        int column_l = 0x00;
        int column_h = 0x11; // 17

        for(int i=0;i<(96*96/8);i++) //1152
        {
            if (((i + batchLimit) % batchLimit) == 0) {
                if (!ch.i2cIsReady(1)) {
                    System.out.print("\nI2C is not ready\n");
                    return false;
                }
            }

            int tmp = 0x00;
            for(int b = 0; b < 8; b++)
            {
                int bits = SeeedLogo[i];
                tmp |= ( ( bits >> ( 7 - b ) ) & 0x01 ) << b;
            }
            //tmp = 0xFF;

            DataOutputBlobWriter<I2CCommandSchema> writer = ch.i2cCommandOpen(i2cAddress);
            queueCmd(writer, SET_ROW_BASE_BYTE + Row);
            queueCmd(writer, column_l);
            queueCmd(writer, column_h);
            queueData(writer, tmp);
            ch.i2cCommandClose();

            Row++;

            if(Row >= 12){
                Row = 0;
                column_l++;
                //logger.info("End Row {}", column_l);
                if(column_l >= 16){
                    column_l = 0x00;
                    column_h += 0x01;
                    //logger.info("End Col {}", column_h);
                }
            }

            if (((i + batchLimit) % batchLimit) == batchLimit - 1) {
                ch.i2cFlushBatch();
            }

        }
        ch.i2cFlushBatch();
        return true;
    }

    final int[] SeeedLogo = new int[] { // 16 * 72 == 1152,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x20, 0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x60, 0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xC0, 0x06, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0xC0, 0x07, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x01, 0xC0, 0x07, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x03, 0x80, 0x03, 0x80, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x03, 0x80, 0x03, 0x80,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x07, 0x80, 0x03, 0xC0, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x07, 0x80, 0x01, 0xC0, 0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x20,
            0x07, 0x80, 0x01, 0xE0, 0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x20, 0x0F, 0x80, 0x01, 0xE0,
            0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x30, 0x0F, 0x00, 0x01, 0xE0, 0x08, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x30, 0x0F, 0x00, 0x01, 0xE0, 0x18, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x30,
            0x0F, 0x00, 0x01, 0xE0, 0x18, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x30, 0x0F, 0x00, 0x01, 0xE0,
            0x18, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x38, 0x0F, 0x00, 0x01, 0xE0, 0x18, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x38, 0x0F, 0x00, 0x01, 0xE0, 0x38, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x38,
            0x0F, 0x80, 0x01, 0xE0, 0x38, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x3C, 0x0F, 0x80, 0x01, 0xE0,
            0x78, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x3E, 0x0F, 0x80, 0x03, 0xE0, 0x78, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x1E, 0x07, 0x80, 0x03, 0xE0, 0xF8, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x1E,
            0x07, 0x80, 0x03, 0xE0, 0xF0, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x1F, 0x07, 0x80, 0x03, 0xC1,
            0xF0, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0F, 0x87, 0xC0, 0x07, 0xC1, 0xF0, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x0F, 0x83, 0xC0, 0x07, 0x83, 0xE0, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0F,
            0xC3, 0xC0, 0x07, 0x87, 0xE0, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x07, 0xE1, 0xE0, 0x07, 0x0F,
            0xC0, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x03, 0xF0, 0xE0, 0x0F, 0x0F, 0x80, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x01, 0xF8, 0xF0, 0x0E, 0x1F, 0x80, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01,
            0xF8, 0x70, 0x1C, 0x3F, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xFC, 0x30, 0x18, 0x7E,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x7F, 0x18, 0x30, 0xFC, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x1F, 0x88, 0x21, 0xF0, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x0F, 0xC4, 0x47, 0xE0, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x03, 0xE0, 0x0F, 0x80,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xF8, 0x3E, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x0E, 0xE0, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x02, 0x00, 0x00, 0x00, 0x00, 0x6C, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x02, 0x00, 0x06, 0x00, 0x00, 0x6C, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x02, 0x00, 0x06,
            0x00, 0x00, 0x60, 0x00, 0x7E, 0x3F, 0x0F, 0xC3, 0xF0, 0xFA, 0x0F, 0xDF, 0xE1, 0x9F, 0xEC, 0x7E,
            0xE6, 0x73, 0x9C, 0xE7, 0x39, 0xCE, 0x1C, 0xDF, 0xE1, 0xB9, 0xEC, 0xE7, 0xE0, 0x61, 0xD8, 0x66,
            0x1B, 0x86, 0x1C, 0x06, 0x61, 0xB0, 0x6D, 0xC3, 0x7C, 0x7F, 0xFF, 0xFF, 0xFF, 0x06, 0x0F, 0x86,
            0x61, 0xB0, 0x6D, 0x83, 0x3E, 0x7F, 0xFF, 0xFF, 0xFF, 0x06, 0x07, 0xC6, 0x61, 0xB0, 0x6D, 0x83,
            0xC3, 0x61, 0x18, 0x46, 0x03, 0x86, 0x18, 0x66, 0x61, 0xB0, 0x6D, 0xC3, 0xFE, 0x7F, 0x9F, 0xE7,
            0xF9, 0xFE, 0x1F, 0xE6, 0x3F, 0x9F, 0xEC, 0xFE, 0x7E, 0x3F, 0x0F, 0xC3, 0xF0, 0xFA, 0x0F, 0xC6,
            0x3F, 0x9F, 0xEC, 0x7E, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x7C, 0x00,
            0x00, 0x20, 0x82, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x44, 0x00, 0x00, 0x20, 0x82, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x6C, 0xF3, 0xCF, 0x70, 0x9E, 0x79, 0xE7, 0x80, 0x00, 0x00,
            0x00, 0x00, 0x7D, 0x9E, 0x68, 0x20, 0xB2, 0xC8, 0x64, 0x00, 0x00, 0x00, 0x00, 0x00, 0x47, 0x9E,
            0x6F, 0x20, 0xB2, 0xF9, 0xE7, 0x80, 0x00, 0x00, 0x00, 0x00, 0x46, 0x9A, 0x61, 0x20, 0xB2, 0xCB,
            0x60, 0x80, 0x00, 0x00, 0x00, 0x00, 0x7C, 0xF3, 0xCF, 0x30, 0x9E, 0x79, 0xE7, 0x90, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x10, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x7C, 0x02, 0x00, 0x00, 0x82, 0x60, 0x00, 0x00,
            0xF8, 0x00, 0x00, 0x40, 0x40, 0x02, 0x00, 0x00, 0x83, 0x60, 0x00, 0x00, 0x8C, 0x00, 0x00, 0x40,
            0x60, 0xB7, 0x79, 0xE7, 0x81, 0xC7, 0x92, 0x70, 0x89, 0xE7, 0x9E, 0x78, 0x7C, 0xE2, 0xC9, 0x2C,
            0x81, 0xCC, 0xD2, 0x40, 0xFB, 0x21, 0xB2, 0x48, 0x40, 0x62, 0xF9, 0x2C, 0x80, 0x8C, 0xD2, 0x40,
            0x8B, 0xE7, 0xB0, 0x48, 0x40, 0xE2, 0xC9, 0x2C, 0x80, 0x84, 0xD2, 0x40, 0x8B, 0x2D, 0x92, 0x48,
            0x7D, 0xB3, 0x79, 0x27, 0x80, 0x87, 0x9E, 0x40, 0x8D, 0xE7, 0x9E, 0x48, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
    };
}
