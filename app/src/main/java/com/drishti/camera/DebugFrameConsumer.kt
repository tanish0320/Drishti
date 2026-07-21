package com.drishti.camera

import android.util.Log
import com.drishti.models.CameraFrame

class DebugFrameConsumer : FrameConsumer {
    override fun onFrame(frame: CameraFrame, releaseCallback: () -> Unit) {
        Log.d(
            "DebugFrameConsumer",
            "Received frame - Timestamp: ${frame.timestamp}, Size: ${frame.width}x${frame.height}, Rotation: ${frame.rotationDegrees}°"
        )
        // Instantly release claim on frame since we only perform lightweight logging
        releaseCallback()
    }
}
