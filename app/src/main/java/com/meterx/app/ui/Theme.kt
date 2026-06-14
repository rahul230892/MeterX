package com.meterx.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF166534),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9FBE1),
    onPrimaryContainer = Color(0xFF062E16),
    secondary = Color(0xFF2563EB),
    secondaryContainer = Color(0xFFDBEAFE),
    tertiary = Color(0xFFB45309),
    background = Color(0xFFF7F9F7),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEEF2EE),
    outlineVariant = Color(0xFFDCE4DC),
    error = Color(0xFFB42318),
    errorContainer = Color(0xFFFFE4E0),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF86D993),
    primaryContainer = Color(0xFF0C4A24),
    secondary = Color(0xFF93C5FD),
    tertiary = Color(0xFFFBBF24),
    background = Color(0xFF101410),
    surface = Color(0xFF171C18),
    surfaceVariant = Color(0xFF252B26),
    error = Color(0xFFFFB4AB),
)

@Composable
fun MeterXTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = MaterialTheme.typography,
        content = content,
    )
}
