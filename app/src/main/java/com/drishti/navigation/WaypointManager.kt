package com.drishti.navigation

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WaypointManager @Inject constructor() {
    private val arrivalRadiusMeters = 6.0f // standard walking arrival detection radius

    /**
     * Updates progress along waypoints. Returns true if active waypoint index changed (completed).
     */
    fun updateProgress(currentLocation: LatLng, session: NavigationSession): Boolean {
        if (!session.isActive || session.waypoints.isEmpty()) return false

        val currentWaypoint = session.currentWaypoint ?: return false
        val distance = currentLocation.distanceTo(currentWaypoint.location)

        if (distance < arrivalRadiusMeters) {
            currentWaypoint.isVisited = true
            val nextIndex = session.currentWaypointIndex + 1
            if (nextIndex < session.waypoints.size) {
                session.currentWaypointIndex = nextIndex
                return true
            }
        }
        return false
    }

    /**
     * Calculates bearing in degrees (0..360) from start coordinates to end coordinates.
     */
    fun calculateBearing(start: LatLng, end: LatLng): Float {
        val startLat = Math.toRadians(start.latitude)
        val startLng = Math.toRadians(start.longitude)
        val endLat = Math.toRadians(end.latitude)
        val endLng = Math.toRadians(end.longitude)

        val dLng = endLng - startLng
        val y = Math.sin(dLng) * Math.cos(endLat)
        val x = Math.cos(startLat) * Math.sin(endLat) - Math.sin(startLat) * Math.cos(endLat) * Math.cos(dLng)
        
        val bearingRad = Math.atan2(y, x)
        val bearingDeg = Math.toDegrees(bearingRad)
        return ((bearingDeg + 360) % 360).toFloat()
    }
}
