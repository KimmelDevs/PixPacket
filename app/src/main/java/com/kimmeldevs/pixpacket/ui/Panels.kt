package com.kimmeldevs.pixpacket.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kimmeldevs.pixpacket.editor.EditorState
import com.kimmeldevs.pixpacket.editor.Tool
import com.kimmeldevs.pixpacket.model.Palettes

enum class PanelTab(val label: String) { TOOLS("Tools"), COLORS("Colors"), FRAMES("Frames"), LAYERS("Layers") }

// region Small building blocks

@Composable
fun ActionButton(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(10.dp)).clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp).width(52.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
        Icon(icon, label, tint = tint, modifier = Modifier.size(22.dp))
        Text(label, fontSize = 10.sp, color = tint, maxLines = 1, overflow = TextOverflow.Clip)
    }
}

@Composable
fun ToggleChip(label: String, selected: Boolean, icon: ImageVector? = null, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 13.sp) },
        leadingIcon = icon?.let { { Icon(it, null, Modifier.size(18.dp)) } },
    )
}

/** Thumbnail of pixels on a checkerboard. [pixels] is read during draw so it tracks edits live. */
@Composable
fun PixelThumb(w: Int, h: Int, modifier: Modifier = Modifier, pixels: () -> IntArray) {
    val buf = remember(w, h) { PixelBuffer(w, h) }
    val checker = remember(w, h) { PixelBuffer(w, h).also { it.update(checkerPixels(w, h)) } }
    Canvas(modifier) {
        val k = minOf(size.width / w, size.height / h)
        val dw = (w * k).toInt().coerceAtLeast(1); val dh = (h * k).toInt().coerceAtLeast(1)
        val o = IntOffset(((size.width - dw) / 2).toInt(), ((size.height - dh) / 2).toInt())
        buf.update(pixels())
        drawPixels(checker, o, IntSize(dw, dh))
        drawPixels(buf, o, IntSize(dw, dh))
    }
}

// endregion

@Composable
fun ToolsPanel(state: EditorState, onPicked: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
        Tool.entries.chunked(8).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { t ->
                    val sel = state.tool == t
                    Box(
                        Modifier.weight(1f).height(50.dp).padding(3.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (sel) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (state.tool == t) onPicked() else state.tool = t
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(t.icon, t.label, tint = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                    }
                }
                repeat(8 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(state.tool.label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            Text(state.tool.hint, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ColorsPanel(
    state: EditorState,
    paletteIndex: Int,
    onPaletteIndex: (Int) -> Unit,
    customPalette: List<Int>,
    onCustomChange: (List<Int>) -> Unit,
    onOpenPicker: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    // Palette sources: presets, then "Custom", "In sprite", "Recent"
    val names = Palettes.presets.map { it.name } + listOf("My palette", "Sprite colors", "Recent")
    val colors: List<Int> = when {
        paletteIndex < Palettes.presets.size -> Palettes.presets[paletteIndex].colors
        paletteIndex == Palettes.presets.size -> customPalette
        paletteIndex == Palettes.presets.size + 1 -> remember(state.version) { state.usedColors() }
        else -> state.recentColors
    }
    val isCustom = paletteIndex == Palettes.presets.size
    var menu by remember { mutableStateOf(false) }

    Column(Modifier.padding(vertical = 6.dp)) {
        Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                FilterChip(
                    selected = true, onClick = { menu = true },
                    label = { Text(names[paletteIndex] + "  ▾") },
                    leadingIcon = { Icon(Icons.Filled.Palette, null, Modifier.size(18.dp)) },
                )
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    names.forEachIndexed { i, n ->
                        DropdownMenuItem(text = { Text(n) }, onClick = { onPaletteIndex(i); menu = false })
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (isCustom) {
                IconButton(onClick = { onCustomChange(customPalette - state.primary) }, enabled = state.primary in customPalette) {
                    Icon(Icons.Filled.Remove, "Remove current color")
                }
            }
            IconButton(onClick = {
                if (state.primary !in customPalette) onCustomChange(customPalette + state.primary)
                state.message = "Added to My palette"
            }) { Icon(Icons.Filled.Add, "Add current color to My palette") }
            FilterChip(selected = false, onClick = onOpenPicker, label = { Text("Mix") })
        }
        // Pages of 16 (2 rows x 8), swipe sideways for more. "My palette" ends with an add slot.
        val cells: List<Int?> = if (isCustom) colors + listOf<Int?>(null) else colors
        val pages = cells.chunked(16)
        val pager = key(paletteIndex) { rememberPagerState { pages.size.coerceAtLeast(1) } }
        // Jump to the last page when a color is added so the new swatch is visible.
        var lastCount by remember(paletteIndex) { mutableIntStateOf(cells.size) }
        LaunchedEffect(cells.size) {
            if (cells.size > lastCount) pager.animateScrollToPage(pages.size - 1)
            lastCount = cells.size
        }
        if (cells.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
                Text("Nothing here yet", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        } else {
            HorizontalPager(pager, Modifier.fillMaxWidth().height(100.dp)) { page ->
                Column(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalArrangement = Arrangement.SpaceEvenly) {
                    pages.getOrElse(page) { emptyList() }.chunked(8).let { rows -> if (rows.size == 1) rows + listOf(emptyList()) else rows }.forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { c ->
                                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    if (c == null) {
                                        Box(
                                            Modifier.size(38.dp).clip(CircleShape)
                                                .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                                .clickable {
                                                    if (state.primary !in customPalette) onCustomChange(customPalette + state.primary)
                                                    else state.message = "That color is already in My palette"
                                                },
                                            contentAlignment = Alignment.Center,
                                        ) { Icon(Icons.Filled.Add, "Add current color", Modifier.size(20.dp)) }
                                    } else {
                                        RoundSwatch(
                                            c, selected = c == state.primary,
                                            modifier = Modifier.combinedClickable(
                                                onClick = { state.primary = c },
                                                onLongClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    state.secondary = c; state.message = "Set as secondary color"
                                                },
                                            ),
                                        )
                                    }
                                }
                            }
                            repeat(8 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            if (pages.size > 1) {
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.Center) {
                    repeat(pages.size) { i ->
                        Box(
                            Modifier.padding(horizontal = 3.dp).size(if (i == pager.currentPage) 8.dp else 6.dp).clip(CircleShape)
                                .background(if (i == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                        )
                    }
                }
            }
        }
        Text(
            (if (pages.size > 1) "Swipe for more  ·  " else "") + "Tap = primary  ·  Long-press = secondary",
            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp),
        )
    }
}

@Composable
fun FramesPanel(state: EditorState) {
    state.version // re-read frame list after add/delete/move
    val listState = rememberLazyListState()
    LaunchedEffect(state.currentFrame) { listState.animateScrollToItem(state.currentFrame.coerceAtLeast(0)) }
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(Modifier.padding(horizontal = 8.dp).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = state.playing, onClick = { state.playing = !state.playing },
                label = { Text(if (state.playing) "Pause" else "Play") },
                leadingIcon = { Icon(if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, Modifier.size(18.dp)) },
            )
            Spacer(Modifier.width(6.dp))
            ToggleChip("Onion skin", state.onionSkin) { state.onionSkin = !state.onionSkin }
            Spacer(Modifier.width(6.dp))
            ToggleChip("Preview", state.showPreview) { state.showPreview = !state.showPreview }
            Spacer(Modifier.width(10.dp))
            IconButton(onClick = { state.setFps(state.sprite.fps - 1) }) { Icon(Icons.Filled.Remove, "Slower") }
            Text("${state.sprite.fps} fps", fontSize = 13.sp)
            IconButton(onClick = { state.setFps(state.sprite.fps + 1) }) { Icon(Icons.Filled.Add, "Faster") }
        }
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.height(72.dp),
        ) {
            items(state.frameCount) { f ->
                val sel = f == state.currentFrame
                Box(
                    Modifier.fillMaxHeight().aspectRatio(1f).clip(RoundedCornerShape(10.dp))
                        .border(if (sel) 3.dp else 1.dp, if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                        .clickable { state.selectFrame(f) }
                        .padding(4.dp)
                ) {
                    PixelThumb(state.width, state.height, Modifier.matchParentSize()) {
                        state.version; state.sprite.composite(f)
                    }
                    Text(
                        "${f + 1}", fontSize = 10.sp, color = Color.White,
                        modifier = Modifier.align(Alignment.BottomEnd).background(Color(0xAA000000), RoundedCornerShape(4.dp)).padding(horizontal = 3.dp),
                    )
                }
            }
            item {
                Box(
                    Modifier.fillMaxHeight().aspectRatio(1f).clip(RoundedCornerShape(10.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                        .clickable { state.addFrame() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Add, "New frame") }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.SpaceEvenly) {
            ActionButton(Icons.Filled.Add, "New") { state.addFrame() }
            ActionButton(Icons.Filled.ContentCopy, "Duplicate") { state.duplicateFrame() }
            ActionButton(Icons.Filled.ChevronLeft, "Move ◀", state.currentFrame > 0) { state.moveFrame(-1) }
            ActionButton(Icons.Filled.ChevronRight, "Move ▶", state.currentFrame < state.frameCount - 1) { state.moveFrame(1) }
            ActionButton(Icons.Filled.Delete, "Delete", state.frameCount > 1) { state.deleteFrame() }
        }
    }
}

@Composable
fun LayersPanel(state: EditorState, onRename: (Int) -> Unit) {
    val layers = state.sprite.layers
    state.version
    Column(Modifier.padding(vertical = 6.dp)) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 150.dp), contentPadding = PaddingValues(horizontal = 10.dp)) {
            items(layers.size) { idx ->
                val i = layers.size - 1 - idx // top layer first
                val l = layers[i]
                val sel = i == state.currentLayer
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { state.selectLayer(i) }
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PixelThumb(state.width, state.height, Modifier.size(38.dp).clip(RoundedCornerShape(6.dp))) {
                        state.version; l.cels[state.currentFrame.coerceAtMost(l.cels.size - 1)]
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(l.name, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                        if (l.opacity < 1f) Text("${(l.opacity * 100).toInt()}% opacity", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { state.toggleLock(i) }) {
                        Icon(if (l.locked) Icons.Filled.Lock else Icons.Filled.LockOpen, if (l.locked) "Unlock" else "Lock",
                            tint = if (l.locked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { state.toggleVisible(i) }) {
                        Icon(if (l.visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, if (l.visible) "Hide" else "Show",
                            tint = if (l.visible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Opacity", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = state.layer.opacity,
                onValueChange = { state.setOpacityLive(state.currentLayer, it) },
                onValueChangeFinished = { state.commitOpacity() },
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Text("${(state.layer.opacity * 100).toInt()}%", fontSize = 12.sp, modifier = Modifier.width(36.dp))
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.SpaceEvenly) {
            ActionButton(Icons.Filled.Add, "New") { state.addLayer() }
            ActionButton(Icons.Filled.ContentCopy, "Duplicate") { state.duplicateLayer() }
            ActionButton(Icons.Filled.ArrowUpward, "Up", state.currentLayer < layers.size - 1) { state.moveLayer(1) }
            ActionButton(Icons.Filled.ArrowDownward, "Down", state.currentLayer > 0) { state.moveLayer(-1) }
            ActionButton(Icons.Filled.CallMerge, "Merge ↓", state.currentLayer > 0) { state.mergeDown() }
            ActionButton(Icons.Filled.DriveFileRenameOutline, "Rename") { onRename(state.currentLayer) }
            ActionButton(Icons.Filled.Delete, "Delete", layers.size > 1) { state.deleteLayer() }
        }
    }
}

/** Primary/secondary color stack: tap the big one to mix, tap the swap arrow to exchange. */
@Composable
fun ColorStack(state: EditorState, onOpenPicker: () -> Unit) {
    Box(Modifier.size(54.dp)) {
        Swatch(state.secondary, false, Modifier.align(Alignment.BottomEnd).border(2.dp, MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp)).clickable { state.swapColors() }, size = 30)
        Swatch(state.primary, false, Modifier.align(Alignment.TopStart).border(2.dp, MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp)).clickable(onClick = onOpenPicker), size = 38)
        Surface(
            shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.align(Alignment.BottomStart).size(18.dp).clickable { state.swapColors() },
        ) { Icon(Icons.Filled.SwapVert, "Swap colors", Modifier.padding(2.dp)) }
    }
}

/** Circular palette swatch, like the reference app; a ring marks the current color. */
@Composable
fun RoundSwatch(color: Int, selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier.size(if (selected) 40.dp else 32.dp).clip(CircleShape)
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
    ) { ColorBox(color, Modifier.matchParentSize()) }
}
