package com.soumik.stark.location_import

import android.content.Context
import com.soumik.stark.core.time.TimeUtils
import com.soumik.stark.core.util.Geo
import com.soumik.stark.data.entity.DailyTotal
import com.soumik.stark.data.entity.Leg
import com.soumik.stark.data.entity.LifetimeTotal
import com.soumik.stark.data.entity.TravelMode
import com.soumik.stark.data.repo.TrackRepository
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime

/**
 * One-time offline import of Google Maps Timeline. Handles all three export shapes:
 *  - Old Takeout "Semantic Location History": `{ "timelineObjects": [ { "activitySegment": … } ] }`
 *  - New on-device Android export (`Timeline.json`): `{ "semanticSegments": [ { "activity": … } ] }`
 *  - New on-device iOS export: a top-level JSON array of the same segment objects.
 *
 * Each movement activity becomes a leg (start/end + distance + duration); this bootstraps history
 * and the odometer. The semantic/on-device exports carry no per-point track, so imported legs have
 * no point geometry — they still count toward distance and the timeline.
 */
class TakeoutImporter(context: Context) {
    private val repo = TrackRepository.get(context)

    suspend fun import(json: String): Int {
        val trimmed = json.trimStart()
        // The iOS on-device export is a bare JSON array; everything else is an object.
        val root: Any = if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONObject(trimmed)

        val old = (root as? JSONObject)?.optJSONArray("timelineObjects")
        if (old != null) return importOld(old)

        val segments = when (root) {
            is JSONArray -> root
            is JSONObject -> root.optJSONArray("semanticSegments")
                ?: root.optJSONArray("timelineObjects")
                ?: return 0
            else -> return 0
        }
        return importNew(segments)
    }

    /** Old Takeout: timelineObjects → activitySegment. */
    private suspend fun importOld(objects: JSONArray): Int {
        var imported = 0
        for (i in 0 until objects.length()) {
            val seg = objects.getJSONObject(i).optJSONObject("activitySegment") ?: continue
            val distance = seg.optDouble("distance", seg.optDouble("distanceMeters", 0.0))
            if (distance <= 0) continue
            val duration = seg.optJSONObject("duration") ?: continue
            val startT = parseTime(duration.opt("startTimestamp")) ?: continue
            val endT = parseTime(duration.opt("endTimestamp")) ?: startT
            if (insertLeg(mapMode(seg.optString("activityType", "")), startT, endT, distance)) imported++
        }
        return imported
    }

    /** New on-device export: each segment may carry an `activity` (movement) block. */
    private suspend fun importNew(segments: JSONArray): Int {
        var imported = 0
        for (i in 0 until segments.length()) {
            val seg = segments.optJSONObject(i) ?: continue
            val activity = seg.optJSONObject("activity") ?: continue  // skip visits / path-only segments
            val startT = parseTime(seg.opt("startTime")) ?: continue
            val endT = parseTime(seg.opt("endTime")) ?: startT

            var distance = numeric(activity.opt("distanceMeters"))
                ?: numeric(activity.opt("distance"))
                ?: 0.0
            if (distance <= 0) {
                // Fall back to the straight-line start→end distance when the export omits it.
                val s = latLng(activity.optJSONObject("start")?.optString("latLng"))
                val e = latLng(activity.optJSONObject("end")?.optString("latLng"))
                if (s != null && e != null) distance = Geo.distanceM(s[0], s[1], e[0], e[1])
            }
            if (distance <= 0) continue

            val type = activity.optJSONObject("topCandidate")?.optString("type")
                ?: activity.optString("type", "")
            if (insertLeg(mapMode(type), startT, endT, distance)) imported++
        }
        return imported
    }

    private suspend fun insertLeg(mode: TravelMode, startT: Long, endT: Long, distance: Double): Boolean {
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
        return true
    }

    // Google's newer types are free-text phrases ("in passenger vehicle", "motorcycling", …).
    private fun mapMode(type: String): TravelMode {
        val t = type.lowercase()
        return when {
            "cycl" in t || "bicycl" in t -> TravelMode.BICYCLE   // pedal bike, not the motorbike
            "run" in t -> TravelMode.RUN
            "walk" in t || "on_foot" in t || "on foot" in t -> TravelMode.WALK
            else -> TravelMode.VEHICLE  // driving / motorcycling / passenger vehicle / unknown
        }
    }

    /** distanceMeters can arrive as a number or a numeric string across exports. */
    private fun numeric(v: Any?): Double? = when (v) {
        null, JSONObject.NULL -> null
        is Number -> v.toDouble()
        is String -> v.toDoubleOrNull()
        else -> null
    }

    /** Parse a "12.9716°, 77.5946°" latLng string into [lat, lng]. */
    private fun latLng(s: String?): DoubleArray? {
        if (s.isNullOrBlank()) return null
        val parts = s.replace("°", "").split(",")
        if (parts.size != 2) return null
        val lat = parts[0].trim().toDoubleOrNull() ?: return null
        val lng = parts[1].trim().toDoubleOrNull() ?: return null
        return doubleArrayOf(lat, lng)
    }

    private fun parseTime(v: Any?): Long? = when (v) {
        null, JSONObject.NULL -> null
        is Number -> v.toLong()
        is String -> v.toLongOrNull()
            ?: try { Instant.parse(v).toEpochMilli() }
            catch (_: Exception) { try { OffsetDateTime.parse(v).toInstant().toEpochMilli() } catch (_: Exception) { null } }
        else -> null
    }
}
