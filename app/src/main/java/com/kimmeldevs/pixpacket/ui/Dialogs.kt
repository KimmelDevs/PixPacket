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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kimmeldevs.pixpacket.io.ExportKind
import com.kimmeldevs.pixpacket.io.ExportOptions
import com.kimmeldevs.pixpacket.model.GridSettings

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
fun NewSpriteDialog(defaultW: Int, defaultH: Int, onDismiss: () -> Unit, onCreate: (name: String, w: Int, h: Int, whiteBg: Boolean) -> Unit) {
    var name by remember { mutableStateOf("") }
    var w by remember { mutableStateOf("$defaultW") }
    var h by remember { mutableStateOf("$defaultH") }
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
    // Opens focused with the old name selected, so typing replaces it.
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val text = value.text
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value, { value = it }, singleLine = true, modifier = Modifier.fillMaxWidth().focusRequester(focus),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onDone(text.trim()) }),
            )
        },
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

enum class ExportTarget { GALLERY, FILES, SHARE }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportDialog(
    w: Int, h: Int, frames: Int, layers: Int,
    onDismiss: () -> Unit,
    onExport: (ExportOptions, ExportTarget) -> Unit,
) {
    var kind by remember { mutableStateOf(if (frames > 1) ExportKind.GIF else ExportKind.PNG) }
    var scale by remember { mutableIntStateOf(if (maxOf(w, h) <= 64) 8 else if (maxOf(w, h) <= 128) 4 else 1) }
    var columns by remember { mutableIntStateOf(0) }
    var perLayer by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExportKind.entries.forEach { k ->
                    val sel = kind == k
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { kind = k }.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(k.label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(k.detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (kind.scalable) {
                    Text("Scale  (${w * scale}×${h * scale} px per frame)", fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1, 2, 4, 8, 12, 16, 32).forEach { s -> FilterChip(scale == s, { scale = s }, { Text("${s}×") }) }
                    }
                }
                if (kind == ExportKind.SHEET) {
                    Text("Layout", fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(columns == 0, { columns = 0 }, { Text("1 row") })
                        listOf(2, 4, 8).forEach { c -> FilterChip(columns == c, { columns = c }, { Text("$c columns") }) }
                    }
                }
                if (kind == ExportKind.FRAMES_ZIP && layers > 1) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Also each layer separately", Modifier.weight(1f)); Switch(perLayer, { perLayer = it })
                    }
                }
                Spacer(Modifier.height(4.dp))
                val opts = ExportOptions(kind, if (kind.scalable) scale else 1, columns, 0, perLayer)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedButton(onClick = { onExport(opts, ExportTarget.SHARE) }, modifier = Modifier.weight(1f)) { Text("Share") }
                    androidx.compose.material3.OutlinedButton(onClick = { onExport(opts, ExportTarget.FILES) }, modifier = Modifier.weight(1f)) { Text("Files") }
                }
                if (kind.isImage) {
                    androidx.compose.material3.Button(onClick = { onExport(opts, ExportTarget.GALLERY) }, modifier = Modifier.fillMaxWidth()) { Text("Save to Gallery") }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/** Per-sprite grid options: pixel lines, tile grid size/offset and line color. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GridDialog(initial: GridSettings, visible: Boolean, onDismiss: () -> Unit, onApply: (GridSettings, Boolean) -> Unit) {
    var show by remember { mutableStateOf(visible) }
    var pixel by remember { mutableStateOf(initial.pixelLines) }
    var tile by remember { mutableStateOf(initial.tileLines) }
    var tw by remember { mutableStateOf("${initial.tileWidth}") }
    var th by remember { mutableStateOf("${initial.tileHeight}") }
    var ox by remember { mutableStateOf("${initial.offsetX}") }
    var oy by remember { mutableStateOf("${initial.offsetY}") }
    var color by remember { mutableIntStateOf(initial.color) }
    var linked by remember { mutableStateOf(initial.tileWidth == initial.tileHeight) }
    val twi = tw.toIntOrNull() ?: 0; val thi = th.toIntOrNull() ?: 0
    val valid = twi in 1..512 && thi in 1..512
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Grid") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Show grid", Modifier.weight(1f), fontWeight = FontWeight.Bold); Switch(show, { show = it }) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Pixel lines"); Text("Between every pixel when zoomed in", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(pixel, { pixel = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Tile grid"); Text("Bold lines every N pixels", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(tile, { tile = it })
                }
                if (tile) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(4, 8, 16, 32, 64).forEach { n ->
                            FilterChip(twi == n && thi == n, { tw = "$n"; th = "$n"; linked = true }, { Text("$n") })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        NumberField("Width", tw, Modifier.weight(1f)) { tw = it; if (linked) th = it }
                        Text("×")
                        NumberField("Height", th, Modifier.weight(1f)) { th = it; if (linked) tw = it }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("Square tiles", Modifier.weight(1f)); Switch(linked, { linked = it; if (it) th = tw }) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField("Offset X", ox, Modifier.weight(1f)) { ox = it }
                        NumberField("Offset Y", oy, Modifier.weight(1f)) { oy = it }
                    }
                }
                Text("Line color", fontWeight = FontWeight.Bold)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf("Dark", "Light", "Accent").forEachIndexed { i, label ->
                        SegmentedButton(color == i, { color = i }, SegmentedButtonDefaults.itemShape(i, 3)) { Text(label) }
                    }
                }
                if (!valid) Text("Tile size must be 1–512", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onApply(GridSettings(pixel, tile, twi, thi, color, ox.toIntOrNull() ?: 0, oy.toIntOrNull() ?: 0), show)
            }) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** After opening a plain image: use it as one frame, or slice it as a sprite sheet. */
@Composable
fun ImportImageDialog(
    name: String, iw: Int, ih: Int, asLayer: Boolean,
    onDismiss: () -> Unit,
    onImport: (frameW: Int, frameH: Int) -> Unit,
) {
    var sheet by remember { mutableStateOf(false) }
    var fw by remember { mutableStateOf("${minOf(iw, ih)}") }
    var fh by remember { mutableStateOf("${minOf(iw, ih)}") }
    val fwi = fw.toIntOrNull() ?: 0; val fhi = fh.toIntOrNull() ?: 0
    val tooBig = !sheet && !asLayer && (iw > 1024 || ih > 1024)
    val valid = if (sheet) fwi in 1..iw && fhi in 1..ih else !tooBig
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import \"$name\"") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Image is ${iw}×$ih px")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(!sheet, { sheet = false }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Single image") }
                    SegmentedButton(sheet, { sheet = true }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Sprite sheet") }
                }
                if (sheet) {
                    Text("Frame size (sliced left→right, top→bottom)", fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        NumberField("Width", fw, Modifier.weight(1f)) { fw = it }
                        Text("×")
                        NumberField("Height", fh, Modifier.weight(1f)) { fh = it }
                    }
                    if (fwi > 0 && fhi > 0) Text("${(iw / fwi).coerceAtLeast(1) * (ih / fhi).coerceAtLeast(1)} frames", color = MaterialTheme.colorScheme.primary)
                } else if (asLayer) {
                    Text("It will be fitted into the canvas as a new layer.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (tooBig) Text("Too large for a sprite (max 1024 px). Slice it as a sheet instead.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { if (sheet) onImport(fwi, fhi) else onImport(iw, ih) }) { Text("Import") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun MoveToFolderDialog(folders: List<String>, current: String?, onDismiss: () -> Unit, onNewFolder: () -> Unit, onMove: (String?) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to folder") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                (listOf<String?>(null) + folders).forEach { f ->
                    val sel = f == current
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { onMove(f) }.padding(14.dp)
                    ) { Text(f ?: "No folder (main gallery)", fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal) }
                }
                TextButton(onClick = onNewFolder) { Text("+ New folder") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
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
        "Layers" to "Long-press or ⋮ for rename, solo, merge and more · double-tap to rename",
        "Grid" to "Menu → Grid settings to set tile size, offset and color",
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
