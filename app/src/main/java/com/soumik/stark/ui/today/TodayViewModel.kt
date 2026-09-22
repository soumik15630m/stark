package com.soumik.stark.ui.today

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.soumik.stark.core.time.TimeUtils
import com.soumik.stark.data.entity.DailyTotal
import com.soumik.stark.data.entity.Leg
import com.soumik.stark.data.entity.LifetimeTotal
import com.soumik.stark.data.repo.TrackRepository
import com.soumik.stark.tracking.service.LiveState
import com.soumik.stark.tracking.service.TrackingController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class TodayViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = TrackRepository.get(app)
    private val todayKey = TimeUtils.todayKey()

    val lifetime: StateFlow<LifetimeTotal?> =
        repo.observeLifetime().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val today: StateFlow<DailyTotal?> =
        repo.observeDaily(todayKey).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val todayTrips: StateFlow<List<Leg>> =
        repo.observeLegsForDay(todayKey)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val live: StateFlow<LiveState> = TrackingController.state
}
