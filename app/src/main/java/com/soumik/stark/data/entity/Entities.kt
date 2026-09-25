package com.soumik.stark.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Confidence of a fix / segment. Distance counts all three; LOW/EST render faded. */
enum class Confidence { HIGH, LOW, EST }

enum class TravelMode { WALK, RUN, BICYCLE, VEHICLE }

enum class PlaceCategory { HOME, WORK, FOOD, FRIENDS, FUEL, OTHER }

@Entity(tableName = "leg", indices = [Index("startT"), Index("dateKey"), Index("outingId")])
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
    val movingDurationS: Long = 0, // time actually moving; excludes idle so avg speed is honest
    val maxSpeedMps: Double = 0.0,
    val pointCount: Int = 0,
    val hasEstimatedGap: Boolean = false,
    val label: String? = null,
    val startPlaceId: Long? = null,
    val endPlaceId: Long? = null,
    val outingId: Long? = null,
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

@Entity(tableName = "place", indices = [Index("geocell")])
data class Place(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latE7: Int,
    val lngE7: Int,
    val radiusM: Int = 60,
    val geocell: Long,
    val name: String? = null,
    val category: PlaceCategory = PlaceCategory.OTHER,
    val isBaseHome: Boolean = false,
    val visitCount: Int = 0,
    val firstSeen: Long = 0,
    val lastSeen: Long = 0,
)

@Entity(tableName = "visit", indices = [Index("placeId"), Index("arriveT")])
data class Visit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val placeId: Long,
    val arriveT: Long,
    val departT: Long?,
    val durationS: Long = 0,
    val offsetMin: Int = 0,
)

@Entity(tableName = "outing", indices = [Index("startT")])
data class Outing(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val basePlaceId: Long?,
    val startT: Long,
    val endT: Long?,
    val offsetMin: Int,
    val distanceM: Double = 0.0,
    val legCount: Int = 0,
    val placeCount: Int = 0,
    val summarySent: Boolean = false,
    val closed: Boolean = false,
)

@Entity(tableName = "fuel_fill", indices = [Index("t")])
data class FuelFill(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long = 1,
    val t: Long,
    val litres: Double,
    val costInr: Double,
    val odoMAtFill: Double,
    val note: String? = null,
    // Ledger flags (design: smart fuel). Together with tank/reserve they anchor the fuel level so
    // km/l, current level and range can be derived from random/partial fills.
    val filledToFull: Boolean = false,   // topped to the brim → level = tank capacity after this fill
    val ranDryBefore: Boolean = false,   // tank was empty before this fill → level = 0 pre-fill
    val onReserveBefore: Boolean = false, // was riding on reserve when refuelling
)

@Entity(tableName = "record")
data class Record(
    @PrimaryKey val type: String,
    val value: Double,
    val achievedT: Long,
    val refLegId: Long? = null,
)

@Entity(tableName = "heat_tile", primaryKeys = ["z", "x", "y"])
data class HeatTile(
    val z: Int,
    val x: Int,
    val y: Int,
    val weight: Int = 0,
)

/** Cold-packed points for an old leg (delta+varint, then deflated). Replaces the Point rows. */
@Entity(tableName = "leg_blob")
data class LegBlob(
    @PrimaryKey val legId: Long,
    val blob: ByteArray,
    val pointCount: Int,
)

@Entity(tableName = "privacy_zone")
data class PrivacyZone(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latE7: Int,
    val lngE7: Int,
    val radiusM: Int,
    val label: String? = null,
)
