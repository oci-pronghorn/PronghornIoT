package com.ociweb.device.testApps;

import com.ociweb.pronghorn.iot.i2c.impl.I2CNativeLinuxBacking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Running the grove pi
 * 
 * i2c protocol found here: https://www.raspberrypi.org/forums/download/file.php?id=8272
 * 
 * @author alexherriott
 */
public class GrovePiExample {
    private static final Logger logger = LoggerFactory.getLogger(GrovePiExample.class);

    // Create a connection to the native Linux I2C lines.
    private static final I2CNativeLinuxBacking i2c = new I2CNativeLinuxBacking();

    // Address of board.
    public static final byte Grove_ADDR = 0x04;

    public static void main(String[] args) {
        logger.info("Starting GrovePi example app.");

        System.out.println("#### Writing data ####");
        System.out.println("");
        pinMode(14,0); //set A0 to input
        pinMode(3,1);  //set 3 to output
        int response;
        int val = 255;
        while (true) {
            
        	response = analogRead(14);
        	//System.out.println(response);
        	analogWrite(3, (byte)(response/4));
        	
            // Sleep for a bit.
//            try {
//                Thread.sleep(250);
//            } catch (Exception e) {
//                logger.error(e.getMessage(), e);
//            }
            
        }
    }

    static void digWrite(int pin, int val){
    	byte[] message = {0x01, 0x02, (byte) pin, (byte) val, 0x00};
    	i2c.write(Grove_ADDR, message);    
    }
    
    static byte digRead(int pin){
    	byte[] message = {0x01, (byte) pin, 0x00, 0x00};
    	i2c.write(Grove_ADDR, message);
    	return i2c.read(Grove_ADDR, 1)[0];
    }
    
    static void pinMode(int pin, int val){
    	byte[] message = {0x01, 0x05, (byte) pin, (byte) val, 0x00};
    	i2c.write(Grove_ADDR, message); 
    }
    
    static void analogWrite(int pin, int val){
    	byte[] message = {0x01, 0x04, (byte) pin, (byte) val, 0x00};
    	i2c.write(Grove_ADDR, message); 
    }
    static int analogRead(int pin){
    	byte[] message = {0x01, 0x03, (byte) pin, 0x00, 0x00};
    	i2c.write(Grove_ADDR, message);
    	byte[] ans = i2c.read(Grove_ADDR, 3);
    	return ans[1]*256+((int)ans[2]&0xFF);
    }
}
