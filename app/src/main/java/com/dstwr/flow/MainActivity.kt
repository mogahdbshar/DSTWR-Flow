package com.dstwr.flow

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.draw.clip
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
@Composable
private fun FlowApp(
    usageAccessGranted: Boolean,
    onOpenUsageAccess: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var protectionEnabled by remember { mutableStateOf(false) }
    var emergencyBlock by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("DSTWR Flow", fontWeight = FontWeight.Bold)
                        Text(
                            "تحكم ذكي في اتصال جهازك",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                navigationIcon = {
                    Surface(
                        modifier = Modifier.padding(start = 12.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
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
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.NotificationsNone, contentDescription = "الإشعارات")
                    }
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Settings, contentDescription = "الإعدادات")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.navigationBarsPadding()
            ) {
                val items = listOf(
                    Triple("الرئيسية", Icons.Default.NetworkCheck, "الرئيسية"),
                    Triple("التطبيقات", Icons.Default.Apps, "التطبيقات"),
                    Triple("الإحصائيات", Icons.Default.Analytics, "الإحصائيات"),
                    Triple("المزيد", Icons.Default.MoreHoriz, "المزيد")
                )
                items.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(item.second, contentDescription = item.first) },
                        label = { Text(item.third) }
                    )
                }
            }
        }
    ) { padding ->
        when (selectedTab) {
            0 -> DashboardContent(
                modifier = Modifier.padding(padding),
                protectionEnabled = protectionEnabled,
                emergencyBlock = emergencyBlock,
                usageAccessGranted = usageAccessGranted,
                onProtectionChange = { protectionEnabled = it },
                onEmergencyBlockChange = { emergencyBlock = it },
                onOpenUsageAccess = onOpenUsageAccess
            )
            1 -> AppsPlaceholder(Modifier.padding(padding))
            2 -> StatsPlaceholder(Modifier.padding(padding))
            else -> MorePlaceholder(Modifier.padding(padding))
        }
    }
}

@Composable
private fun DashboardContent(
    modifier: Modifier,
    protectionEnabled: Boolean,
    emergencyBlock: Boolean,
    usageAccessGranted: Boolean,
    onProtectionChange: (Boolean) -> Unit,
    onEmergencyBlockChange: (Boolean) -> Unit,
    onOpenUsageAccess: () -> Unit
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 12.dp, bottom = 24.dp)
    ) {
        item {
            HeroCard(protectionEnabled, onProtectionChange)
        }
        item {
            TrafficOverview()
        }
        item {
            QuickControlCard(emergencyBlock, onEmergencyBlockChange)
        }
        item {
            PermissionCard(usageAccessGranted, onOpenUsageAccess)
        }
        item {
            SectionTitle("أدوات التحكم")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ActionCard("التطبيقات", "إدارة الاتصال", Icons.Default.Apps, Modifier.weight(1f))
                ActionCard("السرعة", "حدود التحميل", Icons.Default.Speed, Modifier.weight(1f))
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ActionCard("الحصص", "حدود البيانات", Icons.Default.DataUsage, Modifier.weight(1f))
                ActionCard("القواعد", "جدولة ذكية", Icons.Default.Block, Modifier.weight(1f))
            }
        }
        item {
            SectionTitle("نشاط الشبكة")
            GlassCard {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("لا توجد بيانات كافية بعد", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "فعّل صلاحية إحصائيات الاستخدام لبدء بناء سجل دقيق لاستهلاك التطبيقات.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroCard(enabled: Boolean, onChange: (Boolean) -> Unit) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                ) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(12.dp).size(28.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("الحماية الذكية", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        if (enabled) "DSTWR Flow يراقب قواعد الشبكة" else "التحكم متوقف حاليًا",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Switch(checked = enabled, onCheckedChange = onChange)
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(if (enabled) "الحماية مفعلة" else "جاهز للتفعيل", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun TrafficOverview() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MetricCard("اليوم", "0 B", "استهلاك", Modifier.weight(1f))
        MetricCard("الشهر", "0 B", "إجمالي", Modifier.weight(1f))
    }
}

@Composable
private fun MetricCard(title: String, value: String, caption: String, modifier: Modifier) {
    GlassCard(modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(caption, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun QuickControlCard(blocked: Boolean, onChange: (Boolean) -> Unit) {
    GlassCard {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (blocked) MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
            ) {
                Icon(
                    Icons.Default.Block,
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp),
                    tint = if (blocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("قاطع الإنترنت", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("تحكم طارئ في اتصال التطبيقات", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = blocked, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun PermissionCard(granted: Boolean, onOpen: () -> Unit) {
    GlassCard {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DataUsage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("إحصائيات الاستخدام", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (granted) "الصلاحية متاحة" else "مطلوبة لقراءة استهلاك التطبيقات",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (!granted) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
                    Text("فتح إعدادات الصلاحية")
                }
            }
        }
    }
}

@Composable
private fun ActionCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    GlassCard(modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun GlassCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
    content: @Composable () -> Unit
) {
    androidx.compose.material3.Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
        ),
        shape = MaterialTheme.shapes.large,
        content = content
    )
}

@Composable
private fun AppsPlaceholder(modifier: Modifier) {
    EmptySection(modifier, "التطبيقات", "هنا ستكون إدارة كل تطبيق: الحظر، السرعة، الحصص، الجداول والإحصائيات.")
}

@Composable
private fun StatsPlaceholder(modifier: Modifier) {
    EmptySection(modifier, "الإحصائيات", "هنا ستظهر الرسوم اليومية والأسبوعية والشهرية مع فصل Wi-Fi عن بيانات الهاتف.")
}

@Composable
private fun MorePlaceholder(modifier: Modifier) {
    EmptySection(modifier, "المزيد", "الإعدادات، اللغة، الإشعارات، الأذونات، معلومات التطبيق وخيارات الخصوصية.")
}

@Composable
private fun EmptySection(modifier: Modifier, title: String, description: String) {
    Column(
        modifier = modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        GlassCard {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(description, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(14.dp))
                Button(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                    Text("قريبًا في هذه المرحلة")
                }
            }
        }
    }
}
