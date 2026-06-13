package com.example

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics

object AnalyticsHelper {
    private var firebaseAnalytics: FirebaseAnalytics? = null

    fun init(context: Context) {
        try {
            firebaseAnalytics = FirebaseAnalytics.getInstance(context.applicationContext)
            Log.d("AnalyticsHelper", "Firebase Analytics initialized successfully")
        } catch (e: Exception) {
            Log.e("AnalyticsHelper", "Firebase Analytics initialization failed: ${e.message}", e)
        }
    }

    fun logEvent(eventName: String, params: Bundle? = null) {
        try {
            firebaseAnalytics?.logEvent(eventName, params)
            Log.i("AnalyticsHelper", "Event logged: $eventName with params: $params")
        } catch (e: Exception) {
            Log.e("AnalyticsHelper", "Error logging event $eventName: ${e.message}", e)
        }
    }

    fun setUserProperty(name: String, value: String) {
        try {
            firebaseAnalytics?.setUserProperty(name, value)
            Log.i("AnalyticsHelper", "User property set: $name = $value")
        } catch (e: Exception) {
            Log.e("AnalyticsHelper", "Error setting user property $name: ${e.message}", e)
        }
    }
}
