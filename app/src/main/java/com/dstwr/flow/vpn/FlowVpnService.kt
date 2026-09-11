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
import com.dstwr.flow.data.notifications.FlowNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.FileInputStream

/** Local VPN lifecycle and blocking policy controller. */
class FlowVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private var drainJob: Job? = null
    private var applyJob: Job? = null
    private var monitorJob: Job? = null
    private var networkJob: Job? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val applyMutex = Mutex()
    private lateinit var policyEngine: VpnPolicyEngine
    private lateinit var notificationHelper: FlowNotificationHelper
    private lateinit var networkStateMonitor: NetworkStateMonitor
    private val warnedQuotaKeys = mutableSetOf<String>()
    private val reachedQuotaKeys = mutableSetOf<String>()
    private var lastBlockedPackages: Set<String>? = null
    private var lastEmergencyBlock: Boolean? = null
    private var lastNetworkType: NetworkState.Type? = null

    override fun onCreate() {
        super.onCreate()
        policyEngine = VpnPolicyEngine(applicationContext)
        notificationHelper = FlowNotificationHelper(applicationContext)
        networkStateMonitor = NetworkStateMonitor(applicationContext)
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

        try {
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, buildNotification())
        } catch (_: SecurityException) {
            stopSelf()
            return START_NOT_STICKY
        } catch (_: IllegalStateException) {
            stopSelf()
            return START_NOT_STICKY
        }

        val emergencyBlock = if (intent == null) policyEngine.currentEmergencyState()
        else intent.getBooleanExtra(EXTRA_EMERGENCY, false)

        applyJob?.cancel()
        applyJob = serviceScope.launch { applyPolicy(emergencyBlock, force = true) }
        startMonitorIfNeeded()
        startNetworkMonitorIfNeeded()
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)

    override fun onDestroy() {
        applyJob?.cancel()
        monitorJob?.cancel()
        networkJob?.cancel()
        stopTunnelReader()
        closeVpnInterface()
        warnedQuotaKeys.clear()
        reachedQuotaKeys.clear()
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
                launch { applyPolicy(emergency, force = false) }
                runCatching { checkQuotaNotifications() }
            }
        }
    }

    private fun startNetworkMonitorIfNeeded() {
        if (networkJob?.isActive == true) return
        networkJob = serviceScope.launch {
            runCatching {
                networkStateMonitor.states().collectLatest {
                    if (!isActive) return@collectLatest
                    val emergency = policyEngine.currentEmergencyState()
                    applyPolicy(emergency, force = true)
                }
            }
        }
    }

    private suspend fun checkQuotaNotifications() {
        val activeKeys = mutableSetOf<String>()
        if (policyEngine.notificationsEnabled()) {
            policyEngine.quotaAlerts().forEach { alert ->
                if (alert.dailyReached) {
                    val key = "${alert.packageName}:daily"
                    activeKeys += key
                    if (reachedQuotaKeys.add(key)) notificationHelper.notifyQuotaReached(alert.packageName, alert.appLabel, alert.dailyQuotaBytes, FlowNotificationHelper.Period.DAILY)
                } else if (alert.dailyPercent >= 80) {
                    val key = "${alert.packageName}:daily"
                    activeKeys += key
                    if (warnedQuotaKeys.add(key)) notificationHelper.notifyQuotaWarning(alert.packageName, alert.appLabel, alert.dailyUsedBytes, alert.dailyQuotaBytes, alert.dailyPercent, FlowNotificationHelper.Period.DAILY)
                }
                if (alert.monthlyReached) {
                    val key = "${alert.packageName}:monthly"
                    activeKeys += key
                    if (reachedQuotaKeys.add(key)) notificationHelper.notifyQuotaReached(alert.packageName, alert.appLabel, alert.monthlyQuotaBytes, FlowNotificationHelper.Period.MONTHLY)
                } else if (alert.monthlyPercent >= 80) {
                    val key = "${alert.packageName}:monthly"
                    activeKeys += key
                    if (warnedQuotaKeys.add(key)) notificationHelper.notifyQuotaWarning(alert.packageName, alert.appLabel, alert.monthlyUsedBytes, alert.monthlyQuotaBytes, alert.monthlyPercent, FlowNotificationHelper.Period.MONTHLY)
                }
            }
        }
        warnedQuotaKeys.retainAll(activeKeys)
        reachedQuotaKeys.retainAll(activeKeys)
    }

    private suspend fun applyPolicy(emergencyBlock: Boolean, force: Boolean) {
        applyMutex.withLock {
            try {
                val network = networkStateMonitor.currentState()
                val blockedPackages = policyEngine.activeBlockedPackages(emergencyBlock, network.networkType).toSet()
                val unchanged = !force &&
                    lastEmergencyBlock == emergencyBlock &&
                    lastBlockedPackages == blockedPackages &&
                    lastNetworkType == network.networkType

                if (unchanged) return

                if (!emergencyBlock && blockedPackages.isEmpty()) {
                    lastBlockedPackages = emptySet()
                    lastEmergencyBlock = false
                    lastNetworkType = network.networkType
                    stopTunnelOnly()
                    return
                }

                stopTunnelReader()
                closeVpnInterface()
                val established = policyEngine.buildBlockingTunnel(blockedPackages.toList(), emergencyBlock).establish()
                if (established == null) {
                    lastBlockedPackages = null
                    lastEmergencyBlock = null
                    lastNetworkType = null
                    stopTunnelOnly()
                    return
                }

                vpnInterface = established
                lastBlockedPackages = blockedPackages
                lastEmergencyBlock = emergencyBlock
                lastNetworkType = network.networkType
                startTunnelReader(established)
            } catch (_: SecurityException) {
                stopTunnelOnly()
            } catch (_: IllegalStateException) {
                stopTunnelOnly()
            }
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
                    if (isActive) stopTunnelOnly()
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
        monitorJob?.cancel()
        networkJob?.cancel()
        lastBlockedPackages = null
        lastEmergencyBlock = null
        lastNetworkType = null
        warnedQuotaKeys.clear()
        reachedQuotaKeys.clear()
        stopTunnelOnly()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
        else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "DSTWR Flow", NotificationManager.IMPORTANCE_LOW).apply {
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
