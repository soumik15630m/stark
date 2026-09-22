package com.soumik.stark.core.security

import android.content.Context
import com.soumik.stark.core.time.TimeUtils
import com.soumik.stark.core.util.Geo
import com.soumik.stark.core.util.GeoCell
import com.soumik.stark.data.db.StarkDatabase
import com.soumik.stark.data.entity.DailyTotal
import com.soumik.stark.data.entity.Leg
import com.soumik.stark.data.entity.LifetimeTotal
import com.soumik.stark.data.entity.Place
import com.soumik.stark.data.entity.PlaceCategory
import com.soumik.stark.data.entity.TravelMode
import com.soumik.stark.data.repo.TrackRepository

/**
 * Deniable decoy volume (design §6.2): a separate encrypted database with its own key, seeded
 * once with fully synthetic, generic-area data that never reveals the real neighbourhood.
 */
object Decoy {

    /** Switch the active DB to the decoy volume and seed it on first use. */
    suspend fun enter(context: Context) {
        StarkDatabase.switchVolume(context, decoy = true)
        TrackRepository.reset(context)
        seedIfEmpty(context)
    }

    fun leave(context: Context) {
        StarkDatabase.switchVolume(context, decoy = false)
        TrackRepository.reset(context)
    }

    private suspend fun seedIfEmpty(context: Context) {
        val repo = TrackRepository.get(context)
        if (repo.placeDao.all().isNotEmpty()) return

        // Generic downtown coordinates — deliberately not the real home area.
        val homeLat = 28.6139; val homeLng = 77.2090
        val workLat = 28.6280; val workLng = 77.2170
        repo.placeDao.insert(place("Home", homeLat, homeLng, PlaceCategory.HOME, isHome = true))
        repo.placeDao.insert(place("Office", workLat, workLng, PlaceCategory.WORK))

        var life = 0.0
        val now = System.currentTimeMillis()
        for (d in 1..14) {
            val dayStart = now - d * 86_400_000L
            for (trip in 0..1) {
                val startT = dayStart + trip * 9 * 3_600_000L + 8 * 3_600_000L
                val offset = TimeUtils.offsetMinutes(startT)
                val dateKey = TimeUtils.localDateKey(startT, offset)
                val dist = 7000.0 + (d % 3) * 900.0
                val dur = (dist / 8).toLong()
                repo.legDao.insert(
                    Leg(
                        mode = TravelMode.VEHICLE, startT = startT, offsetMin = offset, dateKey = dateKey,
                        endT = startT + dur * 1000, distanceM = dist, durationS = dur,
                        maxSpeedMps = 12.0, pointCount = 0, label = "Commute", closed = true,
                    )
                )
                val daily = repo.totalsDao.daily(dateKey) ?: DailyTotal(dateKey)
                repo.totalsDao.upsertDaily(daily.copy(distanceBikeM = daily.distanceBikeM + dist, distanceAllM = daily.distanceAllM + dist, tripCount = daily.tripCount + 1))
                life += dist
            }
        }
        repo.totalsDao.upsertLifetime(LifetimeTotal(1, life, life))
    }

    private fun place(name: String, lat: Double, lng: Double, cat: PlaceCategory, isHome: Boolean = false) =
        Place(
            latE7 = Geo.toE7(lat), lngE7 = Geo.toE7(lng), geocell = GeoCell.placeCell(lat, lng),
            name = name, category = cat, isBaseHome = isHome, visitCount = 20,
            firstSeen = System.currentTimeMillis(), lastSeen = System.currentTimeMillis(),
        )
}
