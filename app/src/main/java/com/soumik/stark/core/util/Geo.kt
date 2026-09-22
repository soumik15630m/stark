package com.soumik.stark.core.util

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

/** Geo math. Coordinates are handled as decimal degrees here; storage scales to e7 ints. */
object Geo {

    const val EARTH_RADIUS_M = 6_371_000.0

    fun toE7(deg: Double): Int = Math.round(deg * 1e7).toInt()
    fun fromE7(e7: Int): Double = e7 / 1e7

    /**
     * Distance in metres between two points. Uses the equirectangular approximation for the
     * short consecutive hops that dominate the hot path (~mm error under a few hundred metres),
     * and full haversine only for long spans (gap straight-lines).
     */
    fun distanceM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLatDeg = abs(lat2 - lat1)
        val dLngDeg = abs(lng2 - lng1)
        // ~0.02 deg ≈ 2.2 km; below that the flat-earth approximation is well within GPS noise.
        return if (dLatDeg < 0.02 && dLngDeg < 0.02) {
            equirectangularM(lat1, lng1, lat2, lng2)
        } else {
            haversineM(lat1, lng1, lat2, lng2)
        }
    }

    fun equirectangularM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val latRad1 = Math.toRadians(lat1)
        val latRad2 = Math.toRadians(lat2)
        val meanLat = (latRad1 + latRad2) / 2.0
        val x = Math.toRadians(lng2 - lng1) * cos(meanLat)
        val y = Math.toRadians(lat2 - lat1)
        return sqrt(x * x + y * y) * EARTH_RADIUS_M
    }

    fun haversineM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLng / 2) * kotlin.math.sin(dLng / 2)
        val c = 2 * kotlin.math.atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_M * c
    }
}
