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
import java.io.FileInputStream
import java.io.FileOutputStream

/** Local VPN lifecycle and real user-space traffic enforcement controller. */
class FlowVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private var trafficEngine: TrafficEngine? = null
    private var applyJob: Job? = null
    private var monitorJob: Job? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var policyEngine: VpnPolicyEngine
    private lateinit var notificationHelper: FlowNotificationHelper
    private lateinit var networkStateMonitor: NetworkStateMonitor
    private val trafficPolicies = TrafficPolicyRegistry()
    private val speedLimits = SpeedLimitRegistry()
    private val trafficMeter = TrafficMeter()
    private val warnedQuotaKeys = mutableSetOf<String>()
    private val reachedQuotaKeys = mutableSetOf<String>()
    private var lastManagedPackages: Set<String>? = null
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
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        val emergencyBlock = intent?.getBooleanExtra(EXTRA_EMERGENCY, false) == true
        applyJob?.cancel()
        applyJob = serviceScope.launch { applyPolicy(emergencyBlock, force = true) }
        startMonitorIfNeeded()
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)

    override fun onDestroy() {
        applyJob?.cancel(); applyJob = null
        monitorJob?.cancel(); monitorJob = null
        stopTrafficEngine()
        closeVpnInterface()
        warnedQuotaKeys.clear(); reachedQuotaKeys.clear()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }

    private fun startMonitorIfNeeded() {
        if (monitorJob?.isActive == true) return
        monitorJob = serviceScope.launch {
            launch {
                networkStateMonitor.states().collectLatest {
                    if (!isActive) return@collectLatest
                    val emergency = policyEngine.currentEmergencyState()
                    applyJob?.cancel()
                    applyJob = launch { applyPolicy(emergency, force = false) }
                }
            }
            while (isActive) {
                runCatching { checkQuotaNotifications() }
                delay(60_000L)
            }
        }
    }

    private suspend fun checkQuotaNotifications() {
        val activeKeys = mutableSetOf<String>()
        if (policyEngine.notificationsEnabled()) {
            policyEngine.quotaAlerts().forEach { alert ->
                if (alert.dailyReached) {
                    val key = "${alert.packageName}:daily"; activeKeys += key
                    if (reachedQuotaKeys.add(key)) notificationHelper.notifyQuotaReached(alert.packageName, alert.appLabel, alert.dailyQuotaBytes, FlowNotificationHelper.Period.DAILY)
                } else if (alert.dailyPercent >= 80) {
                    val key = "${alert.packageName}:daily"; activeKeys += key
                    if (warnedQuotaKeys.add(key)) notificationHelper.notifyQuotaWarning(alert.packageName, alert.appLabel, alert.dailyUsedBytes, alert.dailyQuotaBytes, alert.dailyPercent, FlowNotificationHelper.Period.DAILY)
                }
                if (alert.monthlyReached) {
                    val key = "${alert.packageName}:monthly"; activeKeys += key
                    if (reachedQuotaKeys.add(key)) notificationHelper.notifyQuotaReached(alert.packageName, alert.appLabel, alert.monthlyQuotaBytes, FlowNotificationHelper.Period.MONTHLY)
                } else if (alert.monthlyPercent >= 80) {
                    val key = "${alert.packageName}:monthly"; activeKeys += key
                    if (warnedQuotaKeys.add(key)) notificationHelper.notifyQuotaWarning(alert.packageName, alert.appLabel, alert.monthlyUsedBytes, alert.monthlyQuotaBytes, alert.monthlyPercent, FlowNotificationHelper.Period.MONTHLY)
                }
            }
        }
        warnedQuotaKeys.retainAll(activeKeys)
        reachedQuotaKeys.retainAll(activeKeys)
    }

    private suspend fun applyPolicy(emergencyBlock: Boolean, force: Boolean) {
        try {
            val networkType = networkStateMonitor.currentState().networkType
            val managedPackages = policyEngine.managedPackages(emergencyBlock).toSet()
            val unchanged = !force && lastEmergencyBlock == emergencyBlock && lastManagedPackages == managedPackages && lastNetworkType == networkType
            if (unchanged) return

            if (!emergencyBlock && managedPackages.isEmpty()) {
                lastManagedPackages = emptySet()
                lastEmergencyBlock = false
                lastNetworkType = networkType
                trafficPolicies.clear()
                speedLimits.clear()
                stopTrafficEngine()
                stopVpnInterfaceOnly()
                return
            }

            stopTrafficEngine()
            closeVpnInterface()
            trafficPolicies.replaceAll(policyEngine.trafficPolicies(emergencyBlock, networkType))
            trafficPolicies.setGlobalBlocked(emergencyBlock)
            speedLimits.clear()
            trafficPolicies.snapshot().forEach { policy ->
                speedLimits.configure(policy.packageName, policy.downloadLimitBytesPerSecond, policy.uploadLimitBytesPerSecond)
            }

            val established = policyEngine.buildBlockingTunnel(this, managedPackages.toList(), emergencyBlock).establish() ?: run {
                stopVpn()
                return
            }
            vpnInterface = established
            lastManagedPackages = managedPackages
            lastEmergencyBlock = emergencyBlock
            lastNetworkType = networkType
            startTrafficEngine(established)
        } catch (_: SecurityException) {
            stopVpn()
        } catch (_: IllegalStateException) {
            stopVpn()
        } catch (_: RuntimeException) {
            stopVpn()
        }
    }

    private fun startTrafficEngine(interfaceFd: ParcelFileDescriptor) {
        stopTrafficEngine()
        val input = FileInputStream(interfaceFd.fileDescriptor)
        val output = FileOutputStream(interfaceFd.fileDescriptor)
        val identityResolver = ConnectionOwnerUidResolver(applicationContext, trafficPolicies)
        val decisionEngine = PacketDecisionEngine(ConnectionTracker(), speedLimits, trafficMeter, identityResolver, trafficPolicies)
        val transport = UserSpaceForwardingTransport(this, serviceScope)
        trafficEngine = TrafficEngine(serviceScope, input, output, transport, decisionEngine).also { it.start() }
    }

    private fun stopTrafficEngine() {
        trafficEngine?.stop()
        trafficEngine = null
    }

    private fun stopVpnInterfaceOnly() = closeVpnInterface()

    private fun closeVpnInterface() {
        runCatching { vpnInterface?.close() }
        vpnInterface = null
    }

    private fun stopVpn() {
        applyJob?.cancel(); applyJob = null
        monitorJob?.cancel(); monitorJob = null
        lastManagedPackages = null; lastEmergencyBlock = null; lastNetworkType = null
        warnedQuotaKeys.clear(); reachedQuotaKeys.clear()
        trafficPolicies.clear(); speedLimits.clear()
        stopTrafficEngine(); closeVpnInterface()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
        else { @Suppress("DEPRECATION") stopForeground(true) }
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
        .setSmallIcon(com.dstwr.flow.R.drawable.ic_dstwr_flow)
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
    }
}
