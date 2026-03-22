package com.mh.icmpclient

import android.net.Network
import com.marsounjan.icmp4a.Icmp
import com.marsounjan.icmp4a.Icmp4a
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class Icmp4aPingExecutor : PingExecutor {

    private val icmp = Icmp4a()

    override fun execute(
        host: String,
        count: Int,
        intervalMillis: Long,
        network: Network?,
    ): Flow<PingChunk> =
        icmp.pingInterval(host = host, count = count, intervalMillis = intervalMillis, network = network)
            .map { status ->
                PingChunk(
                    item = mapStatusToResult(status),
                    resolvedIp = status.ip.hostAddress,
                )
            }

    override fun cancelExecution() {
        // Coroutine cancellation ends collection; no native handle to close.
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
}
