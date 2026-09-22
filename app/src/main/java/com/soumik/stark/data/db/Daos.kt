package com.soumik.stark.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.soumik.stark.data.entity.DailyTotal
import com.soumik.stark.data.entity.Leg
import com.soumik.stark.data.entity.LifetimeTotal
import com.soumik.stark.data.entity.Point
import com.soumik.stark.data.entity.Setting
import kotlinx.coroutines.flow.Flow

@Dao
interface LegDao {
    @Insert
    suspend fun insert(leg: Leg): Long

    @Update
    suspend fun update(leg: Leg)

    @Query("SELECT * FROM leg WHERE id = :id")
    suspend fun byId(id: Long): Leg?

    @Query("SELECT * FROM leg WHERE closed = 1 ORDER BY startT DESC")
    fun observeClosedLegs(): Flow<List<Leg>>

    @Query("SELECT * FROM leg WHERE dateKey = :dateKey AND closed = 1 ORDER BY startT DESC")
    fun observeLegsForDay(dateKey: Int): Flow<List<Leg>>

    @Query("SELECT * FROM leg WHERE dateKey = :dateKey ORDER BY startT ASC")
    suspend fun legsForDay(dateKey: Int): List<Leg>

    @Query("DELETE FROM leg WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface PointDao {
    @Insert
    suspend fun insert(point: Point): Long

    @Insert
    suspend fun insertAll(points: List<Point>)

    @Query("SELECT * FROM point WHERE legId = :legId ORDER BY tUtc ASC")
    suspend fun pointsForLeg(legId: Long): List<Point>

    @Query("SELECT COUNT(*) FROM point")
    suspend fun count(): Int

    @Query("DELETE FROM point WHERE legId = :legId")
    suspend fun deleteForLeg(legId: Long)
}

@Dao
interface TotalsDao {
    @Query("SELECT * FROM daily_total WHERE dateKey = :dateKey")
    suspend fun daily(dateKey: Int): DailyTotal?

    @Query("SELECT * FROM daily_total WHERE dateKey = :dateKey")
    fun observeDaily(dateKey: Int): Flow<DailyTotal?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDaily(total: DailyTotal)

    @Query("SELECT * FROM lifetime_total WHERE vehicleId = :vehicleId")
    suspend fun lifetime(vehicleId: Long = 1): LifetimeTotal?

    @Query("SELECT * FROM lifetime_total WHERE vehicleId = :vehicleId")
    fun observeLifetime(vehicleId: Long = 1): Flow<LifetimeTotal?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLifetime(total: LifetimeTotal)
}

@Dao
interface SettingDao {
    @Query("SELECT value FROM setting WHERE key = :key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(setting: Setting)
}
