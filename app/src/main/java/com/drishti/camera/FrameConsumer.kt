package com.drishti.camera

import com.drishti.models.CameraFrame

interface FrameConsumer {
    /**
     * Called when a new camera frame is ready for consumption.
     * The consumer MUST invoke [releaseCallback] once it is done processing the frame
     * to allow the distributor to release underlying resources (like ImageProxy buffers).
     */
    fun onFrame(frame: CameraFrame, releaseCallback: () -> Unit)
}
