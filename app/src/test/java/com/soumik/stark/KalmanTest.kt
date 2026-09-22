package com.soumik.stark

import com.soumik.stark.core.util.Geo
import com.soumik.stark.tracking.filter.Kalman2D
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class KalmanTest {

    @Test
    fun reduces_jitter_inflation_on_a_straight_line() {
        val rnd = Random(42)
        val baseLat = 12.9716
        val baseLng = 77.5946
        val stepDegLng = 0.0001 // ~10.8 m east per step at this latitude
        val kf = Kalman2D()

        var rawDist = 0.0
        var kfDist = 0.0
        var prevRawLat = baseLat; var prevRawLng = baseLng
        var prevKfLat = baseLat; var prevKfLng = baseLng
        var trueDist = 0.0
        var prevTrueLng = baseLng

        for (i in 0 until 40) {
            val trueLng = baseLng + i * stepDegLng
            // ±4 m GPS noise on each axis.
            val noisyLat = baseLat + (rnd.nextDouble() - 0.5) * 0.00007
            val noisyLng = trueLng + (rnd.nextDouble() - 0.5) * 0.00007
            val out = kf.update(noisyLat, noisyLng, 6f, 1.0, 11f)

            if (i > 0) {
                rawDist += Geo.distanceM(prevRawLat, prevRawLng, noisyLat, noisyLng)
                kfDist += Geo.distanceM(prevKfLat, prevKfLng, out.lat, out.lng)
                trueDist += Geo.distanceM(baseLat, prevTrueLng, baseLat, trueLng)
            }
            prevRawLat = noisyLat; prevRawLng = noisyLng
            prevKfLat = out.lat; prevKfLng = out.lng
            prevTrueLng = trueLng
        }

        // True path ~ 39 * 10.8 ≈ 421 m. Raw noisy sum over-counts; KF should be closer to true.
        assertTrue("kf ($kfDist) should not exceed raw ($rawDist)", kfDist <= rawDist + 1.0)
        assertTrue("kf ($kfDist) within 15% of true ($trueDist)", kotlin.math.abs(kfDist - trueDist) < trueDist * 0.15)
    }

    @Test
    fun tracks_position_toward_measurements() {
        val kf = Kalman2D()
        kf.update(12.9716, 77.5946, 5f, 1.0, 0f)
        val out = kf.update(12.9716, 77.5966, 5f, 1.0, 10f) // jump east
        // Estimate should move east of the origin toward the measurement.
        assertTrue(out.lng > 77.5946)
    }
}
