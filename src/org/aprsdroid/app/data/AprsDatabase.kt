package org.aprsdroid.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.concurrent.atomic.AtomicReference

/**
 * Room database for APRSdroid — the Kotlin/Compose successor to the
 * Scala `StorageDatabase` (a `SQLiteOpenHelper`).
 *
 * Schema is kept byte-compatible with the legacy database so that
 * existing installs upgrade in place. The original schema version was
 * `4` (see `StorageDatabase.DB_VERSION`); we start Room at version `5`
 * to allow Room to take ownership without colliding with the legacy
 * upgrade path. The [MIGRATION_4_5] migration is a no-op — the tables
 * already exist with the correct shape.
 *
 * Singleton access via [get] mirrors the Scala `StorageDatabase.open()`.
 */
@Database(
    entities = [
        PostEntity::class,
        StationEntity::class,
        PositionEntity::class,
        MessageEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class AprsDatabase : RoomDatabase() {

    abstract fun postDao(): PostDao
    abstract fun stationDao(): StationDao
    abstract fun positionDao(): PositionDao
    abstract fun messageDao(): MessageDao

    companion object {
        private const val DB_NAME = "storage.db"
        private const val TAG = "APRSdroid.Storage"

        private val singleton = AtomicReference<AprsDatabase?>()

        /**
         * No-op migration from the legacy SQLiteOpenHelper schema (v4)
         * to Room (v5). The tables and indices already match what Room
         * expects, so nothing needs to change on disk.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Schema is identical — nothing to do. Room now owns it.
            }
        }

        /** Get the process-wide database instance, creating it if needed. */
        @JvmStatic
        fun get(context: Context): AprsDatabase {
            return singleton.get() ?: synchronized(this) {
                singleton.get() ?: build(context).also { singleton.set(it) }
            }
        }

        private fun build(context: Context): AprsDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AprsDatabase::class.java,
                DB_NAME,
            )
                .addMigrations(MIGRATION_4_5)
                .fallbackToDestructiveMigrationOnDowngrade(false)
                .build()
        }

        /** For tests — inject a fresh in-memory instance. */
        @JvmStatic
        fun setForTest(db: AprsDatabase?) {
            singleton.set(db)
        }
    }
}
