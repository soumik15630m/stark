package com.soumik.stark

import com.soumik.stark.data.entity.FuelFill
import com.soumik.stark.domain.fuel.FuelEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FuelEstimatorTest {

    private fun fill(odoKm: Double, litres: Double, cost: Double, full: Boolean = false, dry: Boolean = false) =
        FuelFill(t = odoKm.toLong(), litres = litres, costInr = cost, odoMAtFill = odoKm * 1000.0,
            filledToFull = full, ranDryBefore = dry)

    @Test
    fun full_to_full_gives_clean_mileage() {
        // Full at 1000 km, then top-up of 10 L to full at 1400 km → 400 km on 10 L = 40 km/l.
        val fills = listOf(
            fill(1000.0, 8.0, 800.0, full = true),
            fill(1400.0, 10.0, 1000.0, full = true),
        )
        val r = FuelEstimator.estimate(fills, tankL = 12.0, reserveL = 2.0, currentOdoM = 1400_000.0)
        assertNotNull(r.kmPerL)
        assertEquals(40.0, r.kmPerL!!, 0.001)
        // Just filled to full → current level is the whole tank.
        assertEquals(12.0, r.currentLevelL!!, 0.001)
        assertEquals(480.0, r.rangeKm!!, 0.001)          // 12 L × 40
        assertEquals(400.0, r.distanceToReserveKm!!, 0.001) // (12 − 2) × 40
    }

    @Test
    fun ran_dry_anchors_and_level_drops_with_distance() {
        // Fill to full (12 L) at 2000 km, ride until dry, refill 12 L at 2480 km → 480 km / 12 L = 40 km/l.
        val fills = listOf(
            fill(2000.0, 12.0, 1200.0, full = true),
            fill(2480.0, 12.0, 1200.0, full = true, dry = true),
        )
        // Current odometer 100 km past the last full fill → ~2.5 L burned, ~9.5 L left.
        val r = FuelEstimator.estimate(fills, tankL = 12.0, reserveL = 2.0, currentOdoM = 2580_000.0)
        assertEquals(40.0, r.kmPerL!!, 0.001)
        assertEquals(9.5, r.currentLevelL!!, 0.001)   // 12 − 100/40
        assertTrue(r.rangeKm!! in 379.0..381.0)       // 9.5 × 40
    }

    @Test
    fun no_anchors_yields_no_estimate_but_does_not_crash() {
        val fills = listOf(fill(10.0, 5.0, 500.0), fill(200.0, 4.0, 400.0))
        val r = FuelEstimator.estimate(fills, tankL = 0.0, reserveL = 0.0, currentOdoM = 200_000.0)
        // Partial fills, unknown capacity → no mileage, no crash, rows still returned.
        assertTrue(r.kmPerL == null)
        assertEquals(2, r.rows.size)
        assertNotNull(r.pricePerL)
    }
}
