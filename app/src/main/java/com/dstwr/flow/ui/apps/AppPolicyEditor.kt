package com.dstwr.flow.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dstwr.flow.domain.model.NetworkScope

@Composable
fun AppPolicyEditor(
    row: AppRow,
    onDismiss: () -> Unit,
    onBlockedChange: (Boolean) -> Unit,
    onSpeedLimitsChange: (Long, Long) -> Unit,
    onQuotasChange: (Long, Long) -> Unit,
    onScheduleChange: (Boolean, Int, Int) -> Unit,
    onNetworkScopeChange: (NetworkScope) -> Unit
) {
    var blocked by remember(row.policy.blocked) { mutableStateOf(row.policy.blocked) }
    var download by remember(row.policy.downloadLimitBytesPerSecond) {
        mutableStateOf(bytesToKiB(row.policy.downloadLimitBytesPerSecond))
    }
    var upload by remember(row.policy.uploadLimitBytesPerSecond) {
        mutableStateOf(bytesToKiB(row.policy.uploadLimitBytesPerSecond))
    }
    var daily by remember(row.policy.dailyQuotaBytes) {
        mutableStateOf(bytesToMiB(row.policy.dailyQuotaBytes))
    }
    var monthly by remember(row.policy.monthlyQuotaBytes) {
        mutableStateOf(bytesToMiB(row.policy.monthlyQuotaBytes))
    }
    var schedule by remember(row.policy.scheduleEnabled) { mutableStateOf(row.policy.scheduleEnabled) }
    var start by remember(row.policy.scheduleStartMinutes) { mutableStateOf(minutesText(row.policy.scheduleStartMinutes)) }
    var end by remember(row.policy.scheduleEndMinutes) { mutableStateOf(minutesText(row.policy.scheduleEndMinutes)) }
    var scope by remember(row.policy.networkScope) { mutableStateOf(row.policy.networkScope) }
    var scopeMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(row.app.label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(row.app.packageName, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("حظر الإنترنت")
                        Text(if (blocked) "محظور" else "مسموح")
                    }
                    Switch(checked = blocked, onCheckedChange = { blocked = it })
                }

                HorizontalDivider()
                Text("نوع الشبكة")
                Button(onClick = { scopeMenu = true }) {
                    Text(scopeLabel(scope))
                }
                DropdownMenu(expanded = scopeMenu, onDismissRequest = { scopeMenu = false }) {
                    NetworkScope.entries.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(scopeLabel(item)) },
                            onClick = { scope = item; scopeMenu = false }
                        )
                    }
                }

                OutlinedTextField(
                    value = download,
                    onValueChange = { download = it.filter { char -> char.isDigit() }.take(10) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("حد التنزيل، KB/s") },
                    supportingText = { Text("اتركه فارغًا أو 0 لعدم تحديد السرعة") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = upload,
                    onValueChange = { upload = it.filter { char -> char.isDigit() }.take(10) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("حد الرفع، KB/s") },
                    supportingText = { Text("اتركه فارغًا أو 0 لعدم تحديد السرعة") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = daily,
                    onValueChange = { daily = it.filter { char -> char.isDigit() }.take(10) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("الحصة اليومية، MB") },
                    supportingText = { Text("0 = بدون حصة يومية") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = monthly,
                    onValueChange = { monthly = it.filter { char -> char.isDigit() }.take(10) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("الحصة الشهرية، MB") },
                    supportingText = { Text("0 = بدون حصة شهرية") },
                    singleLine = true
                )

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("جدولة الحظر")
                        Text("الحظر يعمل داخل الفترة المحددة", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = schedule, onCheckedChange = { schedule = it })
                }
                if (schedule) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = start,
                            onValueChange = { start = it.filter { char -> char.isDigit() }.take(4) },
                            modifier = Modifier.weight(1f),
                            label = { Text("البداية HH:mm") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = end,
                            onValueChange = { end = it.filter { char -> char.isDigit() }.take(4) },
                            modifier = Modifier.weight(1f),
                            label = { Text("النهاية HH:mm") },
                            singleLine = true
                        )
                    }
                    Text("مثال: 23:00 إلى 07:00 يعمل عبر منتصف الليل.", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onBlockedChange(blocked)
                onSpeedLimitsChange(parseUnit(download, 1024L), parseUnit(upload, 1024L))
                onQuotasChange(parseUnit(daily, 1024L * 1024L), parseUnit(monthly, 1024L * 1024L))
                onScheduleChange(schedule, parseMinutes(start), parseMinutes(end))
                onNetworkScopeChange(scope)
                onDismiss()
            }) { Text("حفظ") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

private fun scopeLabel(scope: NetworkScope): String = when (scope) {
    NetworkScope.ALL -> "كل الشبكات"
    NetworkScope.WIFI -> "Wi-Fi فقط"
    NetworkScope.MOBILE -> "بيانات الجوال فقط"
}

private fun bytesToKiB(bytes: Long): String =
    if (bytes <= 0L) "" else (bytes / 1024L).toString()

private fun bytesToMiB(bytes: Long): String =
    if (bytes <= 0L) "" else (bytes / (1024L * 1024L)).toString()

private fun parseUnit(value: String, multiplier: Long): Long {
    val amount = value.toLongOrNull()?.coerceAtLeast(0L) ?: return 0L
    return try {
        Math.multiplyExact(amount, multiplier)
    } catch (_: ArithmeticException) {
        Long.MAX_VALUE
    }
}

private fun minutesText(minutes: Int): String {
    val safe = minutes.coerceIn(0, 1439)
    return "%02d%02d".format(safe / 60, safe % 60)
}

private fun parseMinutes(value: String): Int {
    val digits = value.filter { it.isDigit() }.padStart(4, '0').takeLast(4)
    val hour = digits.substring(0, 2).toIntOrNull() ?: 0
    val minute = digits.substring(2, 4).toIntOrNull() ?: 0
    return (hour.coerceIn(0, 23) * 60 + minute.coerceIn(0, 59)).coerceIn(0, 1439)
}
