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
        hasEstimatedGap: Boolean,
    )

    suspend fun closeLeg(legId: Long, endT: Long, durationS: Long, minDistanceM: Double = 50.0)
}
