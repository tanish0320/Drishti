package com.drishti.camera

import com.drishti.models.CameraFrame
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FrameDistributor @Inject constructor() {

    private val consumers = CopyOnWriteArrayList<FrameConsumer>()
    private val frameRefCounts = ConcurrentHashMap<Long, AtomicInteger>()

    /**
     * Registers a new frame consumer.
     */
    fun registerConsumer(consumer: FrameConsumer) {
        if (!consumers.contains(consumer)) {
            consumers.add(consumer)
        }
    }

    /**
     * Unregisters an existing frame consumer.
     */
    fun unregisterConsumer(consumer: FrameConsumer) {
        consumers.remove(consumer)
    }

    /**
     * Unregisters all consumers.
     */
    fun clearConsumers() {
        consumers.clear()
    }

    /**
     * Returns the number of currently registered consumers.
     */
    fun getConsumersCount(): Int {
        return consumers.size
    }

    /**
     * Distributes a frame to all registered consumers.
     * Manages reference counting and closes the underlying [ImageProxy] 
     * once the count reaches 0.
     */
    fun distributeFrame(frame: CameraFrame) {
        val activeConsumers = consumers.toList()
        val numConsumers = activeConsumers.size

        if (numConsumers == 0) {
            // Close immediately to prevent camera lock when no consumers are active
            frame.imageProxy.close()
            return
        }

        val refCount = AtomicInteger(numConsumers)
        frameRefCounts[frame.timestamp] = refCount

        activeConsumers.forEach { consumer ->
            // Create a single-use token to guard against double-release per consumer
            val token = FrameReleaseToken(frame.timestamp, refCount) { timestamp ->
                frameRefCounts.remove(timestamp)
                frame.imageProxy.close()
            }
            try {
                consumer.onFrame(frame) {
                    token.release()
                }
            } catch (e: Exception) {
                // If a consumer encounters an exception, release its claim automatically
                token.release()
            }
        }
    }

    private class FrameReleaseToken(
        private val timestamp: Long,
        private val refCount: AtomicInteger,
        private val onFinalRelease: (Long) -> Unit
    ) {
        private val released = AtomicBoolean(false)

        fun release() {
            if (released.compareAndSet(false, true)) {
                val currentCount = refCount.decrementAndGet()
                if (currentCount == 0) {
                    onFinalRelease(timestamp)
                }
            }
        }
    }
}
