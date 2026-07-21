package com.drishti.speech

import com.drishti.models.SpeechRequest
import kotlinx.coroutines.flow.StateFlow

interface SpeechEngine {
    val currentSpokenMessageState: StateFlow<String>
    val speechQueueLengthState: StateFlow<Int>
    val ttsStatusState: StateFlow<String>
    val lastAnnouncementTimestampState: StateFlow<Long>

    fun speak(request: SpeechRequest)
    fun speakQueueItem(text: String, isCritical: Boolean)
    fun stop()
    fun setLanguage(language: String)
    fun setSpeechRate(rate: Float)
    fun setPitch(pitch: Float)
    fun shutdown()
}
