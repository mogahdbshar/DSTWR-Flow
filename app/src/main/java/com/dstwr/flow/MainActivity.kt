package com.dstwr.flow

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dstwr.flow.ui.theme.DSTWRFlowTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DSTWRFlowTheme {
                FlowApp(
                    usageAccessGranted = hasUsageAccess(),
                    onOpenUsageAccess = { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                    onRequestVpn = { requestVpnPermission() }
                )
            }
        }
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        ) == AppOpsManager.MODE_ALLOWED
    }

    private fun requestVpnPermission() {
        VpnService.prepare(this)?.let { startActivityForResult(it, VPN_REQUEST_CODE) }
    }

    companion object { private const val VPN_REQUEST_CODE = 1001 }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FlowApp(
    usageAccessGranted: Boolean,
    onOpenUsageAccess: () -> Unit,
    onRequestVpn: () -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    var protection by remember { mutableStateOf(false) }
    var emergencyBlock by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { FlowTopBar() },
        bottomBar = {
            NavigationBar(Modifier.navigationBarsPadding()) {
                val items = listOf(
                    Triple("الرئيسية", Icons.Default.NetworkCheck, 0),
                    Triple("التطبيقات", Icons.Default.Apps, 1),
                    Triple("الإحصائيات", Icons.Default.Analytics, 2),
                    Triple("المزيد", Icons.Default.MoreHoriz, 3)
                )
                items.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item.third,
                        onClick = { tab = item.third },
                        icon = { Icon(item.second, contentDescription = item.first) },
                        label = { Text(item.first) }
                    )
                }
            }
        }
    ) { padding ->
        when (tab) {
            0 -> Dashboard(
                modifier = Modifier.padding(padding),
                protection = protection,
                emergency = emergencyBlock,
                usageGranted = usageAccessGranted,
                onProtection = { protection = it; if (it) onRequestVpn() },
                onEmergency = { emergencyBlock = it },
                onUsage = onOpenUsageAccess
            )
            1 -> Section(Modifier.padding(padding), "التطبيقات", "ستُدار هنا سياسات كل تطبيق: السماح، الحظر، الحصة، السرعة والجدولة.")
            2 -> Section(Modifier.padding(padding), "الإحصائيات", "ستظهر هنا بيانات NetworkStatsManager وسجل Room اليومي والأسبوعي والشهري.")
            else -> Section(Modifier.padding(padding), "المزيد", "الإعدادات واللغة والإشعارات والخصوصية والأذونات ومعلومات التطبيق.")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FlowTopBar() {
    TopAppBar(
        title = {
            Column {
                Text("DSTWR Flow", fontWeight = FontWeight.Bold)
                Text("تحكم ذكي في اتصال جهازك", style = MaterialTheme.typography.labelSmall)
            }
        },
        navigationIcon = {
            Surface(
                modifier = Modifier.padding(start = 12.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primary.copy(alpha = .12f)
            ) {
                Icon(
                    Icons.Default.Shield,
                    contentDescription = null,
                    modifier = Modifier.padding(9.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        actions = {
            IconButton(onClick = {}) { Icon(Icons.Default.NotificationsNone, contentDescription = "الإشعارات") }
            IconButton(onClick = {}) { Icon(Icons.Default.Settings, contentDescription = "الإعدادات") }
        }
    )
}

@Composable
private fun Dashboard(
    modifier: Modifier,
    protection: Boolean,
    emergency: Boolean,
    usageGranted: Boolean,
    onProtection: (Boolean) -> Unit,
    onEmergency: (Boolean) -> Unit,
    onUsage: () -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
    ) {
        item {
            GlassCard(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = .10f)) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = .16f)
                        ) {
                            Icon(
                                Icons.Default.Shield,
                                contentDescription = null,
                                modifier = Modifier.padding(12.dp).size(28.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("الحماية الذكية", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(if (protection) "تم طلب تفعيل التحكم المحلي" else "التحكم متوقف حاليًا")
                        }
                        Switch(checked = protection, onCheckedChange = onProtection)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "محرك التحكم سيعمل محليًا عبر VpnService بعد موافقة المستخدم، دون خادم VPN خارجي.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("اليوم", "0 B", Modifier.weight(1f))
                Metric("هذا الشهر", "0 B", Modifier.weight(1f))
            }
        }
        item {
            GlassCard {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Block,
                        contentDescription = null,
                        tint = if (emergency) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("قاطع الإنترنت", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("واجهة التحكم الطارئ، ولن تُنفذ الحظر قبل اكتمال محرك التوجيه.", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = emergency, onCheckedChange = onEmergency)
                }
            }
        }
        item {
            GlassCard {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DataUsage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("إحصائيات الاستخدام", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                if (usageGranted) "الصلاحية متاحة" else "مطلوبة لقراءة استهلاك التطبيقات",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    if (!usageGranted) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = onUsage, modifier = Modifier.fillMaxWidth()) {
                            Text("فتح إعدادات الصلاحية")
                        }
                    }
                }
            }
        }
        item { Text("أدوات التحكم", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Action("التطبيقات", "إدارة الاتصال", Icons.Default.Apps, Modifier.weight(1f))
                Action("السرعة", "حدود التحميل", Icons.Default.Speed, Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Action("الحصص", "حدود البيانات", Icons.Default.DataUsage, Modifier.weight(1f))
                Action("القواعد", "جدولة ذكية", Icons.Default.Block, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Metric(title: String, value: String, modifier: Modifier) {
    GlassCard(modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("استهلاك الشبكة", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun Action(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier
) {
    GlassCard(modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun Section(modifier: Modifier, title: String, description: String) {
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        GlassCard {
            Column(Modifier.padding(20.dp)) {
                Text(description, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("سيتم تفعيل هذه الوحدة مع المحرك")
                }
            }
        }
    }
}

@Composable
private fun GlassCard(
    containerColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f),
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    androidx.compose.material3.Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
        ),
        shape = MaterialTheme.shapes.large,
        content = content
    )
}
