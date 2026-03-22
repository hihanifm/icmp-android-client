package com.mh.icmpclient.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.IBinder
import com.mh.icmpclient.IcmpApp
import com.mh.icmpclient.R
import com.mh.icmpclient.ping.PingBackend
import com.mh.icmpclient.repository.PingRepository
import com.mh.icmpclient.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PingForegroundService : Service() {

    companion object {
        const val ACTION_START = "com.mh.icmpclient.START_PING"
        const val ACTION_STOP = "com.mh.icmpclient.STOP_PING"
        const val EXTRA_HOST = "host"
        const val EXTRA_COUNT = "count"
        const val EXTRA_INTERVAL = "interval"
        const val EXTRA_NETWORK_HANDLE = "network_handle"
        const val EXTRA_PING_BACKEND = "ping_backend"
        private const val NOTIFICATION_ID = 1
        private const val PREFS_NAME = "icmp_prefs"
        private const val CHANNEL_ID = "ping_service"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var repository: PingRepository

    override fun onCreate() {
        super.onCreate()
        repository = (application as IcmpApp).pingRepository
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val host = intent.getStringExtra(EXTRA_HOST) ?: "8.8.8.8"
                val count = intent.getIntExtra(EXTRA_COUNT, 1000)
                val interval = intent.getLongExtra(EXTRA_INTERVAL, 1000L)
                val networkHandle = intent.getLongExtra(EXTRA_NETWORK_HANDLE, -1L)
                val network: Network? = if (networkHandle != -1L && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val cm = getSystemService(ConnectivityManager::class.java)
                    cm.allNetworks.firstOrNull { it.networkHandle == networkHandle }
                } else null

                val backendExtra = intent.getStringExtra(EXTRA_PING_BACKEND)
                val backend = if (backendExtra != null) {
                    PingBackend.fromPrefsValue(backendExtra)
                } else {
                    val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    PingBackend.fromPrefsValue(prefs.getString(PingBackend.PREFS_KEY, null))
                }

                startForeground(NOTIFICATION_ID, buildNotification("Pinging $host..."))

                repository.startPing(
                    host = host,
                    count = count,
                    intervalMillis = interval,
                    scope = serviceScope,
                    network = network,
                    backend = backend,
                )

                serviceScope.launch {
                    repository.state.collectLatest { state ->
                        val stats = state.stats
                        val text = if (stats.avgRtt != null) {
                            "Ping $host — avg: %.1f ms (%d/%d)".format(
                                stats.avgRtt, stats.successCount, stats.pingCount
                            )
                        } else {
                            "Pinging $host..."
                        }
                        updateNotification(text)

                        if (!state.isRunning && stats.pingCount > 0) {
                            stopSelf()
                        }
                    }
                }
            }
            ACTION_STOP -> {
                repository.stopPing()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        repository.stopPing()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.notification_channel_description)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE,
        )
        return builder
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }
}
