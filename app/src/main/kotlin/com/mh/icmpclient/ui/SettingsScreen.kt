package com.mh.icmpclient.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mh.icmpclient.viewmodel.PingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: PingViewModel = viewModel(),
) {
    val intervalMs by viewModel.pingIntervalMillis.collectAsStateWithLifecycle()
    val timeoutMs by viewModel.pingTimeoutMillis.collectAsStateWithLifecycle()

    var intervalText by remember(intervalMs) { mutableStateOf(intervalMs.toString()) }
    var timeoutText by remember(timeoutMs) { mutableStateOf(timeoutMs.toString()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Settings") })
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = "Ping timing",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = intervalText,
                onValueChange = {
                    intervalText = it.filter { c -> c.isDigit() }
                    errorMessage = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Interval (ms)") },
                supportingText = {
                    Text(
                        "Pause after each probe before the next (0–${PingViewModel.MAX_PING_INTERVAL_MS}).",
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = timeoutText,
                onValueChange = {
                    timeoutText = it.filter { c -> c.isDigit() }
                    errorMessage = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Timeout (ms)") },
                supportingText = {
                    Text(
                        "Max wait per probe. Shell ping uses whole seconds for -W (1–${PingViewModel.MAX_PING_TIMEOUT_MS / 1000}s from this value).",
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
            errorMessage?.let { msg ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = msg, color = MaterialTheme.colorScheme.error)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    val intervalParsed = intervalText.toLongOrNull()
                    val timeoutParsed = timeoutText.toLongOrNull()
                    when {
                        intervalParsed == null -> errorMessage = "Interval must be a number."
                        timeoutParsed == null -> errorMessage = "Timeout must be a number."
                        intervalParsed < 0L || intervalParsed > PingViewModel.MAX_PING_INTERVAL_MS ->
                            errorMessage = "Interval must be between 0 and ${PingViewModel.MAX_PING_INTERVAL_MS}."
                        timeoutParsed < 1L || timeoutParsed > PingViewModel.MAX_PING_TIMEOUT_MS ->
                            errorMessage = "Timeout must be between 1 and ${PingViewModel.MAX_PING_TIMEOUT_MS}."
                        else -> {
                            viewModel.setPingIntervalMillis(intervalParsed)
                            viewModel.setPingTimeoutMillis(timeoutParsed)
                            intervalText = intervalParsed.toString()
                            timeoutText = timeoutParsed.toString()
                            errorMessage = null
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save")
            }
        }
    }
}
