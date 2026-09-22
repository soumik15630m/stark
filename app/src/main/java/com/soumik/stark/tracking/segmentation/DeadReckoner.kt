package com.soumik.stark.tracking.segmentation

/**
 * Gap distance estimation (design §4.5, §4.10). For a SHORT gap (tunnel/underpass) where the rider
 * likely held speed, the travelled distance is closer to speed×time than the straight line between
 * the fixes on either side — so we count the larger of the two (truer, never under). For a LONG
 * gap we fall back to the straight line (inertial/speed assumptions drift). Bounded so a wild fix
 * can't inflate the odometer.
 */
object DeadReckoner {
    private const val SHORT_GAP_MS = 60_000L
    private const val MOVING_MPS = 1.0
    private const val MAX_INFLATION = 3.0

    fun gapDistanceM(straightLineM: Double, gapMs: Long, lastSpeedMps: Double): Double {
        if (gapMs > SHORT_GAP_MS || lastSpeedMps < MOVING_MPS) return straightLineM
        val projected = lastSpeedMps * (gapMs / 1000.0)
        val capped = projected.coerceAtMost(straightLineM * MAX_INFLATION)
        return maxOf(straightLineM, capped)
    }
}
