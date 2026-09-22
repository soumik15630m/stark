package com.soumik.stark.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.soumik.stark.core.time.TimeUtils
import com.soumik.stark.data.entity.DailyTotal
import com.soumik.stark.data.entity.LifetimeTotal
import com.soumik.stark.data.repo.TrackRepository
import com.soumik.stark.tracking.service.TrackingController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Backs the PIN-free riding dashboard: live speed + today/lifetime odometers. Reads the encrypted
 * DB via the Keystore-backed key (no PIN needed), so the glanceable numbers are available for a
 * mounted ride while the rest of the app stays locked.
 */
class QuickDashboardViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = TrackRepository.get(app)
    val live = TrackingController.state
    val lifetime: StateFlow<LifetimeTotal?> =
        repo.observeLifetime().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val today: StateFlow<DailyTotal?> =
        repo.observeDaily(TimeUtils.todayKey()).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
