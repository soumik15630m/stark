package com.soumik.stark.tracking.location

import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.Priority

/**
 * Hybrid sampling (design §4.3): a point roughly every ~20 m of movement, and never less often
 * than the time ceiling for liveness. When the live dashboard is closed we allow hardware FIFO
 * batching (maxUpdateDelay) so the chip buffers fixes and wakes the CPU far less often.
 */
object SamplingPolicy {

    enum class Mode { BACKGROUND, DASHBOARD, CHARGING, LOW_POWER, TURNING }

    fun request(mode: Mode): LocationRequest {
        return when (mode) {
            // Let the chip deliver as fast as it can (some do >1 Hz) for a responsive needle.
            Mode.DASHBOARD -> LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
                .setMinUpdateIntervalMillis(500L)
                .setMinUpdateDistanceMeters(0f)
                .setMaxUpdateDelayMillis(0L)
                .build()

            Mode.CHARGING -> LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
                .setMinUpdateIntervalMillis(1000L)
                .setMinUpdateDistanceMeters(0f)
                .setMaxUpdateDelayMillis(2000L)
                .build()

            Mode.BACKGROUND -> LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
                .setMinUpdateIntervalMillis(2000L)
                .setMinUpdateDistanceMeters(15f)
                .setMaxUpdateDelayMillis(15000L)
                .build()

            Mode.LOW_POWER -> LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 8000L)
                .setMinUpdateIntervalMillis(6000L)
                .setMinUpdateDistanceMeters(30f)
                .setMaxUpdateDelayMillis(30000L)
                .build()

            // Curvature-adaptive: dense, real-time through turns so corners aren't cut (design §4.9).
            Mode.TURNING -> LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
                .setMinUpdateIntervalMillis(1000L)
                .setMinUpdateDistanceMeters(0f)
                .setMaxUpdateDelayMillis(0L)
                .build()
        }
    }
}
