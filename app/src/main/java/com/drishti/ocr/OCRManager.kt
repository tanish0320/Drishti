package com.drishti.ocr

import com.drishti.speech.SpeechEngine
import com.drishti.repository.SettingsRepository
import com.drishti.repository.ControllerRepository
import com.drishti.models.AppMode
import com.drishti.models.SpeechRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OCRManager @Inject constructor(
    private val ocrProcessor: OCRProcessor,
    private val speechEngine: SpeechEngine,
    private val settingsRepository: SettingsRepository,
    private val controllerRepository: ControllerRepository
) {
    private val scope = CoroutineScope(Dispatchers.Default)
    private var lastSpokenText = ""
    private var lastSpokenTime = 0L

    companion object {
        // Repeat cooldown for announcements of the same static text block
        private const val REPEAT_COOLDOWN_MS = 8000L
    }

    /**
     * Starts flow observations for text recognition and app mode.
     */
    fun startObserving() {
        scope.launch {
            ocrProcessor.recognizedTextState.collect { text ->
                processOcrText(text)
            }
        }
        
        scope.launch {
            controllerRepository.effectiveMode.collect { mode ->
                if (mode != AppMode.READ) {
                    lastSpokenText = ""
                }
            }
        }
    }

    private fun processOcrText(text: String) {
        val settings = settingsRepository.settings.value
        val isReadMode = controllerRepository.effectiveMode.value == AppMode.READ

        if (!settings.speechEnabled || !settings.ocrEnabled || !isReadMode || text.isEmpty()) return

        val currentTime = System.currentTimeMillis()
        val isNewText = text != lastSpokenText
        val isCooldownElapsed = (currentTime - lastSpokenTime) > REPEAT_COOLDOWN_MS

        if (isNewText || isCooldownElapsed) {
            speechEngine.speak(SpeechRequest(text, settings.language))
            lastSpokenText = text
            lastSpokenTime = currentTime
        }
    }
}
