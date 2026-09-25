package com.soumik.stark.ui.timeline

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.soumik.stark.core.time.TimeUtils
import com.soumik.stark.core.util.Geo
import com.soumik.stark.data.entity.Leg
import com.soumik.stark.data.repo.TrackRepository
import com.soumik.stark.domain.fuel.FuelEstimator
import com.soumik.stark.ui.fuel.FuelViewModel
import com.soumik.stark.ui.map.SpeedTrack
import com.soumik.stark.ui.map.StopPin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
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

/** A home→home outing (or the leftover "Other trips" bucket) with its underlying legs. */
data class OutingGroup(
    val outingId: Long?,
    val title: String,
    val distanceKm: Double,
    val tripCount: Int,
    val spanS: Long,
    val rows: List<LegRow>,
)

/** Headline numbers for the day's summary card. Fuel is an estimate (~) from km × mileage. */
data class DaySummary(
    val km: Double = 0.0,
    val ridingTimeS: Long = 0,
    val maxSpeedKmh: Int = 0,
    val trips: Int = 0,
    val estFuelL: Double? = null,
    val estCostInr: Double? = null,
)

data class DayView(
    val groups: List<OutingGroup> = emptyList(),
    val speedTracks: List<SpeedTrack> = emptyList(),
    val stops: List<StopPin> = emptyList(),
    val summary: DaySummary = DaySummary(),
    val tripCount: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TimelineViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = TrackRepository.get(app)

    val dateKey = MutableStateFlow(TimeUtils.todayKey())
    val day = MutableStateFlow(DayView())

    /** Per-day distance (metres) keyed by yyyymmdd, for the calendar heatmap. */
    val dailyKm: StateFlow<Map<Int, Double>> =
        repo.totalsDao.observeAllDaily()
            .map { list -> list.associate { it.dateKey to it.distanceAllM } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val streakDays = MutableStateFlow(0)

    private val legs: StateFlow<List<Leg>> =
        dateKey.flatMapLatest { repo.observeLegsForDay(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch { legs.collect { build(it) } }
        viewModelScope.launch {
            dailyKm.collect { map -> streakDays.value = computeStreak(map) }
        }
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

            if (prevLastPoint != null && wait >= 60) {
                val big = wait >= 600
                val where = prevEnd?.endPlaceId?.let { repo.placeDao.byId(it)?.name }
                val label = if (big) "Stopped ${fmtWait(wait)}${where?.let { " · $it" } ?: ""}"
                else "Paused ${fmtWait(wait)}"
                stops.add(StopPin(prevLastPoint!!, label, big))
            }
            prevEnd = leg
            prevLastPoint = pts.lastOrNull()?.let { GeoPoint(Geo.fromE7(it.latE7), Geo.fromE7(it.lngE7)) }
        }

        val groups = groupByOuting(sorted, rows)
        val summary = daySummary(sorted)

        day.value = DayView(groups, speedTracks, stops, summary, sorted.size)
    }

    /** Bucket the day's legs by their outing; each outing shows its home→home distance. */
    private suspend fun groupByOuting(sorted: List<Leg>, rows: List<LegRow>): List<OutingGroup> {
        val rowByLegId = rows.associateBy { it.leg.id }
        // Preserve first-seen order per outing, then present latest outing first.
        val order = LinkedHashMap<Long?, MutableList<Leg>>()
        for (leg in sorted) order.getOrPut(leg.outingId) { ArrayList() }.add(leg)

        val groups = ArrayList<OutingGroup>()
        for ((outingId, legsInGroup) in order) {
            val groupRows = legsInGroup.mapNotNull { rowByLegId[it.id] }
            val spanS = ((legsInGroup.last().endT ?: legsInGroup.last().startT) - legsInGroup.first().startT) / 1000
            if (outingId != null) {
                val outing = repo.outingDao.byId(outingId)
                val km = (outing?.distanceM ?: legsInGroup.sumOf { it.distanceM }) / 1000.0
                groups.add(OutingGroup(outingId, "Home → home", km, legsInGroup.size, spanS, groupRows))
            } else {
                groups.add(OutingGroup(null, "Other trips", legsInGroup.sumOf { it.distanceM } / 1000.0, legsInGroup.size, spanS, groupRows))
            }
        }
        return groups.reversed()
    }

    private suspend fun daySummary(sorted: List<Leg>): DaySummary {
        if (sorted.isEmpty()) return DaySummary()
        val key = sorted.first().dateKey
        val daily = repo.totalsDao.daily(key)
        val km = (daily?.distanceAllM ?: sorted.sumOf { it.distanceM }) / 1000.0
        val time = sorted.sumOf { it.durationS }
        val maxKmh = Math.round((sorted.maxOfOrNull { it.maxSpeedMps } ?: 0.0) * 3.6).toInt()
        val trips = daily?.tripCount ?: sorted.size

        // Estimated fuel for the day from the ledger's current mileage + price.
        val fills = repo.fuelDao.all()
        val tank = repo.settingDao.get(FuelViewModel.KEY_TANK_L)?.toDoubleOrNull() ?: 0.0
        val reserve = repo.settingDao.get(FuelViewModel.KEY_RESERVE_L)?.toDoubleOrNull() ?: 0.0
        val odo = repo.totalsDao.lifetime()?.distanceAllM ?: 0.0
        val est = FuelEstimator.estimate(fills, tank, reserve, odo)
        val fuelL = est.kmPerL?.let { if (it > 0) km / it else null }
        val cost = if (fuelL != null && est.pricePerL != null) fuelL * est.pricePerL!! else null

        return DaySummary(km, time, maxKmh, trips, fuelL, cost)
    }

    fun shiftDay(delta: Int) {
        val d = keyToDate(dateKey.value).plusDays(delta.toLong())
        dateKey.value = d.year * 10000 + d.monthValue * 100 + d.dayOfMonth
    }

    fun jumpTo(newKey: Int) { dateKey.value = newKey }

    private fun keyToDate(k: Int) = LocalDate.of(k / 10000, (k / 100) % 100, k % 100)

    private fun computeStreak(byDate: Map<Int, Double>): Int {
        val ridden = byDate.filter { it.value > 0 }.keys
        var d = LocalDate.now()
        var streak = 0
        while (ridden.contains(d.year * 10000 + d.monthValue * 100 + d.dayOfMonth)) { streak++; d = d.minusDays(1) }
        return streak
    }

    private fun fmtWait(s: Long): String {
        val m = s / 60
        return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
    }
}
