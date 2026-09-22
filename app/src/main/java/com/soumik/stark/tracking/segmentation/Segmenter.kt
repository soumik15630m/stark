package com.soumik.stark.tracking.segmentation

import com.soumik.stark.core.time.TimeUtils
import com.soumik.stark.core.util.Geo
import com.soumik.stark.data.entity.Point
import com.soumik.stark.data.entity.TravelMode
import com.soumik.stark.data.repo.TrackSink
import com.soumik.stark.tracking.filter.FilteredFix

/**
 * Turns a stream of filtered fixes into legs with incremental distance, batching writes and
 * detecting stops. Pause (< STOP_THRESHOLD stationary) keeps the leg open; a longer still period
 * closes the leg (design §4.11). Drive from a single thread.
 */
class Segmenter(
    private val repo: TrackSink,
    private val mode: TravelMode = TravelMode.VEHICLE,
    private val stopThresholdMs: Long = 5 * 60 * 1000L,
    private val gapMs: Long = 30_000L,
    private val flushEveryMs: Long = 10_000L,
    private val flushEveryPoints: Int = 20,
) {
    data class Snapshot(
        val tripDistanceM: Double,
        val tripDurationS: Long,
        val maxSpeedMps: Double,
        val speedMps: Double,
        val paused: Boolean,
        val legClosed: Boolean,
    )

    private var legId: Long? = null
    private var startT: Long = 0
    private var lastLat = 0.0
    private var lastLng = 0.0
    private var lastFixT = 0L
    private var hasPrev = false
    private var tripDistanceM = 0.0
    private var maxSpeedMps = 0.0

    private var lastMovementT = 0L
    private val buffer = ArrayList<Point>(flushEveryPoints)
    private var bufferAddedM = 0.0
    private var bufferHasGap = false
    private var lastFlushT = 0L
    private var pendingMaxSpeed = 0.0

    suspend fun onFix(fix: FilteredFix): Snapshot {
        val id = legId ?: openLeg(fix)

        var addedM = 0.0
        var gap = false
        if (hasPrev) {
            addedM = Geo.distanceM(lastLat, lastLng, fix.lat, fix.lng)
            if (fix.tUtc - lastFixT > gapMs && addedM > 0.0) gap = true
        }

        tripDistanceM += addedM
        bufferAddedM += addedM
        if (gap) bufferHasGap = true
        if (fix.speedMps > maxSpeedMps) maxSpeedMps = fix.speedMps.toDouble()
        if (fix.speedMps > pendingMaxSpeed) pendingMaxSpeed = fix.speedMps.toDouble()

        buffer.add(
            Point(
                legId = id,
                tUtc = fix.tUtc,
                offsetMin = TimeUtils.offsetMinutes(fix.tUtc),
                latE7 = Geo.toE7(fix.lat),
                lngE7 = Geo.toE7(fix.lng),
                accuracyM = fix.accuracyM,
                speedMps = fix.speedMps,
                confidence = fix.confidence,
            )
        )

        lastLat = fix.lat
        lastLng = fix.lng
        lastFixT = fix.tUtc
        hasPrev = true
        if (fix.speedMps >= MOVING_MPS) lastMovementT = fix.tUtc

        val stillFor = fix.tUtc - lastMovementT
        val shouldStop = stillFor >= stopThresholdMs
        val paused = !shouldStop && fix.speedMps < MOVING_MPS && stillFor > PAUSE_HINT_MS

        val timeToFlush = fix.tUtc - lastFlushT >= flushEveryMs
        if (buffer.size >= flushEveryPoints || timeToFlush || shouldStop) {
            flush(id, fix.tUtc)
        }

        if (shouldStop) {
            close(fix.tUtc)
            return Snapshot(tripDistanceM, durationS(fix.tUtc), maxSpeedMps, 0.0, paused = false, legClosed = true)
        }

        return Snapshot(
            tripDistanceM = tripDistanceM,
            tripDurationS = durationS(fix.tUtc),
            maxSpeedMps = maxSpeedMps,
            speedMps = fix.speedMps.toDouble(),
            paused = paused,
            legClosed = false,
        )
    }

    /** Flush and close the current leg (manual stop or service teardown). */
    suspend fun finish(now: Long) {
        val id = legId ?: return
        flush(id, now)
        close(now)
    }

    private suspend fun openLeg(fix: FilteredFix): Long {
        val id = repo.openLeg(fix.tUtc, TimeUtils.offsetMinutes(fix.tUtc), mode)
        legId = id
        startT = fix.tUtc
        lastLat = fix.lat
        lastLng = fix.lng
        hasPrev = false // first fix of the leg contributes no delta
        lastMovementT = fix.tUtc
        lastFlushT = fix.tUtc
        tripDistanceM = 0.0
        maxSpeedMps = 0.0
        return id
    }

    private suspend fun flush(id: Long, now: Long) {
        if (buffer.isEmpty() && bufferAddedM == 0.0) return
        repo.flushBatch(
            legId = id,
            points = ArrayList(buffer),
            addedDistanceM = bufferAddedM,
            maxSpeedMps = pendingMaxSpeed,
            endT = now,
            durationS = durationS(now),
            hasEstimatedGap = bufferHasGap,
        )
        buffer.clear()
        bufferAddedM = 0.0
        bufferHasGap = false
        pendingMaxSpeed = 0.0
        lastFlushT = now
    }

    private suspend fun close(now: Long) {
        val id = legId ?: return
        repo.closeLeg(id, now, durationS(now))
        legId = null
        hasPrev = false
        tripDistanceM = 0.0
        maxSpeedMps = 0.0
    }

    private fun durationS(now: Long): Long = ((now - startT) / 1000L).coerceAtLeast(0)

    companion object {
        private const val MOVING_MPS = 0.9f       // ~3.2 km/h; above this counts as movement
        private const val PAUSE_HINT_MS = 20_000L // still this long → surface a "paused" hint
    }
}
