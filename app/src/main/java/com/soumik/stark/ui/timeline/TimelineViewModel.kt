package com.soumik.stark.ui.timeline

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.soumik.stark.core.time.TimeUtils
import com.soumik.stark.core.util.Geo
import com.soumik.stark.data.entity.Leg
import com.soumik.stark.data.repo.TrackRepository
import com.soumik.stark.ui.map.Track
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class TimelineViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = TrackRepository.get(app)

    val dateKey = MutableStateFlow(TimeUtils.todayKey())
    val tracks = MutableStateFlow<List<Track>>(emptyList())

    val legs: StateFlow<List<Leg>> =
        dateKey.flatMapLatest { repo.observeLegsForDay(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch { legs.collect { loadTracks(it) } }
    }

    private suspend fun loadTracks(dayLegs: List<Leg>) {
        tracks.value = dayLegs.mapNotNull { leg ->
            val pts = repo.pointsForLeg(leg.id)
            if (pts.size < 2) null
            else Track(pts.map { GeoPoint(Geo.fromE7(it.latE7), Geo.fromE7(it.lngE7)) })
        }
    }

    fun shiftDay(delta: Int) {
        val d = keyToDate(dateKey.value).plusDays(delta.toLong())
        dateKey.value = d.year * 10000 + d.monthValue * 100 + d.dayOfMonth
    }

    private fun keyToDate(k: Int) = LocalDate.of(k / 10000, (k / 100) % 100, k % 100)
}
