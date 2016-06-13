package com.ociweb.pronghorn.iot.i2c;

/**
 * Represents a generic backing for the GrovePi's I2C lines.
 *
 * TODO: This interface and all of the Grove Pi I2C code can be generified
 *       to work with any device and reduce code duplication.
 *
 * @author Brandon Sanders [brandon@alicorn.io]
 */
public interface I2CBacking {

    /**
     * Reads a message from the I2C device at the specified address.
     *
     * @param address Address of the I2C device to read, e.g., "0x32" for a
     *                GrovePi's LCD RGB backlight.
     * @param bufferSize Size of the byte buffer to read into (and return).
     *
     * @return Data received from the I2C device, or an empty array if no
     *         data was received.
     */
    byte[] read(byte address, int bufferSize);

    /**
     * Writes a message to an I2C device at the specified address.
     *
     * @param address Address of the I2C device to write, e.g., "0x32" for a
     *                GrovePi's LCD RGB backlight.
     * @param message Array of bytes to write to the I2C device.
     */
    void write(byte address, byte... message);
}
