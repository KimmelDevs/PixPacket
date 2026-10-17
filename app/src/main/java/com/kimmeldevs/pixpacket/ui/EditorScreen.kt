package com.kimmeldevs.pixpacket.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BorderOuter
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.verticalScroll
import com.kimmeldevs.pixpacket.io.PaletteStore
import com.kimmeldevs.pixpacket.io.UserPalette
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kimmeldevs.pixpacket.editor.EditorState
import com.kimmeldevs.pixpacket.editor.Tool
import com.kimmeldevs.pixpacket.io.ExportOptions
import com.kimmeldevs.pixpacket.io.Exporter
import com.kimmeldevs.pixpacket.io.Imported
import com.kimmeldevs.pixpacket.io.Importer
import com.kimmeldevs.pixpacket.io.SheetFormat
import com.kimmeldevs.pixpacket.settings.LocalSettings
import com.kimmeldevs.pixpacket.ui.theme.LocalPixExtras
import com.kimmeldevs.pixpacket.io.Prefs
import com.kimmeldevs.pixpacket.io.ProjectStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class EditorDialog { NONE, RESIZE, EXPORT, RENAME_SPRITE, RENAME_LAYER, HELP, PICKER, GRID }

@Composable
fun EditorScreen(state: EditorState, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val viewport = remember { Viewport() }
    val snackbar = remember { SnackbarHostState() }
    var tab by remember { mutableStateOf<PanelTab?>(PanelTab.TOOLS) }
    var dialog by remember { mutableStateOf(EditorDialog.NONE) }
    var renameLayerIndex by remember { mutableIntStateOf(0) }
    var paletteSel by remember { mutableStateOf<PaletteSel>(PaletteSel.Preset(0)) }
    var userPalettes by remember { mutableStateOf(PaletteStore.load(ctx)) }
    fun setUserPalettes(l: List<UserPalette>) { userPalettes = l; PaletteStore.save(ctx, l) }
    var menuOpen by remember { mutableStateOf(false) }
    val settings = LocalSettings.current
    val extras = LocalPixExtras.current
    val left = settings.leftHanded
    var pendingImport by remember { mutableStateOf<Imported.Image?>(null) }
    var pendingSave by remember { mutableStateOf<ExportOptions?>(null) }

    // Keep the display awake while drawing, if enabled.
    val view = LocalView.current
    DisposableEffect(settings.keepScreenOn) {
        view.keepScreenOn = settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    fun save() {
        if (!state.dirty) return
        ProjectStore.save(ctx, state.projectId, state.name, state.sprite)
        state.markSaved()
    }

    // Autosave whenever the app goes to the background and when leaving the editor.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) save() }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs); save() }
    }
    // Also autosave a few seconds after edits stop.
    LaunchedEffect(state.version) {
        delay(4000)
        if (!state.dirty) return@LaunchedEffect
        val snap = state.sprite.deepCopy(); val name = state.name
        state.markSaved()
        withContext(Dispatchers.IO) { runCatching { ProjectStore.save(ctx, state.projectId, name, snap) } }
    }

    BackHandler {
        when {
            state.selection != null -> state.deselect()
            else -> { save(); onBack() }
        }
    }

    LaunchedEffect(state.message) {
        val m = state.message ?: return@LaunchedEffect
        state.message = null
        snackbar.currentSnackbarData?.dismiss()
        snackbar.showSnackbar(m)
    }

    // Main-canvas playback
    LaunchedEffect(state.playing, state.sprite.fps) {
        while (state.playing) {
            delay(1000L / state.sprite.fps)
            if (state.frameCount > 1) state.currentFrame = (state.currentFrame + 1) % state.frameCount
        }
    }

    // First-run tips
    LaunchedEffect(Unit) { if (settings.showTips) { dialog = EditorDialog.HELP; settings.showTips = false } }

    // Import any supported file into this sprite as a new layer (animations fill consecutive frames).
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { Importer.open(ctx, uri) } }
            r.onSuccess { imp ->
                when (imp) {
                    is Imported.Image -> pendingImport = imp
                    is Imported.Document -> {
                        val d = imp.sprite
                        state.importLayerFrames(List(d.frameCount) { d.composite(it) }, d.width, d.height, imp.name)
                        state.message = "Imported ${d.frameCount} frame(s) as a new layer"
                    }
                }
            }.onFailure { state.message = "Couldn't open file: ${it.message}" }
        }
    }

    // "Save to Files": the system picker decides where the file goes.
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri: Uri? ->
        val opts = pendingSave
        pendingSave = null
        if (uri != null && opts != null) {
            val snapshot = state.sprite.deepCopy(); val name = state.name
            scope.launch {
                withContext(Dispatchers.IO) { runCatching { Exporter.saveToUri(ctx, uri, snapshot, name, opts) } }
                    .onSuccess { state.message = "Saved ${Exporter.fileName(name, opts.kind)}" }
                    .onFailure { state.message = "Export failed: ${it.message}" }
            }
        }
    }

    val panel: @Composable () -> Unit = {
                    when (tab) {
                        PanelTab.TOOLS -> ToolsPanel(state, onPicked = { same -> if (same || settings.collapseToolsOnPick) tab = null })
                        PanelTab.COLORS -> ColorsPanel(
                            state, paletteSel, { paletteSel = it }, userPalettes, { setUserPalettes(it) },
                            onOpenPicker = { dialog = EditorDialog.PICKER },
                        )
                        PanelTab.FRAMES -> FramesPanel(state)
                        PanelTab.LAYERS -> LayersPanel(state, onRename = { renameLayerIndex = it; dialog = EditorDialog.RENAME_LAYER })
                        null -> Spacer(Modifier.height(1.dp))
                    }
    }
    val topBar: @Composable () -> Unit = {
        // ── Top bar ──
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { save(); onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to gallery") }
            Column(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable { dialog = EditorDialog.RENAME_SPRITE }.padding(horizontal = 6.dp, vertical = 2.dp)) {
                Text(state.name, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${state.width}×${state.height} · frame ${state.currentFrame + 1}/${state.frameCount} · ${state.layer.name}",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { state.undo() }, enabled = state.canUndo) { Icon(Icons.AutoMirrored.Filled.Undo, "Undo") }
            IconButton(onClick = { state.redo() }, enabled = state.canRedo) { Icon(Icons.AutoMirrored.Filled.Redo, "Redo") }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "More") }
                EditorMenu(
                    state, expanded = menuOpen, onDismiss = { menuOpen = false },
                    onResize = { dialog = EditorDialog.RESIZE },
                    onExport = { dialog = EditorDialog.EXPORT },
                    onImport = { importLauncher.launch(arrayOf("*/*")) },
                    onGrid = { dialog = EditorDialog.GRID },
                    onFit = { viewport.fitRequest++ },
                    onHelp = { dialog = EditorDialog.HELP },
                )
            }
        }

    }
    val canvasArea: @Composable (Modifier) -> Unit = { m ->
        // ── Canvas ──
        Box(m) {
            PixelCanvas(state, viewport)
            if (state.showPreview && state.frameCount > 1) {
                AnimationPreview(state, Modifier.align(if (left) Alignment.TopEnd else Alignment.TopStart).padding(10.dp))
            }
            Surface(
                onClick = { viewport.fitRequest++ },
                shape = RoundedCornerShape(16.dp),
                color = extras.overlay,
                modifier = Modifier.align(if (left) Alignment.TopStart else Alignment.TopEnd).padding(10.dp),
            ) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.ZoomOutMap, null, Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (viewport.scale >= 1f) "${viewport.scale.toInt()}×" else "${(viewport.scale * 100).toInt()}%", fontSize = 12.sp)
                }
            }
            if (state.precisionMode) {
                DrawButton(state, viewport, Modifier.align(if (left) Alignment.BottomStart else Alignment.BottomEnd).padding(18.dp))
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }

    }
    val controlsArea: @Composable (Modifier, Boolean) -> Unit = { m, land ->
        // ── Contextual bars ──
        Surface(m, color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
            Column {
                if (state.selection != null || (state.tool.isSelection && state.hasClipboard)) SelectionBar(state)
                OptionsBar(state, left, onOpenPicker = { dialog = EditorDialog.PICKER })
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (land) {
                    Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) { if (tab != null) panel() }
                } else {
                    AnimatedVisibility(tab != null, enter = expandVertically(), exit = shrinkVertically()) { panel() }
                }
                Dock(tab, state) { t ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    tab = if (tab == t) null else t
                }
            }
        }
    }

    // Landscape: canvas on one side, tools in a side column (on the left for left-handed users).
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    LaunchedEffect(landscape) { viewport.fitRequest++ }
    if (landscape) {
        Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().displayCutoutPadding()) {
            if (left) controlsArea(Modifier.width(360.dp).fillMaxHeight(), true)
            Column(Modifier.weight(1f).fillMaxHeight()) {
                topBar()
                canvasArea(Modifier.weight(1f).fillMaxWidth())
            }
            if (!left) controlsArea(Modifier.width(360.dp).fillMaxHeight(), true)
        }
        LaunchedEffect(Unit) { if (tab == null) tab = PanelTab.TOOLS }
    } else {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
            topBar()
            canvasArea(Modifier.weight(1f).fillMaxWidth())
            controlsArea(Modifier, false)
        }
    }

    when (dialog) {
        EditorDialog.PICKER -> ColorPickerSheet(
            initial = state.primary, recent = state.recentColors.toList(),
            onDismiss = { dialog = EditorDialog.NONE },
            onPick = { state.primary = it; state.addRecent(it); dialog = EditorDialog.NONE },
            onAddToPalette = {
                val (list, idx) = addToUserPalette(userPalettes, paletteSel, it)
                setUserPalettes(list)
                state.primary = it; state.message = "Added to ${list[idx].name}"
                paletteSel = PaletteSel.User(idx)
                dialog = EditorDialog.NONE
            },
        )
        EditorDialog.RESIZE -> ResizeDialog(
            state.width, state.height, onDismiss = { dialog = EditorDialog.NONE },
            onResize = { w, h, a -> state.resizeCanvas(w, h, a); viewport.fitRequest++; dialog = EditorDialog.NONE },
            onScale = { w, h -> state.scaleSprite(w, h); viewport.fitRequest++; dialog = EditorDialog.NONE },
        )
        EditorDialog.EXPORT -> ExportDialog(state.width, state.height, state.frameCount, state.sprite.layers.size, onDismiss = { dialog = EditorDialog.NONE }) { o, target ->
            dialog = EditorDialog.NONE
            val opts = ExportOptions(o.kind, o.scale, o.columns, state.currentFrame, o.perLayer)
            if (target == ExportTarget.FILES) {
                pendingSave = opts
                saveLauncher.launch(Exporter.fileName(state.name, o.kind))
            } else {
                val snapshot = state.sprite.deepCopy(); val name = state.name
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            if (target == ExportTarget.SHARE) { Exporter.share(ctx, snapshot, name, opts); null }
                            else Exporter.saveToGallery(ctx, snapshot, name, opts)
                        }
                    }
                    result.onSuccess { where -> if (where != null) state.message = "Saved to $where" }
                        .onFailure { state.message = "Export failed: ${it.message}" }
                }
            }
        }
        EditorDialog.GRID -> GridDialog(state.sprite.grid, state.showGrid, onDismiss = { dialog = EditorDialog.NONE }) { g, show ->
            state.setGrid(g); state.showGrid = show; dialog = EditorDialog.NONE
        }
        EditorDialog.RENAME_SPRITE -> RenameDialog("Rename sprite", state.name, { dialog = EditorDialog.NONE }) {
            state.name = it; dialog = EditorDialog.NONE
            ProjectStore.save(ctx, state.projectId, state.name, state.sprite)
        }
        EditorDialog.RENAME_LAYER -> RenameDialog("Rename layer", state.sprite.layers[renameLayerIndex].name, { dialog = EditorDialog.NONE }) {
            state.renameLayer(renameLayerIndex, it); dialog = EditorDialog.NONE
        }
        EditorDialog.HELP -> HelpDialog { dialog = EditorDialog.NONE }
        EditorDialog.NONE -> {}
    }

    pendingImport?.let { img ->
        ImportImageDialog(img.name, img.width, img.height, asLayer = true, onDismiss = { pendingImport = null }) { fw, fh ->
            pendingImport = null
            val frames = if (fw == img.width && fh == img.height) listOf(img.pixels) else SheetFormat.slice(img.pixels, img.width, img.height, fw, fh)
            state.importLayerFrames(frames, fw, fh, img.name)
            state.message = if (frames.size > 1) "Imported ${frames.size} frames as a new layer" else "Imported as a new layer"
        }
    }
}

@Composable
private fun Dock(tab: PanelTab?, state: EditorState, onTab: (PanelTab) -> Unit) {
    Row(Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        PanelTab.entries.forEach { t ->
            val sel = tab == t
            val icon: ImageVector = when (t) {
                PanelTab.TOOLS -> state.tool.icon
                PanelTab.COLORS -> Icons.Filled.Palette
                PanelTab.FRAMES -> Icons.Filled.Movie
                PanelTab.LAYERS -> Icons.Filled.Layers
            }
            Column(
                Modifier.weight(1f).padding(3.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (sel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    .clickable { onTab(t) }.padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (t == PanelTab.COLORS) {
                    Box(Modifier.size(22.dp).clip(CircleShape).border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)) {
                        ColorBox(state.primary, Modifier.matchParentSize())
                    }
                } else {
                    Icon(icon, t.label, tint = if (sel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                }
                val label = when (t) {
                    PanelTab.TOOLS -> state.tool.label
                    PanelTab.FRAMES -> "Frames ${state.frameCount}"
                    PanelTab.LAYERS -> "Layers ${state.sprite.layers.size}"
                    else -> t.label
                }
                Text(label, fontSize = 11.sp, maxLines = 1, color = if (sel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun OptionsBar(state: EditorState, leftHanded: Boolean, onOpenPicker: () -> Unit) {
    val t = state.tool
    Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!leftHanded) { ColorStack(state, onOpenPicker); Spacer(Modifier.width(8.dp)) }
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (t.usesSize) {
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { state.brushSize = (state.brushSize - 1).coerceAtLeast(1) }, modifier = Modifier.size(40.dp)) { Icon(Icons.Filled.Remove, "Smaller brush") }
                        Text("${state.brushSize}px", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { state.brushSize = (state.brushSize + 1).coerceAtMost(16) }, modifier = Modifier.size(40.dp)) { Icon(Icons.Filled.Add, "Bigger brush") }
                    }
                }
            }
            if (t == Tool.PENCIL || t == Tool.ERASER || t == Tool.DITHER) ToggleChip("Pixel-perfect", state.pixelPerfect) { state.pixelPerfect = !state.pixelPerfect }
            if (t == Tool.RECT || t == Tool.ELLIPSE) ToggleChip("Filled", state.filledShapes, Icons.Filled.FormatColorFill) { state.filledShapes = !state.filledShapes }
            if (t == Tool.BUCKET || t == Tool.WAND) ToggleChip("Contiguous", state.contiguous) { state.contiguous = !state.contiguous }
            if (t == Tool.SHADE) {
                ToggleChip("Lighten", state.lighten) { state.lighten = true }
                ToggleChip("Darken", !state.lighten) { state.lighten = false }
            }
            if (t.usesMirror) {
                ToggleChip("Mirror X", state.mirrorX) { state.mirrorX = !state.mirrorX }
                ToggleChip("Mirror Y", state.mirrorY) { state.mirrorY = !state.mirrorY }
            }
            if (t == Tool.MOVE) Text(if (state.selection != null) "Moving selection" else "Moving whole layer", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (t.isSelection && state.selection == null) {
                Text("Draw a selection on the canvas", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ToggleChip("Select all", false, Icons.Filled.SelectAll) { state.selectAll() }
            }
            if (t == Tool.GRADIENT || t == Tool.DITHER) Text("Primary → secondary", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (t == Tool.PICKER) Text("Tip: long-press works with any tool", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
        }
        if (leftHanded) { Spacer(Modifier.width(8.dp)); ColorStack(state, onOpenPicker) }
    }
}

@Composable
private fun SelectionBar(state: EditorState) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).horizontalScroll(rememberScrollState()).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val has = state.selection != null
        ActionButton(Icons.Filled.ContentCopy, "Copy", has) { state.copy() }
        ActionButton(Icons.Filled.ContentCut, "Cut", has) { state.cut() }
        ActionButton(Icons.Filled.ContentPaste, "Paste", state.hasClipboard) { state.paste() }
        ActionButton(Icons.Filled.Delete, "Erase", has) { state.deleteSelected() }
        ActionButton(Icons.Filled.FormatColorFill, "Fill", has) { state.fillSelection() }
        ActionButton(Icons.Filled.Flip, "Flip H", has) { state.flipSelection(true) }
        ActionButton(Icons.Filled.SwapVert, "Flip V", has) { state.flipSelection(false) }
        ActionButton(Icons.Filled.Rotate90DegreesCw, "Rotate", has) { state.rotateSelection() }
        ActionButton(Icons.Filled.BorderOuter, "Outline", has) { state.outline() }
        ActionButton(Icons.Filled.SelectAll, "Invert", has) { state.invertSelection() }
        ActionButton(Icons.Filled.Deselect, "Deselect", has) { state.deselect() }
    }
}

@Composable
private fun EditorMenu(
    state: EditorState, expanded: Boolean, onDismiss: () -> Unit,
    onResize: () -> Unit, onExport: () -> Unit, onImport: () -> Unit, onGrid: () -> Unit, onFit: () -> Unit, onHelp: () -> Unit,
) {
    @Composable
    fun item(label: String, icon: ImageVector? = null, checked: Boolean? = null, action: () -> Unit) = DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = icon?.let { { Icon(it, null) } },
        trailingIcon = if (checked == true) ({ Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary) }) else null,
        onClick = { action(); onDismiss() },
    )
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        item("Export / share…", Icons.Filled.FileDownload) { onExport() }
        item("Import file as layer", Icons.Filled.AddPhotoAlternate) { onImport() }
        HorizontalDivider()
        item("Precision cursor", checked = state.precisionMode) { state.precisionMode = !state.precisionMode }
        item("Show grid", checked = state.showGrid) { state.showGrid = !state.showGrid }
        item("Grid settings…", Icons.Filled.GridOn) { onGrid() }
        item("Tile preview", checked = state.tileMode) { state.tileMode = !state.tileMode }
        item("Onion skin", checked = state.onionSkin) { state.onionSkin = !state.onionSkin }
        item("Animation preview", checked = state.showPreview) { state.showPreview = !state.showPreview }
        item("Fit to screen", Icons.Filled.ZoomOutMap) { onFit() }
        HorizontalDivider()
        item("Canvas size / scale…", Icons.Filled.AspectRatio) { onResize() }
        item("Flip sprite horizontally") { state.flipSprite(true) }
        item("Flip sprite vertically") { state.flipSprite(false) }
        item("Rotate sprite 90°") { state.rotateSprite(); onFit() }
        item("Outline (primary color)", Icons.Filled.BorderOuter) { state.outline() }
        item("Replace primary → secondary", Icons.Filled.Brush) {
            state.replaceColorEverywhere(state.primary, state.secondary)
        }
        item("Crop to selection", Icons.Filled.Crop) { state.cropToSelection(); onFit() }
        item("Trim empty borders", Icons.Filled.CropFree) { state.trimToContent(); onFit() }
        item("Clear this cel", Icons.Filled.Delete) { state.clearCel() }
        HorizontalDivider()
        item("Gestures & tips", Icons.Filled.Help) { onHelp() }
    }
}

/** Big thumb-reach button used in precision mode: hold to apply the tool at the cursor. */
@Composable
private fun DrawButton(state: EditorState, vp: Viewport, modifier: Modifier) {
    var pressed by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    Box(
        modifier.size(76.dp).shadow(8.dp, CircleShape).clip(CircleShape)
            .background(if (pressed) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
            .pointerInput(state) {
                awaitEachGesture {
                    awaitFirstDown()
                    pressed = true
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    state.beginStroke(vp.cursor.x, vp.cursor.y)
                    waitForUpOrCancellation()
                    state.endStroke()
                    pressed = false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(state.tool.icon, "Draw", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(32.dp))
    }
}

/** Floating looping preview of the animation (Piskel-style). Tap to enlarge. */
@Composable
private fun AnimationPreview(state: EditorState, modifier: Modifier) {
    var frame by remember { mutableIntStateOf(0) }
    var big by remember { mutableStateOf(false) }
    LaunchedEffect(state.sprite.fps) {
        while (true) { delay(1000L / state.sprite.fps); frame++ }
    }
    val w = state.width; val h = state.height
    val buf = remember(w, h) { PixelBuffer(w, h) }
    val checker = remember(w, h) { PixelBuffer(w, h).also { it.update(checkerPixels(w, h)) } }
    val scratch = remember(w, h) { IntArray(w * h) }
    Box(
        modifier.size(if (big) 150.dp else 76.dp).shadow(6.dp, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp))
            .background(LocalPixExtras.current.canvasBg).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            .clickable { big = !big }.padding(4.dp)
    ) {
        Canvas(Modifier.matchParentSize()) {
            state.version
            val n = state.frameCount
            if (n == 0 || state.sprite.width != w || state.sprite.height != h) return@Canvas
            buf.update(state.sprite.composite(frame % n, scratch))
            val k = minOf(size.width / w, size.height / h)
            val dw = (w * k).toInt(); val dh = (h * k).toInt()
            val o = IntOffset(((size.width - dw) / 2).toInt(), ((size.height - dh) / 2).toInt())
            drawPixels(checker, o, IntSize(dw, dh))
            drawPixels(buf, o, IntSize(dw, dh))
        }
    }
}
