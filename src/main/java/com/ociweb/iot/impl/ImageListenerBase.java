package com.ociweb.iot.impl;

import java.io.File;

/**
 * Listener for responding to image-receive events on a hardware system
 * equipped with a camera or other image-generating systems.
 *
 * @author Brandon Sanders [brandon@alicorn.io] -- Initial API and implementation
 * @author Ray Lo -- Moved and modified
 */
@FunctionalInterface
public interface ImageListenerBase {

    /**
     * Invoked when a new image is received from the hardware.
     *
     * The file reference passed to this method will never be
     * invalidated, and the image file represented by the
     * reference will never be deleted. Therefore, if many images
     * are being created, it's the responsibility of this listener
     * to delete the image files as needed.
     *
     * @param imagePath Complete path to the captured image file.
     */
    void onImage(String imagePath);
}
