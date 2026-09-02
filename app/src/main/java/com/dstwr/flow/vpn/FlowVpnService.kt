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
import com.dstwr.flow.R

/**
 * Local VPN lifecycle foundation.
 *
 * This class deliberately does not pretend to be a packet-forwarding engine.
 * A VPN tunnel without a forwarding loop blackholes traffic, so the tunnel is
 * only established after explicit user consent and the production routing
 * engine will be introduced separately.
 */
class FlowVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        if (vpnInterface == null) {
            vpnInterface = Builder()
                .setSession("DSTWR Flow")
                .setMtu(1500)
                .addAddress("10.10.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .establish()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    private fun stopVpn() {
        vpnInterface?.close()
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "DSTWR Flow", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "حالة التحكم المحلي في الشبكة"
                }
            )
        }
    }

    private fun buildNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_warning)
        .setContentTitle("DSTWR Flow")
        .setContentText("خدمة التحكم المحلي في الشبكة تعمل")
        .setOngoing(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .build()

    companion object {
        const val ACTION_STOP = "com.dstwr.flow.action.STOP_VPN"
        private const val CHANNEL_ID = "dstwr_flow_service"
        private const val NOTIFICATION_ID = 7101
    }
}
