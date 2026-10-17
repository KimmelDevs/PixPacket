package com.kimmeldevs.pixpacket.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** PixPacket uses one dark, low-distraction scheme so artwork colors read true. */
private val PixColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF1A1030),
    primaryContainer = Color(0xFF3B2A6B),
    onPrimaryContainer = Color(0xFFE7DEFF),
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

@Composable
fun PixPacketTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = PixColors, typography = Typography, content = content)
}
