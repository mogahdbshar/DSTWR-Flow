package com.dstwr.flow.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dstwr.flow.domain.util.DataFormatter

@Composable
fun AppPolicyCard(
    row: AppRow,
    onBlockedChange: (Boolean) -> Unit,
    onOpenDetails: () -> Unit
) {
    val hasSpeedLimit = row.policy.downloadLimitBytesPerSecond > 0L || row.policy.uploadLimitBytesPerSecond > 0L
    val hasQuota = row.policy.dailyQuotaBytes > 0L || row.policy.monthlyQuotaBytes > 0L
    val hasSchedule = row.policy.scheduleEnabled

    GlassCardForApps {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                Modifier.size(46.dp),
                MaterialTheme.shapes.medium,
                if (row.blocked) MaterialTheme.colorScheme.error.copy(alpha = .12f)
                else MaterialTheme.colorScheme.primary.copy(alpha = .10f)
            ) {
                Icon(
                    if (row.blocked) Icons.Default.Block else Icons.Default.Apps,
                    null,
                    Modifier.padding(11.dp),
                    if (row.blocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(row.app.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (row.blocked) "محظور" else if (row.app.systemApp) "تطبيق نظام" else "تطبيق مستخدم",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (row.blocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(row.app.packageName, style = MaterialTheme.typography.labelSmall)
                Text(
                    "استخدام اليوم: ${DataFormatter.bytes(row.usage.totalBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "Wi-Fi: ${DataFormatter.bytes(row.usage.wifiBytes)}  •  جوال: ${DataFormatter.bytes(row.usage.mobileBytes)}",
                    style = MaterialTheme.typography.labelSmall
                )

                if (hasSpeedLimit || hasQuota || hasSchedule) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (hasSpeedLimit) {
                            Icon(Icons.Default.Speed, null, Modifier.size(15.dp), MaterialTheme.colorScheme.primary)
                            Text("سرعة", style = MaterialTheme.typography.labelSmall)
                        }
                        if (hasQuota) {
                            Icon(Icons.Default.Storage, null, Modifier.size(15.dp), MaterialTheme.colorScheme.primary)
                            Text("حصة", style = MaterialTheme.typography.labelSmall)
                        }
                        if (hasSchedule) {
                            Icon(Icons.Default.Schedule, null, Modifier.size(15.dp), MaterialTheme.colorScheme.primary)
                            Text("مجدول", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                TextButton(onClick = onOpenDetails) {
                    Text("تفاصيل وإعدادات")
                }
            }
            Switch(checked = row.blocked, onCheckedChange = onBlockedChange)
        }
    }
}

@Composable
private fun GlassCardForApps(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    androidx.compose.material3.Card(
        colors = androidx.compose.material3.CardDefaults.cardColors(
            MaterialTheme.colorScheme.surface.copy(alpha = .84f)
        ),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(0.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = .12f)
        ),
        shape = MaterialTheme.shapes.large,
        content = content
    )
}
