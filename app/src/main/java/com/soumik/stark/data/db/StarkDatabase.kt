package com.soumik.stark.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.soumik.stark.data.entity.Confidence
import com.soumik.stark.data.entity.DailyTotal
import com.soumik.stark.data.entity.Leg
import com.soumik.stark.data.entity.LifetimeTotal
import com.soumik.stark.data.entity.Point
import com.soumik.stark.data.entity.Setting
import com.soumik.stark.data.entity.TravelMode

class Converters {
    @TypeConverter fun confToInt(c: Confidence): Int = c.ordinal
    @TypeConverter fun intToConf(i: Int): Confidence = Confidence.entries[i]
    @TypeConverter fun modeToInt(m: TravelMode): Int = m.ordinal
    @TypeConverter fun intToMode(i: Int): TravelMode = TravelMode.entries[i]
}

@Database(
    entities = [Leg::class, Point::class, DailyTotal::class, LifetimeTotal::class, Setting::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class StarkDatabase : RoomDatabase() {
    abstract fun legDao(): LegDao
    abstract fun pointDao(): PointDao
    abstract fun totalsDao(): TotalsDao
    abstract fun settingDao(): SettingDao

    companion object {
        @Volatile private var instance: StarkDatabase? = null

        // NOTE: v1 field-test build uses an unencrypted Room DB for reliability. The design's
        // SQLCipher/Argon2id layer wraps this same schema and is the next security milestone.
        fun get(context: Context): StarkDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    StarkDatabase::class.java,
                    "stark.db"
                ).enableMultiInstanceInvalidation().build().also { instance = it }
            }
    }
}
