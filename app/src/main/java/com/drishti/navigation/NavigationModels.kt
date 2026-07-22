package com.drishti.navigation

import android.os.Parcelable

data class LatLng(
    val latitude: Double,
    val longitude: Double
) {
    fun distanceTo(other: LatLng): Float {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(
            this.latitude, this.longitude,
            other.latitude, other.longitude,
            results
        )
        return results[0]
    }
}

enum class NavigationIntentType {
    NAVIGATE,
    FIND_NEARBY,
    STOP,
    UNKNOWN
}

data class NavigationIntent(
    val type: NavigationIntentType,
    val destinationQuery: String = "",
    val category: String = "" // "medical store", "pharmacy", "atm", etc.
)

data class Waypoint(
    val index: Int,
    val location: LatLng,
    val instruction: String,
    val distanceToNext: Float = 0f,
    var isVisited: Boolean = false
)

data class NavigationSession(
    val destinationName: String,
    val routePoints: List<LatLng>,
    val waypoints: List<Waypoint>,
    var currentWaypointIndex: Int = 0,
    var distanceRemainingMeters: Float = 0f,
    var durationRemainingSeconds: Int = 0,
    var isActive: Boolean = false
) {
    val currentWaypoint: Waypoint?
        get() = if (currentWaypointIndex in waypoints.indices) waypoints[currentWaypointIndex] else null

    val nextWaypoint: Waypoint?
        get() = if (currentWaypointIndex + 1 in waypoints.indices) waypoints[currentWaypointIndex + 1] else null
}

data class PlaceSearchResult(
    val name: String,
    val address: String,
    val location: LatLng,
    val distanceMeters: Float
)
