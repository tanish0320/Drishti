package com.drishti

import android.app.Application
import android.content.pm.PackageManager
import android.util.Log
import com.google.android.libraries.places.api.Places
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class DrishtiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initializePlaces()
    }

    private fun initializePlaces() {
        try {
            val appInfo = packageManager.getApplicationInfo(
                packageName,
                PackageManager.GET_META_DATA
            )
            val apiKey = appInfo.metaData?.getString("com.google.android.geo.API_KEY") ?: ""
            if (apiKey.isNotEmpty() && !apiKey.contains("ReplaceMe")) {
                Places.initialize(this, apiKey)
                Log.i("DrishtiApplication", "Places SDK initialized successfully.")
            } else {
                Log.w("DrishtiApplication", "Places API Key is missing or placeholder. Places SDK not initialized.")
            }
        } catch (e: Exception) {
            Log.e("DrishtiApplication", "Error initializing Places SDK", e)
        }
    }
}
