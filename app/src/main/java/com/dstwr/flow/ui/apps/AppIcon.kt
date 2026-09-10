package com.dstwr.flow.ui.apps

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.core.graphics.drawable.toBitmap

@Composable
fun AppIcon(
    context: Context,
    packageName: String,
    modifier: Modifier = Modifier
) {
    val drawable = remember(packageName) {
        runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
    }

    Surface(
        modifier = modifier.size(48.dp).clip(RoundedCornerShape(14.dp)),
        color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)
    ) {
        Box {
            drawable?.let { icon: Drawable ->
                val bitmap = remember(icon) { icon.toBitmap(96, 96).asImageBitmap() }
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}
