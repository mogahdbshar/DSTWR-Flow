package com.dstwr.flow.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

private val DstwrBlue = Color(0xFF5B8CFF)
private val DstwrCyan = Color(0xFF46D7FF)
private val DstwrViolet = Color(0xFF8A6CFF)
private val DstwrInk = Color(0xFF0B1020)
private val DstwrSurface = Color(0xFFF6F8FC)

private val LightColors = lightColorScheme(
    primary = DstwrBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3EBFF),
    onPrimaryContainer = DstwrInk,
    secondary = DstwrCyan,
    onSecondary = DstwrInk,
    tertiary = DstwrViolet,
    background = DstwrSurface,
    surface = Color.White,
    surfaceVariant = Color(0xFFEFF2F8),
    outline = Color(0xFF8C96AA),
    onBackground = DstwrInk,
    onSurface = DstwrInk
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9BB6FF),
    onPrimary = Color(0xFF0A1530),
    primaryContainer = Color(0xFF243B78),
    onPrimaryContainer = Color(0xFFE6ECFF),
    secondary = Color(0xFF7DE6FF),
    tertiary = Color(0xFFB7A6FF),
    background = Color(0xFF060914),
    surface = Color(0xFF0D1425),
    surfaceVariant = Color(0xFF171F33),
    outline = Color(0xFF64708A),
    onBackground = Color(0xFFF3F6FF),
    onSurface = Color(0xFFF3F6FF)
)

private val DstwrTypography = Typography().run {
    copy(
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold)
    )
}

@Composable
fun DSTWRFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = DstwrTypography,
        content = content
    )
}
