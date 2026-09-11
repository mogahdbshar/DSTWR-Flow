package com.dstwr.flow.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun DiagnosticsSection(
    vpnPrepared: Boolean,
    usageAccessGranted: Boolean,
    protectionEnabled: Boolean,
    emergencyEnabled: Boolean
) {
    var dialog by remember { mutableStateOf<Dialog?>(null) }

    SettingsCard {
        Icon(Icons.Default.NetworkCheck, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text("التشخيص والمعلومات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("فحص سريع لحالة مكونات DSTWR Flow على هذا الجهاز.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(10.dp))
        DiagnosticRow("VPN", if (vpnPrepared) "جاهز" else "يحتاج موافقة")
        DiagnosticRow("Usage Access", if (usageAccessGranted) "مفعّل" else "غير مفعّل")
        DiagnosticRow("الحماية", if (protectionEnabled) "مفعّلة" else "متوقفة")
        DiagnosticRow("الطوارئ", if (emergencyEnabled) "مفعّلة" else "متوقفة")
        DiagnosticRow("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { dialog = Dialog.DIAGNOSTICS }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Tune, null)
                Text(" تفاصيل الفحص")
            }
            TextButton(onClick = { dialog = Dialog.ABOUT }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Info, null)
                Text(" حول التطبيق")
            }
        }
    }

    when (dialog) {
        Dialog.DIAGNOSTICS -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("تشخيص DSTWR Flow") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("VPN: ${if (vpnPrepared) "جاهز" else "يحتاج موافقة النظام"}")
                    Text("Usage Access: ${if (usageAccessGranted) "مفعّل" else "غير مفعّل"}")
                    Text("الحماية: ${if (protectionEnabled) "مفعّلة" else "متوقفة"}")
                    Text("قاطع الإنترنت: ${if (emergencyEnabled) "مفعّل" else "متوقف"}")
                    Text("الإصدار: 1.0.0")
                    Text("المعمارية الحالية: Kotlin + Jetpack Compose + Room + DataStore + VpnService")
                    Text("الحماية الحالية محلية ولا تعتمد على خادم VPN خارجي.")
                }
            },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("إغلاق") } }
        )
        Dialog.ABOUT -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("DSTWR Flow") },
            text = { Text("تطبيق محلي لمراقبة استهلاك البيانات وإدارة سياسات اتصال التطبيقات. لا تُرسل بيانات الاستخدام إلى خادم خارجي في البنية الحالية.") },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("إغلاق") } }
        )
        null -> Unit
    }
}

private enum class Dialog { DIAGNOSTICS, ABOUT }

@Composable
private fun DiagnosticRow(title: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}
