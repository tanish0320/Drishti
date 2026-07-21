package com.drishti.camera

import com.drishti.models.CameraFrame
import kotlinx.coroutines.flow.Flow

interface FrameProvider {
    fun startFrameStream(): Flow<CameraFrame>
    fun stopFrameStream()
}
