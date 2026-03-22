package com.mh.icmpclient

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Network
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PingViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as IcmpApp).pingRepository

    private val prefs = application.getSharedPreferences("icmp_prefs", Context.MODE_PRIVATE)

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

    private val _maxPingCount = MutableStateFlow(1000)
    val maxPingCount: StateFlow<Int> = _maxPingCount.asStateFlow()

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

    fun startPing(host: String, network: Network? = null) {
        val count = if (_continuous.value) _maxPingCount.value else _pingCount.value
        val backend = _pingBackend.value

        if (_backgroundMode.value) {
            val intent = Intent(getApplication(), PingForegroundService::class.java).apply {
                action = PingForegroundService.ACTION_START
                putExtra(PingForegroundService.EXTRA_HOST, host)
                putExtra(PingForegroundService.EXTRA_COUNT, count)
                putExtra(PingForegroundService.EXTRA_INTERVAL, 1000L)
                putExtra(PingForegroundService.EXTRA_PING_BACKEND, backend.name)
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
                intervalMillis = 1000L,
                scope = viewModelScope,
                network = network,
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
