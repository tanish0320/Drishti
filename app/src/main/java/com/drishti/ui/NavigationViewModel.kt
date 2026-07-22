package com.drishti.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drishti.detection.DecisionEngine
import com.drishti.detection.IndoorSceneReasoner
import com.drishti.detection.SceneAnalysis
import com.drishti.models.NavigationDecision
import com.drishti.navigation.*
import com.drishti.repository.SettingsRepository
import com.drishti.speech.SpeechEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NavigationViewModel @Inject constructor(
    val navigationEngine: NavigationEngine,
    val speechRecognitionManager: SpeechRecognitionManager,
    val voiceCommandManager: VoiceCommandManager,
    val settingsRepository: SettingsRepository,
    val mapsRepository: MapsRepository,
    val speechEngine: SpeechEngine,
    val indoorSceneReasoner: IndoorSceneReasoner,
    val decisionEngine: DecisionEngine
) : ViewModel() {

    val currentLocation: StateFlow<LatLng?> = navigationEngine.currentLocation
    val currentBearing: StateFlow<Float> = navigationEngine.currentBearing
    val currentInstruction: StateFlow<String> = navigationEngine.currentInstruction
    val navigationSession: StateFlow<NavigationSession?> = navigationEngine.navigationSession
    val speechStatus: StateFlow<SpeechRecognitionStatus> = speechRecognitionManager.status
    val recognizedText: StateFlow<String> = speechRecognitionManager.recognizedText
    val currentSpeed: StateFlow<Float> = navigationEngine.currentSpeed
    val gpsAccuracy: StateFlow<Float> = navigationEngine.gpsAccuracy
    val sceneAnalysis: StateFlow<SceneAnalysis> = indoorSceneReasoner.sceneAnalysisState
    val navigationDecision: StateFlow<NavigationDecision> = decisionEngine.navigationDecisionState

    private val _searchCandidates = MutableStateFlow<List<PlaceSearchResult>>(emptyList())
    val searchCandidates: StateFlow<List<PlaceSearchResult>> = _searchCandidates.asStateFlow()

    private val _showCandidateSelection = MutableStateFlow(false)
    val showCandidateSelection: StateFlow<Boolean> = _showCandidateSelection.asStateFlow()

    private val _isSpeechMuted = MutableStateFlow(false)
    val isSpeechMuted: StateFlow<Boolean> = _isSpeechMuted.asStateFlow()

    fun toggleMuteSpeech() {
        _isSpeechMuted.value = !_isSpeechMuted.value
        viewModelScope.launch {
            settingsRepository.updateVoiceNav(!_isSpeechMuted.value)
        }
    }

    fun startListening() {
        speechRecognitionManager.startListening()
    }

    fun stopListeningAndParse() {
        speechRecognitionManager.stopListening()
        viewModelScope.launch {
            val text = speechRecognitionManager.recognizedText.value
            if (text.isNotEmpty()) {
                val intent = voiceCommandManager.parseCommand(text)
                executeIntent(intent)
            }
        }
    }

    fun executeIntent(intent: NavigationIntent) {
        viewModelScope.launch {
            when (intent.type) {
                NavigationIntentType.NAVIGATE, NavigationIntentType.FIND_NEARBY -> {
                    val currentLoc = currentLocation.value ?: LatLng(12.9716, 77.5946)
                    val candidates = mapsRepository.searchPlaces(intent.destinationQuery, currentLoc)
                    if (candidates.isEmpty()) {
                        speechEngine.speakQueueItem("No matches found for: ${intent.destinationQuery}", false)
                    } else if (candidates.size == 1) {
                        navigationEngine.startNavigation(candidates[0].name, candidates[0].location)
                    } else {
                        _searchCandidates.value = candidates
                        _showCandidateSelection.value = true
                        speechEngine.speakQueueItem("Multiple locations found. Please choose from the list.", false)
                    }
                }
                NavigationIntentType.STOP -> {
                    navigationEngine.stopNavigation()
                }
                else -> {
                    // Handle unknown command
                }
            }
        }
    }

    fun selectCandidate(candidate: PlaceSearchResult) {
        _showCandidateSelection.value = false
        _searchCandidates.value = emptyList()
        viewModelScope.launch {
            navigationEngine.startNavigation(candidate.name, candidate.location)
        }
    }

    fun dismissCandidateSelection() {
        _showCandidateSelection.value = false
        _searchCandidates.value = emptyList()
    }

    fun startNavigationDirectly(destination: String) {
        viewModelScope.launch {
            val currentLoc = currentLocation.value ?: LatLng(12.9716, 77.5946)
            val candidates = mapsRepository.searchPlaces(destination, currentLoc)
            if (candidates.isNotEmpty()) {
                navigationEngine.startNavigation(candidates[0].name, candidates[0].location)
            } else {
                navigationEngine.startNavigation(destination)
            }
        }
    }

    fun stopNavigation() {
        navigationEngine.stopNavigation()
    }

    fun startLocationUpdates() {
        navigationEngine.startLocationUpdates()
    }

    fun stopLocationUpdates() {
        navigationEngine.stopLocationUpdates()
    }

    override fun onCleared() {
        super.onCleared()
        speechRecognitionManager.destroy()
        navigationEngine.stopLocationUpdates()
    }
}
