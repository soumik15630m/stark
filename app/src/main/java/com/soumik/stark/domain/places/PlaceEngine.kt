package com.soumik.stark.domain.places

import com.soumik.stark.core.util.Geo
import com.soumik.stark.core.util.GeoCell
import com.soumik.stark.data.entity.Place
import com.soumik.stark.data.entity.Visit
import com.soumik.stark.data.repo.TrackRepository

/**
 * Incremental grid-cell place clustering (design §4B): each stop snaps to a geocell and merges
 * with the nearest existing place within radius, else creates one. O(1)-ish, no batch DBSCAN.
 */
class PlaceEngine(
    private val repo: TrackRepository,
    private val geocode: Geocode?,
    private val mergeRadiusM: Double = 80.0,
) {
    suspend fun nearestPlaceId(latE7: Int, lngE7: Int): Long? =
        nearest(Geo.fromE7(latE7), Geo.fromE7(lngE7))?.id

    private suspend fun nearest(lat: Double, lng: Double): Place? {
        // Fast path: same cell. Fallback: nearest within radius over all places (personal scale).
        repo.placeDao.byCell(GeoCell.placeCell(lat, lng))?.let { return it }
        return repo.placeDao.all().minByOrNull {
            Geo.distanceM(lat, lng, Geo.fromE7(it.latE7), Geo.fromE7(it.lngE7))
        }?.takeIf {
            Geo.distanceM(lat, lng, Geo.fromE7(it.latE7), Geo.fromE7(it.lngE7)) <= mergeRadiusM
        }
    }

    /** Register a stop: merge into or create a place, bump its visit count, log a visit. */
    suspend fun registerStop(latE7: Int, lngE7: Int, t: Long, offsetMin: Int): Long {
        val lat = Geo.fromE7(latE7)
        val lng = Geo.fromE7(lngE7)
        val existing = nearest(lat, lng)
        val placeId: Long
        if (existing != null) {
            repo.placeDao.update(
                existing.copy(visitCount = existing.visitCount + 1, lastSeen = t)
            )
            placeId = existing.id
        } else {
            val name = geocode?.label(lat, lng)
            placeId = repo.placeDao.insert(
                Place(
                    latE7 = latE7,
                    lngE7 = lngE7,
                    geocell = GeoCell.placeCell(lat, lng),
                    name = name,
                    visitCount = 1,
                    firstSeen = t,
                    lastSeen = t,
                )
            )
        }
        repo.visitDao.insert(Visit(placeId = placeId, arriveT = t, departT = null, offsetMin = offsetMin))
        return placeId
    }
}
