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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Opacity
import com.kimmeldevs.pixpacket.settings.LocalSettings
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
import com.kimmeldevs.pixpacket.model.ColorUtil
import com.kimmeldevs.pixpacket.io.PaletteStore
import com.kimmeldevs.pixpacket.io.UserPalette
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.HorizontalDivider

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
    val cells = LocalSettings.current.checker.cellsAcross
    val checker = remember(w, h, cells) { PixelBuffer(w, h).also { it.update(checkerPixels(w, h, cells)) } }
    Canvas(modifier) {
        val k = minOf(size.width / w, size.height / h)
        val dw = (w * k).toInt().coerceAtLeast(1); val dh = (h * k).toInt().coerceAtLeast(1)
        val o = IntOffset(((size.width - dw) / 2).toInt(), ((size.height - dh) / 2).toInt())
        val px = pixels()
        if (px.size != w * h) return@Canvas // size changed; a recomposition with the new size is coming
        buf.update(px)
        drawPixels(checker, o, IntSize(dw, dh))
        drawPixels(buf, o, IntSize(dw, dh))
    }
}

// endregion

@Composable
fun ToolsPanel(state: EditorState, onPicked: (sameTool: Boolean) -> Unit) {
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
                                val same = state.tool == t; state.tool = t; onPicked(same)
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
            Text(state.tool.hint, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Which palette the Colors panel shows. */
sealed interface PaletteSel {
    data class Preset(val index: Int) : PaletteSel
    data class User(val index: Int) : PaletteSel
    data object SpriteColors : PaletteSel
    data object Recent : PaletteSel
}

/** Adds [color] to the selected user palette (or the first one, creating "My palette" if needed). */
fun addToUserPalette(list: List<UserPalette>, sel: PaletteSel, color: Int): Pair<List<UserPalette>, Int> {
    val target = (sel as? PaletteSel.User)?.index?.takeIf { it in list.indices } ?: 0
    if (list.isEmpty()) return listOf(UserPalette("My palette", listOf(color))) to 0
    val p = list[target]
    if (color in p.colors) return list to target
    return list.toMutableList().also { it[target] = p.copy(colors = p.colors + color) } to target
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ColorsPanel(
    state: EditorState,
    sel: PaletteSel,
    onSelect: (PaletteSel) -> Unit,
    userPalettes: List<UserPalette>,
    onUserPalettes: (List<UserPalette>) -> Unit,
    onOpenPicker: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val selUser = (sel as? PaletteSel.User)?.index?.takeIf { it in userPalettes.indices }
    val title = when (sel) {
        is PaletteSel.Preset -> Palettes.presets.getOrNull(sel.index)?.name ?: "Palette"
        is PaletteSel.User -> userPalettes.getOrNull(sel.index)?.name ?: "Palette"
        PaletteSel.SpriteColors -> "Sprite colors"
        PaletteSel.Recent -> "Recent"
    }
    val colors: List<Int> = when (sel) {
        is PaletteSel.Preset -> Palettes.presets.getOrNull(sel.index)?.colors.orEmpty()
        is PaletteSel.User -> userPalettes.getOrNull(sel.index)?.colors.orEmpty()
        PaletteSel.SpriteColors -> remember(state.version) { state.usedColors(512) }
        PaletteSel.Recent -> state.recentColors
    }
    var menu by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    var editing by remember(sel) { mutableStateOf(false) }
    var naming by remember { mutableStateOf<String?>(null) } // "new", "new-with-color", "rename"
    var choosing by remember { mutableStateOf(false) }

    fun newPalette(name: String, cols: List<Int>) {
        val list = userPalettes + UserPalette(name, cols)
        onUserPalettes(list); onSelect(PaletteSel.User(list.lastIndex))
    }

    Column(Modifier.padding(vertical = 6.dp)) {
        Row(Modifier.padding(start = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                FilterChip(
                    selected = true, onClick = { menu = true },
                    label = { Text("$title  ▾", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(Icons.Filled.Palette, null, Modifier.size(18.dp)) },
                )
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (userPalettes.isNotEmpty()) {
                        Text("Your palettes", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                        userPalettes.forEachIndexed { i, up ->
                            DropdownMenuItem(text = { Text("${up.name}  (${up.colors.size})") }, onClick = { onSelect(PaletteSel.User(i)); menu = false })
                        }
                    }
                    DropdownMenuItem(
                        text = { Text("New palette…", color = MaterialTheme.colorScheme.primary) },
                        leadingIcon = { Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.primary) },
                        onClick = { menu = false; naming = "new" },
                    )
                    HorizontalDivider()
                    Text("Presets", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                    Palettes.presets.forEachIndexed { i, pr ->
                        DropdownMenuItem(text = { Text("${pr.name}  (${pr.colors.size})") }, onClick = { onSelect(PaletteSel.Preset(i)); menu = false })
                    }
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Sprite colors") }, onClick = { onSelect(PaletteSel.SpriteColors); menu = false })
                    DropdownMenuItem(text = { Text("Recent") }, onClick = { onSelect(PaletteSel.Recent); menu = false })
                }
            }
            if (selUser != null) {
                IconButton(onClick = { editing = !editing }) {
                    Icon(
                        if (editing) Icons.Filled.Check else Icons.Filled.Edit, if (editing) "Done editing" else "Edit palette",
                        tint = if (editing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            IconButton(onClick = {
                if (selUser != null) {
                    val (list, _) = addToUserPalette(userPalettes, sel, state.primary)
                    if (list === userPalettes) state.message = "That color is already here" else onUserPalettes(list)
                } else choosing = true // presets can't change: ask where to save it
            }) { Icon(Icons.Filled.Add, "Save current color to a palette") }
            Box {
                IconButton(onClick = { more = true }) { Icon(Icons.Filled.MoreVert, "Palette options") }
                DropdownMenu(more, { more = false }) {
                    DropdownMenuItem(text = { Text("New empty palette") }, onClick = { more = false; naming = "new" })
                    DropdownMenuItem(text = { Text("New from sprite colors") }, onClick = {
                        more = false; newPalette(PaletteStore.freshName(userPalettes, "${state.name} colors"), state.usedColors(512))
                    })
                    DropdownMenuItem(text = { Text("Duplicate this palette") }, enabled = colors.isNotEmpty(), onClick = {
                        more = false; newPalette(PaletteStore.freshName(userPalettes, "$title copy"), colors.distinct())
                    })
                    if (selUser != null) {
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { more = false; naming = "rename" })
                        DropdownMenuItem(text = { Text("Sort by hue") }, onClick = {
                            more = false
                            onUserPalettes(userPalettes.toMutableList().also { l ->
                                l[selUser] = l[selUser].copy(colors = l[selUser].colors.sortedWith(compareBy({ ColorUtil.toHsv(it)[1] < 0.1f }, { ColorUtil.toHsv(it)[0] }, { ColorUtil.toHsv(it)[2] })))
                            })
                        })
                        DropdownMenuItem(text = { Text("Delete palette", color = MaterialTheme.colorScheme.error) }, onClick = {
                            more = false
                            onUserPalettes(userPalettes.filterIndexed { i, _ -> i != selUser })
                            onSelect(PaletteSel.Preset(0))
                        })
                    }
                }
            }
            FilterChip(selected = false, onClick = onOpenPicker, label = { Text("Mix") })
        }

        // Unlimited colors: 16 per page (2 rows x 8), swipe sideways for more.
        // Your own palettes end with an add slot.
        val cells: List<Int?> = if (selUser != null && !editing) colors + listOf<Int?>(null) else colors
        val pages = cells.chunked(16)
        val pager = key(sel) { rememberPagerState { pages.size.coerceAtLeast(1) } }
        var lastCount by remember(sel) { mutableIntStateOf(cells.size) }
        LaunchedEffect(cells.size) {
            if (cells.size > lastCount && pages.isNotEmpty()) pager.animateScrollToPage(pages.size - 1)
            lastCount = cells.size
        }
        if (cells.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
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
                                                    val (list, _) = addToUserPalette(userPalettes, sel, state.primary)
                                                    if (list === userPalettes) state.message = "That color is already here" else onUserPalettes(list)
                                                },
                                            contentAlignment = Alignment.Center,
                                        ) { Icon(Icons.Filled.Add, "Add current color", Modifier.size(20.dp)) }
                                    } else if (editing && selUser != null) {
                                        Box(contentAlignment = Alignment.TopEnd, modifier = Modifier.clickable {
                                            onUserPalettes(userPalettes.toMutableList().also { l -> l[selUser] = l[selUser].copy(colors = l[selUser].colors - c) })
                                        }) {
                                            RoundSwatch(c, selected = false)
                                            Box(Modifier.size(16.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error), contentAlignment = Alignment.Center) {
                                                Icon(Icons.Filled.Close, "Remove color", Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onError)
                                            }
                                        }
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
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    if (pages.size <= 10) repeat(pages.size) { i ->
                        Box(
                            Modifier.padding(horizontal = 3.dp).size(if (i == pager.currentPage) 8.dp else 6.dp).clip(CircleShape)
                                .background(if (i == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                        )
                    } else Text("${pager.currentPage + 1} / ${pages.size}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text(
            when {
                editing -> "Tap a color to remove it · ✓ when done"
                pages.size > 1 -> "${colors.size} colors · swipe for more · long-press = secondary"
                else -> "Tap = primary  ·  Long-press = secondary"
            },
            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp),
        )
    }

    if (choosing) SaveColorDialog(
        color = state.primary, palettes = userPalettes,
        onDismiss = { choosing = false },
        onNew = { choosing = false; naming = "new-with-color" },
    ) { i ->
        choosing = false
        val p = userPalettes[i]
        if (state.primary !in p.colors) onUserPalettes(userPalettes.toMutableList().also { it[i] = p.copy(colors = p.colors + state.primary) })
        onSelect(PaletteSel.User(i)) // show where it went
    }

    when (naming) {
        "new" -> RenameDialog("New palette", PaletteStore.freshName(userPalettes), { naming = null }) { n -> naming = null; newPalette(n, emptyList()) }
        "new-with-color" -> RenameDialog("New palette", PaletteStore.freshName(userPalettes), { naming = null }) { n -> naming = null; newPalette(n, listOf(state.primary)) }
        "rename" -> if (selUser != null) RenameDialog("Rename palette", userPalettes[selUser].name, { naming = null }) { n ->
            naming = null
            onUserPalettes(userPalettes.toMutableList().also { l -> l[selUser] = l[selUser].copy(name = n) })
        }
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
                        state.version
                        if (f < state.sprite.frameCount) state.sprite.composite(f) else IntArray(state.width * state.height)
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
            ActionButton(Icons.Filled.SwapHoriz, "Reverse", state.frameCount > 1) { state.reverseFrames() }
            ActionButton(Icons.Filled.Delete, "Delete", state.frameCount > 1) { state.deleteFrame() }
        }
    }
}

@Composable
private fun SmallIcon(icon: ImageVector, desc: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(40.dp)) { Icon(icon, desc, Modifier.size(21.dp)) }
}

@Composable
fun LayersPanel(state: EditorState, onRename: (Int) -> Unit) {
    state.version
    val layers = state.sprite.layers
    val scroll = rememberScrollState()
    Column(Modifier.padding(top = 2.dp, bottom = 6.dp)) {
        // Compact toolbar: the most common layer actions, one tap each.
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (layers.size == 1) "1 layer" else "${layers.size} layers", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
            )
            SmallIcon(Icons.Filled.Add, "New layer") { state.addLayer() }
            SmallIcon(Icons.Filled.ContentCopy, "Duplicate layer") { state.duplicateLayer() }
            SmallIcon(Icons.Filled.ArrowUpward, "Move layer up", state.currentLayer < layers.size - 1) { state.moveLayer(1) }
            SmallIcon(Icons.Filled.ArrowDownward, "Move layer down", state.currentLayer > 0) { state.moveLayer(-1) }
            SmallIcon(Icons.Filled.CallMerge, "Merge down", state.currentLayer > 0) { state.mergeDown() }
            SmallIcon(Icons.Filled.Delete, "Delete layer", layers.size > 1) { state.deleteLayer() }
        }
        // A plain scrolling column: layer counts are small, and it always sizes to its rows.
        Column(
            Modifier.fillMaxWidth().heightIn(max = 210.dp).verticalScroll(scroll).padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (idx in layers.indices) LayerRow(state, layers.size - 1 - idx, onRename) // top layer first
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LayerRow(state: EditorState, i: Int, onRename: (Int) -> Unit) {
    val l = state.sprite.layers.getOrNull(i) ?: return
    val sel = i == state.currentLayer
    var menu by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val onCard = if (sel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
            .combinedClickable(
                onClick = { state.selectLayer(i) },
                onDoubleClick = { onRename(i) },
                onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); state.selectLayer(i); menu = true },
            )
    ) {
        Row(Modifier.height(50.dp).padding(end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { state.toggleVisible(i) }, modifier = Modifier.size(44.dp)) {
                Icon(
                    if (l.visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, if (l.visible) "Hide layer" else "Show layer",
                    tint = if (l.visible) onCard else MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp),
                )
            }
            PixelThumb(state.width, state.height, Modifier.size(38.dp).clip(RoundedCornerShape(6.dp))) {
                state.version; l.cels[state.currentFrame.coerceAtMost(l.cels.size - 1)]
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    l.name, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = onCard.copy(alpha = if (l.visible) 1f else 0.5f),
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                )
                val info = listOfNotNull(
                    if (l.opacity < 1f) "${(l.opacity * 100).toInt()}%" else null,
                    if (l.locked) "locked" else null,
                    if (!l.visible) "hidden" else null,
                ).joinToString(" · ")
                if (info.isNotEmpty()) Text(info, fontSize = 11.sp, color = onCard.copy(alpha = 0.7f))
            }
            IconButton(onClick = { state.toggleLock(i) }, modifier = Modifier.size(40.dp)) {
                Icon(
                    if (l.locked) Icons.Filled.Lock else Icons.Filled.LockOpen, if (l.locked) "Unlock layer" else "Lock layer",
                    tint = if (l.locked) MaterialTheme.colorScheme.secondary else onCard.copy(alpha = 0.55f), modifier = Modifier.size(19.dp),
                )
            }
            Box {
                IconButton(onClick = { state.selectLayer(i); menu = true }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.MoreVert, "Layer options", tint = onCard, modifier = Modifier.size(20.dp))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Filled.DriveFileRenameOutline, null) }, onClick = { menu = false; onRename(i) })
                    DropdownMenuItem(text = { Text("Duplicate") }, leadingIcon = { Icon(Icons.Filled.ContentCopy, null) }, onClick = { menu = false; state.duplicateLayer() })
                    DropdownMenuItem(text = { Text("Solo / show all") }, leadingIcon = { Icon(Icons.Filled.Visibility, null) }, onClick = { menu = false; state.soloLayer(i) })
                    DropdownMenuItem(text = { Text("Merge down") }, leadingIcon = { Icon(Icons.Filled.CallMerge, null) }, enabled = i > 0, onClick = { menu = false; state.mergeDown() })
                    DropdownMenuItem(text = { Text("Clear all frames") }, leadingIcon = { Icon(Icons.Filled.Remove, null) }, onClick = { menu = false; state.clearLayerAllFrames() })
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        enabled = state.sprite.layers.size > 1, onClick = { menu = false; state.deleteLayer() },
                    )
                }
            }
        }
        if (sel) {
            // Opacity sits right on the selected layer.
            Row(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 4.dp).height(32.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Opacity, "Opacity", Modifier.size(16.dp), tint = onCard.copy(alpha = 0.7f))
                Slider(
                    value = l.opacity,
                    onValueChange = { state.setOpacityLive(i, it) },
                    onValueChangeFinished = { state.commitOpacity() },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Text("${(l.opacity * 100).toInt()}%", fontSize = 12.sp, color = onCard, modifier = Modifier.width(38.dp))
            }
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

/** "Save color to…": lists your palettes (with a preview) plus a new-palette option. */
@Composable
private fun SaveColorDialog(color: Int, palettes: List<UserPalette>, onDismiss: () -> Unit, onNew: () -> Unit, onPick: (Int) -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundSwatch(color, selected = false)
                Spacer(Modifier.width(12.dp))
                Text("Save color to…")
            }
        },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (palettes.isEmpty()) Text("You don't have any palettes yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                palettes.forEachIndexed { i, p ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { onPick(i) }.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (color in p.colors) "Already has this color" else "${p.colors.size} colors", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                            p.colors.takeLast(5).forEach { c -> Box(Modifier.size(20.dp).clip(CircleShape).border(1.dp, MaterialTheme.colorScheme.surface, CircleShape)) { ColorBox(c, Modifier.matchParentSize()) } }
                        }
                    }
                }
                androidx.compose.material3.TextButton(onClick = onNew) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("New palette with this color") }
            }
        },
        confirmButton = {},
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
