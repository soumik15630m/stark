package com.soumik.stark

import com.soumik.stark.core.util.Simplify
import org.junit.Assert.assertTrue
import org.junit.Test

class SimplifyTest {

    @Test
    fun collapses_a_nearly_straight_line() {
        val pts = (0..50).map { doubleArrayOf(12.9716 + it * 0.0001, 77.5946) }
        val out = Simplify.douglasPeucker(pts, 10.0)
        assertTrue("straight line should collapse to a handful of points", out.size < 6)
        assertTrue(out.size >= 2)
    }

    @Test
    fun keeps_a_sharp_corner() {
        val pts = ArrayList<DoubleArray>()
        for (i in 0..20) pts.add(doubleArrayOf(12.9716, 77.5946 + i * 0.0001))     // east
        for (i in 1..20) pts.add(doubleArrayOf(12.9716 + i * 0.0001, 77.5946 + 20 * 0.0001)) // then north
        val out = Simplify.douglasPeucker(pts, 8.0)
        // The corner vertex must survive.
        assertTrue(out.any { kotlin.math.abs(it[1] - (77.5946 + 20 * 0.0001)) < 1e-7 && kotlin.math.abs(it[0] - 12.9716) < 1e-6 })
        assertTrue(out.size < pts.size)
    }
}
