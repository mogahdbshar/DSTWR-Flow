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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.dstwr.flow.domain.util.DataFormatter
import com.dstwr.flow.ui.apps.*
import com.dstwr.flow.ui.settings.*
import com.dstwr.flow.ui.stats.*
import com.dstwr.flow.ui.theme.DSTWRFlowTheme
import com.dstwr.flow.vpn.FlowProtectionController
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val appsViewModel: AppsViewModel by viewModels()
    private val usageViewModel: UsageViewModel by viewModels()
    private val settingsViewModel: FlowSettingsViewModel by viewModels()
    private lateinit var protectionController: FlowProtectionController
    private var usageAccessState by mutableStateOf(false)
    private var vpnPreparedState by mutableStateOf(false)

    private val vpnConsentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) lifecycleScope.launch { protectionController.enableProtection() }
        else settingsViewModel.setProtectionEnabled(false)
        refreshPermissionState()
    }

    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) settingsViewModel.setNotificationsEnabled(true)
        refreshPermissionState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        protectionController = FlowProtectionController(applicationContext)
        refreshPermissionState()
        setContent {
            DSTWRFlowTheme {
                val protectionState by settingsViewModel.state.collectAsState()
                FlowApp(
                    hasUsageAccess = usageAccessState,
                    protectionState = protectionState,
                    vpnPrepared = vpnPreparedState,
                    onOpenUsageAccess = { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                    onProtectionChange = ::setProtection,
                    onEmergencyChange = ::setEmergency,
                    onNotificationsChange = ::setNotifications,
                    onRequestNotifications = ::requestNotificationPermission,
                    onRequestVpnConsent = ::requestVpnConsent,
                    onDisableAll = { lifecycleScope.launch { protectionController.disableProtection(); refreshPermissionState() } },
                    appsViewModel = appsViewModel,
                    usageViewModel = usageViewModel
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionState()
        appsViewModel.refresh()
        usageViewModel.refresh()
        if (::protectionController.isInitialized) lifecycleScope.launch { protectionController.reapply() }
    }

    private fun refreshPermissionState() {
        usageAccessState = hasUsageAccess()
        vpnPreparedState = if (::protectionController.isInitialized) protectionController.isPrepared() else VpnService.prepare(this) == null
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        return appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName) == AppOpsManager.MODE_ALLOWED
    }

    private fun setProtection(enabled: Boolean) {
        lifecycleScope.launch {
            if (!enabled) protectionController.disableProtection() else requestVpnConsent()
            refreshPermissionState()
        }
    }

    private fun requestVpnConsent() {
        if (protectionController.isPrepared()) lifecycleScope.launch { protectionController.enableProtection(); refreshPermissionState() }
        else {
            val intent = VpnService.prepare(this@MainActivity)
            if (intent != null) vpnConsentLauncher.launch(intent)
            else lifecycleScope.launch { protectionController.enableProtection(); refreshPermissionState() }
        }
    }

    private fun setEmergency(enabled: Boolean) { lifecycleScope.launch { protectionController.setEmergencyBlock(enabled); refreshPermissionState() } }

    private fun setNotifications(enabled: Boolean) {
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else settingsViewModel.setNotificationsEnabled(enabled)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        else settingsViewModel.setNotificationsEnabled(true)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FlowApp(hasUsageAccess: Boolean, protectionState: FlowProtectionState, vpnPrepared: Boolean, onOpenUsageAccess: () -> Unit, onProtectionChange: (Boolean) -> Unit, onEmergencyChange: (Boolean) -> Unit, onNotificationsChange: (Boolean) -> Unit, onRequestNotifications: () -> Unit, onRequestVpnConsent: () -> Unit, onDisableAll: () -> Unit, appsViewModel: AppsViewModel, usageViewModel: UsageViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    Scaffold(topBar = { FlowTopBar(onSettings = { tab = 3 }, onNotifications = { tab = 3 }) }, bottomBar = { NavigationBar(Modifier.navigationBarsPadding()) { listOf(Triple("الرئيسية", Icons.Default.NetworkCheck, 0), Triple("التطبيقات", Icons.Default.Apps, 1), Triple("الإحصائيات", Icons.Default.Analytics, 2), Triple("المزيد", Icons.Default.MoreHoriz, 3)).forEach { item -> NavigationBarItem(selected = tab == item.third, onClick = { tab = item.third }, icon = { Icon(item.second, item.first) }, label = { Text(item.first) }) } } }) { padding ->
        when (tab) {
            0 -> Dashboard(Modifier.padding(padding), protectionState, hasUsageAccess, usageViewModel.today.collectAsState().value, usageViewModel.month.collectAsState().value, onProtectionChange, onEmergencyChange, onOpenUsageAccess)
            1 -> AppsScreen(Modifier.padding(padding), appsViewModel)
            2 -> StatsScreen(Modifier.padding(padding), hasUsageAccess, usageViewModel, onOpenUsageAccess)
            else -> SettingsScreen(Modifier.padding(padding), protectionState, vpnPrepared, hasUsageAccess, onProtectionChange, onEmergencyChange, onNotificationsChange, onRequestNotifications, onOpenUsageAccess, onRequestVpnConsent, onDisableAll)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FlowTopBar(onSettings: () -> Unit, onNotifications: () -> Unit) {
    TopAppBar(title = { Column { Text("DSTWR Flow", fontWeight = FontWeight.Bold); Text("تحكم ذكي في اتصال جهازك", style = MaterialTheme.typography.labelSmall) } }, navigationIcon = { Surface(Modifier.padding(start = 12.dp), MaterialTheme.shapes.medium, MaterialTheme.colorScheme.primary.copy(alpha = .12f)) { Icon(Icons.Default.Shield, null, Modifier.padding(9.dp), MaterialTheme.colorScheme.primary) } }, actions = { IconButton(onClick = onNotifications) { Icon(Icons.Default.NotificationsNone, "الإشعارات") }; IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "الإعدادات") } })
}

@Composable
private fun AppsScreen(modifier: Modifier, viewModel: AppsViewModel) {
    val apps by viewModel.visibleApps.collectAsState(); val allApps by viewModel.apps.collectAsState(); val loading by viewModel.loading.collectAsState(); val query by viewModel.searchQuery.collectAsState(); val filterMode by viewModel.filterMode.collectAsState(); var selectedApp by remember { mutableStateOf<AppRow?>(null) }
    Box(modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)) {
            item { GlassCard(MaterialTheme.colorScheme.primary.copy(alpha = .10f)) { Column(Modifier.padding(18.dp)) { Text("تطبيقات الجهاز", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Spacer(Modifier.height(6.dp)); Text("ابحث عن تطبيق وحدد القواعد التي تريد تطبيقها عليه.", style = MaterialTheme.typography.bodySmall) } } }
            item { OutlinedTextField(value = query, onValueChange = viewModel::setSearchQuery, modifier = Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { viewModel.setSearchQuery("") }) { Icon(Icons.Default.Clear, "مسح البحث") } }, label = { Text("البحث") }, placeholder = { Text("اسم التطبيق أو اسم الحزمة") }) }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(selected = filterMode == AppFilterMode.ALL, onClick = { viewModel.setFilterMode(AppFilterMode.ALL) }, label = { Text("الكل") }); FilterChip(selected = filterMode == AppFilterMode.BLOCKED, onClick = { viewModel.setFilterMode(AppFilterMode.BLOCKED) }, label = { Text("المحظورة") }); FilterChip(selected = filterMode == AppFilterMode.CONFIGURED, onClick = { viewModel.setFilterMode(AppFilterMode.CONFIGURED) }, label = { Text("المضبوطة") }) } }
            item { Text(if (query.isBlank() && filterMode == AppFilterMode.ALL) "${allApps.size} تطبيق" else "${apps.size} من ${allApps.size} تطبيق", style = MaterialTheme.typography.labelMedium) }
            if (loading) item { GlassCard { Text("جارٍ تحديث التطبيقات...", Modifier.padding(18.dp)) } }
            if (!loading && apps.isEmpty()) item { GlassCard { Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Apps, null, Modifier.size(42.dp), MaterialTheme.colorScheme.primary); Spacer(Modifier.height(10.dp)); Text("لا توجد تطبيقات مطابقة", fontWeight = FontWeight.Bold); Spacer(Modifier.height(4.dp)); Text("جرّب تغيير البحث أو الفلتر.", style = MaterialTheme.typography.bodySmall) } } }
            items(apps, key = { it.app.packageName }) { row -> AppPolicyCard(row = row, onBlockedChange = { viewModel.setBlocked(row.app.packageName, it) }, onOpenDetails = { selectedApp = row }) }
        }
        selectedApp?.let { row -> AppPolicyEditor(row = row, onDismiss = { selectedApp = null }, onBlockedChange = { viewModel.setBlocked(row.app.packageName, it) }, onSpeedLimitsChange = { download, upload -> viewModel.setSpeedLimits(row.app.packageName, download, upload) }, onQuotasChange = { daily, monthly -> viewModel.setQuotas(row.app.packageName, daily, monthly) }, onScheduleChange = { enabled, start, end -> viewModel.setSchedule(row.app.packageName, enabled, start, end) }, onNetworkScopeChange = { scope -> viewModel.setNetworkScope(row.app.packageName, scope) }) }
    }
}

@Composable
private fun Dashboard(modifier: Modifier, protectionState: FlowProtectionState, usageGranted: Boolean, today: UsageSummary, month: UsageSummary, onProtection: (Boolean) -> Unit, onEmergency: (Boolean) -> Unit, onUsage: () -> Unit) {
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)) {
        item { GlassCard(MaterialTheme.colorScheme.primary.copy(alpha = .10f)) { Column(Modifier.padding(20.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Shield, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text("الحماية الذكية", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(if (protectionState.protectionEnabled) "التحكم المحلي مفعّل" else "التحكم متوقف حاليًا") }; Switch(protectionState.protectionEnabled, onProtection) }; Spacer(Modifier.height(10.dp)); Text("تستخدم الحماية VpnService محليًا على الجهاز. لا يوجد خادم VPN خارجي.", style = MaterialTheme.typography.bodySmall) } } }
        item { Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp)) { Metric("اليوم", DataFormatter.bytes(today.total.totalBytes), Modifier.weight(1f)); Metric("هذا الشهر", DataFormatter.bytes(month.total.totalBytes), Modifier.weight(1f)) } }
        item { GlassCard { Column(Modifier.padding(18.dp)) { Text("توزيع اليوم", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); Text("Wi-Fi: ${DataFormatter.bytes(today.wifi)}"); Text("بيانات الجوال: ${DataFormatter.bytes(today.mobile)}"); Text("التطبيقات النشطة: ${today.appCount}") } } }
        item { GlassCard { Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Block, null, tint = if (protectionState.emergencyBlockEnabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text("قاطع الإنترنت", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("يحظر اتصال التطبيقات عبر نفق VPN محلي عند تفعيل الحماية.", style = MaterialTheme.typography.bodySmall) }; Switch(protectionState.emergencyBlockEnabled, onEmergency) } } }
        item { GlassCard { Column(Modifier.padding(18.dp)) { Text("إحصائيات الاستخدام", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(if (usageGranted) "تمت قراءة بيانات الجهاز" else "مطلوبة لقراءة استهلاك التطبيقات", style = MaterialTheme.typography.bodySmall); if (!usageGranted) { Spacer(Modifier.height(10.dp)); OutlinedButton(onUsage, Modifier.fillMaxWidth()) { Text("فتح إعدادات الصلاحية") } } } } }
    }
}
