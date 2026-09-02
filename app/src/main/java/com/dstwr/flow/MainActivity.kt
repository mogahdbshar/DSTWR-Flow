package com.dstwr.flow

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.LinearProgressIndicator
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
import com.dstwr.flow.domain.util.DataFormatter
import com.dstwr.flow.ui.apps.AppRow
import com.dstwr.flow.ui.apps.AppsViewModel
import com.dstwr.flow.ui.stats.AppUsageRow
import com.dstwr.flow.ui.stats.UsageHistoryPoint
import com.dstwr.flow.ui.stats.UsageSummary
import com.dstwr.flow.ui.stats.UsageViewModel
import com.dstwr.flow.ui.theme.DSTWRFlowTheme

class MainActivity : ComponentActivity() {
    private val appsViewModel: AppsViewModel by viewModels()
    private val usageViewModel: UsageViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DSTWRFlowTheme {
                FlowApp(hasUsageAccess(), { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }, { requestVpnPermission() }, appsViewModel, usageViewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        appsViewModel.refresh()
        usageViewModel.refresh()
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        return appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName) == AppOpsManager.MODE_ALLOWED
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
    onRequestVpn: () -> Unit,
    appsViewModel: AppsViewModel,
    usageViewModel: UsageViewModel
) {
    var tab by remember { mutableIntStateOf(0) }
    var protection by remember { mutableStateOf(false) }
    var emergency by remember { mutableStateOf(false) }
    Scaffold(
        topBar = { FlowTopBar() },
        bottomBar = {
            NavigationBar(Modifier.navigationBarsPadding()) {
                listOf(
                    Triple("الرئيسية", Icons.Default.NetworkCheck, 0),
                    Triple("التطبيقات", Icons.Default.Apps, 1),
                    Triple("الإحصائيات", Icons.Default.Analytics, 2),
                    Triple("المزيد", Icons.Default.MoreHoriz, 3)
                ).forEach { item ->
                    NavigationBarItem(tab == item.third, { tab = item.third }, { Icon(item.second, item.first) }, label = { Text(item.first) })
                }
            }
        }
    ) { padding ->
        when (tab) {
            0 -> Dashboard(Modifier.padding(padding), protection, emergency, usageAccessGranted, usageViewModel.today.collectAsState().value, usageViewModel.month.collectAsState().value, { protection = it; if (it) onRequestVpn() }, { emergency = it }, onOpenUsageAccess)
            1 -> AppsScreen(Modifier.padding(padding), appsViewModel)
            2 -> StatsScreen(Modifier.padding(padding), usageAccessGranted, usageViewModel, onOpenUsageAccess)
            else -> Section(Modifier.padding(padding), "المزيد", "الإعدادات واللغة والإشعارات والخصوصية والأذونات ومعلومات التطبيق.")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FlowTopBar() {
    TopAppBar(
        title = { Column { Text("DSTWR Flow", fontWeight = FontWeight.Bold); Text("تحكم ذكي في اتصال جهازك", style = MaterialTheme.typography.labelSmall) } },
        navigationIcon = { Surface(Modifier.padding(start = 12.dp), MaterialTheme.shapes.medium, MaterialTheme.colorScheme.primary.copy(alpha = .12f)) { Icon(Icons.Default.Shield, null, Modifier.padding(9.dp), MaterialTheme.colorScheme.primary) } },
        actions = { IconButton({}) { Icon(Icons.Default.NotificationsNone, "الإشعارات") }; IconButton({}) { Icon(Icons.Default.Settings, "الإعدادات") } }
    )
}

@Composable
private fun AppsScreen(modifier: Modifier, viewModel: AppsViewModel) {
    val apps by viewModel.apps.collectAsState()
    val loading by viewModel.loading.collectAsState()
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)) {
        item { GlassCard(MaterialTheme.colorScheme.primary.copy(alpha = .10f)) { Column(Modifier.padding(18.dp)) { Text("تطبيقات الجهاز", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Spacer(Modifier.height(6.dp)); Text("إدارة الاتصال لكل تطبيق. سياسة الحظر تحفظ محليًا حتى يكتمل محرك الشبكة.", style = MaterialTheme.typography.bodySmall) } } }
        if (loading) item { GlassCard { Text("جارٍ تحديث التطبيقات...", Modifier.padding(18.dp)) } }
        items(apps, key = { it.app.packageName }) { row -> AppPolicyCard(row) { viewModel.setBlocked(row.app.packageName, it) } }
    }
}

@Composable
private fun AppPolicyCard(row: AppRow, onBlockedChange: (Boolean) -> Unit) {
    GlassCard {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(46.dp), MaterialTheme.shapes.medium, if (row.blocked) MaterialTheme.colorScheme.error.copy(alpha = .12f) else MaterialTheme.colorScheme.primary.copy(alpha = .10f)) {
                Icon(if (row.blocked) Icons.Default.Block else Icons.Default.Apps, null, Modifier.padding(11.dp), if (row.blocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(row.app.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(if (row.app.systemApp) "تطبيق نظام" else "تطبيق مستخدم", style = MaterialTheme.typography.labelSmall)
                Text(row.app.packageName, style = MaterialTheme.typography.labelSmall)
                Text("اليوم: ${DataFormatter.bytes(row.usage.totalBytes)} | Wi-Fi: ${DataFormatter.bytes(row.usage.wifiBytes)} | جوال: ${DataFormatter.bytes(row.usage.mobileBytes)}", style = MaterialTheme.typography.labelSmall)
            }
            Switch(row.blocked, onBlockedChange)
        }
    }
}

@Composable
private fun Dashboard(modifier: Modifier, protection: Boolean, emergency: Boolean, usageGranted: Boolean, today: UsageSummary, month: UsageSummary, onProtection: (Boolean) -> Unit, onEmergency: (Boolean) -> Unit, onUsage: () -> Unit) {
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)) {
        item { GlassCard(MaterialTheme.colorScheme.primary.copy(alpha = .10f)) { Column(Modifier.padding(20.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Shield, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text("الحماية الذكية", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(if (protection) "تم طلب تفعيل التحكم المحلي" else "التحكم متوقف حاليًا") }; Switch(protection, onProtection) }; Spacer(Modifier.height(10.dp)); Text("سيعمل التحكم محليًا عبر VpnService بعد موافقة المستخدم، دون خادم VPN خارجي.", style = MaterialTheme.typography.bodySmall) } } }
        item { Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp)) { Metric("اليوم", DataFormatter.bytes(today.total.totalBytes), Modifier.weight(1f)); Metric("هذا الشهر", DataFormatter.bytes(month.total.totalBytes), Modifier.weight(1f)) } }
        item { GlassCard { Column(Modifier.padding(18.dp)) { Text("توزيع اليوم", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); Text("Wi-Fi: ${DataFormatter.bytes(today.wifi)}"); Text("بيانات الجوال: ${DataFormatter.bytes(today.mobile)}"); Text("التطبيقات النشطة: ${today.appCount}") } } }
        item { GlassCard { Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Block, null, tint = if (emergency) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text("قاطع الإنترنت", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("واجهة التحكم الطارئ، ولن تنفذ الحظر قبل اكتمال محرك التوجيه.", style = MaterialTheme.typography.bodySmall) }; Switch(emergency, onEmergency) } } }
        item { GlassCard { Column(Modifier.padding(18.dp)) { Text("إحصائيات الاستخدام", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(if (usageGranted) "تمت قراءة بيانات الجهاز" else "مطلوبة لقراءة استهلاك التطبيقات", style = MaterialTheme.typography.bodySmall); if (!usageGranted) { Spacer(Modifier.height(10.dp)); OutlinedButton(onUsage, Modifier.fillMaxWidth()) { Text("فتح إعدادات الصلاحية") } } } } }
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
        item { GlassCard(MaterialTheme.colorScheme.primary.copy(alpha = .10f)) { Column(Modifier.padding(20.dp)) { Text("الإحصائيات", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text("بيانات مقروءة من عدادات Android الرسمية", style = MaterialTheme.typography.bodySmall); if (!usageGranted) { Spacer(Modifier.height(10.dp)); OutlinedButton(onUsage, Modifier.fillMaxWidth()) { Text("منح صلاحية Usage Access") } } } } }
        item { Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) { listOf("اليوم", "الأسبوع", "الشهر").forEachIndexed { index, title -> OutlinedButton({ period = index }, Modifier.weight(1f)) { Text(if (period == index) "• $title" else title) } } } }
        item { PeriodCard(if (period == 0) "اليوم" else if (period == 1) "هذا الأسبوع" else "هذا الشهر", selected) }
        item { HistoryCard(history) }
        item { GlassCard { Column(Modifier.padding(18.dp)) { Text("أكثر التطبيقات استهلاكًا", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); if (selected.topApps.isEmpty()) Text("لا توجد بيانات استهلاك متاحة بعد.") else selected.topApps.forEach { TopAppRow(it) } } } }
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
    GlassCard { Column(Modifier.padding(18.dp)) { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); Text(DataFormatter.bytes(summary.total.totalBytes), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); Text("Wi-Fi: ${DataFormatter.bytes(summary.wifi)}"); Text("بيانات الجوال: ${DataFormatter.bytes(summary.mobile)}"); Text("التطبيقات ذات الاستهلاك: ${summary.appCount}") } }
}

@Composable
private fun TopAppRow(row: AppUsageRow) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { Surface(Modifier.size(38.dp), MaterialTheme.shapes.medium, MaterialTheme.colorScheme.primary.copy(alpha = .10f)) { Icon(Icons.Default.Apps, null, Modifier.padding(9.dp), MaterialTheme.colorScheme.primary) }; Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(row.app.label, fontWeight = FontWeight.SemiBold); Text(row.app.packageName, style = MaterialTheme.typography.labelSmall) }; Text(DataFormatter.bytes(row.usage.totalBytes), fontWeight = FontWeight.Bold) }
}

@Composable
private fun Metric(title: String, value: String, modifier: Modifier) { GlassCard(modifier = modifier) { Column(Modifier.padding(16.dp)) { Text(title, style = MaterialTheme.typography.labelLarge); Spacer(Modifier.height(8.dp)); Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("استهلاك الشبكة", style = MaterialTheme.typography.labelSmall) } } }

@Composable
private fun Section(modifier: Modifier, title: String, description: String) { Column(modifier.fillMaxSize().padding(20.dp), Arrangement.spacedBy(14.dp)) { Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); GlassCard { Column(Modifier.padding(20.dp)) { Text(description, style = MaterialTheme.typography.bodyLarge); Spacer(Modifier.height(14.dp)); Button({}, Modifier.fillMaxWidth()) { Text("سيتم تفعيل هذه الوحدة مع المحرك") } } } } }

@Composable
private fun GlassCard(containerColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = .84f), modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) { androidx.compose.material3.Card(modifier, colors = CardDefaults.cardColors(containerColor), elevation = CardDefaults.cardElevation(0.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .12f)), shape = MaterialTheme.shapes.large, content = content) }
