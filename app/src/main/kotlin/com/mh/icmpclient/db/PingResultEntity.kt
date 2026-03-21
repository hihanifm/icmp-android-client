package com.mh.icmpclient.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ping_results",
    foreignKeys = [
        ForeignKey(
            entity = PingSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("sessionId")],
)
data class PingResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val sequenceNumber: Int,
    val rttMs: Double? = null,
    val isSuccess: Boolean,
    val errorMessage: String? = null,
    val timestamp: Long,
)
