package com.soumik.stark

import com.soumik.stark.core.util.Geo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoTest {

    @Test
    fun equirectangular_matches_haversine_on_short_hops() {
        // ~20 m north near Bengaluru. Approximation must agree with haversine to sub-cm.
        val lat = 12.9716
        val lng = 77.5946
        val lat2 = lat + 0.00018 // ~20 m
        val eq = Geo.equirectangularM(lat, lng, lat2, lng)
        val hav = Geo.haversineM(lat, lng, lat2, lng)
        assertEquals(hav, eq, 0.01)
        assertTrue(eq in 18.0..22.0)
    }

    @Test
    fun haversine_known_long_distance() {
        // Bengaluru → Chennai, ~290 km great-circle.
        val d = Geo.haversineM(12.9716, 77.5946, 13.0827, 80.2707) / 1000.0
        assertEquals(290.0, d, 15.0)
    }

    @Test
    fun distance_dispatch_is_continuous_across_threshold() {
        val a = 12.0
        val b = 77.0
        // just under and over the 0.02 deg switch point should not jump.
        val below = Geo.distanceM(a, b, a + 0.019, b)
        val above = Geo.distanceM(a, b, a + 0.021, b)
        assertTrue(above > below)
        assertEquals(Geo.haversineM(a, b, a + 0.019, b), below, 0.5)
    }

    @Test
    fun e7_roundtrip_is_lossless_to_cm() {
        val v = 12.9716123
        assertEquals(v, Geo.fromE7(Geo.toE7(v)), 1e-7)
    }
}
