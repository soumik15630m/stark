package com.soumik.stark.tracking.filter

import com.soumik.stark.core.util.Geo
import kotlin.math.sqrt

/**
 * 4-state (x, y, vx, vy) constant-velocity Kalman filter run in a local ENU projection (metres),
 * the single sensor-fusion home from design §4.4. Fuses GNSS position (measurement noise driven
 * by GPS accuracy) with the velocity it estimates; Doppler speed seeds the process noise. Removing
 * GPS jitter makes the summed distance *truer* (jitter inflates distance), never systematically
 * lower.
 *
 * Pure and single-threaded; unit-tested. Coordinates in/out are decimal degrees.
 */
class Kalman2D(
    private val processStdMps2: Double = 2.0, // accel process noise (m/s^2), tuned for a motorbike
) {
    private var initialized = false
    private var originLat = 0.0
    private var originLng = 0.0
    private var mPerDegLat = 111_320.0
    private var mPerDegLng = 111_320.0

    // State
    private var x = 0.0; private var y = 0.0; private var vx = 0.0; private var vy = 0.0
    // Covariance (4x4) stored as arrays; initialised large.
    private val P = Array(4) { DoubleArray(4) }

    fun reset() { initialized = false }

    data class Out(val lat: Double, val lng: Double, val speedMps: Double)

    fun update(lat: Double, lng: Double, accuracyM: Float, dtS: Double, dopplerMps: Float?): Out {
        if (!initialized) return init(lat, lng, accuracyM, dopplerMps)

        val dt = dtS.coerceIn(0.05, 10.0)
        // --- Predict (constant velocity) ---
        x += vx * dt
        y += vy * dt
        val q = processStdMps2 * processStdMps2
        val dt2 = dt * dt; val dt3 = dt2 * dt; val dt4 = dt2 * dt2
        // Discrete white-noise acceleration model contributions.
        val q11 = dt4 / 4 * q; val q13 = dt3 / 2 * q; val q33 = dt2 * q
        // P = F P F^T + Q  (F is constant-velocity). Expand for our 4-state layout [x,y,vx,vy].
        predictCovariance(dt, q11, q13, q33)

        // --- Update with position measurement ---
        val mx = (lng - originLng) * mPerDegLng
        val my = (lat - originLat) * mPerDegLat
        val r = (accuracyM.coerceAtLeast(3f).toDouble()).let { it * it }
        // Kalman gain for position-only measurement on x and y independently (decoupled axes).
        applyPositionUpdate(mx, my, r)

        // Optional velocity magnitude nudge from Doppler: scale velocity toward reported speed.
        if (dopplerMps != null && dopplerMps > 0.5f) {
            val cur = sqrt(vx * vx + vy * vy)
            if (cur > 0.1) {
                val scale = (0.7 + 0.3 * (dopplerMps / cur)).coerceIn(0.5, 2.0)
                vx *= scale; vy *= scale
            }
        }

        val outLat = originLat + y / mPerDegLat
        val outLng = originLng + x / mPerDegLng
        return Out(outLat, outLng, sqrt(vx * vx + vy * vy))
    }

    private fun init(lat: Double, lng: Double, accuracyM: Float, dopplerMps: Float?): Out {
        originLat = lat; originLng = lng
        mPerDegLat = 111_320.0
        mPerDegLng = 111_320.0 * Math.cos(Math.toRadians(lat))
        x = 0.0; y = 0.0; vx = 0.0; vy = 0.0
        for (i in 0..3) for (j in 0..3) P[i][j] = 0.0
        P[0][0] = 25.0; P[1][1] = 25.0; P[2][2] = 100.0; P[3][3] = 100.0
        initialized = true
        return Out(lat, lng, (dopplerMps ?: 0f).toDouble())
    }

    private fun predictCovariance(dt: Double, q11: Double, q13: Double, q33: Double) {
        // For axis x: states (x, vx) = indices (0,2). For axis y: (y, vy) = (1,3).
        // P' = F P F^T + Q per axis.
        predictAxis(0, 2, dt, q11, q13, q33)
        predictAxis(1, 3, dt, q11, q13, q33)
    }

    private fun predictAxis(p: Int, v: Int, dt: Double, q11: Double, q13: Double, q33: Double) {
        val ppp = P[p][p]; val ppv = P[p][v]; val pvv = P[v][v]
        // F = [[1, dt],[0,1]]
        val newPP = ppp + dt * ppv + dt * (ppv + dt * pvv) + q11
        val newPV = ppv + dt * pvv + q13
        val newVV = pvv + q33
        P[p][p] = newPP; P[p][v] = newPV; P[v][p] = newPV; P[v][v] = newVV
    }

    private fun applyPositionUpdate(mx: Double, my: Double, r: Double) {
        updateAxis(0, 2, mx, r)
        updateAxis(1, 3, my, r)
    }

    private fun updateAxis(p: Int, v: Int, meas: Double, r: Double) {
        val posVal = if (p == 0) x else y
        val velVal = if (v == 2) vx else vy
        val s = P[p][p] + r
        val kp = P[p][p] / s
        val kv = P[v][p] / s
        val innov = meas - posVal
        val newPos = posVal + kp * innov
        val newVel = velVal + kv * innov
        if (p == 0) x = newPos else y = newPos
        if (v == 2) vx = newVel else vy = newVel
        // Covariance update: P = (I - K H) P
        val ppp = P[p][p]; val ppv = P[p][v]; val pvp = P[v][p]; val pvv = P[v][v]
        P[p][p] = ppp - kp * ppp
        P[p][v] = ppv - kp * ppv
        P[v][p] = pvp - kv * ppp
        P[v][v] = pvv - kv * ppv
    }
}
