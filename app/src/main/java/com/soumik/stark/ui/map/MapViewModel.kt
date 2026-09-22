package com.soumik.stark.ui.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.soumik.stark.core.util.Geo
import com.soumik.stark.core.util.GeoCell
import com.soumik.stark.data.repo.TrackRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint

data class HeatDot(val point: GeoPoint, val weight: Int)

class MapViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = TrackRepository.get(app)

    val tracks = MutableStateFlow<List<Track>>(emptyList())
    val heat = MutableStateFlow<List<HeatDot>>(emptyList())
    val loading = MutableStateFlow(true)

    init { load() }

    private fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            val legs = repo.legDao.allClosed().take(100)
            val ts = legs.mapNotNull { leg ->
                val pts = repo.pointsForLeg(leg.id)
                if (pts.size < 2) null
                else Track(pts.map { GeoPoint(Geo.fromE7(it.latE7), Geo.fromE7(it.lngE7)) })
            }
            val tiles = repo.heatDao.tilesAt(GeoCell.HEAT_ZOOM)
            val dots = tiles.map {
                val lat = GeoCell.tileToLat(it.y, GeoCell.HEAT_ZOOM)
                val lng = GeoCell.tileToLng(it.x, GeoCell.HEAT_ZOOM)
                HeatDot(GeoPoint(lat, lng), it.weight)
            }
            withContext(Dispatchers.Main) {
                tracks.value = ts
                heat.value = dots
                loading.value = false
            }
        }
    }
}
