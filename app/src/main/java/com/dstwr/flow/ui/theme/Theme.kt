package com.dstwr.flow.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DstwrBlue = Color(0xFF5B8CFF)
private val DstwrCyan = Color(0xFF46D7FF)
private val DstwrInk = Color(0xFF0B1020)
private val DstwrSurface = Color(0xFFF7F9FC)

private val LightColors = lightColorScheme(
    primary = DstwrBlue,
    secondary = DstwrCyan,
    background = DstwrSurface,
    surface = Color.White,
    onBackground = DstwrInk,
    onSurface = DstwrInk
)

private val DarkColors = darkColorScheme(
    primary = DstwrBlue,
    secondary = DstwrCyan,
    background = Color(0xFF070B16),
    surface = Color(0xFF101729),
    onBackground = Color(0xFFF3F6FF),
    onSurface = Color(0xFFF3F6FF)
)

@Composable
fun DSTWRFlowTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content
    )
}
