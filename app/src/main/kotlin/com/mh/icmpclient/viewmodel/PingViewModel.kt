package com.mh.icmpclient.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Network
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mh.icmpclient.IcmpApp
import com.mh.icmpclient.resolveAutoNetworkSessionLabel
import com.mh.icmpclient.ping.PingBackend
import com.mh.icmpclient.service.PingForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PingViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as IcmpApp).pingRepository

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        const val PREFS_NAME = "icmp_prefs"
        const val PREF_PING_INTERVAL_MS = "ping_interval_ms"
        const val PREF_PING_TIMEOUT_MS = "ping_timeout_ms"
        private const val DEFAULT_INTERVAL_MS = 100L
        private const val DEFAULT_TIMEOUT_MS = 1000L
        const val MAX_PING_INTERVAL_MS = 300_000L
        const val MAX_PING_TIMEOUT_MS = 120_000L
    }

    private val _pingBackend = MutableStateFlow(
        PingBackend.fromPrefsValue(prefs.getString(PingBackend.PREFS_KEY, null)),
    )
    val pingBackend: StateFlow<PingBackend> = _pingBackend.asStateFlow()

    val pingState = repository.state

    private val _backgroundMode = MutableStateFlow(false)
    val backgroundMode: StateFlow<Boolean> = _backgroundMode.asStateFlow()

    private val _continuous = MutableStateFlow(true)
    val continuous: StateFlow<Boolean> = _continuous.asStateFlow()

    private val _pingCount = MutableStateFlow(10)
    val pingCount: StateFlow<Int> = _pingCount.asStateFlow()

    private val _maxPingCount = MutableStateFlow(100)
    val maxPingCount: StateFlow<Int> = _maxPingCount.asStateFlow()

    private val _pingIntervalMillis = MutableStateFlow(
        prefs.getLong(PREF_PING_INTERVAL_MS, DEFAULT_INTERVAL_MS).coerceIn(0L, MAX_PING_INTERVAL_MS),
    )
    val pingIntervalMillis: StateFlow<Long> = _pingIntervalMillis.asStateFlow()

    private val _pingTimeoutMillis = MutableStateFlow(
        prefs.getLong(PREF_PING_TIMEOUT_MS, DEFAULT_TIMEOUT_MS).coerceIn(1L, MAX_PING_TIMEOUT_MS),
    )
    val pingTimeoutMillis: StateFlow<Long> = _pingTimeoutMillis.asStateFlow()

    fun setPingBackend(backend: PingBackend) {
        _pingBackend.value = backend
        prefs.edit().putString(PingBackend.PREFS_KEY, backend.name).apply()
    }

    fun setBackgroundMode(enabled: Boolean) {
        _backgroundMode.value = enabled
    }

    fun setContinuous(enabled: Boolean) {
        _continuous.value = enabled
    }

    fun setPingCount(count: Int) {
        _pingCount.value = count
    }

    fun setMaxPingCount(count: Int) {
        _maxPingCount.value = count
    }

    fun setPingIntervalMillis(value: Long) {
        val clamped = value.coerceIn(0L, MAX_PING_INTERVAL_MS)
        _pingIntervalMillis.value = clamped
        prefs.edit().putLong(PREF_PING_INTERVAL_MS, clamped).apply()
    }

    fun setPingTimeoutMillis(value: Long) {
        val clamped = value.coerceIn(1L, MAX_PING_TIMEOUT_MS)
        _pingTimeoutMillis.value = clamped
        prefs.edit().putLong(PREF_PING_TIMEOUT_MS, clamped).apply()
    }

    fun startPing(host: String, network: Network? = null, networkLabel: String = "Auto") {
        val count = if (_continuous.value) _maxPingCount.value else _pingCount.value
        val backend = _pingBackend.value
        val intervalMs = _pingIntervalMillis.value
        val timeoutMs = _pingTimeoutMillis.value
        val resolvedNetworkLabel =
            if (network == null) getApplication<Application>().resolveAutoNetworkSessionLabel()
            else networkLabel

        if (_backgroundMode.value) {
            val intent = Intent(getApplication(), PingForegroundService::class.java).apply {
                action = PingForegroundService.ACTION_START
                putExtra(PingForegroundService.EXTRA_HOST, host)
                putExtra(PingForegroundService.EXTRA_COUNT, count)
                putExtra(PingForegroundService.EXTRA_INTERVAL, intervalMs)
                putExtra(PingForegroundService.EXTRA_TIMEOUT, timeoutMs)
                putExtra(PingForegroundService.EXTRA_PING_BACKEND, backend.name)
                putExtra(PingForegroundService.EXTRA_NETWORK_LABEL, resolvedNetworkLabel)
                if (network != null) {
                    putExtra(PingForegroundService.EXTRA_NETWORK_HANDLE, network.networkHandle)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getApplication<IcmpApp>().startForegroundService(intent)
            } else {
                getApplication<IcmpApp>().startService(intent)
            }
        } else {
            repository.startPing(
                host = host,
                count = count,
                intervalMillis = intervalMs,
                timeoutMillis = timeoutMs,
                scope = viewModelScope,
                network = network,
                networkLabel = resolvedNetworkLabel,
                backend = backend,
            )
        }
    }

    fun stopPing() {
        if (_backgroundMode.value) {
            val intent = Intent(getApplication(), PingForegroundService::class.java).apply {
                action = PingForegroundService.ACTION_STOP
            }
            getApplication<IcmpApp>().startService(intent)
        } else {
            repository.stopPing()
        }
    }

    fun getAllSessions() = repository.getAllSessions()

    fun getResultsForSession(sessionId: Long) = repository.getResultsForSession(sessionId)

    suspend fun deleteSession(sessionId: Long) = repository.deleteSession(sessionId)

    suspend fun getAllSessionsWithResults() = repository.getAllSessionsWithResults()
}
