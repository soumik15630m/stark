package com.soumik.stark.tracking.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** ARMED = enabled, waiting for motion (no GPS). ACTIVE = capturing. PAUSED = user-paused. */
enum class TrackState { DISABLED, ARMED, ACTIVE, PAUSED }

data class LiveState(
    val state: TrackState = TrackState.DISABLED,
    val speedKmh: Double = 0.0,
    val tripDistanceM: Double = 0.0,
    val tripDurationS: Long = 0,
    val tripMaxSpeedKmh: Double = 0.0,
    val lastFixAgeMs: Long = 0,
    val gpsFixCount: Int = 0,
) {
    val enabled: Boolean get() = state != TrackState.DISABLED
    val tracking: Boolean get() = state == TrackState.ACTIVE
    val paused: Boolean get() = state == TrackState.PAUSED
}

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

    fun setState(s: TrackState) {
        _state.value = _state.value.copy(state = s)
    }

    fun reset() {
        _state.value = LiveState()
    }

    val isTracking: Boolean get() = _state.value.state == TrackState.ACTIVE
    val isEnabled: Boolean get() = _state.value.enabled
}
