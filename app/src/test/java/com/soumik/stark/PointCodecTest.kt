package com.soumik.stark

import com.soumik.stark.core.util.PointCodec
import com.soumik.stark.data.entity.Confidence
import com.soumik.stark.data.entity.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PointCodecTest {

    private fun sample(n: Int): List<Point> {
        var lat = 129716000; var lng = 775946000; var t = 1_700_000_000_000L
        return (0 until n).map {
            lat += 270 + (it % 5); lng += 12; t += 2000
            Point(legId = 9, tUtc = t, offsetMin = 330, latE7 = lat, lngE7 = lng, accuracyM = 5.3f, speedMps = 12.34f, confidence = if (it % 7 == 0) Confidence.LOW else Confidence.HIGH)
        }
    }

    @Test
    fun round_trips_losslessly() {
        val pts = sample(500)
        val decoded = PointCodec.decode(PointCodec.encode(pts), 9)
        assertEquals(pts.size, decoded.size)
        for (i in pts.indices) {
            assertEquals(pts[i].latE7, decoded[i].latE7)
            assertEquals(pts[i].lngE7, decoded[i].lngE7)
            assertEquals(pts[i].tUtc, decoded[i].tUtc)
            assertEquals(pts[i].offsetMin, decoded[i].offsetMin)
            assertEquals(pts[i].confidence, decoded[i].confidence)
            assertEquals(pts[i].speedMps.toDouble(), decoded[i].speedMps.toDouble(), 0.01)
            assertEquals(pts[i].accuracyM.toDouble(), decoded[i].accuracyM.toDouble(), 0.1)
        }
    }

    @Test
    fun is_much_smaller_than_naive_rows() {
        val pts = sample(1000)
        val bytes = PointCodec.encode(pts).size
        // Naive would be ~ 1000 * (4+4+8+... ) > 24 KB; packed should be well under 12 bytes/point.
        assertTrue("packed $bytes bytes for 1000 pts", bytes < 12_000)
    }

    @Test
    fun handles_empty() {
        assertEquals(0, PointCodec.decode(PointCodec.encode(emptyList()), 1).size)
    }
}
