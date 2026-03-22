package com.mh.icmpclient.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PingSessionEntity::class, PingResultEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class PingDatabase : RoomDatabase() {

    abstract fun pingDao(): PingDao

    companion object {
        @Volatile
        private var INSTANCE: PingDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ping_sessions ADD COLUMN networkLabel TEXT")
                db.execSQL("ALTER TABLE ping_sessions ADD COLUMN pingBackend TEXT")
                db.execSQL("ALTER TABLE ping_sessions ADD COLUMN intervalMillis INTEGER")
                db.execSQL("ALTER TABLE ping_sessions ADD COLUMN timeoutMillis INTEGER")
            }
        }

        fun getInstance(context: Context): PingDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PingDatabase::class.java,
                    "ping_database"
                ).addMigrations(MIGRATION_1_2)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
