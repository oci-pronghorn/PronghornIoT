package com.ociweb.pronghorn.iot.i2c.impl;

import com.ociweb.pronghorn.iot.i2c.I2CBacking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Native I2C backing implementation for a GrovePi using ioctl.h.
 *
 * @author Brandon Sanders [brandon@alicorn.io]
 */
public class I2CNativeLinuxBacking implements I2CBacking {

    private static final Logger logger = LoggerFactory.getLogger(I2CNativeLinuxBacking.class);

    //Highest value for an I2C address. TODO: Is this the right value?
    private static final int I2C_MAX_ADDRESSES = 0x77;

    //Native C library.
    private static final UnixIoctlLib c = UnixIoctlLib.instance;

    //Native I2C file handle.
    private int i2cFile = -1;

    //Most recent address we've handled in order to restrict duplicat IOCTL calls.
    private byte lastAddress = (byte) -127;

    /**
     * Configures I2C to communicate with the specified byte address.
     *
     * @param address Byte address of the I2C device to configure for.
     */
    private boolean ensureI2CDevice(byte address) {
        if (address > 0 && address <= I2C_MAX_ADDRESSES && lastAddress != address) {
            /**
             * IOCTL will return -1 if it fails for any reason.
             */
            if (c.ioctl(i2cFile, UnixIoctlLib.I2C_SLAVE_FORCE, address) < 0) {
                throw new RuntimeException("Could not configure IOCTL for I2C device at 0x" + Integer.toHexString(address));
            } else {
                lastAddress = address;
                logger.debug("IOCTL configured for I2C device at 0x" + Integer.toHexString(address));
                return true;
            }
        } else if (address < 0 || address > I2C_MAX_ADDRESSES) {
            throw new RuntimeException("I2C Device 0x" + Integer.toHexString(address) + " is outside of the possible I2C address range.");
        }

        return true;
    }

    public I2CNativeLinuxBacking() {
        //Get the I2C file.
        i2cFile = c.open("/dev/i2c-1", UnixIoctlLib.O_RDWR);

        //Make sure it worked....
        if (i2cFile < 0) {
            throw new RuntimeException("Could not open /dev/i2c-1.");
        } else {
            logger.info("Successfully opened /dev/i2c-1.");
        }

        //Close the file when the application shuts down.
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                if (i2cFile >= 0) {
                    c.close(i2cFile);
                }
            }
        });
    }

    @Override public byte[] read(byte address, int bufferSize) {
        //Check if we need to load the address into memory.
        if (ensureI2CDevice(address)) {
            byte[] receiving = new byte[bufferSize];
            c.read(i2cFile, receiving, receiving.length);
            return receiving;
        } else {
            return new byte[] {};
        }
    }

    @Override public void write(byte address, byte... message) {
        //Check if we need to load the address into memory.
        if (ensureI2CDevice(address)) {
            c.write(i2cFile, message, message.length);
        }
    }
}