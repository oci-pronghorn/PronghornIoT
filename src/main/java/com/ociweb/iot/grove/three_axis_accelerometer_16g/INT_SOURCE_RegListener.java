/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.ociweb.iot.grove.three_axis_accelerometer_16g;

/**
 *
 * @author huydo
 */
public interface INT_SOURCE_RegListener extends ThreeAxisAccelerometer_16gListener {
    void regStatus(int byteRead); //byteRead is the 8 bit stored in INT_SOURCR register
    void freefallStatus(int status); // status is 1 if device detects a free fall
}