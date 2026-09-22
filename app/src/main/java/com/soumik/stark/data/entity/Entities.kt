package com.soumik.stark.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Confidence of a fix / segment. Distance counts all three; LOW/EST render faded. */
enum class Confidence { HIGH, LOW, EST }

enum class TravelMode { WALK, RUN, BICYCLE, VEHICLE }

@Entity(tableName = "leg", indices = [Index("startT"), Index("dateKey")])
data class Leg(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long = 1,
    val mode: TravelMode = TravelMode.VEHICLE,
    val startT: Long,
    val offsetMin: Int,
    val dateKey: Int,
    val endT: Long? = null,
    val distanceM: Double = 0.0,
    val durationS: Long = 0,
    val maxSpeedMps: Double = 0.0,
    val pointCount: Int = 0,
    val hasEstimatedGap: Boolean = false,
    val label: String? = null,
    val closed: Boolean = false,
)

@Entity(
    tableName = "point",
    indices = [Index("legId"), Index("tUtc")]
)
data class Point(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val legId: Long,
    val tUtc: Long,
    val offsetMin: Int,
    val latE7: Int,
    val lngE7: Int,
    val accuracyM: Float,
    val speedMps: Float,
    val confidence: Confidence,
)

/** Incremental per-day totals, keyed by local yyyymmdd. */
@Entity(tableName = "daily_total")
data class DailyTotal(
    @PrimaryKey val dateKey: Int,
    val distanceBikeM: Double = 0.0,
    val distanceAllM: Double = 0.0,
    val tripCount: Int = 0,
)

/** The headline odometer. Single row per vehicle. */
@Entity(tableName = "lifetime_total")
data class LifetimeTotal(
    @PrimaryKey val vehicleId: Long = 1,
    val distanceBikeM: Double = 0.0,
    val distanceAllM: Double = 0.0,
)

@Entity(tableName = "setting")
data class Setting(
    @PrimaryKey val key: String,
    val value: String,
)
