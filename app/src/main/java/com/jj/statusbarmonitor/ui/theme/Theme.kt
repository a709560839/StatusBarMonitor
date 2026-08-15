package com.jj.statusbarmonitor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val MdThemePrimary = Color(0xFF6DB0FF)
val MdThemeOnPrimary = Color(0xFF002F6C)
val MdThemePrimaryContainer = Color(0xFF1E2838)
val MdThemeOnPrimaryContainer = Color(0xFFD4E3FF)

val MdThemeSurface = Color(0xFF0F1115)
val CardSurface = Color(0xFF1B1E26)
val MdThemeOnSurface = Color(0xFFF0F3F9)
val MdThemeOnSurfaceVariant = Color(0xFF9FA5B5)
val CardStroke = Color(0xFF2B2F3D)

val StatusConnected = Color(0xFF34D399)
val StatusConnectedBg = Color(0xFF133E2B)
val StatusDisconnected = Color(0xFFF87171)
val StatusDisconnectedBg = Color(0xFF4A1515)

private val DarkColorScheme = darkColorScheme(
    primary = MdThemePrimary,
    onPrimary = MdThemeOnPrimary,
    primaryContainer = MdThemePrimaryContainer,
    onPrimaryContainer = MdThemeOnPrimaryContainer,
    background = MdThemeSurface,
    onBackground = MdThemeOnSurface,
    surface = CardSurface,
    onSurface = MdThemeOnSurface,
    surfaceVariant = CardSurface,
    onSurfaceVariant = MdThemeOnSurfaceVariant,
    outline = CardStroke,
    outlineVariant = CardStroke
)

@Composable
fun StatusBarMonitorTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
