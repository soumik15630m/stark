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
class TrackRepository private constructor(private val db: StarkDatabase) : TrackSink {

    private val legDao = db.legDao()
    private val pointDao = db.pointDao()
    private val totalsDao = db.totalsDao()

    fun observeLifetime(): Flow<LifetimeTotal?> = totalsDao.observeLifetime()
    fun observeDaily(dateKey: Int): Flow<DailyTotal?> = totalsDao.observeDaily(dateKey)
    fun observeClosedLegs(): Flow<List<Leg>> = legDao.observeClosedLegs()
    fun observeLegsForDay(dateKey: Int): Flow<List<Leg>> = legDao.observeLegsForDay(dateKey)
    suspend fun pointsForLeg(legId: Long): List<com.soumik.stark.data.entity.Point> =
        pointDao.pointsForLeg(legId)
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

    /**
     * Persist a batch of accepted points and fold their added distance into the leg and the
     * day/lifetime odometers in one transaction. [addedDistanceM] is the distance summed over
     * only these new points (delta from the leg's previous last point onward).
     */
    override suspend fun flushBatch(
        legId: Long,
        points: List<Point>,
        addedDistanceM: Double,
        maxSpeedMps: Double,
        endT: Long,
        durationS: Long,
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
                    maxSpeedMps = maxOf(leg.maxSpeedMps, maxSpeedMps),
                    pointCount = leg.pointCount + points.size,
                    hasEstimatedGap = leg.hasEstimatedGap || hasEstimatedGap,
                )
            )
            val bike = leg.mode == TravelMode.VEHICLE
            val dateKey = leg.dateKey
            val daily = totalsDao.daily(dateKey) ?: DailyTotal(dateKey)
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

    /** Close a leg. A leg shorter than [minDistanceM] is discarded (parked GPS noise / false start). */
    override suspend fun closeLeg(legId: Long, endT: Long, durationS: Long, minDistanceM: Double) {
        db.withTransaction {
            val leg = legDao.byId(legId) ?: return@withTransaction
            if (leg.distanceM < minDistanceM) {
                // Roll back this leg's contribution: it never reached a real trip.
                val bike = leg.mode == TravelMode.VEHICLE
                val daily = totalsDao.daily(leg.dateKey)
                if (daily != null) {
                    totalsDao.upsertDaily(
                        daily.copy(
                            distanceAllM = (daily.distanceAllM - leg.distanceM).coerceAtLeast(0.0),
                            distanceBikeM = (daily.distanceBikeM - if (bike) leg.distanceM else 0.0)
                                .coerceAtLeast(0.0),
                            tripCount = (daily.tripCount - 1).coerceAtLeast(0),
                        )
                    )
                }
                val life = totalsDao.lifetime()
                if (life != null) {
                    totalsDao.upsertLifetime(
                        life.copy(
                            distanceAllM = (life.distanceAllM - leg.distanceM).coerceAtLeast(0.0),
                            distanceBikeM = (life.distanceBikeM - if (bike) leg.distanceM else 0.0)
                                .coerceAtLeast(0.0),
                        )
                    )
                }
                pointDao.deleteForLeg(legId)
                legDao.delete(legId)
            } else {
                legDao.update(leg.copy(endT = endT, durationS = durationS, closed = true))
            }
        }
    }

    companion object {
        @Volatile private var instance: TrackRepository? = null
        fun get(context: Context): TrackRepository =
            instance ?: synchronized(this) {
                instance ?: TrackRepository(StarkDatabase.get(context)).also { instance = it }
            }
    }
}
