package com.mh.icmpclient

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mh.icmpclient.db.PingResultEntity
import com.mh.icmpclient.db.PingSessionEntity
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val PAGE_SIZE = 10

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: PingViewModel = viewModel()) {
    val sessions by viewModel.getAllSessions().collectAsStateWithLifecycle(initialValue = emptyList())
    var selectedSession by remember { mutableStateOf<PingSessionEntity?>(null) }
    var visibleCount by remember { mutableIntStateOf(PAGE_SIZE) }

    Column(modifier = Modifier.fillMaxSize()) {
        if (selectedSession != null) {
            SessionDetailScreen(
                session = selectedSession!!,
                viewModel = viewModel,
                onBack = { selectedSession = null },
            )
        } else {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            TopAppBar(
                title = { Text("Ping History") },
                actions = {
                    if (sessions.isNotEmpty()) {
                        IconButton(onClick = {
                            scope.launch {
                                exportAllSessionsCsv(context, viewModel)
                            }
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Export all sessions")
                        }
                    }
                },
            )

            if (sessions.isEmpty()) {
                Text(
                    "No sessions yet",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                val displayedSessions = sessions.take(visibleCount)
                LazyColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
                    items(displayedSessions, key = { it.id }) { session ->
                        SessionCard(
                            session = session,
                            onClick = { selectedSession = session },
                            onDelete = { viewModel },
                            viewModel = viewModel,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    if (visibleCount < sessions.size) {
                        item {
                            LaunchedEffect(Unit) {
                                visibleCount += PAGE_SIZE
                            }
                            Text(
                                "Loading...",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionCard(
    session: PingSessionEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    viewModel: PingViewModel,
) {
    val scope = rememberCoroutineScope()
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(session.host, style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = {
                    scope.launch { viewModel.deleteSession(session.id) }
                }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
            Text(
                dateFormat.format(Date(session.startTime)),
                style = MaterialTheme.typography.bodySmall,
            )
            val duration = session.endTime?.let { end ->
                val secs = (end - session.startTime) / 1000
                if (secs >= 60) "${secs / 60}m ${secs % 60}s" else "${secs}s"
            }
            Text(
                buildString {
                    append("${session.pingCount} pings")
                    if (duration != null) append(" · $duration")
                    if (session.pingCount > 0) {
                        val loss = ((session.pingCount - session.successCount) * 100) / session.pingCount
                        append(" · ${loss}% loss")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
            )
            if (session.avgRtt != null) {
                Text(
                    "min/avg/max = %.1f/%.1f/%.1f ms".format(
                        session.minRtt, session.avgRtt, session.maxRtt,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionDetailScreen(
    session: PingSessionEntity,
    viewModel: PingViewModel,
    onBack: () -> Unit,
) {
    val results by viewModel.getResultsForSession(session.id)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(session.host) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                IconButton(onClick = { exportCsv(context, session, results) }) {
                    Icon(Icons.Default.Share, contentDescription = "Export CSV")
                }
            },
        )

        if (results.isNotEmpty()) {
            LatencyChart(
                dataPoints = results.map { r ->
                    ChartDataPoint(
                        sequenceNumber = r.sequenceNumber,
                        rttMs = r.rttMs,
                        isSuccess = r.isSuccess,
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
            items(results) { result ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "seq=${result.sequenceNumber}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                    if (result.isSuccess) {
                        Text(
                            "%.1f ms".format(result.rttMs),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    } else {
                        Text(
                            result.errorMessage ?: "failed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}

private fun exportCsv(
    context: Context,
    session: PingSessionEntity,
    results: List<PingResultEntity>,
) {
    val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
    val file = File(exportDir, "ping_${session.host}_${session.id}.csv")

    file.bufferedWriter().use { writer ->
        writer.appendLine("seq,timestamp,rtt_ms,success,error")
        results.forEach { r ->
            writer.appendLine(
                "${r.sequenceNumber},${r.timestamp},${r.rttMs ?: ""},${r.isSuccess},${r.errorMessage ?: ""}"
            )
        }
    }

    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Export ping results"))
}

private suspend fun exportAllSessionsCsv(context: Context, viewModel: PingViewModel) {
    val sessionsWithResults = viewModel.getAllSessionsWithResults()
    if (sessionsWithResults.isEmpty()) return

    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
    val file = File(exportDir, "ping_all_sessions.csv")

    file.bufferedWriter().use { writer ->
        writer.appendLine("session_id,host,start_time,seq,timestamp,rtt_ms,success,error")
        sessionsWithResults.forEach { (session, results) ->
            val startFormatted = dateFormat.format(Date(session.startTime))
            results.forEach { r ->
                writer.appendLine(
                    "${session.id},${session.host},$startFormatted,${r.sequenceNumber},${r.timestamp},${r.rttMs ?: ""},${r.isSuccess},${r.errorMessage ?: ""}"
                )
            }
        }
    }

    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Export all sessions"))
}
