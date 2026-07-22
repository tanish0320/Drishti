package com.drishti.speech

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.drishti.models.SpeechRequest
import com.drishti.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeechEngineImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val speechQueue: SpeechQueue
) : SpeechEngine {

    private val _currentSpokenMessageState = MutableStateFlow<String>("")
    override val currentSpokenMessageState: StateFlow<String> = _currentSpokenMessageState.asStateFlow()

    private val _speechQueueLengthState = MutableStateFlow<Int>(0)
    override val speechQueueLengthState: StateFlow<Int> = _speechQueueLengthState.asStateFlow()

    private val _ttsStatusState = MutableStateFlow<String>("INITIALIZING")
    override val ttsStatusState: StateFlow<String> = _ttsStatusState.asStateFlow()

    private val _lastAnnouncementTimestampState = MutableStateFlow<Long>(0L)
    override val lastAnnouncementTimestampState: StateFlow<Long> = _lastAnnouncementTimestampState.asStateFlow()

    private var tts: TextToSpeech? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                _ttsStatusState.value = "READY"
                setupProgressListener()
                observeSettings()
                playNextInQueue()
            } else {
                _ttsStatusState.value = "INIT_FAILED"
                Log.e("SpeechEngineImpl", "TextToSpeech initialization failed with status: $status")
            }
        }
    }

    private fun setupProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d("DrishtiDebug", "Speech started: ${utteranceId ?: ""}")
                _currentSpokenMessageState.value = utteranceId ?: ""
                _lastAnnouncementTimestampState.value = System.currentTimeMillis()
            }

            override fun onDone(utteranceId: String?) {
                Log.d("DrishtiDebug", "Speech stopped: ${utteranceId ?: ""}")
                _currentSpokenMessageState.value = ""
                coroutineScope.launch {
                    playNextInQueue()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                Log.d("DrishtiDebug", "Speech stopped (error): ${utteranceId ?: ""}")
                _currentSpokenMessageState.value = ""
                _ttsStatusState.value = "ERROR"
                coroutineScope.launch {
                    playNextInQueue()
                }
            }
        })
    }

    private fun observeSettings() {
        coroutineScope.launch {
            settingsRepository.settings.collect { settings ->
                if (settings.speechEnabled) {
                    setLanguage(settings.language)
                    setSpeechRate(settings.speechRate)
                    setPitch(settings.pitch)
                } else {
                    stop()
                }
            }
        }
    }

    override fun speak(request: SpeechRequest) {
        val settings = settingsRepository.settings.value
        if (!settings.speechEnabled) return
        
        speakQueueItem(request.text, false)
    }

    override fun speakQueueItem(text: String, isCritical: Boolean) {
        val settings = settingsRepository.settings.value
        if (!settings.speechEnabled) return

        if (isCritical) {
            speechQueue.clear()
            _speechQueueLengthState.value = 0
            if (_ttsStatusState.value == "READY") {
                speakInternal(text, true)
            }
        } else {
            speechQueue.enqueue(text, false)
            _speechQueueLengthState.value = speechQueue.size()
            if (_ttsStatusState.value == "READY" && tts?.isSpeaking == false) {
                playNextInQueue()
            }
        }
    }

    private fun speakInternal(text: String, isCritical: Boolean) {
        val queueMode = if (isCritical) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, text)
        }
        
        val result = tts?.speak(text, queueMode, params, text)
        if (result == TextToSpeech.ERROR) {
            Log.e("SpeechEngineImpl", "Error executing speak command for text: $text")
            coroutineScope.launch {
                playNextInQueue()
            }
        }
    }

    private fun playNextInQueue() {
        _speechQueueLengthState.value = speechQueue.size()
        if (_ttsStatusState.value != "READY") return
        if (speechQueue.isEmpty()) return
        val item = speechQueue.poll()
        if (item != null) {
            speakInternal(item.text, item.isCritical)
        }
    }

    override fun stop() {
        speechQueue.clear()
        _speechQueueLengthState.value = 0
        _currentSpokenMessageState.value = ""
        tts?.stop()
    }

    override fun setLanguage(language: String) {
        val locale = when (language.lowercase()) {
            "hindi", "hi" -> Locale("hi", "IN")
            else -> Locale.US
        }
        tts?.let {
            val result = it.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("SpeechEngineImpl", "Language not supported or missing data: $language")
                _ttsStatusState.value = "LANG_UNSUPPORTED"
            } else {
                if (_ttsStatusState.value == "LANG_UNSUPPORTED") {
                    _ttsStatusState.value = "READY"
                }
            }
        }
    }

    override fun setSpeechRate(rate: Float) {
        tts?.setSpeechRate(rate)
    }

    override fun setPitch(pitch: Float) {
        tts?.setPitch(pitch)
    }

    override fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
            _ttsStatusState.value = "SHUTDOWN"
            Log.i("SpeechEngineImpl", "TextToSpeech engine shut down successfully.")
        } catch (e: Exception) {
            Log.e("SpeechEngineImpl", "Error during TextToSpeech shutdown", e)
        }
    }
}
