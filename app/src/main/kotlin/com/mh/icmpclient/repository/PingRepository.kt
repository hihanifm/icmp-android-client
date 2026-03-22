package com.mh.icmpclient.repository

import com.mh.icmpclient.db.PingDao
import com.mh.icmpclient.db.PingResultEntity
import com.mh.icmpclient.db.PingSessionEntity
import com.mh.icmpclient.model.PingResultItem
import com.mh.icmpclient.model.PingState
import com.mh.icmpclient.model.PingStats
import com.mh.icmpclient.ping.Icmp4aPingExecutor
import com.mh.icmpclient.ping.PingBackend
import com.mh.icmpclient.ping.PingExecutor
import com.mh.icmpclient.ping.ShellPingExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PingRepository(private val dao: PingDao) {

    private val icmp4aExecutor = Icmp4aPingExecutor()
    private val shellExecutor = ShellPingExecutor()

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
        backend: PingBackend = PingBackend.ICMP4A,
    ) {
        stopPing()
        rttValues.clear()
        _state.value = PingState(isRunning = true, host = host)

        val executor: PingExecutor = when (backend) {
            PingBackend.ICMP4A -> icmp4aExecutor
            PingBackend.SHELL -> shellExecutor
        }

        pingJob = scope.launch {
            val sessionId = dao.insertSession(
                PingSessionEntity(host = host, startTime = System.currentTimeMillis())
            )
            currentSessionId = sessionId

            try {
                executor.execute(
                    host = host,
                    count = count,
                    intervalMillis = intervalMillis,
                    network = network,
                ).collect { chunk ->
                    val item = chunk.item
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
                    val newIp = chunk.resolvedIp ?: currentState.resolvedIp
                    _state.value = currentState.copy(
                        resolvedIp = newIp,
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
        shellExecutor.cancelExecution()
        icmp4aExecutor.cancelExecution()
        pingJob?.cancel()
        pingJob = null
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
