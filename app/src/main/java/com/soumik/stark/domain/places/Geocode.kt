package com.soumik.stark.domain.places

import android.content.Context
import android.location.Geocoder
import java.util.Locale

/** Best-effort on-device reverse geocoding (design §3.2, keyless). Never blocks tracking. */
class Geocode(context: Context) {
    private val geocoder = if (Geocoder.isPresent()) Geocoder(context, Locale.US) else null

    @Suppress("DEPRECATION")
    fun label(lat: Double, lng: Double): String? {
        val g = geocoder ?: return null
        return try {
            val results = g.getFromLocation(lat, lng, 1)
            val a = results?.firstOrNull() ?: return null
            a.featureName ?: a.thoroughfare ?: a.subLocality ?: a.locality
        } catch (_: Exception) {
            null
        }
    }
}
