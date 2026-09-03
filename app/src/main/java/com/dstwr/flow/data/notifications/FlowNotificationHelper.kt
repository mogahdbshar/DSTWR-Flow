package com.dstwr.flow.data.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.dstwr.flow.R

/** Creates local, low-noise notifications for policy events. */
class FlowNotificationHelper(context: Context) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(NotificationManager::class.java)

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "تنبيهات DSTWR Flow",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "تنبيهات استهلاك وحصص الشبكة"
                }
            )
        }
    }

    fun notifyQuotaWarning(
        packageName: String,
        appLabel: String,
        usedBytes: Long,
        quotaBytes: Long,
        percent: Int
    ) {
        if (!canNotify() || quotaBytes <= 0L) return
        val safePercent = percent.coerceIn(0, 100)
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_flow)
            .setContentTitle("اقتراب حصة الشبكة")
            .setContentText("$appLabel استخدم ${safePercent}% من الحصة المحددة")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "استهلاك التطبيق: ${formatBytes(usedBytes)} من ${formatBytes(quotaBytes)} ($safePercent%)."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        manager.notify(notificationId(packageName, TYPE_WARNING), notification)
    }

    fun notifyQuotaReached(
        packageName: String,
        appLabel: String,
        quotaBytes: Long
    ) {
        if (!canNotify() || quotaBytes <= 0L) return
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_flow)
            .setContentTitle("تم بلوغ حصة الشبكة")
            .setContentText("تم إيقاف اتصال $appLabel بسبب بلوغ الحصة")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "بلغ التطبيق الحصة المحددة: ${formatBytes(quotaBytes)}."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        manager.notify(notificationId(packageName, TYPE_REACHED), notification)
    }

    fun cancelForPackage(packageName: String) {
        manager.cancel(notificationId(packageName, TYPE_WARNING))
        manager.cancel(notificationId(packageName, TYPE_REACHED))
    }

    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    private fun notificationId(packageName: String, type: Int): Int =
        20_000_000 + (packageName.hashCode() and 0x00FF_FFFF) * 2 + type

    private fun formatBytes(value: Long): String {
        val safe = value.coerceAtLeast(0L)
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var number = safe.toDouble()
        var index = 0
        while (number >= 1024.0 && index < units.lastIndex) {
            number /= 1024.0
            index++
        }
        return if (index == 0) "${safe} B" else "%.1f %s".format(number, units[index])
    }

    companion object {
        const val TYPE_WARNING = 1
        const val TYPE_REACHED = 2
        const val CHANNEL_ID = "dstwr_flow_policy_alerts"
    }
}
