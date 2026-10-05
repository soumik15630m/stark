package com.soumik.stark.domain

import com.soumik.stark.data.entity.Leg
import com.soumik.stark.data.entity.TravelMode

/**
 * Speed-based travel-mode fallback. Activity Recognition often misses short walks and leaves a
 * tracked trip at the default VEHICLE; this refines that from the trip's own speed profile. A real
 * ride always spikes above a brisk-walk speed at some point, so slow riding is never downgraded.
 * AR-confirmed modes (anything other than the default VEHICLE) are always kept.
 */
object ModeClassifier {
    private const val WALK_MAX_KMH = 10.0  // a walk never really exceeds a brisk pace
    private const val RUN_MAX_KMH = 18.0
    private const val RUN_AVG_KMH = 12.0

    fun infer(leg: Leg): TravelMode {
        if (leg.mode != TravelMode.VEHICLE) return leg.mode
        val maxKmh = leg.maxSpeedMps * 3.6
        val avgKmh = if (leg.movingDurationS > 0) leg.distanceM / leg.movingDurationS * 3.6 else 0.0
        return when {
            maxKmh <= WALK_MAX_KMH -> TravelMode.WALK
            maxKmh <= RUN_MAX_KMH && avgKmh <= RUN_AVG_KMH -> TravelMode.RUN
            else -> TravelMode.VEHICLE
        }
    }
}
