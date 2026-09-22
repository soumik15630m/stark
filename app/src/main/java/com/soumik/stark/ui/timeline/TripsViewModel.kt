package com.soumik.stark.ui.timeline

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.soumik.stark.data.entity.Leg
import com.soumik.stark.data.repo.TrackRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class TripsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = TrackRepository.get(app)

    val byDay: StateFlow<List<Pair<Int, List<Leg>>>> =
        repo.observeClosedLegs()
            .map { legs -> legs.groupBy { it.dateKey }.toList().sortedByDescending { it.first } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
