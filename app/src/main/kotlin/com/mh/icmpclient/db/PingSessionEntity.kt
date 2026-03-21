package com.mh.icmpclient.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ping_sessions")
data class PingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val host: String,
    val startTime: Long,
    val endTime: Long? = null,
    val pingCount: Int = 0,
    val successCount: Int = 0,
    val minRtt: Double? = null,
    val avgRtt: Double? = null,
    val maxRtt: Double? = null,
)
