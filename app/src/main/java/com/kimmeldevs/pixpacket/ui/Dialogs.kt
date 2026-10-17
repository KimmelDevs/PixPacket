package com.kimmeldevs.pixpacket.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kimmeldevs.pixpacket.io.ExportKind

private val sizePresets = listOf(8, 16, 24, 32, 48, 64, 96, 128, 256)

@Composable
private fun NumberField(label: String, value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = { t -> onChange(t.filter { it.isDigit() }.take(4)) },
        label = { Text(label) }, singleLine = true, modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewSpriteDialog(onDismiss: () -> Unit, onCreate: (name: String, w: Int, h: Int, whiteBg: Boolean) -> Unit) {
    var name by remember { mutableStateOf("") }
    var w by remember { mutableStateOf("32") }
    var h by remember { mutableStateOf("32") }
    var whiteBg by remember { mutableStateOf(false) }
    val wi = w.toIntOrNull() ?: 0; val hi = h.toIntOrNull() ?: 0
    val valid = wi in 1..512 && hi in 1..512
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New sprite") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, placeholder = { Text("Untitled") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Size", fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    sizePresets.forEach { s ->
                        FilterChip(selected = wi == s && hi == s, onClick = { w = "$s"; h = "$s" }, label = { Text("$s") })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    NumberField("Width", w, Modifier.weight(1f)) { w = it }
                    Text("×")
                    NumberField("Height", h, Modifier.weight(1f)) { h = it }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("White background", Modifier.weight(1f))
                    Switch(whiteBg, { whiteBg = it })
                }
                if (!valid) Text("Size must be 1–512 px", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(name.ifBlank { "Untitled" }, wi, hi, whiteBg) }, enabled = valid) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun RenameDialog(title: String, initial: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { onDone(text.trim()) }, enabled = text.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Resize canvas (with 3x3 anchor) or scale the artwork (nearest neighbour). */
@Composable
fun ResizeDialog(w0: Int, h0: Int, onDismiss: () -> Unit, onResize: (w: Int, h: Int, anchor: Int) -> Unit, onScale: (w: Int, h: Int) -> Unit) {
    var scaleMode by remember { mutableStateOf(false) }
    var w by remember { mutableStateOf("$w0") }
    var h by remember { mutableStateOf("$h0") }
    var keepRatio by remember { mutableStateOf(true) }
    var anchor by remember { mutableIntStateOf(4) }
    val wi = w.toIntOrNull() ?: 0; val hi = h.toIntOrNull() ?: 0
    val valid = wi in 1..512 && hi in 1..512
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Canvas size") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(!scaleMode, { scaleMode = false }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Resize canvas") }
                    SegmentedButton(scaleMode, { scaleMode = true }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Scale art") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    NumberField("Width", w, Modifier.weight(1f)) {
                        w = it
                        if (keepRatio) it.toIntOrNull()?.let { nw -> h = "${(nw * h0 + w0 / 2) / w0}" }
                    }
                    Text("×")
                    NumberField("Height", h, Modifier.weight(1f)) {
                        h = it
                        if (keepRatio) it.toIntOrNull()?.let { nh -> w = "${(nh * w0 + h0 / 2) / h0}" }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Keep proportions", Modifier.weight(1f)); Switch(keepRatio, { keepRatio = it })
                }
                if (scaleMode) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0.5f, 2f, 3f, 4f).forEach { k ->
                            FilterChip(false, { w = "${(w0 * k).toInt().coerceAtLeast(1)}"; h = "${(h0 * k).toInt().coerceAtLeast(1)}" }, { Text(if (k < 1) "½×" else "${k.toInt()}×") })
                        }
                    }
                } else {
                    Text("Anchor", fontWeight = FontWeight.Bold)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (r in 0..2) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (c in 0..2) {
                                val i = r * 3 + c
                                Box(
                                    Modifier.size(40.dp).clip(RoundedCornerShape(6.dp))
                                        .background(if (anchor == i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest)
                                        .clickable { anchor = i }
                                )
                            }
                        }
                    }
                }
                if (!valid) Text("Size must be 1–512 px", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { if (scaleMode) onScale(wi, hi) else onResize(wi, hi, anchor) }) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportDialog(
    w: Int, h: Int, frames: Int,
    onDismiss: () -> Unit,
    onExport: (kind: ExportKind, scale: Int, columns: Int, share: Boolean) -> Unit,
) {
    var kind by remember { mutableStateOf(if (frames > 1) ExportKind.GIF else ExportKind.PNG) }
    var scale by remember { mutableIntStateOf(if (maxOf(w, h) <= 64) 8 else if (maxOf(w, h) <= 128) 4 else 1) }
    var columns by remember { mutableIntStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ExportKind.entries.forEach { k ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .border(if (kind == k) 2.dp else 1.dp, if (kind == k) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                            .clickable { kind = k }.padding(12.dp)
                    ) { Text(k.label) }
                }
                Text("Scale  (${w * scale}×${h * scale} px per frame)", fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1, 2, 4, 8, 12, 16, 32).forEach { s -> FilterChip(scale == s, { scale = s }, { Text("${s}×") }) }
                }
                if (kind == ExportKind.SHEET) {
                    Text("Layout", fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(columns == 0, { columns = 0 }, { Text("1 row") })
                        listOf(2, 4, 8).forEach { c -> FilterChip(columns == c, { columns = c }, { Text("$c columns") }) }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedButton(onClick = { onExport(kind, scale, columns, true) }, modifier = Modifier.weight(1f)) { Text("Share") }
                    androidx.compose.material3.Button(onClick = { onExport(kind, scale, columns, false) }, modifier = Modifier.weight(1f)) { Text("Save") }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
fun HelpDialog(onDismiss: () -> Unit) {
    val tips = listOf(
        "One finger" to "Draw with the selected tool",
        "Two fingers" to "Pinch to zoom, drag to move around",
        "Two-finger tap" to "Undo",
        "Three-finger tap" to "Redo",
        "Long-press canvas" to "Pick the color under your finger",
        "Tap a swatch" to "Set primary color · long-press sets secondary",
        "Color stack" to "Tap the big square to mix colors, the small one to swap",
        "Precision cursor" to "Menu → Precision cursor: drag to aim, hold the round button to draw",
        "Selections" to "Select / Lasso / Wand, then copy, cut, flip, rotate or Move them",
        "Dock tabs" to "Tap the active tab again to hide the panel and get more canvas",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How to use PixPacket") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                tips.forEach { (a, b) ->
                    Column {
                        Text(a, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(b, fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it") } },
    )
}
