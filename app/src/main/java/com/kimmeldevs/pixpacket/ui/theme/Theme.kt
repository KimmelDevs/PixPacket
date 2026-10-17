package com.kimmeldevs.pixpacket.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.kimmeldevs.pixpacket.settings.Accent
import com.kimmeldevs.pixpacket.settings.ThemeMode

/** Colors that sit outside Material's scheme: the area behind the canvas and floating chips. */
data class PixExtras(val canvasBg: Color, val overlay: Color, val isDark: Boolean)

val LocalPixExtras = staticCompositionLocalOf { PixExtras(CanvasBg, Color(0xCC22212B), true) }

private fun darkScheme(accent: Color) = darkColorScheme(
    primary = accent,
    onPrimary = Color(0xFF16121F),
    primaryContainer = lerp(Color(0xFF22212B), accent, 0.35f),
    onPrimaryContainer = Color(0xFFF1EDFF),
    secondary = Teal,
    onSecondary = Color(0xFF00201C),
    secondaryContainer = Color(0xFF1F4A45),
    onSecondaryContainer = Color(0xFFBDF2EA),
    background = Bg,
    onBackground = Color(0xFFE6E4EE),
    surface = Surface,
    onSurface = Color(0xFFE6E4EE),
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = Color(0xFFB4B1C2),
    surfaceContainer = Surface,
    surfaceContainerHigh = SurfaceHigh,
    surfaceContainerHighest = Color(0xFF34333F),
    surfaceContainerLow = Color(0xFF1B1B23),
    outline = Color(0xFF5A5869),
    outlineVariant = Color(0xFF3A3946),
    error = Color(0xFFFF6B7A),
)

private fun lightScheme(accent: Color) = lightColorScheme(
    primary = accent,
    onPrimary = Color.White,
    primaryContainer = lerp(Color.White, accent, 0.22f),
    onPrimaryContainer = Color(0xFF1C1430),
    secondary = Color(0xFF00796B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC9EFE9),
    onSecondaryContainer = Color(0xFF00201C),
    background = Color(0xFFF4F3F8),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFECEAF2),
    onSurfaceVariant = Color(0xFF55536A),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEFEEF4),
    surfaceContainerHighest = Color(0xFFE4E2EC),
    surfaceContainerLow = Color(0xFFF7F6FB),
    outline = Color(0xFF9A98AA),
    outlineVariant = Color(0xFFD6D4E0),
    error = Color(0xFFC62839),
)

@Composable
fun PixPacketTheme(mode: ThemeMode = ThemeMode.DARK, accent: Accent = Accent.WREN, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val scheme = if (dark) darkScheme(Color(accent.dark)) else lightScheme(Color(accent.light))
    val extras = if (dark) PixExtras(CanvasBg, Color(0xCC22212B), true) else PixExtras(Color(0xFFDDDCE5), Color(0xE6FFFFFF), false)
    CompositionLocalProvider(LocalPixExtras provides extras) {
        MaterialTheme(colorScheme = scheme, typography = Typography, content = content)
    }
}
