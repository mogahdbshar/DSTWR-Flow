package com.dstwr.flow.data.apps

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo

 data class InstalledApp(
    val packageName: String,
    val label: String,
    val uid: Int,
    val systemApp: Boolean
)

class AppInventoryRepository(private val context: Context) {
    fun getLaunchableApps(): List<InstalledApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { info ->
                val app = info.activityInfo.applicationInfo
                InstalledApp(app.packageName, app.loadLabel(pm).toString(), app.uid, (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
}
