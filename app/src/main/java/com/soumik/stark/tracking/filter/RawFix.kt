package com.soumik.stark.tracking.filter

import android.location.Location

/** Platform-independent raw fix, so the filter can be unit-tested without android.location. */
data class RawFix(
    val tUtc: Long,
    val lat: Double,
    val lng: Double,
    val accuracyM: Float,
    val hasAccuracy: Boolean,
    val speedMps: Float,
    val hasSpeed: Boolean,
) {
    companion object {
        fun from(loc: Location) = RawFix(
            tUtc = loc.time,
            lat = loc.latitude,
            lng = loc.longitude,
            accuracyM = if (loc.hasAccuracy()) loc.accuracy else 0f,
            hasAccuracy = loc.hasAccuracy(),
            speedMps = if (loc.hasSpeed()) loc.speed else 0f,
            hasSpeed = loc.hasSpeed(),
        )
    }
}
