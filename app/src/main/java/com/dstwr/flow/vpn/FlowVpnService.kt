package com.dstwr.flow.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream

/**
 * Local VPN lifecycle and blocking policy controller.
 *
 * This phase implements deliberate blocking, not a full VPN proxy. Active
 * blocked apps are routed into the local tunnel with no upstream forwarding.
 * Packets arriving at the tunnel are drained and discarded.
 *
 * A lightweight monitor re-evaluates schedules and quotas while protection is
 * active, rebuilding the tunnel only when the effective blocked-app set changes.
 */
class FlowVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private var drainJob: Job? = null
    private var applyJob: Job? = null
    private var monitorJob: Job? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var policyEngine: VpnPolicyEngine
    private var lastBlockedPackages: Set<String>? = null
    private var lastEmergencyBlock: Boolean? = null

    override fun onCreate() {
        super.onCreate()
        policyEngine = VpnPolicyEngine(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVpn()
                return START_NOT_STICKY
            }
            ACTION_APPLY, null -> Unit
            else -> Unit
        }

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        val emergencyBlock = intent?.getBooleanExtra(EXTRA_EMERGENCY, false) == true

        applyJob?.cancel()
        applyJob = serviceScope.launch {
            applyPolicy(emergencyBlock, force = true)
        }
        startMonitorIfNeeded()
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)

    override fun onDestroy() {
        applyJob?.cancel()
        applyJob = null
        monitorJob?.cancel()
        monitorJob = null
        stopTunnelReader()
        closeVpnInterface()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startMonitorIfNeeded() {
        if (monitorJob?.isActive == true) return
        monitorJob = serviceScope.launch {
            while (isActive) {
                delay(MONITOR_INTERVAL_MS)
                if (!isActive) break
                val emergency = policyEngine.currentEmergencyState()
                applyJob?.cancel()
                applyJob = launch { applyPolicy(emergency, force = false) }
            }
        }
    }

    private suspend fun applyPolicy(emergencyBlock: Boolean, force: Boolean) {
        try {
            val blockedPackages = policyEngine.activeBlockedPackages(emergencyBlock).toSet()
            val unchanged = !force &&
                lastEmergencyBlock == emergencyBlock &&
                lastBlockedPackages == blockedPackages

            if (unchanged) return

            if (!emergencyBlock && blockedPackages.isEmpty()) {
                lastBlockedPackages = emptySet()
                lastEmergencyBlock = false
                stopTunnelOnly()
                return
            }

            stopTunnelReader()
            closeVpnInterface()

            val established = policyEngine
                .buildBlockingTunnel(blockedPackages.toList(), emergencyBlock)
                .establish()

            if (established == null) {
                stopVpn()
                return
            }

            vpnInterface = established
            lastBlockedPackages = blockedPackages
            lastEmergencyBlock = emergencyBlock
            startTunnelReader(established)
        } catch (_: SecurityException) {
            stopVpn()
        } catch (_: IllegalStateException) {
            stopVpn()
        }
    }

    private fun startTunnelReader(interfaceFd: ParcelFileDescriptor) {
        stopTunnelReader()
        drainJob = serviceScope.launch {
            FileInputStream(interfaceFd.fileDescriptor).use { input ->
                val buffer = ByteArray(BUFFER_SIZE)
                try {
                    while (isActive) {
                        val count = input.read(buffer)
                        if (count < 0) break
                    }
                } catch (_: Exception) {
                    if (isActive) stopVpn()
                }
            }
        }
    }

    private fun stopTunnelReader() {
        drainJob?.cancel()
        drainJob = null
    }

    private fun closeVpnInterface() {
        vpnInterface?.close()
        vpnInterface = null
    }

    private fun stopTunnelOnly() {
        stopTunnelReader()
        closeVpnInterface()
    }

    private fun stopVpn() {
        applyJob?.cancel()
        applyJob = null
        monitorJob?.cancel()
        monitorJob = null
        lastBlockedPackages = null
        lastEmergencyBlock = null
        stopTunnelOnly()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "DSTWR Flow",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "حالة التحكم المحلي في الشبكة"
                }
            )
        }
    }

    private fun buildNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_warning)
        .setContentTitle("DSTWR Flow")
        .setContentText("التحكم المحلي في الشبكة يعمل")
        .setOngoing(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .build()

    companion object {
        const val ACTION_STOP = "com.dstwr.flow.action.STOP_VPN"
        const val ACTION_APPLY = "com.dstwr.flow.action.APPLY_POLICY"
        const val EXTRA_EMERGENCY = "emergency_block"
        private const val CHANNEL_ID = "dstwr_flow_service"
        private const val NOTIFICATION_ID = 7101
        private const val BUFFER_SIZE = 32767
        private const val MONITOR_INTERVAL_MS = 60_000L
    }
}
