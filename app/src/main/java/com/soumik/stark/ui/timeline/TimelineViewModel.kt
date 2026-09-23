package com.soumik.stark.ui.timeline

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.soumik.stark.core.time.TimeUtils
import com.soumik.stark.core.util.Geo
import com.soumik.stark.data.entity.Leg
import com.soumik.stark.data.repo.TrackRepository
import com.soumik.stark.ui.map.SpeedTrack
import com.soumik.stark.ui.map.StopPin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import java.time.LocalDate

data class LegRow(
    val leg: Leg,
    val startName: String?,
    val endName: String?,
    val waitBeforeS: Long,      // idle time since the previous trip ended
    val avgMovingKmh: Int,
)

data class DayView(
    val rows: List<LegRow> = emptyList(),
    val speedTracks: List<SpeedTrack> = emptyList(),
    val stops: List<StopPin> = emptyList(),
    val outingKm: Double = 0.0,     // aggregate home-to-home distance for the day
    val outingSpanS: Long = 0,
    val tripCount: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TimelineViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = TrackRepository.get(app)

    val dateKey = MutableStateFlow(TimeUtils.todayKey())
    val day = MutableStateFlow(DayView())

    private val legs: StateFlow<List<Leg>> =
        dateKey.flatMapLatest { repo.observeLegsForDay(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch { legs.collect { build(it) } }
    }

    private suspend fun build(dayLegs: List<Leg>) {
        val sorted = dayLegs.sortedBy { it.startT }
        val speedTracks = ArrayList<SpeedTrack>()
        val stops = ArrayList<StopPin>()
        val rows = ArrayList<LegRow>()

        var prevEnd: Leg? = null
        var prevLastPoint: GeoPoint? = null
        for (leg in sorted) {
            val pts = repo.pointsForLeg(leg.id)
            if (pts.size >= 2) {
                speedTracks.add(
                    SpeedTrack(
                        pts.map { GeoPoint(Geo.fromE7(it.latE7), Geo.fromE7(it.lngE7)) },
                        pts.map { it.speedMps * 3.6 },
                    )
                )
            }
            val startName = leg.startPlaceId?.let { repo.placeDao.byId(it)?.name }
            val endName = leg.endPlaceId?.let { repo.placeDao.byId(it)?.name }
            val wait = prevEnd?.let { (leg.startT - (it.endT ?: it.startT)).coerceAtLeast(0) / 1000 } ?: 0L
            val avg = if (leg.movingDurationS > 0) Math.round(leg.distanceM / leg.movingDurationS * 3.6).toInt() else 0
            rows.add(LegRow(leg, startName, endName, wait, avg))

            // A stop/pause marker sits where the previous trip ended.
            if (prevLastPoint != null && wait >= 60) {
                val big = wait >= 600 // ≥10 min → a real stop (bus-stop icon); shorter → brief pause
                val where = prevEnd?.endPlaceId?.let { repo.placeDao.byId(it)?.name }
                val label = if (big) "Stopped ${fmtWait(wait)}${where?.let { " · $it" } ?: ""}"
                else "Paused ${fmtWait(wait)}"
                stops.add(StopPin(prevLastPoint!!, label, big))
            }
            prevEnd = leg
            prevLastPoint = pts.lastOrNull()?.let { GeoPoint(Geo.fromE7(it.latE7), Geo.fromE7(it.lngE7)) }
        }

        val outingKm = sorted.sumOf { it.distanceM }
        val span = if (sorted.isEmpty()) 0L
        else ((sorted.last().endT ?: sorted.last().startT) - sorted.first().startT) / 1000

        day.value = DayView(rows.reversed(), speedTracks, stops, outingKm, span, sorted.size)
    }

    fun shiftDay(delta: Int) {
        val d = keyToDate(dateKey.value).plusDays(delta.toLong())
        dateKey.value = d.year * 10000 + d.monthValue * 100 + d.dayOfMonth
    }

    private fun keyToDate(k: Int) = LocalDate.of(k / 10000, (k / 100) % 100, k % 100)

    private fun fmtWait(s: Long): String {
        val m = s / 60
        return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
    }
}
