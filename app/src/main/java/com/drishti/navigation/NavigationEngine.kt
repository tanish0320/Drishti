package com.drishti.navigation

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.*
import com.drishti.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NavigationEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val routeManager: RouteManager,
    private val waypointManager: WaypointManager,
    private val settingsRepository: SettingsRepository
) {
    private val scope = CoroutineScope(Dispatchers.Default)
    private var fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
    
    private val _currentLocation = MutableStateFlow<LatLng?>(null)
    val currentLocation: StateFlow<LatLng?> = _currentLocation.asStateFlow()

    private val _currentBearing = MutableStateFlow(0f)
    val currentBearing: StateFlow<Float> = _currentBearing.asStateFlow()

    private val _currentInstruction = MutableStateFlow("Tap Voice Command to start navigating.")
    val currentInstruction: StateFlow<String> = _currentInstruction.asStateFlow()

    private val _navigationSession = MutableStateFlow<NavigationSession?>(null)
    val navigationSession: StateFlow<NavigationSession?> = _navigationSession.asStateFlow()

    private val _currentSpeed = MutableStateFlow(0f)
    val currentSpeed: StateFlow<Float> = _currentSpeed.asStateFlow()

    private val _gpsAccuracy = MutableStateFlow(0f)
    val gpsAccuracy: StateFlow<Float> = _gpsAccuracy.asStateFlow()

    private var locationCallback: LocationCallback? = null
    private var lastRecalculationTime = 0L

    companion object {
        private const val OFF_ROUTE_THRESHOLD_METERS = 20.0f
        private const val RECALCULATION_COOLDOWN_MS = 10000L
    }

    init {
        // Observe route changes from RouteManager
        scope.launch {
            routeManager.activeSession.collect { session ->
                _navigationSession.value = session
                if (session == null) {
                    _currentInstruction.value = "Tap Voice Command to start navigating."
                } else {
                    updateInstructionForSession(session)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        if (locationCallback != null) return

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L).apply {
            setMinUpdateIntervalMillis(1000L)
            setWaitForAccurateLocation(false)
        }.build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val loc = locationResult.lastLocation ?: return
                val newLatLng = LatLng(loc.latitude, loc.longitude)
                
                // Track user's compass direction
                if (loc.hasBearing()) {
                    _currentBearing.value = loc.bearing
                } else {
                    _currentLocation.value?.let { prev ->
                        if (prev.distanceTo(newLatLng) > 1.0f) {
                            _currentBearing.value = waypointManager.calculateBearing(prev, newLatLng)
                        }
                    }
                }
                
                _currentLocation.value = newLatLng
                _currentSpeed.value = loc.speed
                _gpsAccuracy.value = loc.accuracy
                processLocationUpdate(newLatLng)
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
            Log.i("NavigationEngine", "Location updates started successfully.")
        } catch (e: Exception) {
            Log.e("NavigationEngine", "Failed to start location updates", e)
        }
    }

    fun stopLocationUpdates() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
            locationCallback = null
            Log.i("NavigationEngine", "Location updates stopped.")
        }
    }

    private fun processLocationUpdate(location: LatLng) {
        val session = _navigationSession.value ?: return
        if (!session.isActive) return

        // 1. Update waypoints progress
        val changed = waypointManager.updateProgress(location, session)
        
        // 2. Check if we've drifted too far off course
        if (checkIfOffRoute(location, session)) {
            val currentTime = System.currentTimeMillis()
            if (settingsRepository.settings.value.routeRecalculation &&
                (currentTime - lastRecalculationTime > RECALCULATION_COOLDOWN_MS)) {
                lastRecalculationTime = currentTime
                recalculateRoute(location, session.destinationName)
                return
            }
        }

        // 3. Update session properties (remaining distance/time)
        updateSessionMetrics(location, session)

        if (changed) {
            updateInstructionForSession(session)
        }
    }

    private fun updateInstructionForSession(session: NavigationSession) {
        val waypoint = session.currentWaypoint
        if (waypoint != null) {
            _currentInstruction.value = waypoint.instruction
        } else {
            _currentInstruction.value = "You have arrived at ${session.destinationName}."
            session.isActive = false
        }
    }

    private fun updateSessionMetrics(location: LatLng, session: NavigationSession) {
        val currentWp = session.currentWaypoint ?: return
        val distanceToWp = location.distanceTo(currentWp.location)

        // Sum distance to current waypoint + distance of remaining waypoints
        var totalDist = distanceToWp
        for (i in (session.currentWaypointIndex + 1) until session.waypoints.size) {
            totalDist += session.waypoints[i].distanceToNext
        }

        session.distanceRemainingMeters = totalDist
        session.durationRemainingSeconds = (totalDist / 1.4).toInt() // 1.4 m/s walking speed
        
        _navigationSession.value = session.copy() // trigger StateFlow emission
    }

    private fun checkIfOffRoute(location: LatLng, session: NavigationSession): Boolean {
        if (session.routePoints.isEmpty()) return false
        
        // Find closest point in route polyline
        val minDistance = session.routePoints.minOf { pt -> location.distanceTo(pt) }
        return minDistance > OFF_ROUTE_THRESHOLD_METERS
    }

    private fun recalculateRoute(location: LatLng, destinationName: String) {
        Log.w("NavigationEngine", "User drifted off-route! Recalculating route...")
        _currentInstruction.value = "Off route. Recalculating path..."
        
        val lastSession = _navigationSession.value
        val destCoords = lastSession?.routePoints?.lastOrNull()

        scope.launch {
            try {
                routeManager.calculateRoute(location, destinationName, destCoords)
            } catch (e: Exception) {
                Log.e("NavigationEngine", "Route recalculation failed", e)
            }
        }
    }

    suspend fun startNavigation(destinationName: String, destinationCoords: LatLng? = null) {
        // Try to get current location first, default to center coordinates of Bangalore if null
        val startLoc = _currentLocation.value ?: LatLng(12.9716, 77.5946)
        _currentInstruction.value = "Calculating route to $destinationName..."
        
        try {
            routeManager.calculateRoute(startLoc, destinationName, destinationCoords)
        } catch (e: Exception) {
            _currentInstruction.value = "Failed to find route. Destination not found."
            Log.e("NavigationEngine", "Error starting navigation session", e)
        }
    }

    fun stopNavigation() {
        routeManager.clearRoute()
    }
}
