package com.ociweb.device.impl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
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

public class EdisonPinManager {

    public final Path[] gpio; 
    public final Path[] gpioDirection;
    public final SeekableByteChannel[] gpioDirectionChannel;   
    
    
    public final Path[] gpioDebugCurrentPinMux;
    public final Path[] gpioValue;
    public final SeekableByteChannel[] gpioChannel;   
    
    public final short[]    gpioPinInt;
    public final String[] gpioPinString;
    public FileSystemProvider provider;

    
    public static final Path[] PATH_A = new Path[] {
             FileSystems.getDefault().getPath("/sys/bus/iio/devices/iio:device1", "in_voltage0_raw"),
             FileSystems.getDefault().getPath("/sys/bus/iio/devices/iio:device1", "in_voltage1_raw"),
             FileSystems.getDefault().getPath("/sys/bus/iio/devices/iio:device1", "in_voltage2_raw"),
             FileSystems.getDefault().getPath("/sys/bus/iio/devices/iio:device1", "in_voltage3_raw"),
             FileSystems.getDefault().getPath("/sys/bus/iio/devices/iio:device1", "in_voltage4_raw"), //for I2C
             FileSystems.getDefault().getPath("/sys/bus/iio/devices/iio:device1", "in_voltage5_raw"), //for I2C
    };
//             Paths.get("/sys/bus/iio/devices/iio:device1/in_voltage0_raw"),
//             Paths.get("/sys/bus/iio/devices/iio:device1/in_voltage1_raw"),
//             Paths.get("/sys/bus/iio/devices/iio:device1/in_voltage2_raw"),
//             Paths.get("/sys/bus/iio/devices/iio:device1/in_voltage3_raw"), 
//             Paths.get("/sys/bus/iio/devices/iio:device1/in_voltage4_raw"), //TODO: what if we read these data lines for I2C?
//             Paths.get("/sys/bus/iio/devices/iio:device1/in_voltage5_raw")  //TODO: what if we read these data lines for I2C?
//             
//    };
    public final SeekableByteChannel[] pathAChannel = new SeekableByteChannel[6];   
    
    private static final Path PATH_GPIO_EXPORT   = Paths.get("/sys/class/gpio/export");
    private static final Path PATH_GPIO_UNEXPORT = Paths.get("/sys/class/gpio/unexport");
        
    public static final byte[] OUT            = "out".getBytes();
    public static final byte[] IN             = "in".getBytes();
    public static final byte[] DRECTION_HIGH  = "high".getBytes();
    public static final byte[] DIRECTION_LOW  = "low".getBytes();
    public static final byte[] VALUE_HIGH     = "1".getBytes();
    public static final byte[] VALUE_LOW      = "0".getBytes();  
    public static final byte[] MODE_0         = "mode0".getBytes();
    public static final byte[] MODE_1         = "mode1".getBytes();
    public static final byte[] MODE_2         = "mode2".getBytes();
    public static final byte[] PULLUP         = "pullup".getBytes();
    
//    public static final ByteBuffer I2C_LOW;
//    public static final ByteBuffer I2C_HIGH;
    public static final ByteBuffer I2C_LOW = ByteBuffer.wrap(VALUE_LOW);
    public static final ByteBuffer I2C_HIGH = ByteBuffer.wrap(VALUE_HIGH);
    public static final ByteBuffer I2C_OUT;
    public static final ByteBuffer I2C_IN;
    public static final ByteBuffer I2C_DIRECTION_LOW;
    public static final ByteBuffer I2C_DIRECTION_HIGH;
    public static final ByteBuffer[] BIT_BYTES = new ByteBuffer[]{I2C_LOW, I2C_HIGH};
 
    
    public static final int I2C_CLOCK = 19;
    public static final int I2C_DATA = 18;
        
    private static final Set<OpenOption> readOptions = new HashSet<OpenOption>();
    private static final Set<OpenOption> i2cOptions = new HashSet<OpenOption>();

    private static ByteBuffer[] readIntBuffer;
    private static ByteBuffer[] readBitBuffer;
    
    static {
        
//        I2C_LOW = ByteBuffer.allocateDirect(VALUE_LOW.length);
//        I2C_LOW.put(VALUE_LOW);
//        I2C_LOW.clear();
//        
//        I2C_HIGH = ByteBuffer.allocateDirect(VALUE_HIGH.length);
//        I2C_HIGH.put(VALUE_HIGH);
//        I2C_HIGH.clear();
        
        I2C_OUT = ByteBuffer.allocateDirect(OUT.length);
        I2C_OUT.put(OUT);
        I2C_OUT.clear();
        
        I2C_IN = ByteBuffer.allocateDirect(IN.length);
        I2C_IN.put(IN);
        I2C_IN.clear();
        
        I2C_DIRECTION_LOW = ByteBuffer.allocateDirect(DIRECTION_LOW.length);
        I2C_DIRECTION_LOW.put(DIRECTION_LOW);
        I2C_DIRECTION_LOW.clear();
        
        I2C_DIRECTION_HIGH = ByteBuffer.allocateDirect(DRECTION_HIGH.length);
        I2C_DIRECTION_HIGH.put(DRECTION_HIGH);
        I2C_DIRECTION_HIGH.clear();
        
        
        i2cOptions.add(StandardOpenOption.READ);
        i2cOptions.add(StandardOpenOption.WRITE);
        i2cOptions.add(StandardOpenOption.SYNC);
        
        readOptions.add(StandardOpenOption.READ);        

        int a = PATH_A.length;
        readIntBuffer = new ByteBuffer[a];
        while (--a>=0) {
            readIntBuffer[a] = ByteBuffer.allocate(16);            
        }
        
        int b = EdisonConstants.GPIO_PINS.length;
        readBitBuffer = new ByteBuffer[b];
        while (--b>=0) {
            readBitBuffer[b] = ByteBuffer.allocate(1);
        }
    }
    
    public EdisonPinManager(short[] pins) {
        
        gpioPinInt = pins;
        gpio = new Path[pins.length];    
        gpioDirection = new Path[pins.length];
        gpioDirectionChannel = new SeekableByteChannel[pins.length];
        gpioValue = new Path[pins.length];
        gpioChannel = new SeekableByteChannel[pins.length];
        gpioPinString = new String[pins.length];
        gpioDebugCurrentPinMux = new Path[pins.length];//NOTE only needed for mode array
        
        
        FileSystem fileSystem = FileSystems.getDefault();
        this.provider = fileSystem.provider();
        
        int i = pins.length;
        StringBuilder sb = new StringBuilder();
        sb.append("/sys/class/gpio/gpio");
        int baseLen = sb.length();
        while (--i>=0) {
            
            if (pins[i]>=0) {
                gpioPinString[i] = Integer.toString(pins[i]);
                
                gpioDebugCurrentPinMux[i] = fileSystem.getPath("/sys/kernel/debug/gpio_debug/gpio"+gpioPinString[i]+"/current_pinmux");

                sb.setLength(baseLen);
                sb.append(gpioPinString[i]);
                gpio[i]          = fileSystem.getPath(sb.toString());
                                
                int withIdLen = sb.length();
                sb.append("/direction");
                gpioDirection[i] = fileSystem.getPath(sb.toString());
                
                sb.setLength(withIdLen);
                sb.append("/value");
                gpioValue[i]    = fileSystem.getPath(sb.toString());   
                
                
            }
        }
    }

    public void setDirectionLow(int i) {
        writeDirection(i,I2C_DIRECTION_LOW,this);
    }

    public void setDirectionHigh(int i) {
        writeDirection(i,I2C_DIRECTION_HIGH,this);
    }
    
    
    public void setDebugCurrentPinmuxMode0(int i) {
        if (null!= gpioDebugCurrentPinMux[i]) {
            try {
                Files.write(gpioDebugCurrentPinMux[i],MODE_0);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void setDebugCurrentPinmuxMode1(int i) {
        try {
            Files.write(gpioDebugCurrentPinMux[i],MODE_1);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void setDebugCurrentPinmuxMode2(int i) {
        try {
            Files.write(gpioDebugCurrentPinMux[i],MODE_2);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void setDirectionIn(int i) {
        writeDirection(i,I2C_IN,this);
    }

    public void setDirectionOut(int i) {
        writeDirection(i,I2C_OUT,this);
    }

    public void setValueHigh(int i) {
        writeValue(i,I2C_HIGH, this);
    }


    public void setValueLow(int i) {
        writeValue(i,I2C_LOW, this);
    }

    public void ensureDevice(int i) {
        
        if (null!=gpio[i] && !gpio[i].toFile().exists()) {
            try {
                Files.write(PATH_GPIO_EXPORT, gpioPinString[i].getBytes());
            } catch (IOException e) {
               throw new RuntimeException(e);
            }
        }
                
    }
    
    public void removeDevice(int i) {
        if (null!=gpio[i] && !gpio[i].toFile().exists()) {
            try {
                Files.write(PATH_GPIO_UNEXPORT, gpioPinString[i].getBytes());
            } catch (IOException e) {
               throw new RuntimeException(e);
            }
        }
    }
    
    public static void writeValue(int port, ByteBuffer data, EdisonPinManager d) {
            try {
                SeekableByteChannel channel = d.gpioChannel[port];
                if (null == channel) {
                        
                  channel = d.provider.newByteChannel(d.gpioValue[port],i2cOptions);
                  d.gpioChannel[port] = channel;
                 
                }
                
                data.clear();
                int limit = data.limit();
                do {
                    limit -= channel.write(data);
                } while (limit>0);//Caution, this is blocking.
                channel.position(0);

            
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
    }
    

    
    
    public static void writeDirection(int port, ByteBuffer data, EdisonPinManager d) {
        try {
            SeekableByteChannel channel = d.gpioDirectionChannel[port];
            if (null == channel) {
                    
              channel = d.provider.newByteChannel(d.gpioDirection[port],i2cOptions);
              d.gpioDirectionChannel[port] = channel;
                           
           }
            data.clear();
            do {
                channel.write(data);
            } while (data.hasRemaining());//Caution, this is blocking.
            channel.position(0);

        
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

   }

//    public static boolean readLengthTest(int idx, int len) {
//
//        try {
//            ByteBuffer buffer = readIntBuffer[idx];
//            
//            loadValueIntoBuffer(idx, buffer);
//            
//            return buffer.remaining()>len;
//           
//       } catch (IOException e) {
//           throw new RuntimeException(e);
//       }
//    }
    
    public static int readInt(int idx) {

            try {
                ByteBuffer buffer = readIntBuffer[idx];
                
                loadValueIntoBuffer(idx, buffer);
                
                int i = buffer.remaining();
                int result = 0;    
                byte c;
                while (--i>=0 && ((c=buffer.get())>='0')) {
                    result= (result*10)+(c-'0');
                }
        
               return result;  
           } catch (IOException e) {
               throw new RuntimeException(e);
           }
    }

    private static void loadValueIntoBuffer(int idx, ByteBuffer buffer) throws IOException {

        
        SeekableByteChannel bc = EdisonGPIO.gpioLinuxPins.pathAChannel[idx];
        if (null == bc) {
            
            /////////////
            //R&D code not ready for use
            //attempting to memory map this driver
            ////////////
//            FileChannel fc = null;
//            try {
//                fc = FileChannel.open(PATH_A[idx], StandardOpenOption.READ, StandardOpenOption.CREATE_NEW);
//            } catch (Exception e) {
//                System.out.println("Error opening file: " + e.getMessage());
//            }
//            MappedByteBuffer mbb = null;
//            try {
//                mbb = fc.map(FileChannel.MapMode.READ_ONLY, 0, 100);
//                
//                
//            } catch (IOException e) {
//                System.out.println("Error mapping file: " + e.getMessage()+"   "+PATH_A[idx]);
//            }
            
            
            
            EdisonGPIO.gpioLinuxPins.pathAChannel[idx] = bc =EdisonGPIO.gpioLinuxPins.provider.newByteChannel(PATH_A[idx], readOptions);
        }
        
        buffer.clear();
        
    //    long start = System.nanoTime();
        do {            
            
            while (bc.read(buffer)>=0){}//read everything available
 
            //if length is 0 read this again.
        } while (buffer.position()==0);// || (buffer.get(0)<'0'));
  //      long duration = System.nanoTime()-start;
        
        buffer.flip();              
        bc.position(0);
        
        
    //    System.out.println("read duration "+duration+"ns");
        
        
        
    }
    
    
    public static int readBit(int idx) {
            try {        
                
                SeekableByteChannel bc = EdisonGPIO.gpioLinuxPins.gpioChannel[idx];
                if (null == bc) {
                    bc = EdisonGPIO.gpioLinuxPins.provider.newByteChannel(EdisonGPIO.gpioLinuxPins.gpioValue[idx],i2cOptions);
                    EdisonGPIO.gpioLinuxPins.gpioChannel[idx] = bc;
               }
                
                ByteBuffer buffer = readBitBuffer[idx];
                buffer.clear();
                while (bc.read(buffer)==0){}//only need 1
             
                buffer.flip();
                bc.position(0);
                return buffer.get()&0x1;
            } catch (IOException e) {
               throw new RuntimeException(e);
            }
            

    }

	public static void writeBit(int connector, int value, EdisonPinManager gpiolinuxpins) {
		writeValue(connector, BIT_BYTES[value],gpiolinuxpins);
		
	}

}
