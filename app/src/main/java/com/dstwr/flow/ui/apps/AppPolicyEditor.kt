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
        mutableStateOf(if (row.policy.downloadLimitBytesPerSecond == 0L) "" else row.policy.downloadLimitBytesPerSecond.toString())
    }
    var upload by remember(row.policy.uploadLimitBytesPerSecond) {
        mutableStateOf(if (row.policy.uploadLimitBytesPerSecond == 0L) "" else row.policy.uploadLimitBytesPerSecond.toString())
    }
    var daily by remember(row.policy.dailyQuotaBytes) {
        mutableStateOf(if (row.policy.dailyQuotaBytes == 0L) "" else row.policy.dailyQuotaBytes.toString())
    }
    var monthly by remember(row.policy.monthlyQuotaBytes) {
        mutableStateOf(if (row.policy.monthlyQuotaBytes == 0L) "" else row.policy.monthlyQuotaBytes.toString())
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
                Text(row.app.packageName)
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
                    onValueChange = { download = it.filter(Char::isDigit) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("حد التنزيل، بايت/ثانية") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = upload,
                    onValueChange = { upload = it.filter(Char::isDigit) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("حد الرفع، بايت/ثانية") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = daily,
                    onValueChange = { daily = it.filter(Char::isDigit) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("الحصة اليومية، بايت") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = monthly,
                    onValueChange = { monthly = it.filter(Char::isDigit) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("الحصة الشهرية، بايت") },
                    singleLine = true
                )

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("جدولة الحظر")
                    Switch(checked = schedule, onCheckedChange = { schedule = it })
                }
                if (schedule) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = start,
                            onValueChange = { start = it.filter(Char::isDigit).take(4) },
                            modifier = Modifier.weight(1f),
                            label = { Text("البداية HHMM") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = end,
                            onValueChange = { end = it.filter(Char::isDigit).take(4) },
                            modifier = Modifier.weight(1f),
                            label = { Text("النهاية HHMM") },
                            singleLine = true
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onBlockedChange(blocked)
                onSpeedLimitsChange(download.toLongOrNull() ?: 0L, upload.toLongOrNull() ?: 0L)
                onQuotasChange(daily.toLongOrNull() ?: 0L, monthly.toLongOrNull() ?: 0L)
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

private fun minutesText(minutes: Int): String {
    val safe = minutes.coerceIn(0, 1439)
    return "%02d%02d".format(safe / 60, safe % 60)
}

private fun parseMinutes(value: String): Int {
    val digits = value.filter(Char::isDigit).padStart(4, '0').takeLast(4)
    val hour = digits.substring(0, 2).toIntOrNull() ?: 0
    val minute = digits.substring(2, 4).toIntOrNull() ?: 0
    return (hour.coerceIn(0, 23) * 60 + minute.coerceIn(0, 59)).coerceIn(0, 1439)
}
