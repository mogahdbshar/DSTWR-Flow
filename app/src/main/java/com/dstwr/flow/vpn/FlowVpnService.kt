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

/** Local traffic-control service with native full forwarding and selective blocking modes. */
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
    private var lastForwardingMode: Boolean? = null

    override fun onCreate() {
        super.onCreate()
        policyEngine = VpnPolicyEngine(applicationContext)
        notificationHelper = FlowNotificationHelper(applicationContext)
        networkStateMonitor = NetworkStateMonitor(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }
        try {
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, buildNotification())
        } catch (_: SecurityException) {
            stopSelf(); return START_NOT_STICKY
        } catch (_: IllegalStateException) {
            stopSelf(); return START_NOT_STICKY
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
        applyJob?.cancel(); monitorJob?.cancel(); networkJob?.cancel()
        stopTunnelOnly()
        warnedQuotaKeys.clear(); reachedQuotaKeys.clear()
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
                    applyPolicy(emergency, force = false)
                }
            }
        }
    }

    private suspend fun checkQuotaNotifications() {
        val activeKeys = mutableSetOf<String>()
        if (policyEngine.notificationsEnabled()) {
            policyEngine.quotaAlerts().forEach { alert ->
                processQuota(alert.packageName, alert.appLabel, alert.dailyPercent, alert.dailyReached, alert.dailyUsedBytes, alert.dailyQuotaBytes, FlowNotificationHelper.Period.DAILY, activeKeys)
                processQuota(alert.packageName, alert.appLabel, alert.monthlyPercent, alert.monthlyReached, alert.monthlyUsedBytes, alert.monthlyQuotaBytes, FlowNotificationHelper.Period.MONTHLY, activeKeys)
            }
        }
        warnedQuotaKeys.retainAll(activeKeys)
        reachedQuotaKeys.retainAll(activeKeys)
    }

    private fun processQuota(
        packageName: String,
        appLabel: String,
        percent: Int,
        reached: Boolean,
        usedBytes: Long,
        quotaBytes: Long,
        period: FlowNotificationHelper.Period,
        activeKeys: MutableSet<String>
    ) {
        if (quotaBytes <= 0L) return
        val key = "$packageName:${period.code}"
        activeKeys += key
        if (reached) {
            warnedQuotaKeys.remove(key)
            notificationHelper.cancelWarning(packageName, period)
            if (reachedQuotaKeys.add(key)) notificationHelper.notifyQuotaReached(packageName, appLabel, quotaBytes, period)
        } else if (percent >= 80) {
            reachedQuotaKeys.remove(key)
            if (warnedQuotaKeys.add(key)) notificationHelper.notifyQuotaWarning(packageName, appLabel, usedBytes, quotaBytes, percent, period)
        }
    }

    private suspend fun applyPolicy(emergencyBlock: Boolean, force: Boolean) {
        applyMutex.withLock {
            try {
                val network = networkStateMonitor.currentState()
                val blockedPackages = policyEngine.activeBlockedPackages(emergencyBlock, network.networkType).toSet()
                val forwarding = !emergencyBlock && blockedPackages.isEmpty()
                val unchanged = !force &&
                    lastEmergencyBlock == emergencyBlock &&
                    lastBlockedPackages == blockedPackages &&
                    lastNetworkType == network.networkType &&
                    lastForwardingMode == forwarding
                if (unchanged) return

                stopTunnelOnly()

                if (forwarding) {
                    if (!network.connected) {
                        lastBlockedPackages = emptySet()
                        lastEmergencyBlock = false
                        lastNetworkType = network.networkType
                        lastForwardingMode = false
                        return
                    }

                    val established = policyEngine.buildForwardingTunnel().establish()
                    if (established == null) {
                        lastBlockedPackages = null
                        lastEmergencyBlock = null
                        lastNetworkType = null
                        lastForwardingMode = null
                        return
                    }

                    val tunFd = runCatching { established.detachFd() }.getOrElse {
                        established.close()
                        -1
                    }
                    if (tunFd < 0 || !NativeTun2Socks.start(tunFd)) {
                        if (tunFd >= 0) runCatching { android.system.Os.close(tunFd) }
                        lastBlockedPackages = null
                        lastEmergencyBlock = null
                        lastNetworkType = null
                        lastForwardingMode = null
                        return
                    }

                    lastBlockedPackages = emptySet()
                    lastEmergencyBlock = false
                    lastNetworkType = network.networkType
                    lastForwardingMode = true
                    return
                }

                val established = policyEngine.buildBlockingTunnel(blockedPackages.toList(), emergencyBlock).establish()
                if (established == null) {
                    lastBlockedPackages = null
                    lastEmergencyBlock = null
                    lastNetworkType = null
                    lastForwardingMode = null
                    return
                }
                vpnInterface = established
                lastBlockedPackages = blockedPackages
                lastEmergencyBlock = emergencyBlock
                lastNetworkType = network.networkType
                lastForwardingMode = false
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
                try { while (isActive) { if (input.read(buffer) < 0) break } }
                catch (_: Exception) { if (isActive) stopTunnelOnly() }
            }
        }
    }

    private fun stopTunnelReader() { drainJob?.cancel(); drainJob = null }

    private fun closeVpnInterface() { vpnInterface?.close(); vpnInterface = null }

    private fun stopTunnelOnly() {
        stopTunnelReader()
        NativeTun2Socks.stop()
        closeVpnInterface()
    }

    private fun stopVpn() {
        applyJob?.cancel(); monitorJob?.cancel(); networkJob?.cancel()
        lastBlockedPackages = null; lastEmergencyBlock = null; lastNetworkType = null; lastForwardingMode = null
        warnedQuotaKeys.clear(); reachedQuotaKeys.clear(); stopTunnelOnly()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE) else {
            @Suppress("DEPRECATION") stopForeground(true)
        }
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "DSTWR Flow", NotificationManager.IMPORTANCE_LOW).apply { description = "حالة التحكم المحلي في الشبكة" }
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
