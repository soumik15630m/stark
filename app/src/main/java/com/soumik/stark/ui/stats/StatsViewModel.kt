package com.soumik.stark.ui.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.soumik.stark.data.entity.DailyTotal
import com.soumik.stark.data.entity.Record
import com.soumik.stark.data.repo.TrackRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class StatsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = TrackRepository.get(app)

    val daily: StateFlow<List<DailyTotal>> =
        repo.totalsDao.observeAllDaily().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val records: StateFlow<List<Record>> =
        repo.recordDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hourHistogram = MutableStateFlow(DoubleArray(24))
    val streakDays = MutableStateFlow(0)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val legs = repo.legDao.allClosed()
            val hours = DoubleArray(24)
            legs.forEach { leg ->
                val localMs = leg.startT + leg.offsetMin * 60_000L
                val hour = ((localMs / 3_600_000L) % 24).toInt()
                hours[hour] += leg.distanceM
            }
            hourHistogram.value = hours
            streakDays.value = computeStreak(repo.totalsDao.allDaily().map { it.dateKey to it.distanceAllM })
        }
    }

    private fun computeStreak(days: List<Pair<Int, Double>>): Int {
        val ridden = days.filter { it.second > 0 }.map { keyToDate(it.first) }.toSet()
        var streak = 0
        var d = LocalDate.now()
        while (ridden.contains(d)) { streak++; d = d.minusDays(1) }
        return streak
    }

    private fun keyToDate(key: Int) = LocalDate.of(key / 10000, (key / 100) % 100, key % 100)
}
