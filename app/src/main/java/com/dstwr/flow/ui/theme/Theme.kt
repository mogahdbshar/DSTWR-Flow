package com.dstwr.flow.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DstwrBlue = Color(0xFF5B8CFF)
private val DstwrCyan = Color(0xFF46D7FF)
private val DstwrInk = Color(0xFF0B1020)
private val DstwrSurface = Color(0xFFF7F9FC)
private val DstwrDarkSurface = Color(0xFF101729)

private val LightColors = lightColorScheme(
    primary = DstwrBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE7FF),
    onPrimaryContainer = DstwrInk,
    secondary = DstwrCyan,
    onSecondary = DstwrInk,
    background = DstwrSurface,
    surface = Color.White,
    surfaceVariant = Color(0xFFE9EEF7),
    onBackground = DstwrInk,
    onSurface = DstwrInk,
    outline = Color(0xFF7B879B)
)

private val DarkColors = darkColorScheme(
    primary = DstwrBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF294A99),
    onPrimaryContainer = Color(0xFFEAF0FF),
    secondary = DstwrCyan,
    onSecondary = DstwrInk,
    background = Color(0xFF070B16),
    surface = DstwrDarkSurface,
    surfaceVariant = Color(0xFF1A2236),
    onBackground = Color(0xFFF3F6FF),
    onSurface = Color(0xFFF3F6FF),
    outline = Color(0xFF8995AC)
)

@Composable
fun DSTWRFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content
    )
}
