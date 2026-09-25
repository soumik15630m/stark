package com.soumik.stark.ui.trip_detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.soumik.stark.core.util.Geo
import com.soumik.stark.data.entity.Point
import com.soumik.stark.ui.map.SpeedTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint

/** Replays a whole home→home outing: every leg's route stitched into one continuous playback. */
class OutingReplayViewModel(app: Application, private val outingId: Long) : AndroidViewModel(app) {
    private val repo = com.soumik.stark.data.repo.TrackRepository.get(app)

    data class State(
        val title: String = "Home → home",
        val distanceM: Double = 0.0,
        val durationS: Long = 0,
        val maxSpeedMps: Double = 0.0,
        val tripCount: Int = 0,
        val perLegTracks: List<SpeedTrack> = emptyList(), // drawn separately so parked gaps aren't joined
        val geoAll: List<GeoPoint> = emptyList(),          // concatenated path the marker walks
        val speedsAll: List<Float> = emptyList(),
    )

    val state = MutableStateFlow(State())

    init {
        viewModelScope.launch {
            val outing = repo.outingDao.byId(outingId)
            val legs = repo.legDao.legsForOuting(outingId).sortedBy { it.startT }
            val tracks = ArrayList<SpeedTrack>()
            val geoAll = ArrayList<GeoPoint>()
            val speedsAll = ArrayList<Float>()
            var maxSpeed = 0.0
            for (leg in legs) {
                val pts: List<Point> = repo.pointsForLeg(leg.id)
                if (pts.size >= 2) {
                    val g = pts.map { GeoPoint(Geo.fromE7(it.latE7), Geo.fromE7(it.lngE7)) }
                    tracks.add(SpeedTrack(g, pts.map { it.speedMps * 3.6 }))
                    geoAll.addAll(g)
                    speedsAll.addAll(pts.map { it.speedMps })
                }
                if (leg.maxSpeedMps > maxSpeed) maxSpeed = leg.maxSpeedMps
            }
            val distance = outing?.distanceM ?: legs.sumOf { it.distanceM }
            val span = if (legs.isEmpty()) 0L
            else ((legs.last().endT ?: legs.last().startT) - legs.first().startT) / 1000
            state.value = State(
                distanceM = distance,
                durationS = span,
                maxSpeedMps = maxSpeed,
                tripCount = legs.size,
                perLegTracks = tracks,
                geoAll = geoAll,
                speedsAll = speedsAll,
            )
        }
    }
}
