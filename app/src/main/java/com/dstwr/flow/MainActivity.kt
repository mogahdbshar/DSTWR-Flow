package com.dstwr.flow

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallTopAppBar
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dstwr.flow.ui.theme.DSTWRFlowTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DSTWRFlowTheme {
                Dashboard(
                    usageAccessGranted = hasUsageAccess(),
                    onOpenUsageAccess = {
                        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }
                )
            }
        }
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@androidx.compose.runtime.Composable
private fun Dashboard(
    usageAccessGranted: Boolean,
    onOpenUsageAccess: () -> Unit
) {
    var monitoringEnabled by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = { Text("DSTWR Flow") },
                navigationIcon = {
                    Icon(Icons.Default.Shield, contentDescription = null)
                },
                actions = {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("لوحة التحكم", style = MaterialTheme.typography.headlineMedium)
            Text(
                "راقب استهلاك الإنترنت وتحكم في اتصال التطبيقات من جهازك.",
                style = MaterialTheme.typography.bodyLarge
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("حالة الحماية", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(if (monitoringEnabled) "المراقبة مفعلة" else "المراقبة متوقفة")
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { monitoringEnabled = !monitoringEnabled },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (monitoringEnabled) "إيقاف المراقبة" else "تشغيل المراقبة")
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("صلاحية إحصائيات الاستخدام", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(if (usageAccessGranted) "تم منح الصلاحية" else "تحتاج إلى منح الصلاحية من إعدادات النظام")
                    if (!usageAccessGranted) {
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = onOpenUsageAccess,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("فتح إعدادات الصلاحية")
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard("اليوم", "0 B", Modifier.weight(1f))
                StatCard("هذا الشهر", "0 B", Modifier.weight(1f))
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun StatCard(title: String, value: String, modifier: Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}
