package com.ociweb.pronghorn.iot.i2c.impl;

import com.ociweb.pronghorn.iot.i2c.I2CBacking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.device.config.GroveConnectionConfiguration;

/**
 * Java I2C backing implementation for a Grove using the amazing technique of
 * bit-banging GPIO ports.
 *
 * TODO: Completely untested and mostly unimplemented. Probably needs rewriting in order to work.
 *
 * TODO: One very apparent issue with this backing is that it loiters in the
 *       read/write functions for a long time since it will attempt to
 *       perform the entire operation all at once instead of across multiple
 *       loops (like the Edison's bitbanged implementation).
 *
 * @author Brandon Sanders [brandon@alicorn.io]
 */
public class I2CGroveJavaBacking implements I2CBacking {
    private static final Logger logger = LoggerFactory.getLogger(I2CGroveJavaBacking.class);

    //Nanoseconds to pause between I2C commands.
    private static final int NS_PAUSE = 10 * 1000;

    //GrovePi GPIO connection.
    private final GroveConnectionConfiguration config;

    /**
     * Puts the thread to sleep just long enough for I2C based off of
     * {@link #NS_PAUSE}.
     */
    public void pause() {
        try { Thread.sleep(NS_PAUSE / 1000000, NS_PAUSE % 1000000); }
        catch (InterruptedException e) { throw new RuntimeException(e); }
    }

    /**
     * Writes a bit to this backing's I2C line.
     *
     * @param bit Bit to write.
     */
    private void writeBit(boolean bit) {
        if (bit) config.i2cSetDataHigh();
        else config.i2cSetDataLow();
        config.i2cClockOut();

        config.i2cSetClockHigh();
        config.i2cClockIn();

        while (config.i2cReadClock() == 0) {
            logger.warn("Clock stretching in writeBit...");
        }

        config.i2cClockOut();
        config.i2cSetClockLow();
    }

    /**
     * Writes a byte to this backing's I2C line.
     *
     * @param b Byte to write.
     */
    private void writeByte(int b) {
        for (int bit = 0; bit < 8; bit++) {
            writeBit((b & 0x80) != 0);
            b <<= 1;
        }
    }

    /**
     * Writes the master start bit to the I2C line.
     */
    private void masterStart() {
        config.i2cSetDataLow();

        config.i2cClockOut();
        config.i2cSetClockLow();
    }

    /**
     * Writes the master's message to the I2C line.
     */
    private void masterWrite(byte address, byte... message) {
        writeByte(address);
        logger.info("Starting write to address 0x" + Integer.toHexString(address));

        for (byte b : message) {
            writeByte(b);
            logger.info("Wrote 0x" + Integer.toHexString(b));
        }
    }

    /**
     * Writes the master stop bit to the I2C line.
     */
    private void masterStop() {
        config.i2cSetDataLow();

        config.i2cClockIn();

        while (config.i2cReadClock() == 0) {
            System.out.println("Clock stretching in masterStop...");
        }

        config.i2cSetDataHigh();
    }

    public I2CGroveJavaBacking(GroveConnectionConfiguration config) {
        this.config = config;
    }

    @Override public byte[] read(byte address, int bufferSize) {
        //TODO: Unimplemented.
        throw new RuntimeException("Bit-banged reads aren't supported yet.");
    }

    @Override public void write(byte address, byte... message) {
        masterStart();
        masterWrite(address, message);
        masterStop();
    }
}
