package com.soumik.stark.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.soumik.stark.data.entity.Confidence
import com.soumik.stark.data.entity.DailyTotal
import com.soumik.stark.data.entity.FuelFill
import com.soumik.stark.data.entity.HeatTile
import com.soumik.stark.data.entity.Leg
import com.soumik.stark.data.entity.LifetimeTotal
import com.soumik.stark.data.entity.Outing
import com.soumik.stark.data.entity.Place
import com.soumik.stark.data.entity.PlaceCategory
import com.soumik.stark.data.entity.Point
import com.soumik.stark.data.entity.PrivacyZone
import com.soumik.stark.data.entity.Record
import com.soumik.stark.data.entity.Setting
import com.soumik.stark.data.entity.TravelMode
import com.soumik.stark.data.entity.Visit

class Converters {
    @TypeConverter fun confToInt(c: Confidence): Int = c.ordinal
    @TypeConverter fun intToConf(i: Int): Confidence = Confidence.entries[i]
    @TypeConverter fun modeToInt(m: TravelMode): Int = m.ordinal
    @TypeConverter fun intToMode(i: Int): TravelMode = TravelMode.entries[i]
    @TypeConverter fun catToInt(c: PlaceCategory): Int = c.ordinal
    @TypeConverter fun intToCat(i: Int): PlaceCategory = PlaceCategory.entries[i]
}

@Database(
    entities = [
        Leg::class, Point::class, DailyTotal::class, LifetimeTotal::class, Setting::class,
        Place::class, Visit::class, Outing::class, FuelFill::class, Record::class,
        HeatTile::class, PrivacyZone::class,
    ],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class StarkDatabase : RoomDatabase() {
    abstract fun legDao(): LegDao
    abstract fun pointDao(): PointDao
    abstract fun totalsDao(): TotalsDao
    abstract fun settingDao(): SettingDao
    abstract fun placeDao(): PlaceDao
    abstract fun visitDao(): VisitDao
    abstract fun outingDao(): OutingDao
    abstract fun fuelDao(): FuelDao
    abstract fun recordDao(): RecordDao
    abstract fun heatDao(): HeatDao
    abstract fun privacyDao(): PrivacyDao

    companion object {
        @Volatile private var instance: StarkDatabase? = null

        // v1 uses SQLCipher when a key is set (see StarkDatabase.openEncrypted); this plaintext
        // builder is the fallback / pre-lock path. Destructive migration is acceptable pre-release.
        fun get(context: Context): StarkDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    StarkDatabase::class.java,
                    "stark.db"
                ).enableMultiInstanceInvalidation()
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
