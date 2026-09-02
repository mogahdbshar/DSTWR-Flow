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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Local VPN lifecycle and blocking policy controller.
 *
 * This phase implements deliberate blocking, not a full VPN proxy. Active
 * blocked apps are routed into the local tunnel with no upstream forwarding.
 */
class FlowVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var policyEngine: VpnPolicyEngine

    override fun onCreate() {
        super.onCreate()
        policyEngine = VpnPolicyEngine(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        val emergencyBlock = intent?.getBooleanExtra(EXTRA_EMERGENCY, false) == true

        serviceScope.launch {
            applyPolicy(emergencyBlock)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)

    override fun onDestroy() {
        stopVpn()
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun applyPolicy(emergencyBlock: Boolean) {
        val blockedPackages = policyEngine.activeBlockedPackages(emergencyBlock)
        if (!emergencyBlock && blockedPackages.isEmpty()) {
            stopVpn()
            return
        }

        vpnInterface?.close()
        vpnInterface = policyEngine
            .buildBlockingTunnel(blockedPackages, emergencyBlock)
            .establish()

        if (vpnInterface == null) stopVpn()
    }

    private fun stopVpn() {
        vpnInterface?.close()
        vpnInterface = null
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
        const val EXTRA_EMERGENCY = "emergency_block"
        private const val CHANNEL_ID = "dstwr_flow_service"
        private const val NOTIFICATION_ID = 7101
    }
}
