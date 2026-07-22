package com.drishti.navigation

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceCommandManager @Inject constructor() {

    fun parseCommand(spokenText: String): NavigationIntent {
        val normalized = spokenText.trim().lowercase()

        // 1. Check for cancellation / stop commands
        if (normalized.contains("stop navigation") || normalized.contains("cancel navigation") || normalized.contains("stop navigate") || normalized == "stop") {
            return NavigationIntent(type = NavigationIntentType.STOP)
        }

        // 2. Extract destination based on common walking voice phrases
        val prefixes = listOf(
            "take me to the nearest",
            "take me to",
            "navigate to the nearest",
            "navigate to",
            "go to the nearest",
            "go to",
            "find the nearest",
            "find"
        )

        for (prefix in prefixes) {
            if (normalized.startsWith(prefix)) {
                val destination = normalized.removePrefix(prefix).trim().trim('.', '?')
                if (destination.isNotEmpty()) {
                    val category = getCategoryForDestination(destination)
                    return NavigationIntent(
                        type = if (prefix.contains("nearest")) NavigationIntentType.FIND_NEARBY else NavigationIntentType.NAVIGATE,
                        destinationQuery = destination,
                        category = category
                    )
                }
            }
        }

        // 3. Fallback matching anywhere in string
        if (normalized.contains("navigate") || normalized.contains("take me to") || normalized.contains("go to")) {
            val destination = normalized
                .replace("navigate to", "")
                .replace("navigate", "")
                .replace("take me to", "")
                .replace("go to", "")
                .replace("nearest", "")
                .trim().trim('.', '?')
            if (destination.isNotEmpty()) {
                val category = getCategoryForDestination(destination)
                return NavigationIntent(
                    type = NavigationIntentType.NAVIGATE,
                    destinationQuery = destination,
                    category = category
                )
            }
        }

        // 4. Default raw fallback if it is just a name like "Apollo Pharmacy"
        if (normalized.isNotEmpty()) {
            return NavigationIntent(
                type = NavigationIntentType.NAVIGATE,
                destinationQuery = normalized,
                category = getCategoryForDestination(normalized)
            )
        }

        return NavigationIntent(type = NavigationIntentType.UNKNOWN)
    }

    private fun getCategoryForDestination(destination: String): String {
        return when {
            destination.contains("medical") || destination.contains("pharmacy") || destination.contains("chemist") || destination.contains("apollo") -> "medical store"
            destination.contains("atm") || destination.contains("bank") -> "atm"
            destination.contains("mall") || destination.contains("store") || destination.contains("orion") -> "store"
            destination.contains("airport") || destination.contains("bangalore") -> "airport"
            destination.contains("station") || destination.contains("metro") || destination.contains("railway") -> "station"
            destination.contains("home") -> "home"
            else -> "location"
        }
    }
}
