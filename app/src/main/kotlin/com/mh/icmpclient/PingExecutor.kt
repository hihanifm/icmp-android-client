package com.mh.icmpclient

import android.net.Network
import kotlinx.coroutines.flow.Flow

enum class PingBackend {
    ICMP4A,
    SHELL,
    ;

    companion object {
        const val PREFS_KEY = "ping_backend"

        fun fromPrefsValue(value: String?): PingBackend =
            entries.firstOrNull { it.name == value } ?: ICMP4A
    }
}

/** One logical ping outcome plus optional resolved IPv4 from a PING header line. */
data class PingChunk(
    val item: PingResultItem,
    val resolvedIp: String? = null,
)

interface PingExecutor {
    fun execute(
        host: String,
        count: Int,
        intervalMillis: Long,
        network: Network?,
    ): Flow<PingChunk>

    /** Stop any in-flight work (e.g. destroy shell process). Safe to call when idle. */
    fun cancelExecution()
}
