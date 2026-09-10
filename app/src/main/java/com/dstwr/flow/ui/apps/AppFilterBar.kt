package com.dstwr.flow.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Search and filter controls for the applications list. */
@Composable
fun AppFilterBar(
    query: String,
    mode: AppFilterMode,
    onQueryChange: (String) -> Unit,
    onModeChange: (AppFilterMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("بحث عن تطبيق") },
            placeholder = { Text("اسم التطبيق أو اسم الحزمة") }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = mode == AppFilterMode.ALL,
                onClick = { onModeChange(AppFilterMode.ALL) },
                label = { Text("الكل") },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = mode == AppFilterMode.BLOCKED,
                onClick = { onModeChange(AppFilterMode.BLOCKED) },
                label = { Text("المحظورة") },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = mode == AppFilterMode.CONFIGURED,
                onClick = { onModeChange(AppFilterMode.CONFIGURED) },
                label = { Text("المضبوطة") },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
