package com.dstwr.flow.ui.apps

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
    GlassCardForApps {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
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
            Column(Modifier.weight(1f)) {
                androidx.compose.material3.Text(row.app.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                androidx.compose.material3.Text(
                    if (row.app.systemApp) "تطبيق نظام" else "تطبيق مستخدم",
                    style = MaterialTheme.typography.labelSmall
                )
                androidx.compose.material3.Text(row.app.packageName, style = MaterialTheme.typography.labelSmall)
                androidx.compose.material3.Text(
                    "اليوم: ${DataFormatter.bytes(row.usage.totalBytes)} | Wi-Fi: ${DataFormatter.bytes(row.usage.wifiBytes)} | جوال: ${DataFormatter.bytes(row.usage.mobileBytes)}",
                    style = MaterialTheme.typography.labelSmall
                )
                androidx.compose.material3.TextButton(onClick = onOpenDetails) {
                    androidx.compose.material3.Text("تفاصيل وإعدادات")
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
