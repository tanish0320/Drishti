package com.drishti.speech

import com.drishti.models.NavigationDecision
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NavigationSpeechGenerator @Inject constructor(
    private val formatter: SpeechFormatter
) {
    /**
     * Translates a navigation decision into a clean audio-ready speech string.
     */
    fun generateSpeech(decision: NavigationDecision): String {
        return formatter.format(decision)
    }
}
