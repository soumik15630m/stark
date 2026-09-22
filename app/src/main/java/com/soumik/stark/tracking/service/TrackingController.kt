package com.soumik.stark.tracking.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LiveState(
    val tracking: Boolean = false,
    val speedKmh: Double = 0.0,
    val tripDistanceM: Double = 0.0,
    val tripDurationS: Long = 0,
    val tripMaxSpeedKmh: Double = 0.0,
    val lastFixAgeMs: Long = 0,
    val gpsFixCount: Int = 0,
    val paused: Boolean = false,
)

/** Process-wide live tracking state, published by the service and observed by the UI. */
object TrackingController {
    private val _state = MutableStateFlow(LiveState())
    val state: StateFlow<LiveState> = _state.asStateFlow()

    fun update(transform: (LiveState) -> LiveState) {
        _state.value = transform(_state.value)
    }

    fun set(state: LiveState) {
        _state.value = state
    }

    fun reset() {
        _state.value = LiveState()
    }

    val isTracking: Boolean get() = _state.value.tracking
}
