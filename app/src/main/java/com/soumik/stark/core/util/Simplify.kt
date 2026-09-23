package com.soumik.stark.core.util

/**
 * Douglas–Peucker polyline simplification with a radial-distance prefilter (design §4B). Used for
 * display geometry only — the raw points stay the source of truth for distance. Keeps crisp
 * corners at junctions while dropping redundant points, so a route reads cleanly at every zoom.
 */
object Simplify {

    /** (lat, lng) pairs in, simplified (lat, lng) pairs out. [toleranceM] ~ per-zoom pixel budget. */
    fun douglasPeucker(points: List<DoubleArray>, toleranceM: Double): List<DoubleArray> {
        if (points.size < 3) return points
        val pre = radialPrefilter(points, toleranceM * 0.4)
        if (pre.size < 3) return pre
        val keep = BooleanArray(pre.size)
        keep[0] = true; keep[pre.size - 1] = true
        dp(pre, 0, pre.size - 1, toleranceM, keep)
        return pre.filterIndexed { i, _ -> keep[i] }
    }

    private fun radialPrefilter(points: List<DoubleArray>, minM: Double): List<DoubleArray> {
        val out = ArrayList<DoubleArray>(points.size)
        out.add(points.first())
        var prev = points.first()
        for (i in 1 until points.size - 1) {
            if (Geo.distanceM(prev[0], prev[1], points[i][0], points[i][1]) >= minM) {
                out.add(points[i]); prev = points[i]
            }
        }
        out.add(points.last())
        return out
    }

    private fun dp(pts: List<DoubleArray>, first: Int, last: Int, tol: Double, keep: BooleanArray) {
        if (last <= first + 1) return
        var maxD = 0.0; var idx = -1
        for (i in first + 1 until last) {
            val d = perpDistanceM(pts[i], pts[first], pts[last])
            if (d > maxD) { maxD = d; idx = i }
        }
        if (maxD > tol && idx != -1) {
            keep[idx] = true
            dp(pts, first, idx, tol, keep)
            dp(pts, idx, last, tol, keep)
        }
    }

    /** Perpendicular distance (metres) from p to the segment a–b, in a local flat projection. */
    private fun perpDistanceM(p: DoubleArray, a: DoubleArray, b: DoubleArray): Double {
        val mPerLat = 111_320.0
        val mPerLng = 111_320.0 * Math.cos(Math.toRadians(a[0]))
        val px = (p[1] - a[1]) * mPerLng; val py = (p[0] - a[0]) * mPerLat
        val bx = (b[1] - a[1]) * mPerLng; val by = (b[0] - a[0]) * mPerLat
        val len2 = bx * bx + by * by
        if (len2 == 0.0) return Math.hypot(px, py)
        val t = ((px * bx + py * by) / len2).coerceIn(0.0, 1.0)
        val cx = bx * t; val cy = by * t
        return Math.hypot(px - cx, py - cy)
    }
}
