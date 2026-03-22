package com.mh.icmpclient.model

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
