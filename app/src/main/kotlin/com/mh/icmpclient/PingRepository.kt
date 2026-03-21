package com.mh.icmpclient

import com.mh.icmpclient.db.PingDao
import com.mh.icmpclient.db.PingResultEntity
import com.mh.icmpclient.db.PingSessionEntity
import com.marsounjan.icmp4a.Icmp
import com.marsounjan.icmp4a.Icmp4a
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PingResultItem(
    val sequenceNumber: Int,
    val rttMs: Double?,
    val isSuccess: Boolean,
    val errorMessage: String?,
    val timestamp: Long,
)

data class PingStats(
    val pingCount: Int = 0,
    val successCount: Int = 0,
    val minRtt: Double? = null,
    val avgRtt: Double? = null,
    val maxRtt: Double? = null,
)

data class PingState(
    val isRunning: Boolean = false,
    val host: String = "",
    val resolvedIp: String? = null,
    val results: List<PingResultItem> = emptyList(),
    val stats: PingStats = PingStats(),
    val error: String? = null,
)

class PingRepository(private val dao: PingDao) {

    private val icmp = Icmp4a()

    private val _state = MutableStateFlow(PingState())
    val state: StateFlow<PingState> = _state.asStateFlow()

    private val _pingResults = MutableSharedFlow<PingResultItem>(extraBufferCapacity = 64)
    val pingResults: SharedFlow<PingResultItem> = _pingResults.asSharedFlow()

    private var pingJob: Job? = null
    private var currentSessionId: Long? = null
    private val rttValues = mutableListOf<Double>()

    fun startPing(
        host: String,
        count: Int,
        intervalMillis: Long,
        scope: CoroutineScope,
        network: android.net.Network? = null,
    ) {
        stopPing()
        rttValues.clear()
        _state.value = PingState(isRunning = true, host = host)

        pingJob = scope.launch {
            val sessionId = dao.insertSession(
                PingSessionEntity(host = host, startTime = System.currentTimeMillis())
            )
            currentSessionId = sessionId

            try {
                icmp.pingInterval(host = host, count = count, intervalMillis = intervalMillis, network = network)
                    .collect { pingStatus ->
                        val item = mapStatusToResult(pingStatus)
                        dao.insertResult(
                            PingResultEntity(
                                sessionId = sessionId,
                                sequenceNumber = item.sequenceNumber,
                                rttMs = item.rttMs,
                                isSuccess = item.isSuccess,
                                errorMessage = item.errorMessage,
                                timestamp = item.timestamp,
                            )
                        )
                        if (item.isSuccess && item.rttMs != null) {
                            rttValues.add(item.rttMs)
                        }
                        val currentState = _state.value
                        val newResults = currentState.results + item
                        val stats = computeStats(newResults)
                        _state.value = currentState.copy(
                            resolvedIp = pingStatus.ip.hostAddress,
                            results = newResults,
                            stats = stats,
                            error = null,
                        )
                        _pingResults.emit(item)
                    }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message, isRunning = false)
            } finally {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    finalizeSession(sessionId)
                }
                _state.value = _state.value.copy(isRunning = false)
            }
        }
    }

    fun stopPing() {
        pingJob?.cancel()
        pingJob = null
        currentSessionId?.let { id ->
            // Finalization happens in the coroutine's finally block
        }
    }

    private suspend fun finalizeSession(sessionId: Long) {
        val stats = _state.value.stats
        dao.finalizeSession(
            sessionId = sessionId,
            endTime = System.currentTimeMillis(),
            pingCount = stats.pingCount,
            successCount = stats.successCount,
            minRtt = stats.minRtt,
            avgRtt = stats.avgRtt,
            maxRtt = stats.maxRtt,
        )
        currentSessionId = null
    }

    private fun mapStatusToResult(status: Icmp.PingStatus): PingResultItem {
        val now = System.currentTimeMillis()
        return when (val result = status.result) {
            is Icmp.PingResult.Success -> PingResultItem(
                sequenceNumber = result.sequenceNumber,
                rttMs = result.ms.toDouble(),
                isSuccess = true,
                errorMessage = null,
                timestamp = now,
            )
            is Icmp.PingResult.Failed -> PingResultItem(
                sequenceNumber = status.packetsTransmitted,
                rttMs = null,
                isSuccess = false,
                errorMessage = result.message,
                timestamp = now,
            )
        }
    }

    private fun computeStats(results: List<PingResultItem>): PingStats {
        val successResults = results.filter { it.isSuccess }
        val rtts = successResults.mapNotNull { it.rttMs }
        return PingStats(
            pingCount = results.size,
            successCount = successResults.size,
            minRtt = rtts.minOrNull(),
            avgRtt = if (rtts.isNotEmpty()) rtts.average() else null,
            maxRtt = rtts.maxOrNull(),
        )
    }

    fun getAllSessions() = dao.getAllSessions()

    fun getResultsForSession(sessionId: Long) = dao.getResultsForSession(sessionId)

    suspend fun deleteSession(sessionId: Long) = dao.deleteSession(sessionId)

    suspend fun getAllSessionsWithResults(): List<Pair<PingSessionEntity, List<PingResultEntity>>> {
        val sessions = dao.getAllSessionsSnapshot()
        val allResults = dao.getAllResults()
        val resultsBySession = allResults.groupBy { it.sessionId }
        return sessions.map { session -> session to (resultsBySession[session.id] ?: emptyList()) }
    }
}
