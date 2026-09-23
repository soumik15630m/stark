package com.soumik.stark.data.repo

import com.soumik.stark.data.entity.Point
import com.soumik.stark.data.entity.TravelMode

/** Persistence surface the segmenter writes through. Lets the segmentation logic be unit-tested. */
interface TrackSink {
    suspend fun openLeg(startT: Long, offsetMin: Int, mode: TravelMode): Long

    suspend fun flushBatch(
        legId: Long,
        points: List<Point>,
        addedDistanceM: Double,
        maxSpeedMps: Double,
        endT: Long,
        durationS: Long,
        movingDurationS: Long,
        hasEstimatedGap: Boolean,
    )

    /**
     * Close a leg; returns the leg id if kept, or null if discarded. A leg is discarded when it's
     * too short OR never reached a real moving speed (parked GPS drift while idle/asleep).
     */
    suspend fun closeLeg(
        legId: Long,
        endT: Long,
        durationS: Long,
        minDistanceM: Double = 50.0,
        minMaxSpeedMps: Double = 2.5,
    ): Long?
}
