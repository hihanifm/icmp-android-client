package com.mh.icmpclient.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PingSessionEntity::class, PingResultEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class PingDatabase : RoomDatabase() {

    abstract fun pingDao(): PingDao

    companion object {
        @Volatile
        private var INSTANCE: PingDatabase? = null

        fun getInstance(context: Context): PingDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PingDatabase::class.java,
                    "ping_database"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
