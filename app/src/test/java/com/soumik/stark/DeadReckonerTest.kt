package com.soumik.stark

import com.soumik.stark.tracking.segmentation.DeadReckoner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeadReckonerTest {

    @Test
    fun short_gap_at_speed_counts_more_than_straight_line() {
        // 30 s tunnel at 20 m/s ≈ 600 m travelled, but GPS straight line across it is only 400 m.
        val d = DeadReckoner.gapDistanceM(straightLineM = 400.0, gapMs = 30_000, lastSpeedMps = 20.0)
        assertEquals(600.0, d, 1.0)
    }

    @Test
    fun long_gap_falls_back_to_straight_line() {
        val d = DeadReckoner.gapDistanceM(straightLineM = 5000.0, gapMs = 120_000, lastSpeedMps = 20.0)
        assertEquals(5000.0, d, 0.001)
    }

    @Test
    fun never_undercounts_straight_line() {
        val d = DeadReckoner.gapDistanceM(straightLineM = 500.0, gapMs = 20_000, lastSpeedMps = 5.0)
        assertTrue(d >= 500.0)
    }

    @Test
    fun bounded_against_a_wild_fix() {
        // Even a high speed can't inflate beyond 3x the straight line.
        val d = DeadReckoner.gapDistanceM(straightLineM = 100.0, gapMs = 50_000, lastSpeedMps = 50.0)
        assertTrue(d <= 300.0)
    }
}
