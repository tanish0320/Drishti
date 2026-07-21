package com.drishti.speech

import java.util.concurrent.LinkedBlockingQueue
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeechQueue @Inject constructor() {

    private val queue = LinkedBlockingQueue<SpeechQueueItem>()

    fun enqueue(text: String, isCritical: Boolean) {
        // If critical, we usually want to clear normal announcements and interrupt
        if (isCritical) {
            queue.clear()
        }
        queue.put(SpeechQueueItem(text, isCritical, System.currentTimeMillis()))
    }

    fun poll(): SpeechQueueItem? {
        return queue.poll()
    }

    fun clear() {
        queue.clear()
    }

    fun size(): Int = queue.size

    fun isEmpty(): Boolean = queue.isEmpty()
}

data class SpeechQueueItem(
    val text: String,
    val isCritical: Boolean,
    val timestamp: Long
)
