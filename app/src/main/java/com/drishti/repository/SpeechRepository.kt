package com.drishti.repository

import com.drishti.speech.SpeechEngine
import com.drishti.models.SpeechRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeechRepository @Inject constructor(
    private val speechEngine: SpeechEngine
) {
    fun speakText(text: String, language: String) {
        // TODO: Create SpeechRequest and invoke speechEngine in Phase 2
        speechEngine.speak(SpeechRequest(text, language))
    }

    fun stopSpeaking() {
        // TODO: Call speechEngine.stop() in Phase 2
        speechEngine.stop()
    }
}
