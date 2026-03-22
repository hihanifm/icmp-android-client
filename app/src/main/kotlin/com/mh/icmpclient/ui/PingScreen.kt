package com.mh.icmpclient.ui

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mh.icmpclient.ping.PingBackend
import com.mh.icmpclient.viewmodel.PingViewModel

private data class NetworkOption(val network: Network?, val label: String)

private data class BackendOption(val backend: PingBackend, val label: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PingScreen(modifier: Modifier = Modifier, viewModel: PingViewModel = viewModel()) {
    val state by viewModel.pingState.collectAsStateWithLifecycle()
    val backgroundMode by viewModel.backgroundMode.collectAsStateWithLifecycle()
    val continuous by viewModel.continuous.collectAsStateWithLifecycle()
    val pingCount by viewModel.pingCount.collectAsStateWithLifecycle()
    val maxPingCount by viewModel.maxPingCount.collectAsStateWithLifecycle()
    val pingBackend by viewModel.pingBackend.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val prefs = context.getSharedPreferences("icmp_prefs", android.content.Context.MODE_PRIVATE)
    var host by rememberSaveable { mutableStateOf(prefs.getString("last_host", "8.8.8.8")!!) }
    val listState = rememberLazyListState()

    val networkOptions = remember(context) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val options = mutableListOf(NetworkOption(null, "Auto"))
        cm.allNetworks.forEach { network ->
            val caps = cm.getNetworkCapabilities(network) ?: return@forEach
            val label = buildString {
                when {
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> append("WiFi")
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> append("Cellular")
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> append("Ethernet")
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> append("Bluetooth")
                    caps.hasTransport(7) -> append("Satellite") // TRANSPORT_SATELLITE (API 36+)
                    else -> append("Other")
                }
            }
            options.add(NetworkOption(network, label))
        }
        options.toList()
    }
    var selectedNetwork by remember { mutableStateOf(networkOptions.first()) }
    var networkDropdownExpanded by remember { mutableStateOf(false) }

    val backendOptions = remember {
        listOf(
            BackendOption(PingBackend.ICMP4A, "In-app (ICMP)"),
            BackendOption(PingBackend.SHELL, "System ping"),
        )
    }
    var selectedBackend by remember(pingBackend) { mutableStateOf(backendOptions.first { it.backend == pingBackend }) }
    var backendDropdownExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(state.results.size) {
        if (state.results.isNotEmpty()) {
            listState.animateScrollToItem(state.results.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text("Host") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isRunning,
        )

        Spacer(Modifier.height(8.dp))

        ExposedDropdownMenuBox(
            expanded = networkDropdownExpanded,
            onExpandedChange = { if (!state.isRunning) networkDropdownExpanded = it },
        ) {
            OutlinedTextField(
                value = selectedNetwork.label,
                onValueChange = {},
                readOnly = true,
                label = { Text("Network") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = networkDropdownExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                enabled = !state.isRunning,
            )
            ExposedDropdownMenu(
                expanded = networkDropdownExpanded,
                onDismissRequest = { networkDropdownExpanded = false },
            ) {
                networkOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            selectedNetwork = option
                            networkDropdownExpanded = false
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        ExposedDropdownMenuBox(
            expanded = backendDropdownExpanded,
            onExpandedChange = { if (!state.isRunning) backendDropdownExpanded = it },
        ) {
            OutlinedTextField(
                value = selectedBackend.label,
                onValueChange = {},
                readOnly = true,
                label = { Text("Ping engine") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = backendDropdownExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                enabled = !state.isRunning,
            )
            ExposedDropdownMenu(
                expanded = backendDropdownExpanded,
                onDismissRequest = { backendDropdownExpanded = false },
            ) {
                backendOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            selectedBackend = option
                            viewModel.setPingBackend(option.backend)
                            backendDropdownExpanded = false
                        },
                    )
                }
            }
        }

        if (selectedBackend.backend == PingBackend.SHELL && selectedNetwork.network != null) {
            Text(
                "System ping uses default routing; the selected network may be ignored.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Spacer(Modifier.height(8.dp))

        // Toggles row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Background", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = backgroundMode,
                    onCheckedChange = { viewModel.setBackgroundMode(it) },
                    enabled = !state.isRunning,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Continuous", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = continuous,
                    onCheckedChange = { viewModel.setContinuous(it) },
                    enabled = !state.isRunning,
                )
            }
        }

        if (continuous) {
            OutlinedTextField(
                value = maxPingCount.toString(),
                onValueChange = { it.toIntOrNull()?.let { c -> viewModel.setMaxPingCount(c) } },
                label = { Text("Max count") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isRunning,
            )
            Spacer(Modifier.height(8.dp))
        } else {
            OutlinedTextField(
                value = pingCount.toString(),
                onValueChange = { it.toIntOrNull()?.let { c -> viewModel.setPingCount(c) } },
                label = { Text("Count") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isRunning,
            )
            Spacer(Modifier.height(8.dp))
        }

        // Start/Stop
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    prefs.edit().putString("last_host", host).apply()
                    viewModel.startPing(host, selectedNetwork.network)
                },
                enabled = !state.isRunning && host.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) {
                Text("Start")
            }
            OutlinedButton(
                onClick = { viewModel.stopPing() },
                enabled = state.isRunning,
                modifier = Modifier.weight(1f),
            ) {
                Text("Stop")
            }
        }

        Spacer(Modifier.height(8.dp))

        // Stats bar
        if (state.stats.pingCount > 0) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    val stats = state.stats
                    val loss = if (stats.pingCount > 0) {
                        ((stats.pingCount - stats.successCount) * 100) / stats.pingCount
                    } else 0
                    Text(
                        "Packets: ${stats.pingCount} sent, ${stats.successCount} received, $loss% loss",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (stats.minRtt != null) {
                        Text(
                            "RTT min/avg/max = %.1f/%.1f/%.1f ms".format(
                                stats.minRtt, stats.avgRtt, stats.maxRtt
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    state.resolvedIp?.let {
                        Text("IP: $it", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        state.error?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(8.dp))

        // Latency chart
        if (state.results.isNotEmpty()) {
            LatencyChart(
                dataPoints = state.results.map { result ->
                    ChartDataPoint(
                        sequenceNumber = result.sequenceNumber,
                        rttMs = result.rttMs,
                        isSuccess = result.isSuccess,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        }

        // Results list
        LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
            items(state.results) { result ->
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
                            color = Color.Red,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}
