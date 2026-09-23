package com.soumik.stark.domain

import android.content.Context
import com.soumik.stark.data.repo.TrackRepository
import com.soumik.stark.domain.outing.OutingEngine
import com.soumik.stark.domain.outing.OutingSummary
import com.soumik.stark.domain.places.Geocode
import com.soumik.stark.domain.places.PlaceEngine
import com.soumik.stark.domain.stats.HeatEngine
import com.soumik.stark.domain.stats.RecordsEngine

/**
 * Runs once per closed leg, off the tracking hot path: resolve start/end places, group into the
 * current outing, update records and the heatmap, and surface a back-home summary when due.
 */
class PostTripProcessor(private val context: Context) {
    private val repo = TrackRepository.get(context)
    private val outings = OutingEngine(repo)
    private val records = RecordsEngine(repo)
    private val heat = HeatEngine(repo)

    suspend fun process(legId: Long): OutingSummary? {
        val leg = repo.legDao.byId(legId) ?: return null
        val points = repo.pointsForLeg(legId)
        if (points.isEmpty()) return null

        val googleKey = repo.setting(SETTING_GOOGLE_KEY)
        val places = PlaceEngine(repo, Geocode(context, googleKey))
        val start = points.first()
        val end = points.last()
        val startPlaceId = places.nearestPlaceId(start.latE7, start.lngE7)
        val endPlaceId = places.registerStop(end.latE7, end.lngE7, leg.endT ?: leg.startT, leg.offsetMin)

        var withPlaces = leg.copy(startPlaceId = startPlaceId, endPlaceId = endPlaceId)
        // Auto-label recurring routes (design §8.3): seen this start→end pair before → name it.
        if (withPlaces.label == null && startPlaceId != null && endPlaceId != null) {
            if (repo.legDao.countRoute(startPlaceId, endPlaceId) >= 2) {
                val localHour = ((leg.startT + leg.offsetMin * 60_000L) / 3_600_000L % 24).toInt()
                withPlaces = withPlaces.copy(
                    label = when (localHour) {
                        in 5..11 -> "Morning commute"
                        in 16..21 -> "Evening return"
                        else -> "Regular ride"
                    }
                )
            }
        }
        repo.legDao.update(withPlaces)

        heat.addLeg(points)
        val newRecord = records.onLegClosed(withPlaces)
        return outings.onLegClosed(withPlaces, Math.round(leg.maxSpeedMps * 3.6).toInt(), newRecord)
    }

    companion object {
        const val SETTING_GOOGLE_KEY = "google_api_key"
    }
}
