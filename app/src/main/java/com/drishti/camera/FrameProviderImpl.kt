package com.drishti.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.drishti.models.CameraFrame
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FrameProviderImpl @Inject constructor(
    private val frameDistributor: FrameDistributor
) : FrameProvider, ImageAnalysis.Analyzer {

    override fun startFrameStream(): Flow<CameraFrame> {
        // Dynamically creates a consumer that maps to flow emissions.
        // This ensures compatibility with flow-based subscribers.
        return callbackFlow {
            val consumer = object : FrameConsumer {
                override fun onFrame(frame: CameraFrame, releaseCallback: () -> Unit) {
                    trySend(frame)
                    // Release claim on the frame immediately since standard flow collection
                    // is pulled asynchronously.
                    releaseCallback()
                }
            }
            frameDistributor.registerConsumer(consumer)
            awaitClose {
                frameDistributor.unregisterConsumer(consumer)
            }
        }
    }

    override fun stopFrameStream() {
        // Managed by CameraX lifecycle controller
    }

    override fun analyze(imageProxy: ImageProxy) {
        val timestamp = imageProxy.imageInfo.timestamp
        val width = imageProxy.width
        val height = imageProxy.height
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees

        val cameraFrame = CameraFrame(
            timestamp = timestamp,
            width = width,
            height = height,
            rotationDegrees = rotationDegrees,
            imageProxy = imageProxy
        )

        // Delegate frame distribution to FrameDistributor which owns the close lifecycle
        frameDistributor.distributeFrame(cameraFrame)
    }
}
