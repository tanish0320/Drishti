package com.drishti.navigation

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.drishti.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MapsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {

    private fun retrieveApiKey(): String {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_META_DATA
            )
            val key = appInfo.metaData?.getString("com.google.android.geo.API_KEY") ?: ""
            if (key.isEmpty() || key.contains("ReplaceMe")) "" else key
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Search destinations matching the query around the current location.
     */
    suspend fun searchPlaces(
        query: String,
        origin: LatLng
    ): List<PlaceSearchResult> = withContext(Dispatchers.IO) {
        val apiKey = retrieveApiKey()
        if (apiKey.isEmpty()) {
            Log.i("MapsRepository", "No production API key configured. Utilizing local fallback place search.")
            return@withContext getLocalMockPlaces(query, origin)
        }

        try {
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val urlString = "https://maps.googleapis.com/maps/api/place/textsearch/json?" +
                    "query=$encodedQuery" +
                    "&location=${origin.latitude},${origin.longitude}" +
                    "&radius=10000" +
                    "&key=$apiKey"

            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()

                val json = JSONObject(response.toString())
                val status = json.optString("status")
                if (status == "OK" || status == "ZERO_RESULTS") {
                    val resultsArray = json.getJSONArray("results")
                    val resultsList = mutableListOf<PlaceSearchResult>()

                    for (i in 0 until resultsArray.length()) {
                        val result = resultsArray.getJSONObject(i)
                        val name = result.optString("name", "Unknown Location")
                        val address = result.optString("formatted_address", "Unknown Address")
                        val geometry = result.getJSONObject("geometry")
                        val loc = geometry.getJSONObject("location")
                        val placeLatLng = LatLng(loc.getDouble("lat"), loc.getDouble("lng"))
                        val distance = origin.distanceTo(placeLatLng)

                        resultsList.add(
                            PlaceSearchResult(
                                name = name,
                                address = address,
                                location = placeLatLng,
                                distanceMeters = distance
                            )
                        )
                    }
                    resultsList.sortBy { it.distanceMeters }
                    return@withContext resultsList
                } else {
                    Log.w("MapsRepository", "Places textsearch returned non-OK status: $status")
                }
            }
        } catch (e: Exception) {
            Log.e("MapsRepository", "Places API textsearch request failed", e)
        }

        return@withContext getLocalMockPlaces(query, origin)
    }

    /**
     * Obtains the walking route from origin to destination.
     * Gracefully falls back to mock route generation if internet/API key is unavailable.
     */
    suspend fun getWalkingRoute(
        origin: LatLng,
        destinationQuery: String,
        destinationCoords: LatLng? = null
    ): NavigationSession = withContext(Dispatchers.IO) {
        val dest = destinationCoords ?: resolveQueryToCoords(destinationQuery, origin)
        val settings = settingsRepository.settings.value
        val apiKey = retrieveApiKey()

        if (apiKey.isNotEmpty()) {
            try {
                val urlString = "https://maps.googleapis.com/maps/api/directions/json?" +
                        "origin=${origin.latitude},${origin.longitude}" +
                        "&destination=${dest.latitude},${dest.longitude}" +
                        "&mode=walking" +
                        (if (settings.avoidBusyRoads) "&avoid=highways" else "") +
                        "&key=$apiKey"

                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    reader.close()

                    val json = JSONObject(response.toString())
                    val status = json.optString("status")
                    if (status == "OK") {
                        return@withContext parseGoogleDirectionsResponse(json, destinationQuery)
                    } else {
                        Log.w("MapsRepository", "Google Maps Directions API returned status: $status. Using fallback route.")
                    }
                }
            } catch (e: Exception) {
                Log.e("MapsRepository", "Failed to contact Google Directions API. Using fallback route.", e)
            }
        }

        // Fallback mock route generator
        return@withContext generateFallbackRoute(origin, dest, destinationQuery)
    }

    private fun resolveQueryToCoords(query: String, current: LatLng): LatLng {
        // Resolve natural query landmarks to relative coordinates around current position
        val offset = when {
            query.contains("medical", ignoreCase = true) || query.contains("pharmacy", ignoreCase = true) || query.contains("apollo", ignoreCase = true) -> Pair(0.002, 0.001)
            query.contains("atm", ignoreCase = true) || query.contains("bank", ignoreCase = true) -> Pair(-0.001, 0.002)
            query.contains("mall", ignoreCase = true) || query.contains("orion", ignoreCase = true) -> Pair(0.003, -0.002)
            query.contains("airport", ignoreCase = true) -> Pair(0.008, 0.008)
            query.contains("station", ignoreCase = true) || query.contains("railway", ignoreCase = true) -> Pair(-0.003, -0.001)
            query.contains("home", ignoreCase = true) -> Pair(-0.001, -0.001)
            else -> Pair(0.002, 0.002) // default offset
        }
        return LatLng(current.latitude + offset.first, current.longitude + offset.second)
    }

    private fun parseGoogleDirectionsResponse(json: JSONObject, destinationName: String): NavigationSession {
        val routePoints = mutableListOf<LatLng>()
        val waypoints = mutableListOf<Waypoint>()

        val route = json.getJSONArray("routes").getJSONObject(0)
        val leg = route.getJSONArray("legs").getJSONObject(0)
        val steps = leg.getJSONArray("steps")

        var distanceRemaining = leg.getJSONObject("distance").optDouble("value", 0.0).toFloat()
        val durationRemaining = leg.getJSONObject("duration").optInt("value", 0)

        for (i in 0 until steps.length()) {
            val step = steps.getJSONObject(i)
            val stepStart = step.getJSONObject("start_location")
            val stepLatLng = LatLng(stepStart.getDouble("lat"), stepStart.getDouble("lng"))
            
            // Clean HTML tags from Google Turn Instructions
            val htmlInstruction = step.optString("html_instructions", "Walk straight")
            val cleanInstruction = htmlInstruction.replace(Regex("<[^>]*>"), "")
            val stepDistance = step.getJSONObject("distance").optDouble("value", 0.0).toFloat()

            waypoints.add(
                Waypoint(
                    index = i,
                    location = stepLatLng,
                    instruction = cleanInstruction,
                    distanceToNext = stepDistance
                )
            )

            // Decode polyline points
            val polylineStr = step.getJSONObject("polyline").getString("points")
            routePoints.addAll(decodePolyline(polylineStr))
        }

        // Add destination point
        val endLocObj = leg.getJSONObject("end_location")
        val destLatLng = LatLng(endLocObj.getDouble("lat"), endLocObj.getDouble("lng"))
        if (routePoints.isEmpty() || routePoints.last() != destLatLng) {
            routePoints.add(destLatLng)
        }

        return NavigationSession(
            destinationName = destinationName,
            routePoints = routePoints,
            waypoints = waypoints,
            currentWaypointIndex = 0,
            distanceRemainingMeters = distanceRemaining,
            durationRemainingSeconds = durationRemaining,
            isActive = true
        )
    }

    private fun generateFallbackRoute(origin: LatLng, dest: LatLng, destinationName: String): NavigationSession {
        Log.i("MapsRepository", "Generating fallback walking route from $origin to $dest")
        val routePoints = mutableListOf<LatLng>()
        val waypoints = mutableListOf<Waypoint>()

        // Generate a simple intermediate step (L-shape route for realism)
        val intermediate = LatLng(dest.latitude, origin.longitude)
        
        routePoints.add(origin)
        
        // Populate path points
        val segments = 20
        for (i in 0..segments) {
            val fraction = i.toDouble() / segments
            val lat = origin.latitude + (intermediate.latitude - origin.latitude) * fraction
            val lng = origin.longitude + (intermediate.longitude - origin.longitude) * fraction
            routePoints.add(LatLng(lat, lng))
        }
        for (i in 0..segments) {
            val fraction = i.toDouble() / segments
            val lat = intermediate.latitude + (dest.latitude - intermediate.latitude) * fraction
            val lng = intermediate.longitude + (dest.longitude - intermediate.longitude) * fraction
            routePoints.add(LatLng(lat, lng))
        }

        // Create turn-by-turn waypoints
        val dist1 = origin.distanceTo(intermediate)
        val dist2 = intermediate.distanceTo(dest)
        
        waypoints.add(
            Waypoint(
                index = 0,
                location = origin,
                instruction = "Walk straight for ${dist1.toInt()} meters toward intermediate crossing.",
                distanceToNext = dist1
            )
        )
        waypoints.add(
            Waypoint(
                index = 1,
                location = intermediate,
                instruction = "Turn right and walk ${dist2.toInt()} meters toward your destination.",
                distanceToNext = dist2
            )
        )
        waypoints.add(
            Waypoint(
                index = 2,
                location = dest,
                instruction = "Arrived at $destinationName.",
                distanceToNext = 0f
            )
        )

        return NavigationSession(
            destinationName = destinationName,
            routePoints = routePoints,
            waypoints = waypoints,
            currentWaypointIndex = 0,
            distanceRemainingMeters = dist1 + dist2,
            durationRemainingSeconds = ((dist1 + dist2) / 1.4).toInt(), // average walking speed 1.4 m/s
            isActive = true
        )
    }

    private fun getLocalMockPlaces(query: String, origin: LatLng): List<PlaceSearchResult> {
        val list = mutableListOf<PlaceSearchResult>()
        val queryLower = query.lowercase()

        when {
            queryLower.contains("medical") || queryLower.contains("pharmacy") || queryLower.contains("apollo") -> {
                list.add(
                    PlaceSearchResult(
                        name = "Apollo Pharmacy (Indiranagar)",
                        address = "12th Main Road, Indiranagar, Bengaluru",
                        location = LatLng(origin.latitude + 0.002, origin.longitude + 0.001),
                        distanceMeters = origin.distanceTo(LatLng(origin.latitude + 0.002, origin.longitude + 0.001))
                    )
                )
                list.add(
                    PlaceSearchResult(
                        name = "Apollo Pharmacy (MG Road)",
                        address = "MG Road Metro Station, Bengaluru",
                        location = LatLng(origin.latitude + 0.009, origin.longitude + 0.005),
                        distanceMeters = origin.distanceTo(LatLng(origin.latitude + 0.009, origin.longitude + 0.005))
                    )
                )
            }
            queryLower.contains("airport") || queryLower.contains("bangalore") -> {
                list.add(
                    PlaceSearchResult(
                        name = "Kempegowda International Airport Bengaluru",
                        address = "Devanahalli, Bengaluru, Karnataka",
                        location = LatLng(origin.latitude + 0.08, origin.longitude + 0.08),
                        distanceMeters = origin.distanceTo(LatLng(origin.latitude + 0.08, origin.longitude + 0.08))
                    )
                )
            }
            queryLower.contains("atm") || queryLower.contains("bank") -> {
                list.add(
                    PlaceSearchResult(
                        name = "SBI ATM (Near Post Office)",
                        address = "HAL 2nd Stage, Bengaluru",
                        location = LatLng(origin.latitude - 0.001, origin.longitude + 0.002),
                        distanceMeters = origin.distanceTo(LatLng(origin.latitude - 0.001, origin.longitude + 0.002))
                    )
                )
                list.add(
                    PlaceSearchResult(
                        name = "HDFC Bank ATM",
                        address = "Indiranagar Double Road, Bengaluru",
                        location = LatLng(origin.latitude + 0.003, origin.longitude - 0.002),
                        distanceMeters = origin.distanceTo(LatLng(origin.latitude + 0.003, origin.longitude - 0.002))
                    )
                )
            }
            queryLower.contains("home") -> {
                list.add(
                    PlaceSearchResult(
                        name = "Home (My Residence)",
                        address = "Your Saved Location",
                        location = LatLng(origin.latitude - 0.001, origin.longitude - 0.001),
                        distanceMeters = origin.distanceTo(LatLng(origin.latitude - 0.001, origin.longitude - 0.001))
                    )
                )
            }
            else -> {
                list.add(
                    PlaceSearchResult(
                        name = "$query (Estimated Destination)",
                        address = "Near current location",
                        location = LatLng(origin.latitude + 0.002, origin.longitude + 0.002),
                        distanceMeters = origin.distanceTo(LatLng(origin.latitude + 0.002, origin.longitude + 0.002))
                    )
                )
            }
        }
        return list
    }

    private fun decodePolyline(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shl 1).inv() else result shl 1
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shl 1).inv() else result shl 1
            lng += dlng

            val p = LatLng(
                lat.toDouble() / 1e5,
                lng.toDouble() / 1e5
            )
            poly.add(p)
        }
        return poly
    }
}
