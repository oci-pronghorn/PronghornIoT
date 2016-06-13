package com.ociweb.device.impl.grovepi;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.spi.FileSystemProvider;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Brandon Sanders [brandon@alicorn.io]
 */
public class GrovePiPinManager {

    public final Path[] gpio;
    public final Path[] gpioDirection;
    public final Path[] gpioValue;
    public final Path[] gpioDebugCurrentPinMux;

    public final short[] gpioPinInt;
    public final String[] gpioPinString;
    public FileSystemProvider provider;
    
    private static final Path PATH_GPIO_EXPORT = Paths.get("/sys/class/gpio/export");
    private static final Path PATH_GPIO_UNEXPORT = Paths.get("/sys/class/gpio/unexport");

    public static final byte[] OUT = "out".getBytes();
    public static final byte[] IN = "in".getBytes();
    public static final byte[] DRECTION_HIGH = "out".getBytes();
    public static final byte[] DIRECTION_LOW = "in".getBytes();
    public static final byte[] VALUE_HIGH = "1".getBytes();
    public static final byte[] VALUE_LOW = "0".getBytes();

    public static final ByteBuffer I2C_LOW = ByteBuffer.wrap(VALUE_LOW);
    public static final ByteBuffer I2C_HIGH = ByteBuffer.wrap(VALUE_HIGH);
    public static final ByteBuffer[] BIT_BYTES = new ByteBuffer[]{I2C_LOW, I2C_HIGH};
    public static final int I2C_CLOCK = 1;
    public static final int I2C_DATA = 0;

    private static final Set<OpenOption> readOptions = new HashSet<OpenOption>();
    private static final Set<OpenOption> i2cOptions = new HashSet<OpenOption>();

    private static ByteBuffer[] readBitBuffer;

    static {
        i2cOptions.add(StandardOpenOption.READ);
        i2cOptions.add(StandardOpenOption.WRITE);

        readOptions.add(StandardOpenOption.READ);
        readOptions.add(StandardOpenOption.SYNC);

        int b = GrovePiConstants.GPIO_PINS.length;
        readBitBuffer = new ByteBuffer[b];
        while (--b >= 0)
            readBitBuffer[b] = ByteBuffer.allocate(1);
    }

    public GrovePiPinManager(final short[] pins) {

        gpioPinInt = pins;
        gpio = new Path[pins.length];
        gpioDirection = new Path[pins.length];
        gpioValue = new Path[pins.length];
        gpioPinString = new String[pins.length];
        gpioDebugCurrentPinMux = new Path[pins.length];

        final FileSystem fileSystem = FileSystems.getDefault();
        provider = fileSystem.provider();

        int i = pins.length;
        final StringBuilder sb = new StringBuilder();

        sb.append("/sys/class/gpio/gpio");

        final int baseLen = sb.length();
        while (--i >= 0) {
            if (pins[i] >= 0) {
                gpioPinString[i] = Integer.toString(pins[i]);

                gpioDebugCurrentPinMux[i] = fileSystem.getPath("/sys/kernel/debug/gpio_debug/gpio" + gpioPinString[i] + "/current_pinmux");

                sb.setLength(baseLen);
                sb.append(gpioPinString[i]);
                gpio[i] = fileSystem.getPath(sb.toString());

                final int withIdLen = sb.length();

                sb.append("/direction");
                gpioDirection[i] = fileSystem.getPath(sb.toString());

                sb.setLength(withIdLen);

                sb.append("/value");
                gpioValue[i] = fileSystem.getPath(sb.toString());
            }
        }
    }

    public void setDirectionLow(final int i) {
        if (null != gpioDirection[i]) try {
            Files.write(gpioDirection[i], DIRECTION_LOW);
        }
        catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void setDirectionHigh(final int i) {
        if (null != gpioDirection[i]) try {
            Files.write(gpioDirection[i], DRECTION_HIGH);
        }
        catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void setDirectionIn(final int i) {
        try {
            Files.write(gpioDirection[i], IN);
        }
        catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void setDirectionOut(final int i) {
        try {
            Files.write(gpioDirection[i], OUT);
        }
        catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void setValueHigh(final int i) {
        try {
            Files.write(gpioValue[i], VALUE_HIGH);
        }
        catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void setValueLow(final int i) {
        try {
            Files.write(gpioValue[i], VALUE_LOW);
        }
        catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void ensureDevice(final int i) {

        if ((null != gpio[i]) && !gpio[i].toFile().exists()) try {
            Files.write(PATH_GPIO_EXPORT, gpioPinString[i].getBytes());
        }
        catch (final IOException e) {
            throw new RuntimeException(e);
        }

    }

    public void removeDevice(final int i) {
        if ((null != gpio[i]) && !gpio[i].toFile().exists()) try {
            Files.write(PATH_GPIO_UNEXPORT, gpioPinString[i].getBytes());
        }
        catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void writeValue(final int port, final ByteBuffer data, final GrovePiPinManager d) {
        try {
            final SeekableByteChannel channel = d.provider.newByteChannel(d.gpioValue[port],
                                                                          i2cOptions);
            do channel.write(data);
            while (data.hasRemaining()); //Caution, TODO: this is blocking.
            data.flip();
            channel.close();
        }
        catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    public static int readBit(final int idx) {
        GrovePiGPIO.gpioLinuxPins.removeDevice(idx);
        GrovePiGPIO.gpioLinuxPins.ensureDevice(idx);

        try {
            final ByteBuffer buffer = readBitBuffer[idx];
            final SeekableByteChannel bc = GrovePiGPIO.gpioLinuxPins.provider.newByteChannel(GrovePiGPIO.gpioLinuxPins.gpioValue[idx],
                                                                                             readOptions);
            buffer.clear();
            while (bc.read(buffer) == 0) {}//only need 1
            bc.close();
            buffer.flip();
            return buffer.get() & 0x1;
        }
        catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

	public static void writeBit(int connector, int value, final GrovePiPinManager d) {
		writeValue(connector, BIT_BYTES[value],d);
		
	}
}
