package com.soumik.stark.location_import

import android.content.Context
import com.soumik.stark.core.time.TimeUtils
import com.soumik.stark.data.entity.DailyTotal
import com.soumik.stark.data.entity.Leg
import com.soumik.stark.data.entity.LifetimeTotal
import com.soumik.stark.data.entity.TravelMode
import com.soumik.stark.data.repo.TrackRepository
import org.json.JSONObject
import java.time.Instant

/**
 * One-time offline import of Google Maps Timeline (Takeout "Semantic Location History").
 * Each activity segment becomes a leg (start/end + distance + duration); this bootstraps history
 * and the odometer. Full per-point tracks aren't in the semantic export, so imported legs have no
 * point geometry — they still count toward distance and the timeline.
 */
class TakeoutImporter(context: Context) {
    private val repo = TrackRepository.get(context)

    suspend fun import(json: String): Int {
        val root = JSONObject(json)
        val objects = root.optJSONArray("timelineObjects") ?: return 0
        var imported = 0
        for (i in 0 until objects.length()) {
            val seg = objects.getJSONObject(i).optJSONObject("activitySegment") ?: continue
            val distance = seg.optDouble("distance", seg.optDouble("distanceMeters", 0.0))
            if (distance <= 0) continue
            val duration = seg.optJSONObject("duration") ?: continue
            val startT = parseTime(duration.opt("startTimestamp")) ?: continue
            val endT = parseTime(duration.opt("endTimestamp")) ?: startT
            val mode = mapMode(seg.optString("activityType", ""))
            val offset = TimeUtils.offsetMinutes(startT)
            val dateKey = TimeUtils.localDateKey(startT, offset)

            repo.legDao.insert(
                Leg(
                    mode = mode, startT = startT, offsetMin = offset, dateKey = dateKey, endT = endT,
                    distanceM = distance, durationS = ((endT - startT) / 1000).coerceAtLeast(0),
                    pointCount = 0, label = "Imported", closed = true,
                )
            )
            val bike = mode == TravelMode.VEHICLE
            val daily = repo.totalsDao.daily(dateKey) ?: DailyTotal(dateKey)
            repo.totalsDao.upsertDaily(
                daily.copy(
                    distanceAllM = daily.distanceAllM + distance,
                    distanceBikeM = daily.distanceBikeM + if (bike) distance else 0.0,
                    tripCount = daily.tripCount + 1,
                )
            )
            val life = repo.totalsDao.lifetime() ?: LifetimeTotal()
            repo.totalsDao.upsertLifetime(
                life.copy(
                    distanceAllM = life.distanceAllM + distance,
                    distanceBikeM = life.distanceBikeM + if (bike) distance else 0.0,
                )
            )
            imported++
        }
        return imported
    }

    private fun mapMode(type: String): TravelMode = when (type.uppercase()) {
        "WALKING", "ON_FOOT", "STILL" -> TravelMode.WALK
        "RUNNING" -> TravelMode.RUN
        "CYCLING", "ON_BICYCLE" -> TravelMode.BICYCLE
        else -> TravelMode.VEHICLE
    }

    private fun parseTime(v: Any?): Long? = when (v) {
        null, JSONObject.NULL -> null
        is Number -> v.toLong()
        is String -> v.toLongOrNull() ?: try { Instant.parse(v).toEpochMilli() } catch (_: Exception) { null }
        else -> null
    }
}
