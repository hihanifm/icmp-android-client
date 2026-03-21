package com.mh.icmpclient.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PingDao {

    @Insert
    suspend fun insertSession(session: PingSessionEntity): Long

    @Query("UPDATE ping_sessions SET endTime = :endTime, pingCount = :pingCount, successCount = :successCount, minRtt = :minRtt, avgRtt = :avgRtt, maxRtt = :maxRtt WHERE id = :sessionId")
    suspend fun finalizeSession(
        sessionId: Long,
        endTime: Long,
        pingCount: Int,
        successCount: Int,
        minRtt: Double?,
        avgRtt: Double?,
        maxRtt: Double?,
    )

    @Insert
    suspend fun insertResult(result: PingResultEntity)

    @Query("SELECT * FROM ping_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<PingSessionEntity>>

    @Query("SELECT * FROM ping_results WHERE sessionId = :sessionId ORDER BY sequenceNumber ASC")
    fun getResultsForSession(sessionId: Long): Flow<List<PingResultEntity>>

    @Query("DELETE FROM ping_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)

    @Query("SELECT * FROM ping_sessions ORDER BY startTime DESC")
    suspend fun getAllSessionsSnapshot(): List<PingSessionEntity>

    @Query("SELECT * FROM ping_results ORDER BY sessionId ASC, sequenceNumber ASC")
    suspend fun getAllResults(): List<PingResultEntity>
}
