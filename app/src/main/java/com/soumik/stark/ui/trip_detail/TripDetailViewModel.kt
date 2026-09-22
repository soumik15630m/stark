package com.soumik.stark.ui.trip_detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.soumik.stark.core.util.Geo
import com.soumik.stark.data.entity.Leg
import com.soumik.stark.data.entity.Point
import com.soumik.stark.data.entity.TravelMode
import com.soumik.stark.data.repo.TrackRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint

class TripDetailViewModel(app: Application, private val legId: Long) : AndroidViewModel(app) {
    private val repo = TrackRepository.get(app)

    val leg: StateFlow<Leg?> =
        repo.legDao.observeById(legId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val points = MutableStateFlow<List<Point>>(emptyList())
    val geo = MutableStateFlow<List<GeoPoint>>(emptyList())
    val startPlace = MutableStateFlow<String?>(null)
    val endPlace = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            val pts = repo.pointsForLeg(legId)
            points.value = pts
            geo.value = pts.map { GeoPoint(Geo.fromE7(it.latE7), Geo.fromE7(it.lngE7)) }
            leg.value?.let { l ->
                l.startPlaceId?.let { startPlace.value = repo.placeDao.byId(it)?.name }
                l.endPlaceId?.let { endPlace.value = repo.placeDao.byId(it)?.name }
            }
        }
    }

    fun setMode(mode: TravelMode) = viewModelScope.launch { repo.setLegMode(legId, mode) }
    fun setLabel(label: String?) = viewModelScope.launch { repo.setLegLabel(legId, label?.ifBlank { null }) }
    fun delete(onDone: () -> Unit) = viewModelScope.launch { repo.deleteLeg(legId); onDone() }
    fun mergePrevious() = viewModelScope.launch { repo.mergeWithPrevious(legId); reload() }
    fun share(context: android.content.Context) = viewModelScope.launch {
        com.soumik.stark.share.TripShare.shareGpx(context, legId)
    }
    fun splitAt(pointId: Long) = viewModelScope.launch { repo.splitLeg(legId, pointId); reload() }

    private suspend fun reload() {
        val pts = repo.pointsForLeg(legId)
        points.value = pts
        geo.value = pts.map { GeoPoint(Geo.fromE7(it.latE7), Geo.fromE7(it.lngE7)) }
    }
}
