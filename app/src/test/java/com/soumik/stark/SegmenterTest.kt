package com.soumik.stark

import com.soumik.stark.data.entity.Confidence
import com.soumik.stark.data.entity.Point
import com.soumik.stark.data.entity.TravelMode
import com.soumik.stark.data.repo.TrackSink
import com.soumik.stark.tracking.filter.FilteredFix
import com.soumik.stark.tracking.segmentation.Segmenter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeSink : TrackSink {
    var legs = 0
    var totalDistanceM = 0.0
    var pointsWritten = 0
    var closed = false
    var closedWithDistance = 0.0

    override suspend fun openLeg(startT: Long, offsetMin: Int, mode: TravelMode): Long {
        legs++
        return legs.toLong()
    }

    override suspend fun flushBatch(
        legId: Long,
        points: List<Point>,
        addedDistanceM: Double,
        maxSpeedMps: Double,
        endT: Long,
        durationS: Long,
        hasEstimatedGap: Boolean,
    ) {
        totalDistanceM += addedDistanceM
        pointsWritten += points.size
    }

    override suspend fun closeLeg(legId: Long, endT: Long, durationS: Long, minDistanceM: Double): Long? {
        closed = true
        closedWithDistance = totalDistanceM
        return if (totalDistanceM >= minDistanceM) legId else null
    }
}

class SegmenterTest {

    private fun fix(t: Long, lat: Double, lng: Double, speed: Float) =
        FilteredFix(t, lat, lng, 5f, speed, Confidence.HIGH)

    @Test
    fun straight_ride_sums_distance() = runBlocking {
        val sink = FakeSink()
        val seg = Segmenter(sink, TravelMode.VEHICLE)
        // 6 fixes, each ~30 m north (0.00027 deg), 2 s apart, clearly moving.
        val base = 12.9716
        var t = 0L
        for (i in 0 until 6) {
            seg.onFix(fix(t, base + i * 0.00027, 77.5946, speed = 15f))
            t += 2000
        }
        seg.finish(t)
        // 5 hops * ~30 m ≈ 150 m.
        assertEquals(150.0, sink.closedWithDistance, 8.0)
        assertEquals(1, sink.legs)
        assertTrue(sink.pointsWritten >= 6)
    }

    @Test
    fun stationary_adds_no_distance() = runBlocking {
        val sink = FakeSink()
        val seg = Segmenter(sink, TravelMode.VEHICLE)
        val lat = 12.9716
        var t = 0L
        for (i in 0 until 5) {
            seg.onFix(fix(t, lat, 77.5946, speed = 0f)) // same coords, no movement
            t += 2000
        }
        seg.finish(t)
        assertEquals(0.0, sink.totalDistanceM, 0.001)
    }

    @Test
    fun long_still_period_closes_leg() = runBlocking {
        val sink = FakeSink()
        val seg = Segmenter(sink, TravelMode.VEHICLE, stopThresholdMs = 60_000L)
        val base = 12.9716
        // Move a bit, then sit still past the stop threshold.
        seg.onFix(fix(0, base, 77.5946, speed = 15f))
        seg.onFix(fix(2000, base + 0.0009, 77.5946, speed = 15f)) // ~100 m
        val snap = seg.onFix(fix(70_000, base + 0.0009, 77.5946, speed = 0f))
        assertTrue(snap.legClosed)
        assertTrue(sink.closed)
    }
}
