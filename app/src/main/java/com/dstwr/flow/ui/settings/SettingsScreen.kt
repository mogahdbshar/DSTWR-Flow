package com.dstwr.flow.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dstwr.flow.ui.theme.GlassCard

/** Settings surface kept free of Android permission implementation details. */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    state: FlowProtectionState,
    vpnPrepared: Boolean,
    usageAccessGranted: Boolean,
    onProtectionChange: (Boolean) -> Unit,
    onEmergencyChange: (Boolean) -> Unit,
    onNotificationsChange: (Boolean) -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenUsageAccess: () -> Unit,
    onRequestVpnConsent: () -> Unit,
    onDisableAll: () -> Unit
) {
    Column(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("الإعدادات", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("كل إعدادات DSTWR Flow تعمل محليًا على الجهاز.", style = MaterialTheme.typography.bodyMedium)

        SettingsCard {
            Icon(Icons.Default.Shield, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            SettingSwitchRow("الحماية الذكية", "تشغيل محرك التحكم المحلي", state.protectionEnabled, onProtectionChange)
            SettingSwitchRow("قاطع الإنترنت", "حظر الاتصال عند تفعيل الحماية", state.emergencyBlockEnabled, onEmergencyChange)
        }

        SettingsCard {
            Icon(Icons.Default.NotificationsNone, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            SettingSwitchRow("تنبيهات الحصص", "تنبيه عند الاقتراب من الحصة أو بلوغها", state.notificationsEnabled, onNotificationsChange)
            if (!state.notificationsEnabled) {
                OutlinedButton(onRequestNotifications, Modifier.fillMaxWidth()) {
                    Text("تفعيل إشعارات النظام")
                }
            }
        }

        SettingsCard {
            Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text("الصلاحيات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(if (vpnPrepared) "VPN: جاهز" else "VPN: يحتاج موافقة النظام", style = MaterialTheme.typography.bodyMedium)
            Text(if (usageAccessGranted) "Usage Access: مفعّل" else "Usage Access: غير مفعّل", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            if (!vpnPrepared) {
                OutlinedButton(onRequestVpnConsent, Modifier.fillMaxWidth()) { Text("منح صلاحية VPN") }
            }
            if (!usageAccessGranted) {
                OutlinedButton(onOpenUsageAccess, Modifier.fillMaxWidth()) { Text("فتح صلاحية إحصائيات الاستخدام") }
            }
        }

        SettingsCard {
            Icon(Icons.Default.Storage, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text("الخصوصية والبيانات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("DSTWR Flow لا يحتاج خادمًا خارجيًا لعمل الحماية الحالية. سياسات التطبيقات وإعداداتك محفوظة محليًا.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            Button(onDisableAll, Modifier.fillMaxWidth()) { Text("إيقاف كل الحماية") }
        }

        Text("DSTWR Flow 1.0.0", style = MaterialTheme.typography.labelMedium, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    GlassCard(content = content)
}

@Composable
private fun SettingSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
