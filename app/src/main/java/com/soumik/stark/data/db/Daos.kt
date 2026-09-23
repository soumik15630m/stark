package com.soumik.stark.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.soumik.stark.data.entity.DailyTotal
import com.soumik.stark.data.entity.FuelFill
import com.soumik.stark.data.entity.HeatTile
import com.soumik.stark.data.entity.Leg
import com.soumik.stark.data.entity.LifetimeTotal
import com.soumik.stark.data.entity.Outing
import com.soumik.stark.data.entity.Place
import com.soumik.stark.data.entity.Point
import com.soumik.stark.data.entity.PrivacyZone
import com.soumik.stark.data.entity.Record
import com.soumik.stark.data.entity.Setting
import com.soumik.stark.data.entity.Visit
import kotlinx.coroutines.flow.Flow

@Dao
interface LegDao {
    @Insert suspend fun insert(leg: Leg): Long
    @Update suspend fun update(leg: Leg)
    @Query("SELECT * FROM leg WHERE id = :id") suspend fun byId(id: Long): Leg?
    @Query("SELECT * FROM leg WHERE id = :id") fun observeById(id: Long): Flow<Leg?>

    @Query("SELECT * FROM leg WHERE closed = 1 ORDER BY startT DESC")
    fun observeClosedLegs(): Flow<List<Leg>>

    @Query("SELECT * FROM leg WHERE dateKey = :dateKey AND closed = 1 ORDER BY startT DESC")
    fun observeLegsForDay(dateKey: Int): Flow<List<Leg>>

    @Query("SELECT * FROM leg WHERE closed = 1 ORDER BY startT DESC")
    suspend fun allClosed(): List<Leg>

    @Query("SELECT * FROM leg WHERE outingId = :outingId AND closed = 1 ORDER BY startT ASC")
    suspend fun legsForOuting(outingId: Long): List<Leg>

    @Query("SELECT * FROM leg WHERE dateKey = :dateKey ORDER BY startT ASC")
    suspend fun legsForDay(dateKey: Int): List<Leg>

    @Query("SELECT COUNT(*) FROM leg WHERE startPlaceId = :s AND endPlaceId = :e AND closed = 1")
    suspend fun countRoute(s: Long, e: Long): Int

    @Query("DELETE FROM leg WHERE id = :id") suspend fun delete(id: Long)
}

@Dao
interface PointDao {
    @Insert suspend fun insert(point: Point): Long
    @Insert suspend fun insertAll(points: List<Point>)
    @Query("SELECT * FROM point WHERE legId = :legId ORDER BY tUtc ASC")
    suspend fun pointsForLeg(legId: Long): List<Point>
    @Query("SELECT COUNT(*) FROM point") suspend fun count(): Int
    @Query("DELETE FROM point WHERE legId = :legId") suspend fun deleteForLeg(legId: Long)
    @Query("UPDATE point SET legId = :newLeg WHERE legId = :oldLeg")
    suspend fun reassign(oldLeg: Long, newLeg: Long)

    @Query("UPDATE point SET legId = :newLeg WHERE id = :pointId")
    suspend fun reassignPoint(pointId: Long, newLeg: Long)
}

@Dao
interface TotalsDao {
    @Query("SELECT * FROM daily_total WHERE dateKey = :dateKey") suspend fun daily(dateKey: Int): DailyTotal?
    @Query("SELECT * FROM daily_total WHERE dateKey = :dateKey") fun observeDaily(dateKey: Int): Flow<DailyTotal?>
    @Query("SELECT * FROM daily_total ORDER BY dateKey DESC") fun observeAllDaily(): Flow<List<DailyTotal>>
    @Query("SELECT * FROM daily_total ORDER BY dateKey ASC") suspend fun allDaily(): List<DailyTotal>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertDaily(total: DailyTotal)

    @Query("SELECT * FROM lifetime_total WHERE vehicleId = :vehicleId") suspend fun lifetime(vehicleId: Long = 1): LifetimeTotal?
    @Query("SELECT * FROM lifetime_total WHERE vehicleId = :vehicleId") fun observeLifetime(vehicleId: Long = 1): Flow<LifetimeTotal?>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertLifetime(total: LifetimeTotal)
}

@Dao
interface SettingDao {
    @Query("SELECT value FROM setting WHERE key = :key") suspend fun get(key: String): String?
    @Query("SELECT value FROM setting WHERE key = :key") fun observe(key: String): Flow<String?>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(setting: Setting)
}

@Dao
interface PlaceDao {
    @Insert suspend fun insert(place: Place): Long
    @Update suspend fun update(place: Place)
    @Query("SELECT * FROM place WHERE id = :id") suspend fun byId(id: Long): Place?
    @Query("SELECT * FROM place WHERE geocell = :cell LIMIT 1") suspend fun byCell(cell: Long): Place?
    @Query("SELECT * FROM place WHERE isBaseHome = 1 LIMIT 1") suspend fun home(): Place?
    @Query("SELECT * FROM place ORDER BY visitCount DESC") fun observeAll(): Flow<List<Place>>
    @Query("SELECT * FROM place ORDER BY visitCount DESC") suspend fun all(): List<Place>
}

@Dao
interface VisitDao {
    @Insert suspend fun insert(visit: Visit): Long
    @Update suspend fun update(visit: Visit)
    @Query("SELECT * FROM visit ORDER BY arriveT DESC") suspend fun all(): List<Visit>
}

@Dao
interface OutingDao {
    @Insert suspend fun insert(outing: Outing): Long
    @Update suspend fun update(outing: Outing)
    @Query("SELECT * FROM outing WHERE id = :id") suspend fun byId(id: Long): Outing?
    @Query("SELECT * FROM outing WHERE closed = 0 ORDER BY startT DESC LIMIT 1") suspend fun openOuting(): Outing?
    @Query("SELECT * FROM outing ORDER BY startT DESC") fun observeAll(): Flow<List<Outing>>
}

@Dao
interface FuelDao {
    @Insert suspend fun insert(fill: FuelFill): Long
    @Query("DELETE FROM fuel_fill WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT * FROM fuel_fill ORDER BY t DESC") fun observeAll(): Flow<List<FuelFill>>
    @Query("SELECT * FROM fuel_fill ORDER BY t ASC") suspend fun all(): List<FuelFill>
}

@Dao
interface RecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(record: Record)
    @Query("SELECT * FROM record WHERE type = :type") suspend fun get(type: String): Record?
    @Query("SELECT * FROM record") fun observeAll(): Flow<List<Record>>
}

@Dao
interface HeatDao {
    @Query("SELECT weight FROM heat_tile WHERE z = :z AND x = :x AND y = :y") suspend fun weight(z: Int, x: Int, y: Int): Int?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(tile: HeatTile)
    @Query("SELECT * FROM heat_tile WHERE z = :z") suspend fun tilesAt(z: Int): List<HeatTile>
}

@Dao
interface PrivacyDao {
    @Insert suspend fun insert(zone: PrivacyZone): Long
    @Query("DELETE FROM privacy_zone WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT * FROM privacy_zone") fun observeAll(): Flow<List<PrivacyZone>>
    @Query("SELECT * FROM privacy_zone") suspend fun all(): List<PrivacyZone>
}
