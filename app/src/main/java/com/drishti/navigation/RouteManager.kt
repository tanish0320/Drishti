package com.drishti.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RouteManager @Inject constructor(
    private val mapsRepository: MapsRepository
) {
    private val _activeSession = MutableStateFlow<NavigationSession?>(null)
    val activeSession: StateFlow<NavigationSession?> = _activeSession.asStateFlow()

    suspend fun calculateRoute(
        origin: LatLng,
        destinationQuery: String,
        destinationCoords: LatLng? = null
    ): NavigationSession {
        val session = mapsRepository.getWalkingRoute(origin, destinationQuery, destinationCoords)
        _activeSession.value = session
        return session
    }

    fun clearRoute() {
        _activeSession.value = null
    }

    fun updateSession(session: NavigationSession) {
        _activeSession.value = session
    }
}
