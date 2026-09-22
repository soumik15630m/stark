package com.soumik.stark.core.util

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan
import kotlin.math.cos

/**
 * Integer spatial keys (design §4B): snap coordinates to grid cells and slippy tiles so
 * "which place / heat tile is this?" is an O(1) key lookup, not a distance scan.
 */
object GeoCell {

    /** ~55 m grid cell for incremental place clustering. Packs lat/lng cell indices into a long. */
    private const val CELL_DEG = 0.0005

    fun placeCell(lat: Double, lng: Double): Long {
        val latIdx = Math.floor((lat + 90.0) / CELL_DEG).toLong()
        val lngIdx = Math.floor((lng + 180.0) / CELL_DEG).toLong()
        return latIdx * 1_000_000L + lngIdx
    }

    /** Slippy-map tile X at zoom z. */
    fun tileX(lng: Double, z: Int): Int {
        val n = 1 shl z
        return ((lng + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
    }

    fun tileY(lat: Double, z: Int): Int {
        val n = 1 shl z
        val latRad = Math.toRadians(lat)
        val y = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n
        return y.toInt().coerceIn(0, n - 1)
    }

    fun tileToLat(y: Int, z: Int): Double {
        val n = 1 shl z
        val t = PI * (1 - 2.0 * y / n)
        return Math.toDegrees(atan(sinh(t)))
    }

    fun tileToLng(x: Int, z: Int): Double {
        val n = 1 shl z
        return x.toDouble() / n * 360.0 - 180.0
    }

    const val HEAT_ZOOM = 18
}
