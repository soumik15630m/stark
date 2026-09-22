package com.soumik.stark.tracking.filter

import com.soumik.stark.core.util.Geo
import com.soumik.stark.data.entity.Confidence

data class FilteredFix(
    val tUtc: Long,
    val lat: Double,
    val lng: Double,
    val accuracyM: Float,
    val speedMps: Float,
    val confidence: Confidence,
)

/**
 * Per-fix filter. Order: accuracy gate (adaptive) → teleport rejection → stationary snap.
 * Stateful; drive it from a single thread (the tracking pipeline dispatcher).
 *
 * Full 2D Kalman fusion is the design's end state; this v1 uses the accuracy/teleport/stationary
 * gates that actually drive distance correctness, plus Doppler speed. Distance smoothing beyond
 * this is a later layer, not a rewrite of the gate logic.
 */
class LocationFilter(
    private val baseAccuracyGateM: Float = 20f,
    private val relaxedAccuracyGateM: Float = 60f,
    private val speedCeilingMps: Float = 70f, // ~252 km/h; above this is a GPS teleport
    private val stationarySpeedMps: Float = 0.7f, // ~2.5 km/h
    private val stationaryRadiusM: Double = 15.0, // parked drift stays under this; real movement exceeds it
) {
    private var last: FilteredFix? = null
    private val recentAccuracy = ArrayDeque<Float>(6)
    private val kalman = Kalman2D()
    private var lastEmitT = 0L

    /** Anchor held while stationary so parked drift accumulates no distance. */
    private var stationaryAnchor: FilteredFix? = null

    fun reset() {
        last = null
        recentAccuracy.clear()
        stationaryAnchor = null
        kalman.reset()
        lastEmitT = 0L
    }

    fun accept(fix: RawFix): FilteredFix? {
        // A fix with no reported accuracy is treated as usable (emulator / older chips); a
        // reported accuracy is gated normally.
        val acc = if (fix.hasAccuracy) fix.accuracyM else 0f
        pushAccuracy(acc)

        val allRecentPoor = recentAccuracy.size >= 4 && recentAccuracy.all { it > baseAccuracyGateM }
        val gate = if (allRecentPoor) relaxedAccuracyGateM else baseAccuracyGateM
        if (fix.hasAccuracy && acc > gate) return null

        val speed = if (fix.hasSpeed) fix.speedMps else derivedSpeed(fix)
        val prev = last

        if (prev != null) {
            val dtS = (fix.tUtc - prev.tUtc) / 1000.0
            if (dtS > 0) {
                val dist = Geo.distanceM(prev.lat, prev.lng, fix.lat, fix.lng)
                val implied = dist / dtS
                if (implied > speedCeilingMps) return null // teleport
            }
        }

        val confidence = when {
            allRecentPoor || acc > baseAccuracyGateM -> Confidence.LOW
            else -> Confidence.HIGH
        }

        // Stationary snap: while stopped, hold a single anchor point instead of logging drift.
        // We treat a fix as stationary only when reported speed is ~0 AND the position hasn't
        // moved beyond parked-drift radius from the anchor — so genuine slow movement (or a
        // device that under-reports Doppler speed) still counts.
        val anchor = stationaryAnchor
        val movedFromAnchor = anchor?.let { Geo.distanceM(it.lat, it.lng, fix.lat, fix.lng) } ?: 0.0
        if (speed < stationarySpeedMps && movedFromAnchor < stationaryRadiusM) {
            kalman.reset() // re-init the fusion filter when movement resumes
            lastEmitT = 0L
            if (anchor == null) {
                val f = FilteredFix(fix.tUtc, fix.lat, fix.lng, acc, 0f, confidence)
                stationaryAnchor = f
                last = f
                return f
            }
            val held = anchor.copy(tUtc = fix.tUtc, speedMps = 0f)
            last = held
            return held
        }

        stationaryAnchor = null
        val dt = if (lastEmitT == 0L) 0.0 else (fix.tUtc - lastEmitT) / 1000.0
        val k = kalman.update(fix.lat, fix.lng, acc, dt, if (fix.hasSpeed) fix.speedMps else null)
        val outSpeed = if (fix.hasSpeed) fix.speedMps else k.speedMps.toFloat()
        val f = FilteredFix(fix.tUtc, k.lat, k.lng, acc, outSpeed, confidence)
        last = f
        lastEmitT = fix.tUtc
        return f
    }

    private fun derivedSpeed(fix: RawFix): Float {
        val prev = last ?: return 0f
        val dtS = (fix.tUtc - prev.tUtc) / 1000.0
        if (dtS <= 0) return 0f
        val dist = Geo.distanceM(prev.lat, prev.lng, fix.lat, fix.lng)
        return (dist / dtS).toFloat()
    }

    private fun pushAccuracy(acc: Float) {
        if (recentAccuracy.size == 6) recentAccuracy.removeFirst()
        recentAccuracy.addLast(acc)
    }
}
