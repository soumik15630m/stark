package com.soumik.stark.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
        HeatTile::class, PrivacyZone::class, com.soumik.stark.data.entity.LegBlob::class,
    ],
    version = 4,
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
    abstract fun legBlobDao(): LegBlobDao

    companion object {
        @Volatile private var instance: StarkDatabase? = null
        @Volatile private var loaded = false

        // Real migrations so app updates NEVER wipe ride history. Add one per schema bump.
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE leg ADD COLUMN movingDurationS INTEGER NOT NULL DEFAULT 0")
            }
        }
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `leg_blob` (`legId` INTEGER NOT NULL, `blob` BLOB NOT NULL, `pointCount` INTEGER NOT NULL, PRIMARY KEY(`legId`))")
            }
        }
        private val MIGRATIONS = arrayOf(MIGRATION_2_3, MIGRATION_3_4)

        /**
         * Full-database encryption via SQLCipher (design §6.1). The passphrase comes from the
         * Keystore-wrapped master key. The decoy volume is a separate file with its own key
         * (design §6.2); which one opens is chosen by [decoy].
         */
        fun get(context: Context, decoy: Boolean = false): StarkDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext, decoy).also { instance = it }
            }

        private fun build(context: Context, decoy: Boolean): StarkDatabase {
            if (!loaded) { System.loadLibrary("sqlcipher"); loaded = true }
            val passphrase = com.soumik.stark.core.crypto.DbKeys.passphrase(context, decoy)
            val factory = net.zetetic.database.sqlcipher.SupportOpenHelperFactory(passphrase)
            val name = if (decoy) "stark_decoy.db" else "stark_enc.db"
            return Room.databaseBuilder(context, StarkDatabase::class.java, name)
                .openHelperFactory(factory)
                .addMigrations(*MIGRATIONS)
                // Only ever wipe on a DOWNGRADE (installing an older build); upgrades migrate.
                .fallbackToDestructiveMigrationOnDowngrade()
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        // Reclaim space in small steps and keep the WAL from ballooning on long rides.
                        try {
                            db.query("PRAGMA auto_vacuum=INCREMENTAL").close()
                            db.query("PRAGMA wal_autocheckpoint=1000").close()
                            db.query("PRAGMA incremental_vacuum").close()
                        } catch (_: Exception) {}
                    }
                })
                .build()
        }

        /** Swap to the decoy (or real) volume; the caller must also reset dependent singletons. */
        fun switchVolume(context: Context, decoy: Boolean) {
            synchronized(this) {
                instance?.close()
                instance = build(context.applicationContext, decoy)
            }
        }
    }
}
