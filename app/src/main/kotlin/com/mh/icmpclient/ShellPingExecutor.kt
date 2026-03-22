package com.mh.icmpclient

import android.net.Network
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.atomic.AtomicReference

/**
 * Runs `/system/bin/ping` in short processes.
 * [network] is ignored: the child process cannot be bound to a specific [android.net.Network] like icmp4a can.
 */
class ShellPingExecutor : PingExecutor {

    private val currentProcess = AtomicReference<Process?>(null)

    override fun execute(
        host: String,
        count: Int,
        intervalMillis: Long,
        network: Network?,
    ): Flow<PingChunk> =
        flow {
            repeat(count) { iteration ->
                coroutineContext.ensureActive()
                var resolvedIp: String? = null
                var gotReply = false
                val capturedLines = mutableListOf<String>()

                val proc =
                    try {
                        ProcessBuilder(
                            "/system/bin/ping",
                            "-c",
                            "1",
                            "-W",
                            "5",
                            host,
                        )
                            .redirectErrorStream(true)
                            .start()
                    } catch (e: Exception) {
                        emit(
                            PingChunk(
                                item = PingResultItem(
                                    sequenceNumber = iteration,
                                    rttMs = null,
                                    isSuccess = false,
                                    errorMessage = e.message ?: "failed to start ping",
                                    timestamp = System.currentTimeMillis(),
                                ),
                                resolvedIp = null,
                            ),
                        )
                        if (iteration < count - 1) delay(intervalMillis)
                        return@repeat
                    }

                currentProcess.set(proc)
                try {
                    proc.inputStream.bufferedReader().use { reader ->
                        reader.lineSequence().forEach { line ->
                            capturedLines.add(line)
                            parsePingHeader(line)?.let { resolvedIp = it }
                            parseReplyLine(line)?.let { (seq, rtt) ->
                                gotReply = true
                                emit(
                                    PingChunk(
                                        item = PingResultItem(
                                            sequenceNumber = seq,
                                            rttMs = rtt,
                                            isSuccess = true,
                                            errorMessage = null,
                                            timestamp = System.currentTimeMillis(),
                                        ),
                                        resolvedIp = resolvedIp,
                                    ),
                                )
                            }
                        }
                    }
                    val exit = proc.waitFor()
                    if (!gotReply) {
                        val tail = capturedLines.takeLast(3).joinToString("\n").take(200)
                        val msg = when {
                            tail.isNotEmpty() -> tail
                            exit != 0 -> "ping exited with code $exit"
                            else -> "no reply"
                        }
                        emit(
                            PingChunk(
                                item = PingResultItem(
                                    sequenceNumber = iteration,
                                    rttMs = null,
                                    isSuccess = false,
                                    errorMessage = msg,
                                    timestamp = System.currentTimeMillis(),
                                ),
                                resolvedIp = resolvedIp,
                            ),
                        )
                    }
                } finally {
                    proc.destroyForcibly()
                    currentProcess.compareAndSet(proc, null)
                }

                if (iteration < count - 1) delay(intervalMillis)
            }
        }.flowOn(Dispatchers.IO)

    override fun cancelExecution() {
        currentProcess.getAndSet(null)?.destroyForcibly()
    }

    companion object {
        private val pingHeaderRegex = Regex("""PING\s+\S+\s+\(([^)]+)\)""")
        private val replyRegex = Regex("""icmp_seq=(\d+).*?time[=<]([0-9.]+)\s*ms""")

        fun parsePingHeader(line: String): String? =
            pingHeaderRegex.find(line.trim())?.groupValues?.getOrNull(1)

        fun parseReplyLine(line: String): Pair<Int, Double>? {
            val m = replyRegex.find(line) ?: return null
            val seq = m.groupValues[1].toIntOrNull() ?: return null
            val rtt = m.groupValues[2].toDoubleOrNull() ?: return null
            return seq to rtt
        }
    }
}
