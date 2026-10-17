package com.kimmeldevs.pixpacket.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kimmeldevs.pixpacket.R
import com.kimmeldevs.pixpacket.settings.Accent
import com.kimmeldevs.pixpacket.settings.CheckerSize
import com.kimmeldevs.pixpacket.settings.LocalSettings
import com.kimmeldevs.pixpacket.settings.ThemeMode
import com.kimmeldevs.pixpacket.ui.theme.LocalPixExtras

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val s = LocalSettings.current
    val ctx = LocalContext.current
    val dark = LocalPixExtras.current.isDark
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Section("Appearance") {
                Label("Theme")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { i, m ->
                        SegmentedButton(s.theme == m, { s.theme = m }, SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size)) { Text(m.label) }
                    }
                }
                Label("Accent color")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Accent.entries.forEach { a ->
                        val c = Color(if (dark) a.dark else a.light)
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { s.accent = a }.padding(4.dp)) {
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).background(c)
                                    .then(if (s.accent == a) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier),
                                contentAlignment = Alignment.Center,
                            ) { if (s.accent == a) Icon(Icons.Filled.Check, null, tint = if (dark) Color.Black else Color.White) }
                            Text(a.label, fontSize = 11.sp)
                        }
                    }
                }
                Label("Transparency checkerboard")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    CheckerSize.entries.forEachIndexed { i, c ->
                        SegmentedButton(s.checker == c, { s.checker = c }, SegmentedButtonDefaults.itemShape(i, CheckerSize.entries.size)) { Text(c.label) }
                    }
                }
            }

            Section("Drawing & gestures") {
                Toggle("Haptic feedback", "Small vibrations on taps, tool changes and undo", s.haptics) { s.haptics = it }
                Toggle("Finger-tap undo / redo", "Two-finger tap undoes, three-finger tap redoes", s.gestureUndo) { s.gestureUndo = it }
                Toggle("Long-press eyedropper", "Hold still on the canvas to pick a color", s.longPressPick) { s.longPressPick = it }
                Toggle("Left-handed layout", "Moves the color stack, draw button and overlays to the other side", s.leftHanded) { s.leftHanded = it }
                Toggle("Hide tools after picking one", "Gives the canvas more room right away", s.collapseToolsOnPick) { s.collapseToolsOnPick = it }
                Toggle("Keep screen on while drawing", null, s.keepScreenOn) { s.keepScreenOn = it }
            }

            Section("New sprites") {
                Label("Default canvas size")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(16, 24, 32, 48, 64, 128).forEach { n ->
                        FilterChip(s.defaultWidth == n && s.defaultHeight == n, { s.defaultWidth = n; s.defaultHeight = n }, { Text("${n}×$n") })
                    }
                }
                Stepper("Default animation speed", "${s.defaultFps} fps", { s.defaultFps = (s.defaultFps - 1).coerceAtLeast(1) }) { s.defaultFps = (s.defaultFps + 1).coerceAtMost(30) }
                Label("Default tile grid")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(4, 8, 16, 32).forEach { n -> FilterChip(s.defaultTileGrid == n, { s.defaultTileGrid = n }, { Text("$n px") }) }
                }
            }

            Section("Library") {
                Toggle("Confirm before deleting", "Deleted sprites stay in Recently deleted for 30 days either way", s.confirmDelete) { s.confirmDelete = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Gesture tips", fontSize = 15.sp)
                        Text("Show the how-to card next time you open a sprite", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { s.showTips = true }, enabled = !s.showTips) { Text(if (s.showTips) "On" else "Show again") }
                }
            }

            Section("About") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.logo_wren), null, Modifier.size(48.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("PixPacket", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        val v = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"
                        Text("Version $v · by KimmelDevs", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Opens & saves .aseprite, .piskel, PNG, GIF", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
        }
    }
}

@Composable
private fun Label(text: String) = Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun Toggle(title: String, subtitle: String?, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onChange(!value) }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, fontSize = 15.sp)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(value, onChange)
    }
}

@Composable
private fun Stepper(title: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 15.sp, modifier = Modifier.weight(1f))
        IconButton(onClick = onMinus) { Icon(Icons.Filled.Remove, "Less") }
        Text(value, fontWeight = FontWeight.Bold)
        IconButton(onClick = onPlus) { Icon(Icons.Filled.Add, "More") }
    }
}
