package com.dstwr.flow

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.runtime.collectAsState
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.dstwr.flow.domain.util.DataFormatter
import com.dstwr.flow.ui.apps.AppPolicyCard
import com.dstwr.flow.ui.apps.AppPolicyEditor
import com.dstwr.flow.ui.apps.AppRow
import com.dstwr.flow.ui.apps.AppsViewModel
import com.dstwr.flow.ui.settings.FlowProtectionState
import com.dstwr.flow.ui.settings.FlowSettingsViewModel
import com.dstwr.flow.ui.settings.SettingsScreen
import com.dstwr.flow.ui.stats.AppUsageRow
import com.dstwr.flow.ui.stats.UsageHistoryPoint
import com.dstwr.flow.ui.stats.UsageSummary
import com.dstwr.flow.ui.stats.UsageViewModel
import com.dstwr.flow.ui.theme.DSTWRFlowTheme
import com.dstwr.flow.vpn.FlowProtectionController
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val appsViewModel: AppsViewModel by viewModels()
    private val usageViewModel: UsageViewModel by viewModels()
    private val settingsViewModel: FlowSettingsViewModel by viewModels()
    private lateinit var protectionController: FlowProtectionController

    private val vpnConsentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            lifecycleScope.launch { protectionController.enableProtection() }
        } else {
            settingsViewModel.setProtectionEnabled(false)
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) settingsViewModel.setNotificationsEnabled(true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        protectionController = FlowProtectionController(applicationContext)
        setContent {
            DSTWRFlowTheme {
                val protectionState by settingsViewModel.state.collectAsState()
                FlowApp(
                    hasUsageAccess = hasUsageAccess(),
                    protectionState = protectionState,
                    vpnPrepared = protectionController.isPrepared(),
                    onOpenUsageAccess = { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                    onProtectionChange = ::setProtection,
                    onEmergencyChange = ::setEmergency,
                    onNotificationsChange = ::setNotifications,
                    onRequestNotifications = ::requestNotificationPermission,
                    onRequestVpnConsent = ::requestVpnConsent,
                    onDisableAll = { lifecycleScope.launch { protectionController.disableProtection() } },
                    appsViewModel = appsViewModel,
                    usageViewModel = usageViewModel
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        appsViewModel.refresh()
        usageViewModel.refresh()
        if (::protectionController.isInitialized) {
            lifecycleScope.launch { protectionController.reapply() }
        }
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        return appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        ) == AppOpsManager.MODE_ALLOWED
    }

    private fun setProtection(enabled: Boolean) {
        lifecycleScope.launch {
            if (!enabled) {
                protectionController.disableProtection()
                return@launch
            }
            requestVpnConsent()
        }
    }

    private fun requestVpnConsent() {
        if (protectionController.isPrepared()) {
            lifecycleScope.launch { protectionController.enableProtection() }
        } else {
            val intent = VpnService.prepare(this@MainActivity)
            if (intent != null) vpnConsentLauncher.launch(intent)
            else lifecycleScope.launch { protectionController.enableProtection() }
        }
    }

    private fun setEmergency(enabled: Boolean) {
        lifecycleScope.launch { protectionController.setEmergencyBlock(enabled) }
    }

    private fun setNotifications(enabled: Boolean) {
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            settingsViewModel.setNotificationsEnabled(enabled)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            settingsViewModel.setNotificationsEnabled(true)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FlowApp(
    hasUsageAccess: Boolean,
    protectionState: FlowProtectionState,
    vpnPrepared: Boolean,
    onOpenUsageAccess: () -> Unit,
    onProtectionChange: (Boolean) -> Unit,
    onEmergencyChange: (Boolean) -> Unit,
    onNotificationsChange: (Boolean) -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestVpnConsent: () -> Unit,
    onDisableAll: () -> Unit,
    appsViewModel: AppsViewModel,
    usageViewModel: UsageViewModel
) {
    var tab by remember { mutableIntStateOf(0) }
    Scaffold(
        topBar = { FlowTopBar(onSettings = { tab = 3 }, onNotifications = { tab = 3 }) },
        bottomBar = {
            NavigationBar(Modifier.navigationBarsPadding()) {
                listOf(
                    Triple("الرئيسية", Icons.Default.NetworkCheck, 0),
                    Triple("التطبيقات", Icons.Default.Apps, 1),
                    Triple("الإحصائيات", Icons.Default.Analytics, 2),
                    Triple("المزيد", Icons.Default.MoreHoriz, 3)
                ).forEach { item ->
                    NavigationBarItem(
                        selected = tab == item.third,
                        onClick = { tab = item.third },
                        icon = { Icon(item.second, item.first) },
                        label = { Text(item.first) }
                    )
                }
            }
        }
    ) { padding ->
        when (tab) {
            0 -> Dashboard(
                Modifier.padding(padding), protectionState, hasUsageAccess,
                usageViewModel.today.collectAsState().value,
                usageViewModel.month.collectAsState().value,
                onProtectionChange, onEmergencyChange, onOpenUsageAccess
            )
            1 -> AppsScreen(Modifier.padding(padding), appsViewModel)
            2 -> StatsScreen(Modifier.padding(padding), hasUsageAccess, usageViewModel, onOpenUsageAccess)
            else -> SettingsScreen(
                modifier = Modifier.padding(padding),
                state = protectionState,
                vpnPrepared = vpnPrepared,
                usageAccessGranted = hasUsageAccess,
                onProtectionChange = onProtectionChange,
                onEmergencyChange = onEmergencyChange,
                onNotificationsChange = onNotificationsChange,
                onRequestNotifications = onRequestNotifications,
                onOpenUsageAccess = onOpenUsageAccess,
                onRequestVpnConsent = onRequestVpnConsent,
                onDisableAll = onDisableAll
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FlowTopBar(onSettings: () -> Unit, onNotifications: () -> Unit) {
    TopAppBar(
        title = {
            Column {
                Text("DSTWR Flow", fontWeight = FontWeight.Bold)
                Text("تحكم ذكي في اتصال جهازك", style = MaterialTheme.typography.labelSmall)
            }
        },
        navigationIcon = {
            Surface(
                Modifier.padding(start = 12.dp), MaterialTheme.shapes.medium,
                MaterialTheme.colorScheme.primary.copy(alpha = .12f)
            ) {
                Icon(Icons.Default.Shield, null, Modifier.padding(9.dp), MaterialTheme.colorScheme.primary)
            }
        },
        actions = {
            IconButton(onClick = onNotifications) { Icon(Icons.Default.NotificationsNone, "الإشعارات") }
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "الإعدادات") }
        }
    )
}

@Composable
private fun AppsScreen(modifier: Modifier, viewModel: AppsViewModel) {
    val apps by viewModel.apps.collectAsState()
    val loading by viewModel.loading.collectAsState()
    var selectedApp by remember { mutableStateOf<AppRow?>(null) }

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
        ) {
            item {
                GlassCard(MaterialTheme.colorScheme.primary.copy(alpha = .10f)) {
                    Column(Modifier.padding(18.dp)) {
                        Text("تطبيقات الجهاز", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text("اضغط على أي تطبيق لفتح إعدادات الحظر والحصص والجدولة وسرعات الشبكة.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (loading) item { GlassCard { Text("جارٍ تحديث التطبيقات...", Modifier.padding(18.dp)) } }
            items(apps, key = { it.app.packageName }) { row ->
                AppPolicyCard(
                    row = row,
                    onBlockedChange = { viewModel.setBlocked(row.app.packageName, it) },
                    onOpenDetails = { selectedApp = row }
                )
            }
        }

        selectedApp?.let { row ->
            AppPolicyEditor(
                row = row,
                onDismiss = { selectedApp = null },
                onBlockedChange = { viewModel.setBlocked(row.app.packageName, it) },
                onSpeedLimitsChange = { download, upload -> viewModel.setSpeedLimits(row.app.packageName, download, upload) },
                onQuotasChange = { daily, monthly -> viewModel.setQuotas(row.app.packageName, daily, monthly) },
                onScheduleChange = { enabled, start, end -> viewModel.setSchedule(row.app.packageName, enabled, start, end) },
                onNetworkScopeChange = { scope -> viewModel.setNetworkScope(row.app.packageName, scope) }
            )
        }
    }
}

@Composable
private fun Dashboard(
    modifier: Modifier,
    protectionState: FlowProtectionState,
    usageGranted: Boolean,
    today: UsageSummary,
    month: UsageSummary,
    onProtection: (Boolean) -> Unit,
    onEmergency: (Boolean) -> Unit,
    onUsage: () -> Unit
) {
    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
    ) {
        item {
            GlassCard(MaterialTheme.colorScheme.primary.copy(alpha = .10f)) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shield, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("الحماية الذكية", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(if (protectionState.protectionEnabled) "التحكم المحلي مفعّل" else "التحكم متوقف حاليًا")
                        }
                        Switch(protectionState.protectionEnabled, onProtection)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("تستخدم الحماية VpnService محليًا على الجهاز. لا يوجد خادم VPN خارجي.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp)) {
                Metric("اليوم", DataFormatter.bytes(today.total.totalBytes), Modifier.weight(1f))
                Metric("هذا الشهر", DataFormatter.bytes(month.total.totalBytes), Modifier.weight(1f))
            }
        }
        item {
            GlassCard {
                Column(Modifier.padding(18.dp)) {
                    Text("توزيع اليوم", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Wi-Fi: ${DataFormatter.bytes(today.wifi)}")
                    Text("بيانات الجوال: ${DataFormatter.bytes(today.mobile)}")
                    Text("التطبيقات النشطة: ${today.appCount}")
                }
            }
        }
        item {
            GlassCard {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Block, null, tint = if (protectionState.emergencyBlockEnabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("قاطع الإنترنت", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("يحظر اتصال التطبيقات عبر نفق VPN محلي عند تفعيل الحماية.", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(protectionState.emergencyBlockEnabled, onEmergency)
                }
            }
        }
        item {
            GlassCard {
                Column(Modifier.padding(18.dp)) {
                    Text("إحصائيات الاستخدام", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(if (usageGranted) "تمت قراءة بيانات الجهاز" else "مطلوبة لقراءة استهلاك التطبيقات", style = MaterialTheme.typography.bodySmall)
                    if (!usageGranted) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onUsage, Modifier.fillMaxWidth()) { Text("فتح إعدادات الصلاحية") }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsScreen(modifier: Modifier, usageGranted: Boolean, viewModel: UsageViewModel, onUsage: () -> Unit) {
    val today by viewModel.today.collectAsState()
    val week by viewModel.week.collectAsState()
    val month by viewModel.month.collectAsState()
    val history by viewModel.history.collectAsState()
    val loading by viewModel.loading.collectAsState()
    var period by remember { mutableIntStateOf(0) }
    val selected = when (period) { 0 -> today; 1 -> week; else -> month }

    LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)) {
        item {
            GlassCard(MaterialTheme.colorScheme.primary.copy(alpha = .10f)) {
                Column(Modifier.padding(20.dp)) {
                    Text("الإحصائيات", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("بيانات مقروءة من عدادات Android الرسمية", style = MaterialTheme.typography.bodySmall)
                    if (!usageGranted) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onUsage, Modifier.fillMaxWidth()) { Text("منح صلاحية Usage Access") }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                listOf("اليوم", "الأسبوع", "الشهر").forEachIndexed { index, title ->
                    OutlinedButton({ period = index }, Modifier.weight(1f)) { Text(if (period == index) "• $title" else title) }
                }
            }
        }
        item { PeriodCard(if (period == 0) "اليوم" else if (period == 1) "هذا الأسبوع" else "هذا الشهر", selected) }
        item { HistoryCard(history) }
        item {
            GlassCard {
                Column(Modifier.padding(18.dp)) {
                    Text("أكثر التطبيقات استهلاكًا", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    if (selected.topApps.isEmpty()) Text("لا توجد بيانات استهلاك متاحة بعد.") else selected.topApps.forEach { TopAppRow(it) }
                }
            }
        }
        item { if (loading) Text("جارٍ تحديث الإحصائيات...") }
        item { OutlinedButton(viewModel::refresh, Modifier.fillMaxWidth()) { Text("تحديث البيانات") } }
    }
}

@Composable
private fun HistoryCard(points: List<UsageHistoryPoint>) {
    GlassCard {
        Column(Modifier.padding(18.dp)) {
            Text("سجل آخر القراءات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            if (points.isEmpty()) Text("سيظهر الرسم بعد تسجيل أول قراءات الاستخدام.", style = MaterialTheme.typography.bodySmall)
            else {
                val max = points.maxOfOrNull { it.totalBytes }?.coerceAtLeast(1L) ?: 1L
                Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
                    points.takeLast(24).forEach { point ->
                        val fraction = (point.totalBytes.toDouble() / max).toFloat().coerceIn(.03f, 1f)
                        Box(Modifier.weight(1f).fillMaxHeight(fraction).background(MaterialTheme.colorScheme.primary.copy(alpha = .65f)))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text("الأقدم", style = MaterialTheme.typography.labelSmall); Text("الأحدث", style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

@Composable
private fun PeriodCard(title: String, summary: UsageSummary) {
    GlassCard {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(DataFormatter.bytes(summary.total.totalBytes), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Wi-Fi: ${DataFormatter.bytes(summary.wifi)}")
            Text("بيانات الجوال: ${DataFormatter.bytes(summary.mobile)}")
            Text("التطبيقات ذات الاستهلاك: ${summary.appCount}")
        }
    }
}

@Composable
private fun TopAppRow(row: AppUsageRow) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(Modifier.size(38.dp), MaterialTheme.shapes.medium, MaterialTheme.colorScheme.primary.copy(alpha = .10f)) {
            Icon(Icons.Default.Apps, null, Modifier.padding(9.dp), MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(row.app.label, fontWeight = FontWeight.SemiBold)
            Text(row.app.packageName, style = MaterialTheme.typography.labelSmall)
        }
        Text(DataFormatter.bytes(row.usage.totalBytes), fontWeight = FontWeight.Bold)
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
private fun GlassCard(
    containerColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = .84f),
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    androidx.compose.material3.Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor),
        elevation = CardDefaults.cardElevation(0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .12f)),
        shape = MaterialTheme.shapes.large,
        content = content
    )
}
