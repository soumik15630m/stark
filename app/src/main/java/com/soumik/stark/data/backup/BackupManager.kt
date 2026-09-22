package com.soumik.stark.data.backup

import android.content.Context
import com.soumik.stark.data.entity.DailyTotal
import com.soumik.stark.data.entity.FuelFill
import com.soumik.stark.data.entity.Leg
import com.soumik.stark.data.entity.LifetimeTotal
import com.soumik.stark.data.entity.Place
import com.soumik.stark.data.entity.Point
import com.soumik.stark.data.entity.TravelMode
import com.soumik.stark.data.repo.TrackRepository
import org.json.JSONArray
import org.json.JSONObject

/** Serialises the whole dataset to JSON for the `.stk` container, and restores it. */
class BackupManager(context: Context) {
    private val repo = TrackRepository.get(context)

    suspend fun exportJson(): String {
        val root = JSONObject()
        root.put("version", 2)
        root.put("exportedAt", System.currentTimeMillis())

        val legs = repo.legDao.allClosed()
        val legArr = JSONArray()
        val pointArr = JSONArray()
        legs.forEach { leg ->
            legArr.put(legToJson(leg))
            repo.pointsForLeg(leg.id).forEach { pointArr.put(pointToJson(it)) }
        }
        root.put("legs", legArr)
        root.put("points", pointArr)

        root.put("places", JSONArray().apply { repo.placeDao.all().forEach { put(placeToJson(it)) } })
        root.put("fuel", JSONArray().apply { repo.fuelDao.all().forEach { put(fuelToJson(it)) } })
        root.put("daily", JSONArray().apply { repo.totalsDao.allDaily().forEach { put(dailyToJson(it)) } })
        repo.totalsDao.lifetime()?.let { root.put("lifetime", lifetimeToJson(it)) }
        return root.toString()
    }

    /** Restore replacing existing data. Returns number of legs imported. */
    suspend fun importJson(json: String): Int {
        val root = JSONObject(json)
        val legs = root.optJSONArray("legs") ?: JSONArray()
        val oldToNew = HashMap<Long, Long>()
        for (i in 0 until legs.length()) {
            val o = legs.getJSONObject(i)
            val oldId = o.getLong("id")
            val newId = repo.legDao.insert(jsonToLeg(o).copy(id = 0))
            oldToNew[oldId] = newId
        }
        val points = root.optJSONArray("points") ?: JSONArray()
        val batch = ArrayList<Point>(512)
        for (i in 0 until points.length()) {
            val o = points.getJSONObject(i)
            val leg = oldToNew[o.getLong("legId")] ?: continue
            batch.add(jsonToPoint(o).copy(id = 0, legId = leg))
            if (batch.size >= 500) { repo.pointDao.insertAll(batch); batch.clear() }
        }
        if (batch.isNotEmpty()) repo.pointDao.insertAll(batch)

        root.optJSONArray("places")?.let { for (i in 0 until it.length()) repo.placeDao.insert(jsonToPlace(it.getJSONObject(i)).copy(id = 0)) }
        root.optJSONArray("fuel")?.let { for (i in 0 until it.length()) repo.fuelDao.insert(jsonToFuel(it.getJSONObject(i)).copy(id = 0)) }
        root.optJSONArray("daily")?.let { for (i in 0 until it.length()) repo.totalsDao.upsertDaily(jsonToDaily(it.getJSONObject(i))) }
        root.optJSONObject("lifetime")?.let { repo.totalsDao.upsertLifetime(jsonToLifetime(it)) }
        return oldToNew.size
    }

    private fun legToJson(l: Leg) = JSONObject().apply {
        put("id", l.id); put("mode", l.mode.name); put("startT", l.startT); put("offsetMin", l.offsetMin)
        put("dateKey", l.dateKey); put("endT", l.endT ?: JSONObject.NULL); put("distanceM", l.distanceM)
        put("durationS", l.durationS); put("maxSpeedMps", l.maxSpeedMps); put("pointCount", l.pointCount)
        put("hasEstimatedGap", l.hasEstimatedGap); put("label", l.label ?: JSONObject.NULL); put("closed", l.closed)
    }
    private fun jsonToLeg(o: JSONObject) = Leg(
        id = o.getLong("id"), mode = TravelMode.valueOf(o.optString("mode", "VEHICLE")),
        startT = o.getLong("startT"), offsetMin = o.getInt("offsetMin"), dateKey = o.getInt("dateKey"),
        endT = if (o.isNull("endT")) null else o.getLong("endT"), distanceM = o.getDouble("distanceM"),
        durationS = o.getLong("durationS"), maxSpeedMps = o.getDouble("maxSpeedMps"),
        pointCount = o.getInt("pointCount"), hasEstimatedGap = o.optBoolean("hasEstimatedGap"),
        label = if (o.isNull("label")) null else o.optString("label"), closed = o.optBoolean("closed", true),
    )
    private fun pointToJson(p: Point) = JSONObject().apply {
        put("legId", p.legId); put("tUtc", p.tUtc); put("offsetMin", p.offsetMin); put("latE7", p.latE7)
        put("lngE7", p.lngE7); put("accuracyM", p.accuracyM.toDouble()); put("speedMps", p.speedMps.toDouble()); put("confidence", p.confidence.ordinal)
    }
    private fun jsonToPoint(o: JSONObject) = Point(
        legId = o.getLong("legId"), tUtc = o.getLong("tUtc"), offsetMin = o.getInt("offsetMin"),
        latE7 = o.getInt("latE7"), lngE7 = o.getInt("lngE7"), accuracyM = o.getDouble("accuracyM").toFloat(),
        speedMps = o.getDouble("speedMps").toFloat(), confidence = com.soumik.stark.data.entity.Confidence.entries[o.getInt("confidence")],
    )
    private fun placeToJson(p: Place) = JSONObject().apply {
        put("latE7", p.latE7); put("lngE7", p.lngE7); put("radiusM", p.radiusM); put("geocell", p.geocell)
        put("name", p.name ?: JSONObject.NULL); put("category", p.category.name); put("isBaseHome", p.isBaseHome)
        put("visitCount", p.visitCount); put("firstSeen", p.firstSeen); put("lastSeen", p.lastSeen)
    }
    private fun jsonToPlace(o: JSONObject) = Place(
        latE7 = o.getInt("latE7"), lngE7 = o.getInt("lngE7"), radiusM = o.optInt("radiusM", 60), geocell = o.getLong("geocell"),
        name = if (o.isNull("name")) null else o.optString("name"),
        category = com.soumik.stark.data.entity.PlaceCategory.valueOf(o.optString("category", "OTHER")),
        isBaseHome = o.optBoolean("isBaseHome"), visitCount = o.optInt("visitCount"),
        firstSeen = o.optLong("firstSeen"), lastSeen = o.optLong("lastSeen"),
    )
    private fun fuelToJson(f: FuelFill) = JSONObject().apply {
        put("t", f.t); put("litres", f.litres); put("costInr", f.costInr); put("odoMAtFill", f.odoMAtFill); put("note", f.note ?: JSONObject.NULL)
    }
    private fun jsonToFuel(o: JSONObject) = FuelFill(
        t = o.getLong("t"), litres = o.getDouble("litres"), costInr = o.getDouble("costInr"),
        odoMAtFill = o.getDouble("odoMAtFill"), note = if (o.isNull("note")) null else o.optString("note"),
    )
    private fun dailyToJson(d: DailyTotal) = JSONObject().apply {
        put("dateKey", d.dateKey); put("distanceBikeM", d.distanceBikeM); put("distanceAllM", d.distanceAllM); put("tripCount", d.tripCount)
    }
    private fun jsonToDaily(o: JSONObject) = DailyTotal(o.getInt("dateKey"), o.getDouble("distanceBikeM"), o.getDouble("distanceAllM"), o.getInt("tripCount"))
    private fun lifetimeToJson(l: LifetimeTotal) = JSONObject().apply { put("distanceBikeM", l.distanceBikeM); put("distanceAllM", l.distanceAllM) }
    private fun jsonToLifetime(o: JSONObject) = LifetimeTotal(1, o.getDouble("distanceBikeM"), o.getDouble("distanceAllM"))
}
