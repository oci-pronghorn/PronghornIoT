package com.ociweb.device.grove;

import com.ociweb.device.config.GroveConnectionConfiguration;
import com.ociweb.device.grove.schema.GroveI2CSchema;
import com.ociweb.pronghorn.pipe.FieldReferenceOffsetManager;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.stage.PronghornStage;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;

public class GroveShieldV2I2CStage extends PronghornStage {

    private int taskAtHand;
    private int stepAtHand;
    
    private int byteToSend;
    private int byteToSendPos;
    
    //holds the same array as used by the Blob from the ring.
    private byte[] bytesToSendBacking; //set before send
    private int    bytesToSendRemaining;
    private int    bytesToSendPosition;
    private int    bytesToSendMask;
    private int    bytesToSendReleaseSize;
        
    
    private int bitFromBus;
    
    private static final int TASK_NONE = 0;
    private static final int TASK_MASTER_START = 1;
    private static final int TASK_MASTER_STOP  = 2;
    private static final int TASK_WRITE_BYTES  = 3;
    
    public final GroveConnectionConfiguration config;
    
    private final Pipe<GroveI2CSchema> request;
    private final Pipe<GroveI2CSchema> response;
  
    
    //I2C is a little complex to ensure correctness.  As a result this stage is not aware of any 
    //specific grove modules which may be attached. It only does the sending and receiving of bytes
    
    public GroveShieldV2I2CStage(GraphManager gm, Pipe<GroveI2CSchema> request, Pipe<GroveI2CSchema> response, GroveConnectionConfiguration config) {
        super(gm, request, response);
        
        this.request = request;
        this.response = response;
        this.config = config;
        
        //Fixed at a slow 100K per second for broad compatibility
        GraphManager.addNota(gm, GraphManager.SCHEDULE_RATE, 100*1000, this);
        GraphManager.addNota(gm, GraphManager.PRODUCER, GraphManager.PRODUCER, this);
        
    }
    
    public GroveShieldV2I2CStage(GraphManager gm, Pipe<GroveI2CSchema> request, GroveConnectionConfiguration config) {
        super(gm, request, NONE);
        
        this.request = request;
        this.response = null;
        this.config = config;
        
        //Fixed at a slow 100K per second for broad compatibility
        
        GraphManager.addNota(gm, GraphManager.SCHEDULE_RATE, 1000*1000, this);
        GraphManager.addNota(gm, GraphManager.PRODUCER, GraphManager.PRODUCER, this);
        
    }
    
    
    @Override
    public void startup() {
        //bus read/write priority should be the lowest an need not conflict with other tasks.
        //if shared with another thread wanting to be high leave it alone
        if (Thread.currentThread().getPriority()!=Thread.MAX_PRIORITY) {
            Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
        }
        
        if (config.configI2C) {
           config.beginPinConfiguration();
           config.configurePinsForI2C();
           config.endPinConfiguration();
        } else {
            System.out.println("warning, i2s stage used but not turned on");
        }
        //starting in the known state where both are high
        config.i2cSetClockHigh();
        config.i2cSetDataHigh();
        
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
      if (0==config.i2cReadClock() ) {
          throw new RuntimeException("expected clock to be high for start");
      }        
      if (0==config.i2cReadData() ) {
          throw new RuntimeException("expected data to be high for start");
      }
        
    }
    
    long lastRun = 0;
    
    @Override
    public void run() {
      
        long now = System.currentTimeMillis();
        long dif = now-lastRun;
     //   System.out.println("dif "+dif);
        lastRun = now;
        
        if (TASK_NONE == taskAtHand) {
        //    System.out.println("read request");
            readRequest();//may populate task at hand
        }
        
        switch (taskAtHand) {
            case TASK_MASTER_START:
                //System.out.println("task master start "+stepAtHand);
                masterStart();
                break;
            case TASK_WRITE_BYTES:
               // System.out.println("task write bytes data "+stepAtHand);
                writeBytes();
                break;
            case TASK_MASTER_STOP:
               // System.out.println("task master stop "+stepAtHand);
                masterStop();

                break;
        }        
        //Must return after this point to ensure the clock speed is respected.
    }

        
    
    private void readRequest() {
        // TODO Auto-generated method stub
        if (Pipe.hasContentToRead(request)) {
            
            int msgId = Pipe.takeMsgIdx(request);
            if (msgId<0) {
                requestShutdown();
                return;
            }
            
            int meta = Pipe.takeRingByteMetaData(request);
            int len = Pipe.takeRingByteLen(request);
                        
            bytesToSendBacking = Pipe.byteBackingArray(meta, request);
            bytesToSendMask = Pipe.blobMask(request);
            bytesToSendPosition = Pipe.bytePosition(meta, request, len);
            bytesToSendRemaining = len;
            bytesToSendReleaseSize =  FieldReferenceOffsetManager.RAW_BYTES.fragDataSize[0];
                        
            taskAtHand = TASK_MASTER_START;
            stepAtHand = 0;
            
            byteToSend = bytesToSendBacking[bytesToSendMask&bytesToSendPosition++];
            byteToSendPos = 8;
            //System.out.println("sending byte "+Integer.toHexString(byteToSend));
        }
        
        
        
    }


    private void masterStart() {

        switch (stepAtHand) {
            case 0:            
                config.i2cSetClockHigh();
               // config.i2cClockIn();
                while (0 == config.i2cReadClock()) {
                    //client may hold down clock must wait until it goes back up.
                    //This is a spinning block dependent upon the other end of i2c
                }             
                //config.i2cClockOut();
                
                if (0==config.i2cReadData()) {
                    System.out.println("failure, unable to be master, data line should be high");
                    taskAtHand = TASK_NONE;
                    return;
                }
                
                config.i2cSetDataLow(); //lower data while clock is high
                stepAtHand = 1;
                break;//pause
            case 1:
                if (0==config.i2cReadClock()) {
                   throw new RuntimeException("expected clock to be high");
                }
                config.i2cSetClockLow();
                stepAtHand = 2;
                break;//pause  
            case 2:
                if (1==config.i2cReadData()) {
                    System.out.println("failure, unable to be master SDA");
                    taskAtHand = TASK_NONE;
                    return;
                }
                if (1==config.i2cReadClock()) {
                    System.out.println("failure, unable to be master SCL");
                    taskAtHand = TASK_NONE;
                    return;
                }
                stepAtHand = 0;
                taskAtHand = TASK_WRITE_BYTES;
                break;//done
            default:
                throw new UnsupportedOperationException();
        }

    }


    private void masterStop() {
        switch (stepAtHand) {
            case 0:
            config.i2cSetClockHigh();
                while (0==config.i2cReadClock()) {
                      //This is a spinning block dependent upon the other end of i2c
                }
                stepAtHand = 1;
                break;//pause
            case 1:
                config.i2cSetDataHigh();
                stepAtHand = 0;
                taskAtHand = 0;
//                
//            try {
//                Thread.sleep(1500);
//            } catch (InterruptedException e) {
//                // TODO Auto-generated catch block
//                e.printStackTrace();
//            }
                
                break;
            default:
                throw new UnsupportedOperationException();
        }        
    }

    
    
    private void writeBytes() {
        switch (stepAtHand) {
            case 0:
                  //byteToSendPos starts with 8
                  if (0==(1 & (byteToSend >> (--byteToSendPos)))) {
                      //System.out.println("0 from pos "+byteToSendPos+" of "+Integer.toBinaryString(byteToSend));
                      config.i2cSetDataLow();
                  } else {
                      //System.out.println("1 from pos "+byteToSendPos+" of "+Integer.toBinaryString(byteToSend));
                      config.i2cSetDataHigh();
                  }        
                  config.i2cSetClockHigh();
                  while (0==config.i2cReadClock()) { //TODO: these spin checks should return and re-enter as needed.
                      //This is a spinning block dependent upon the other end of i2c
                  }      
                  stepAtHand = 1;
                  break;//pause
            case 1:
                  if (0!=(1 & (byteToSend >> byteToSendPos)) && config.i2cReadData()==0 ) {
                      throw new RuntimeException("Unable to confirm data set high");           
                  }
                  config.i2cSetClockLow();
                                  
                  if (0 == byteToSendPos) {                      
                      stepAtHand = 2; //now read the ack for this byte
                  } else { //we will start on the next bit.
                      stepAtHand = 0;
                  }
                  break;//pause
            case 2:
                config.i2cSetDataHigh();
                config.i2cSetClockHigh();
                while (0==config.i2cReadClock()) {
                      //This is a spinning block dependent upon the other end of i2c
                }
                config.i2cDataIn();   //needed so we can read the ack.
                stepAtHand = 3;
                break;//pause
            case 3:
                config.i2cSetClockLow();        
              
//            try {
//                Thread.sleep(10);
//            } catch (InterruptedException e1) {
//                // TODO Auto-generated catch block
//                e1.printStackTrace();
//            }
                
                int ack = config.i2cReadData();
                System.out.println("ack value "+ack);
                config.i2cDataOut();
                stepAtHand = 4;
                break;//pause
            case 4:
                                
                config.i2cSetDataHigh();                
                stepAtHand = 0;
                
                if (--bytesToSendRemaining<=0) {
                    taskAtHand = TASK_MASTER_STOP; //we are all done
                    
                    //release the resources for more data
                    Pipe.confirmLowLevelRead(request,bytesToSendReleaseSize);
                    Pipe.releaseReads(request);
                    
                    
                } else {       
                    
//                    try {
//                        Thread.sleep(1500);
//                    } catch (InterruptedException e) {
//                        // TODO Auto-generated catch block
//                        e.printStackTrace();
//                    }
                        
                    
                    byteToSend = bytesToSendBacking[bytesToSendMask&bytesToSendPosition++];
                    byteToSendPos = 8;
                    //System.out.println("sending byte "+Integer.toHexString(byteToSend));
                    //now back to zero to send the next byte 
                }
                break;//pause
            default:
                throw new UnsupportedOperationException();        
        }
    }
    
    
    
    private void readBit() {
        switch (stepAtHand) {
            case 0:
                  //wait for clock to be highwriteBytes
                  while (0==config.i2cReadClock() ) {
                    //This is a spinning block dependent upon the other end of i2c
                  }//writeValue
                  //clock is now high
                  bitFromBus = config.i2cReadData();                    
                  stepAtHand = 1;
                  break;//pause
            case 1:
                  config.i2cSetClockLow();
                
                  stepAtHand = 0;
                  
                  taskAtHand = 0;
                  
                  break;//pause
            default:
                throw new UnsupportedOperationException();        
        }
        
    }
    

    @Override
    public void shutdown() {
        
        
    }

      
    
    
    
    
//    
//    public static boolean readMsg(EdisonPinManager d, int addr) {
//        //request data.
//        if (!start_I2C(d) ) {
//            //unable to master the bus 
//            System.out.println("unable to master the bus");
//            return false;
//        };
//        int value =  (addr<<1) | 0; //zero for write
//        int nack = sendI2CByte(d, value); 
//        System.out.println("ADDR NACK:"+nack);
//
//        int register = 1;
//        nack = sendI2CByte(d, register);
//        System.out.println("register NACK:"+nack);
//        
//        if (!start_I2C(d) ) {
//            //unable to master the bus 
//            System.out.println("unable to master the bus");
//            return false;
//        };  
//        
//        value =  (addr<<1 ) | 1; //one for write
//        nack = sendI2CByte(d, value);        
//        System.out.println("ADDR NACK:"+nack);
//        
//        //read data
//        int dat  = 0;
//        int i = 32;
//        while (--i>=0) {
//            dat = ((dat<<1)|readI2CBit(d));            
//        }
//        System.out.println("read:"+dat);
//        
//        stop_I2C(d);        
//        
//        return true;
//    }
//    
//    public static void sendMsg(EdisonPinManager d, int addr, byte ... msg) {
//        if (!start_I2C(d) ) {
//            //unable to master the bus 
//            System.out.println("unable to master the bus");
//                public static final int MSG_TIME_10 = 0x0;
//        } else {
//        
//            sendAddr(d, addr, 0 /*1 is read*/);
//            sendBytes(d, msg);
//        
//            stop_I2C(d);
//        }
//    }
//
//    private static void sendAddr(EdisonPinManager d, int addr, int isRead) {
//        int value =  (addr<<1 ) | isRead; //zero for write
//        int nack = sendI2CByte(d, value);        
//       // System.out.println("ignored ADDR NACK:"+nack+"  from  "+Integer.toHexString(addr));
//    }
//
//    private static void sendBytes(EdisonPinManager d, byte... msg) {
//        int nack;
//        int i = 0;
//        while (i<msg.length) {
//            nack = sendI2CByte(d, msg[i]);
//            
//           // System.out.println("ignored NACK:"+nack+"  sent "+msg[i]);
//            
//            i++;
//        }
//    }
//    
    
    
    
    
//    ///////////////
      //not needed 
//    private static void delay() {
//        try {
//            Thread.sleep(1000);//500);//  //1500);

//    //////////////////////////
    ///orginal test script
//    
//    public static void writeI2C(byte[] data, EdisonPinManager d) {
//        //this is always on 18/19
////        System.err.println("write to I2C");
////        ByteBuffer src = ByteBuffer.allocate(80);
////        
////        //all Edison i2c communication in on device 6
////       // Path i2cDevice = Paths.get("/sys/class/i2c-dev/i2c-6"); //mapped to 18(SDA)/19(SCL)
////        
//        
//        
////        ///beep works
//        //TODO:   build stage to play multiple notes out buzzer
//        //TODO:   build schema patttern after midd format
////        int j = 100;
////        while (--j>=0) {
////            writeValue(4, I2C_HIGH, d);
////            pause();
////            writeValue(4, I2C_LOW, d);
////            pause();
////        }
//        
//        
//        delay();
//        delay();
//        
////        //setup starting state with both high.
//        GroveShieldV2EdisonSensorStage.configI2COut(); //we are now the master of the bus, i2cdetect will no longer work
//        writeValue(I2C_DATA, I2C_HIGH, d);
//        writeValue(I2C_CLOCK, I2C_HIGH, d);
//        pause();
//
//        delay();
//        delay();
//        
//        boolean testGuage = false;
//        if (testGuage) {        
//            
//            //When data line goes high the clock is dropped
//            testCodeForLevelMonitoring(d); //NOTE: returns both to low
//            
//            writeValue(I2C_DATA, I2C_HIGH, d);
//            writeValue(I2C_CLOCK, I2C_HIGH, d);
//            pause();
//        }
//                
//        //Start case must have both high
//        if (0==readBit(I2C_CLOCK,d) ) {
//            throw new RuntimeException("expected clock to be high for start");
//        }        
//        if (0==readBit(I2C_DATA,d) ) {
//            throw new RuntimeException("expected data to be high for start");
//        }
//        
//      //Init so we can adjust each color.
//      sendMsg(d, Grove_LCD_RGB.RGB_ADDRESS, (byte)0, (byte)0);     
//      sendMsg(d, Grove_LCD_RGB.RGB_ADDRESS, (byte)1, (byte)0);
//      sendMsg(d, Grove_LCD_RGB.RGB_ADDRESS, (byte)Grove_LCD_RGB.REG_OUTPUT, (byte)0xAA);
//        
//        setBacklightColor(d, (byte)0xFF, (byte)0x00, (byte)0x00);      
//        System.out.println("red");        
//
//        //        try {
////            Thread.sleep(1000);
////        } catch (InterruptedException e) {
////        }
////        
////        
////        setBacklightColor(d, (byte)0x00, (byte)0xFF, (byte)0x00);      
////        System.out.println("green");        
////        try {
////            Thread.sleep(1000);
////        } catch (InterruptedException e) {
////        }
////        
////        
////        setBacklightColor(d, (byte)0x00, (byte)0x0, (byte)0xFF);
////        System.out.println("blue");
//      
//        
//
//        
//      //      top bit is always 1 for following commands except on last call #7
//      //NOTE: we only have 2 regesters 0 and 1 in the rs bit                 #6
//      
////      start_I2C(d);
////      sendAddr(d, Grove_LCD_RGB.LCD_ADDRESS, 0 /*1 is read*/);
////      delay();
////      delay();
////      sendBytes(d, (byte)0x80, (byte)0b00111100);
////     // delay(40);
////      delay();
////      delay();
////      sendBytes(d,  (byte)0x80,(byte)0b00001101); //last blit is blink on
////      //delay(40);
////      delay();
////      delay();
////      sendBytes(d,  (byte)0x80,(byte)0b00000001); //clear display 
////    //  delay(1531);
////      delay();
////      delay();
////      sendBytes(d,  (byte)0x80,(byte)0b00000110); //data entry inc data shift off.
////      delay();
////      delay();
////      //stop_I2C(d);
//      
//
//        
//        
//   //   int displayFunction = Grove_LCD_RGB.LCD_2LINE | Grove_LCD_RGB.LCD_FUNCTIONSET;
//
//      //send multiple times to ensure it sticks.
//   //   sendAddr(d, Grove_LCD_RGB.LCD_ADDRESS, 0);  
//       int line = Grove_LCD_RGB.LCD_2LINE;//LCD_5x10DOTS;
//      sendMsg(d, Grove_LCD_RGB.LCD_ADDRESS, (byte)0, (byte)(Grove_LCD_RGB.LCD_FUNCTIONSET | line));
//      delay(50000);// micro
//      sendMsg(d, Grove_LCD_RGB.LCD_ADDRESS, (byte)0, (byte)(Grove_LCD_RGB.LCD_FUNCTIONSET | line));
//      delay(4500);// miro
//      sendMsg(d, Grove_LCD_RGB.LCD_ADDRESS, (byte)0, (byte)(Grove_LCD_RGB.LCD_FUNCTIONSET | line));
//      delay(150);// micro
//      sendMsg(d, Grove_LCD_RGB.LCD_ADDRESS, (byte)0, (byte)(Grove_LCD_RGB.LCD_FUNCTIONSET | line));
//      sendMsg(d, Grove_LCD_RGB.LCD_ADDRESS, (byte)0, (byte)(Grove_LCD_RGB.LCD_FUNCTIONSET | line));
////      
////      delay();
////      sendMsg(d, Grove_LCD_RGB.LCD_ADDRESS, (byte)0, (byte)(Grove_LCD_RGB.LCD_DISPLAYCONTROL | 
////              (Grove_LCD_RGB.LCD_DISPLAYON /*| Grove_LCD_RGB.LCD_CURSOROFF | Grove_LCD_RGB.LCD_BLINKOFF)) */ )));     
//      delay();
//      sendMsg(d, Grove_LCD_RGB.LCD_ADDRESS, (byte)0, (byte)(Grove_LCD_RGB.LCD_DISPLAYCONTROL |    (Grove_LCD_RGB.LCD_DISPLAYOFF | Grove_LCD_RGB.LCD_CURSOROFF )));     
//      delay();
//      sendMsg(d, Grove_LCD_RGB.LCD_ADDRESS, (byte)0, (byte)Grove_LCD_RGB.LCD_CLEARDISPLAY);
//      
//      
//////      
////      //standard for romance languages
////      sendMsg(d, Grove_LCD_RGB.LCD_ADDRESS, (byte)0, (byte)(Grove_LCD_RGB.LCD_ENTRYMODESET | ( Grove_LCD_RGB.LCD_ENTRYLEFT | Grove_LCD_RGB.LCD_ENTRYSHIFTDECREMENT) ));
////      delay();
////
////      start_I2C(d);
////      sendAddr(d, Grove_LCD_RGB.LCD_ADDRESS, 0);  
////      sendBytes(d, (byte)0x40); 
////      int i = 50;
////      while (--i>30) {
////          delay();
////          sendBytes(d, (byte)i);
////          System.out.println(i);
////      }
////
////      stop_I2C(d);
//
//          
//        setBacklightColor(d, (byte)0x00, (byte)0x00, (byte)0xFF);
//    
//            
//        System.err.println("finished I2C");
//    }
//

    
    
    
    ///////////////////////
    //should be a message an not hardcoded
//    public static void setBacklightColor(EdisonPinManager d, byte r, byte g, byte b) {
//
//
//          sendMsg(d, Grove_LCD_RGB.RGB_ADDRESS, (byte)Grove_LCD_RGB.REG_RED, (byte)0);
//          sendMsg(d, Grove_LCD_RGB.RGB_ADDRESS, (byte)Grove_LCD_RGB.REG_GREEN, (byte)0);
//          sendMsg(d, Grove_LCD_RGB.RGB_ADDRESS, (byte)Grove_LCD_RGB.REG_BLUE, (byte)0);
//          sendMsg(d, Grove_LCD_RGB.RGB_ADDRESS, (byte)Grove_LCD_RGB.REG_RED, r);
//          delay();
//          sendMsg(d, Grove_LCD_RGB.RGB_ADDRESS, (byte)Grove_LCD_RGB.REG_GREEN, g);
//          sendMsg(d, Grove_LCD_RGB.RGB_ADDRESS, (byte)Grove_LCD_RGB.REG_BLUE, b);
//
//    }
//
    ////////////////////////////
    ///not sure we need this?
//    private static void testCodeForLevelMonitoring(EdisonPinManager d) {
//        //start with both high then both low.
//        int j = 2;
//        while (--j>=0) {
//        
//            if (j==1) {
//                writeValue(I2C_DATA, I2C_HIGH, d);
//                writeValue(I2C_CLOCK, I2C_HIGH, d);                
//            } else {
//                writeValue(I2C_DATA, I2C_LOW, d);
//                writeValue(I2C_CLOCK, I2C_LOW, d);
//            }
//                                 
//            
//            int i = 20;
//            while (--i>=0) {
//                if (0==(1&i)) {
//                    writeValue(I2C_DATA, I2C_HIGH, d);
//                } else {
//                    writeValue(I2C_DATA, I2C_LOW, d);
//                }
//                
//                try {
//                    Thread.sleep(80);
//                } catch (InterruptedException e) {
//                    // TODO Auto-generated catch block
//                    e.printStackTrace();
//                }
//            }
//            
//            if (j==1) {
//                writeValue(I2C_DATA, I2C_HIGH, d);
//                writeValue(I2C_CLOCK, I2C_HIGH, d);                
//            } else {
//                writeValue(I2C_DATA, I2C_LOW, d);
//                writeValue(I2C_CLOCK, I2C_LOW, d);
//            }
//            
//            i = 20;
//            while (--i>=0) {
//                if (0==(1&i)) {
//                    writeValue(I2C_CLOCK, I2C_HIGH, d);
//                } else {
//                    writeValue(I2C_CLOCK, I2C_LOW, d);
//                }
//                try {
//                    Thread.sleep(80);
//                } catch (InterruptedException e) {
//                    // TODO Auto-generated catch block
//                    e.printStackTrace();
//                }
//            }            
//       
//          
//        }
//    }
  
    
}
