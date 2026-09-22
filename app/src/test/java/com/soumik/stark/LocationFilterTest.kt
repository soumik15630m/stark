package com.soumik.stark

import com.soumik.stark.data.entity.Confidence
import com.soumik.stark.tracking.filter.LocationFilter
import com.soumik.stark.tracking.filter.RawFix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationFilterTest {

    private fun fix(t: Long, lat: Double, lng: Double, acc: Float = 5f, speed: Float = 10f) =
        RawFix(t, lat, lng, acc, hasAccuracy = true, speedMps = speed, hasSpeed = true)

    @Test
    fun rejects_poor_accuracy_fix() {
        val f = LocationFilter()
        assertNull(f.accept(fix(0, 12.9716, 77.5946, acc = 50f)))
    }

    @Test
    fun accepts_good_fix_as_high_confidence() {
        val f = LocationFilter()
        val out = f.accept(fix(0, 12.9716, 77.5946, acc = 6f))
        assertNotNull(out)
        assertEquals(Confidence.HIGH, out!!.confidence)
    }

    @Test
    fun rejects_teleport() {
        val f = LocationFilter()
        assertNotNull(f.accept(fix(0, 12.9716, 77.5946)))
        // ~1.1 km jump in 1 s => impossible speed, must be dropped.
        assertNull(f.accept(fix(1000, 12.9816, 77.5946)))
    }

    @Test
    fun stationary_snap_holds_anchor_against_drift() {
        val f = LocationFilter()
        val anchor = f.accept(fix(0, 12.9716, 77.5946, speed = 0f))!!
        // Parked drift: coords wander but speed stays ~0; output must snap back to the anchor.
        val drifted = f.accept(fix(2000, 12.97163, 77.59464, speed = 0.1f))!!
        assertEquals(anchor.lat, drifted.lat, 1e-9)
        assertEquals(anchor.lng, drifted.lng, 1e-9)
        assertEquals(0f, drifted.speedMps, 1e-6f)
    }

    @Test
    fun zero_speed_but_real_movement_is_not_snapped() {
        val f = LocationFilter()
        val a = f.accept(fix(0, 12.9716, 77.5946, speed = 0f))!!
        // ~40 m away with speed still reported 0 (device under-reporting Doppler): must count.
        val moved = f.accept(fix(2000, 12.97196, 77.5946, speed = 0f))!!
        assertTrue(moved.lat != a.lat)
    }

    @Test
    fun missing_accuracy_is_accepted() {
        val f = LocationFilter()
        val raw = RawFix(0, 12.9716, 77.5946, 0f, hasAccuracy = false, speedMps = 10f, hasSpeed = true)
        assertNotNull(f.accept(raw))
    }
}
