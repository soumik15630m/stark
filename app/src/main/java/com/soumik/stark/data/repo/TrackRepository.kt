package com.soumik.stark.data.repo

import android.content.Context
import androidx.room.withTransaction
import com.soumik.stark.core.time.TimeUtils
import com.soumik.stark.data.db.StarkDatabase
import com.soumik.stark.data.entity.DailyTotal
import com.soumik.stark.data.entity.Leg
import com.soumik.stark.data.entity.LifetimeTotal
import com.soumik.stark.data.entity.Point
import com.soumik.stark.data.entity.TravelMode
import kotlinx.coroutines.flow.Flow

/**
 * Single writer for tracked data. Distance is summed incrementally as points arrive, so the
 * odometer totals are always current with no full recompute (design §4.5).
 */
class TrackRepository private constructor(val db: StarkDatabase) : TrackSink {

    val legDao = db.legDao()
    val pointDao = db.pointDao()
    val totalsDao = db.totalsDao()
    val placeDao = db.placeDao()
    val visitDao = db.visitDao()
    val outingDao = db.outingDao()
    val fuelDao = db.fuelDao()
    val recordDao = db.recordDao()
    val heatDao = db.heatDao()
    val privacyDao = db.privacyDao()
    val settingDao = db.settingDao()

    fun observeLifetime(): Flow<LifetimeTotal?> = totalsDao.observeLifetime()
    fun observeDaily(dateKey: Int): Flow<DailyTotal?> = totalsDao.observeDaily(dateKey)
    fun observeClosedLegs(): Flow<List<Leg>> = legDao.observeClosedLegs()
    fun observeLegsForDay(dateKey: Int): Flow<List<Leg>> = legDao.observeLegsForDay(dateKey)
    suspend fun pointsForLeg(legId: Long): List<Point> = pointDao.pointsForLeg(legId)
    suspend fun pointCount(): Int = pointDao.count()

    override suspend fun openLeg(startT: Long, offsetMin: Int, mode: TravelMode): Long {
        val dateKey = TimeUtils.localDateKey(startT, offsetMin)
        return db.withTransaction {
            val id = legDao.insert(
                Leg(startT = startT, offsetMin = offsetMin, dateKey = dateKey, mode = mode)
            )
            val daily = totalsDao.daily(dateKey) ?: DailyTotal(dateKey)
            totalsDao.upsertDaily(daily.copy(tripCount = daily.tripCount + 1))
            id
        }
    }

    override suspend fun flushBatch(
        legId: Long,
        points: List<Point>,
        addedDistanceM: Double,
        maxSpeedMps: Double,
        endT: Long,
        durationS: Long,
        movingDurationS: Long,
        hasEstimatedGap: Boolean,
    ) {
        if (points.isEmpty() && addedDistanceM == 0.0) return
        db.withTransaction {
            if (points.isNotEmpty()) pointDao.insertAll(points)
            val leg = legDao.byId(legId) ?: return@withTransaction
            legDao.update(
                leg.copy(
                    endT = endT,
                    distanceM = leg.distanceM + addedDistanceM,
                    durationS = durationS,
                    movingDurationS = movingDurationS,
                    maxSpeedMps = maxOf(leg.maxSpeedMps, maxSpeedMps),
                    pointCount = leg.pointCount + points.size,
                    hasEstimatedGap = leg.hasEstimatedGap || hasEstimatedGap,
                )
            )
            val bike = leg.mode == TravelMode.VEHICLE
            val daily = totalsDao.daily(leg.dateKey) ?: DailyTotal(leg.dateKey)
            totalsDao.upsertDaily(
                daily.copy(
                    distanceAllM = daily.distanceAllM + addedDistanceM,
                    distanceBikeM = daily.distanceBikeM + if (bike) addedDistanceM else 0.0,
                )
            )
            val life = totalsDao.lifetime() ?: LifetimeTotal()
            totalsDao.upsertLifetime(
                life.copy(
                    distanceAllM = life.distanceAllM + addedDistanceM,
                    distanceBikeM = life.distanceBikeM + if (bike) addedDistanceM else 0.0,
                )
            )
        }
    }

    override suspend fun closeLeg(
        legId: Long, endT: Long, durationS: Long, minDistanceM: Double, minMaxSpeedMps: Double,
    ): Long? {
        return db.withTransaction {
            val leg = legDao.byId(legId) ?: return@withTransaction null
            val drift = leg.distanceM < minDistanceM || leg.maxSpeedMps < minMaxSpeedMps
            if (drift) {
                rollbackTotals(leg)
                pointDao.deleteForLeg(legId)
                legDao.delete(legId)
                null
            } else {
                legDao.update(leg.copy(endT = endT, durationS = durationS, closed = true))
                legId
            }
        }
    }

    private suspend fun rollbackTotals(leg: Leg) {
        val bike = leg.mode == TravelMode.VEHICLE
        totalsDao.daily(leg.dateKey)?.let { daily ->
            totalsDao.upsertDaily(
                daily.copy(
                    distanceAllM = (daily.distanceAllM - leg.distanceM).coerceAtLeast(0.0),
                    distanceBikeM = (daily.distanceBikeM - if (bike) leg.distanceM else 0.0).coerceAtLeast(0.0),
                    tripCount = (daily.tripCount - 1).coerceAtLeast(0),
                )
            )
        }
        totalsDao.lifetime()?.let { life ->
            totalsDao.upsertLifetime(
                life.copy(
                    distanceAllM = (life.distanceAllM - leg.distanceM).coerceAtLeast(0.0),
                    distanceBikeM = (life.distanceBikeM - if (bike) leg.distanceM else 0.0).coerceAtLeast(0.0),
                )
            )
        }
    }

    /** Delete a kept leg and reverse its odometer contribution (user delete from trip detail). */
    suspend fun deleteLeg(legId: Long) {
        db.withTransaction {
            val leg = legDao.byId(legId) ?: return@withTransaction
            rollbackTotals(leg)
            pointDao.deleteForLeg(legId)
            legDao.delete(legId)
        }
    }

    /** Change a leg's mode, moving its distance between the bike and all-mode day/lifetime buckets. */
    suspend fun setLegMode(legId: Long, mode: TravelMode) {
        db.withTransaction {
            val leg = legDao.byId(legId) ?: return@withTransaction
            if (leg.mode == mode) return@withTransaction
            val wasBike = leg.mode == TravelMode.VEHICLE
            val nowBike = mode == TravelMode.VEHICLE
            if (wasBike != nowBike) {
                val delta = if (nowBike) leg.distanceM else -leg.distanceM
                totalsDao.daily(leg.dateKey)?.let {
                    totalsDao.upsertDaily(it.copy(distanceBikeM = (it.distanceBikeM + delta).coerceAtLeast(0.0)))
                }
                totalsDao.lifetime()?.let {
                    totalsDao.upsertLifetime(it.copy(distanceBikeM = (it.distanceBikeM + delta).coerceAtLeast(0.0)))
                }
            }
            legDao.update(leg.copy(mode = mode))
        }
    }

    suspend fun setLegLabel(legId: Long, label: String?) {
        legDao.byId(legId)?.let { legDao.update(it.copy(label = label)) }
    }

    /** Split a leg into two at [atPointId]; the tail becomes a new leg. Totals are unchanged. */
    suspend fun splitLeg(legId: Long, atPointId: Long) {
        db.withTransaction {
            val leg = legDao.byId(legId) ?: return@withTransaction
            val points = pointDao.pointsForLeg(legId)
            val idx = points.indexOfFirst { it.id == atPointId }
            if (idx <= 0 || idx >= points.size - 1) return@withTransaction
            val head = points.subList(0, idx + 1)
            val tail = points.subList(idx + 1, points.size)

            val headDist = pathDistance(head)
            val tailDist = pathDistance(tail)
            val newLegId = legDao.insert(
                leg.copy(
                    id = 0,
                    startT = tail.first().tUtc,
                    endT = leg.endT,
                    distanceM = tailDist,
                    durationS = ((leg.endT ?: tail.last().tUtc) - tail.first().tUtc) / 1000,
                    pointCount = tail.size,
                    maxSpeedMps = tail.maxOf { it.speedMps }.toDouble(),
                )
            )
            tail.forEach { p -> pointDao.reassignPoint(p.id, newLegId) }
            legDao.update(
                leg.copy(
                    endT = head.last().tUtc,
                    distanceM = headDist,
                    durationS = (head.last().tUtc - head.first().tUtc) / 1000,
                    pointCount = head.size,
                    maxSpeedMps = head.maxOf { it.speedMps }.toDouble(),
                )
            )
            totalsDao.daily(leg.dateKey)?.let { totalsDao.upsertDaily(it.copy(tripCount = it.tripCount + 1)) }
        }
    }

    /** Merge a leg into the immediately-preceding leg of the same day. */
    suspend fun mergeWithPrevious(legId: Long) {
        db.withTransaction {
            val leg = legDao.byId(legId) ?: return@withTransaction
            val prev = legDao.legsForDay(leg.dateKey)
                .filter { it.closed && it.startT < leg.startT }
                .maxByOrNull { it.startT } ?: return@withTransaction
            pointDao.reassign(leg.id, prev.id)
            legDao.update(
                prev.copy(
                    endT = leg.endT,
                    distanceM = prev.distanceM + leg.distanceM,
                    durationS = ((leg.endT ?: leg.startT) - prev.startT) / 1000,
                    pointCount = prev.pointCount + leg.pointCount,
                    maxSpeedMps = maxOf(prev.maxSpeedMps, leg.maxSpeedMps),
                    endPlaceId = leg.endPlaceId,
                    hasEstimatedGap = prev.hasEstimatedGap || leg.hasEstimatedGap,
                )
            )
            legDao.delete(leg.id)
            totalsDao.daily(leg.dateKey)?.let { totalsDao.upsertDaily(it.copy(tripCount = (it.tripCount - 1).coerceAtLeast(0))) }
        }
    }

    private fun pathDistance(points: List<Point>): Double {
        var d = 0.0
        for (i in 1 until points.size) {
            d += com.soumik.stark.core.util.Geo.distanceM(
                com.soumik.stark.core.util.Geo.fromE7(points[i - 1].latE7),
                com.soumik.stark.core.util.Geo.fromE7(points[i - 1].lngE7),
                com.soumik.stark.core.util.Geo.fromE7(points[i].latE7),
                com.soumik.stark.core.util.Geo.fromE7(points[i].lngE7),
            )
        }
        return d
    }

    suspend fun renamePlace(id: Long, name: String?) {
        placeDao.byId(id)?.let { placeDao.update(it.copy(name = name?.ifBlank { null })) }
    }

    suspend fun setPlaceCategory(id: Long, category: com.soumik.stark.data.entity.PlaceCategory) {
        placeDao.byId(id)?.let { placeDao.update(it.copy(category = category)) }
    }

    suspend fun setHomePlace(id: Long) {
        db.withTransaction {
            placeDao.home()?.let { if (it.id != id) placeDao.update(it.copy(isBaseHome = false)) }
            placeDao.byId(id)?.let { placeDao.update(it.copy(isBaseHome = true, category = com.soumik.stark.data.entity.PlaceCategory.HOME)) }
        }
    }

    suspend fun setting(key: String): String? = settingDao.get(key)
    suspend fun putSetting(key: String, value: String) =
        settingDao.put(com.soumik.stark.data.entity.Setting(key, value))

    companion object {
        @Volatile private var instance: TrackRepository? = null
        fun get(context: Context): TrackRepository =
            instance ?: synchronized(this) {
                instance ?: TrackRepository(StarkDatabase.get(context)).also { instance = it }
            }

        /** Rebind the repo to the current (possibly just-switched) database instance. */
        fun reset(context: Context) {
            synchronized(this) { instance = TrackRepository(StarkDatabase.get(context)) }
        }
    }
}
