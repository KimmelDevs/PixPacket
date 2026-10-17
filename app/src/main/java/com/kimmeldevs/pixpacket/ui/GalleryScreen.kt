package com.kimmeldevs.pixpacket.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kimmeldevs.pixpacket.R
import com.kimmeldevs.pixpacket.io.Imported
import com.kimmeldevs.pixpacket.io.Importer
import com.kimmeldevs.pixpacket.io.Library
import com.kimmeldevs.pixpacket.io.ProjectInfo
import com.kimmeldevs.pixpacket.io.ProjectStore
import com.kimmeldevs.pixpacket.io.SheetFormat
import com.kimmeldevs.pixpacket.model.Sprite
import com.kimmeldevs.pixpacket.settings.LocalSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class SortBy(val label: String) { RECENT("Recently edited"), NAME("Name"), SIZE("Canvas size") }

@Composable
fun GalleryScreen(
    onOpen: (String) -> Unit,
    onCreate: (name: String, w: Int, h: Int, whiteBg: Boolean, folder: String?) -> Unit,
    onImported: (name: String, sprite: Sprite, folder: String?) -> Unit,
    onSettings: () -> Unit,
) {
    val ctx = LocalContext.current
    val settings = LocalSettings.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var refresh by remember { mutableIntStateOf(0) }
    var projects by remember { mutableStateOf<List<ProjectInfo>?>(null) }
    var library by remember { mutableStateOf(Library.load(ctx)) }
    var folder by rememberSaveable { mutableStateOf<String?>(null) }
    var sort by rememberSaveable { mutableStateOf(SortBy.RECENT) }
    var showNew by remember { mutableStateOf(false) }
    var newFolderFor by remember { mutableStateOf<ProjectInfo?>(null) } // project to move after creating, if any
    var showNewFolder by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<ProjectInfo?>(null) }
    var deleting by remember { mutableStateOf<ProjectInfo?>(null) }
    var moving by remember { mutableStateOf<ProjectInfo?>(null) }
    var renamingFolder by remember { mutableStateOf<String?>(null) }
    var deletingFolder by remember { mutableStateOf<String?>(null) }
    var pendingImage by remember { mutableStateOf<Imported.Image?>(null) }
    var busy by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var showTrash by rememberSaveable { mutableStateOf(false) }
    var trash by remember { mutableStateOf<List<ProjectInfo>>(emptyList()) }
    var confirmEmpty by remember { mutableStateOf(false) }

    LaunchedEffect(refresh) {
        projects = withContext(Dispatchers.IO) { ProjectStore.list(ctx) }
        trash = withContext(Dispatchers.IO) { ProjectStore.listTrash(ctx) }
        library = Library.load(ctx)
        if (folder != null && folder !in library.folders) folder = null
    }
    BackHandler(enabled = folder != null && !showTrash) { folder = null }
    if (showTrash) {
        RecentlyDeleted(
            trash,
            onBack = { showTrash = false },
            onRestore = { p -> ProjectStore.restore(ctx, p.id); refresh++; scope.launch { snackbar.showSnackbar("Restored \"${p.name}\"") } },
            onDeleteForever = { p -> ProjectStore.deleteForever(ctx, p.id); refresh++ },
            onEmpty = { confirmEmpty = true },
        )
        if (confirmEmpty) AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text("Empty Recently deleted?") },
            text = { Text("${trash.size} sprite(s) will be gone for good. This can't be undone.") },
            confirmButton = { TextButton(onClick = { ProjectStore.emptyTrash(ctx); confirmEmpty = false; refresh++ }) { Text("Delete all", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text("Cancel") } },
        )
        return
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { Importer.open(ctx, uri) } }
            busy = false
            result.onSuccess { r ->
                when (r) {
                    is Imported.Document -> onImported(r.name, r.sprite, folder)
                    is Imported.Image -> pendingImage = r
                }
            }.onFailure { snackbar.showSnackbar("Couldn't open file: ${it.message}") }
        }
    }

    fun sorted(list: List<ProjectInfo>) = when (sort) {
        SortBy.RECENT -> list
        SortBy.NAME -> list.sortedBy { it.name.lowercase() }
        SortBy.SIZE -> list.sortedByDescending { it.width * it.height }
    }
    val all = projects.orEmpty()
    val visible = sorted(all.filter { it.folder == folder })

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 110.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.statusBarsPadding(),
        ) {
            header {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                    if (folder == null) {
                        Image(painterResource(R.drawable.logo_wren), null, Modifier.size(44.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("PixPacket", fontSize = 26.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                            Text("Pixel art & animation", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        IconButton(onClick = { folder = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "All sprites") }
                        Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(folder!!, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        var fm by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { fm = true }) { Icon(Icons.Filled.MoreVert, "Folder options") }
                            DropdownMenu(fm, { fm = false }) {
                                DropdownMenuItem(text = { Text("Rename folder") }, onClick = { fm = false; renamingFolder = folder })
                                DropdownMenuItem(text = { Text("Delete folder") }, onClick = { fm = false; deletingFolder = folder })
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Sort") }
                        DropdownMenu(sortMenu, { sortMenu = false }) {
                            SortBy.entries.forEach { s ->
                                DropdownMenuItem(text = { Text(s.label, fontWeight = if (s == sort) FontWeight.Bold else FontWeight.Normal) }, onClick = { sort = s; sortMenu = false })
                            }
                        }
                    }
                    IconButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) { Icon(Icons.Filled.Upload, "Import file") }
                    IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Settings") }
                }
            }

            if (folder == null) {
                header { SectionTitle("Folders") }
                items(library.folders, key = { "f:$it" }) { f ->
                    FolderCard(f, all.filter { it.folder == f }, onOpen = { folder = f },
                        onRename = { renamingFolder = f }, onDelete = { deletingFolder = f })
                }
                item(key = "newfolder") { NewFolderCard { showNewFolder = true } }
                if (trash.isNotEmpty()) item(key = "trash") { TrashCard(trash.size) { showTrash = true } }
                header { SectionTitle(if (library.folders.isEmpty()) "Sprites" else "Not in a folder") }
            }

            if (projects != null && visible.isEmpty()) {
                header {
                    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Brush, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(10.dp))
                        Text(if (folder == null) "No sprites yet" else "This folder is empty", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (folder == null) "Start a new canvas or import a PNG, GIF, .aseprite or .piskel file."
                            else "Create a sprite here, or move one in from its ⋮ menu.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { onCreate("Untitled", settings.defaultWidth, settings.defaultHeight, false, folder) }, modifier = Modifier.height(48.dp)) { Text("Start drawing") }
                            TextButton(onClick = { importLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.height(48.dp)) { Text("Import") }
                        }
                    }
                }
            }
            items(visible, key = { it.id }) { p ->
                ProjectCard(
                    p, onOpen = { onOpen(p.id) }, onRename = { renaming = p },
                    onDuplicate = { scope.launch { withContext(Dispatchers.IO) { ProjectStore.duplicate(ctx, p.id) }; refresh++ } },
                    onMove = { moving = p },
                    onDelete = {
                        if (settings.confirmDelete) deleting = p
                        else { ProjectStore.delete(ctx, p.id); refresh++; scope.launch { snackbar.showSnackbar("Moved to Recently deleted") } }
                    },
                )
            }
        }
        ExtendedFloatingActionButton(
            onClick = { showNew = true },
            icon = { Icon(Icons.Filled.Add, null) },
            text = { Text("New sprite") },
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp),
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        if (busy) CircularProgressIndicator(Modifier.align(Alignment.Center))
    }

    if (showNew) NewSpriteDialog(settings.defaultWidth, settings.defaultHeight, onDismiss = { showNew = false }) { n, w, h, bg ->
        showNew = false; onCreate(n, w, h, bg, folder)
    }
    if (showNewFolder) RenameDialog("New folder", "", { showNewFolder = false; newFolderFor = null }) { name ->
        Library.update(ctx) { if (name !in it.folders) it.folders.add(name); newFolderFor?.let { p -> it.assign(p.id, name) } }
        showNewFolder = false; newFolderFor = null; refresh++
    }
    renaming?.let { p ->
        RenameDialog("Rename sprite", p.name, { renaming = null }) { ProjectStore.rename(ctx, p.id, it); renaming = null; refresh++ }
    }
    moving?.let { p ->
        MoveToFolderDialog(library.folders, p.folder, onDismiss = { moving = null },
            onNewFolder = { newFolderFor = p; moving = null; showNewFolder = true }) { f ->
            Library.update(ctx) { it.assign(p.id, f) }; moving = null; refresh++
        }
    }
    renamingFolder?.let { f ->
        RenameDialog("Rename folder", f, { renamingFolder = null }) { n ->
            Library.update(ctx) { it.renameFolder(f, n) }
            if (folder == f) folder = n
            renamingFolder = null; refresh++
        }
    }
    deletingFolder?.let { f ->
        AlertDialog(
            onDismissRequest = { deletingFolder = null },
            title = { Text("Delete folder \"$f\"?") },
            text = { Text("The sprites inside are kept and moved back to the main gallery.") },
            confirmButton = { TextButton(onClick = { Library.update(ctx) { it.deleteFolder(f) }; if (folder == f) folder = null; deletingFolder = null; refresh++ }) { Text("Delete folder") } },
            dismissButton = { TextButton(onClick = { deletingFolder = null }) { Text("Cancel") } },
        )
    }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \"${p.name}\"?") },
            text = { Text("It moves to Recently deleted, where you can restore it for ${ProjectStore.TRASH_DAYS} days.") },
            confirmButton = { TextButton(onClick = { ProjectStore.delete(ctx, p.id); deleting = null; refresh++ }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
    pendingImage?.let { img ->
        ImportImageDialog(img.name, img.width, img.height, asLayer = false, onDismiss = { pendingImage = null }) { fw, fh ->
            pendingImage = null
            val frames = if (fw == img.width && fh == img.height) listOf(img.pixels) else SheetFormat.slice(img.pixels, img.width, img.height, fw, fh)
            onImported(img.name, Sprite.fromFrames(fw, fh, frames, settings.defaultFps), folder)
        }
    }
}

private fun LazyGridScope.header(content: @Composable () -> Unit) = item(span = { GridItemSpan(maxLineSpan) }) { content() }

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderCard(name: String, items: List<ProjectInfo>, onOpen: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.combinedClickable(onClick = onOpen, onLongClick = { menu = true })) {
            // 2x2 preview of the newest sprites in the folder.
            Column(Modifier.fillMaxWidth().aspectRatio(1.4f).padding(10.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                for (r in 0..1) Row(Modifier.weight(1f)) {
                    for (c in 0..1) {
                        val p = items.getOrNull(r * 2 + c)
                        Box(Modifier.weight(1f).fillMaxSize().padding(3.dp), contentAlignment = Alignment.Center) {
                            p?.thumb?.let {
                                Image(it.asImageBitmap(), null, Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Fit, filterQuality = FilterQuality.None)
                            }
                        }
                    }
                }
            }
            Row(Modifier.padding(start = 12.dp, end = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Folder, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${items.size} sprite" + if (items.size == 1) "" else "s", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Folder options") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                        DropdownMenuItem(text = { Text("Delete folder") }, onClick = { menu = false; onDelete() })
                    }
                }
            }
        }
    }
}

@Composable
private fun NewFolderCard(onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.background,
        modifier = Modifier.border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))) {
        Column(Modifier.fillMaxWidth().aspectRatio(1.05f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Filled.CreateNewFolder, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text("New folder", fontWeight = FontWeight.Bold)
            Text("Group your sprites", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectCard(p: ProjectInfo, onOpen: () -> Unit, onRename: () -> Unit, onDuplicate: () -> Unit, onMove: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.combinedClickable(onClick = onOpen, onLongClick = { menu = true })) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).padding(10.dp).clip(RoundedCornerShape(10.dp))) {
                PixelThumb(p.width, p.height, Modifier.matchParentSize()) { IntArray(p.width * p.height) }
                p.thumb?.let {
                    Image(it.asImageBitmap(), p.name, Modifier.matchParentSize(), contentScale = ContentScale.Fit, filterQuality = FilterQuality.None)
                }
            }
            Row(Modifier.padding(start = 12.dp, end = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${p.width}×${p.height}" + if (p.frames > 1) " · ${p.frames} frames" else "",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Sprite options") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                        DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menu = false; onDuplicate() })
                        DropdownMenuItem(text = { Text("Move to folder…") }, onClick = { menu = false; onMove() })
                        DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDelete() })
                    }
                }
            }
        }
    }
}

@Composable
private fun TrashCard(count: Int, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().aspectRatio(1.05f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Filled.DeleteOutline, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text("Recently deleted", fontWeight = FontWeight.Bold)
            Text("$count sprite" + (if (count == 1) "" else "s") + " · ${ProjectStore.TRASH_DAYS} days", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RecentlyDeleted(
    items: List<ProjectInfo>,
    onBack: () -> Unit,
    onRestore: (ProjectInfo) -> Unit,
    onDeleteForever: (ProjectInfo) -> Unit,
    onEmpty: () -> Unit,
) {
    BackHandler(onBack = onBack)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 40.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding(),
    ) {
        header {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Column(Modifier.weight(1f)) {
                    Text("Recently deleted", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Kept for ${ProjectStore.TRASH_DAYS} days, then removed", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (items.isNotEmpty()) TextButton(onClick = onEmpty) { Text("Empty", color = MaterialTheme.colorScheme.error) }
            }
        }
        if (items.isEmpty()) header {
            Text("Nothing here.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
        }
        items(items, key = { it.id }) { p ->
            val daysLeft = (ProjectStore.TRASH_DAYS - (System.currentTimeMillis() - p.deletedAt) / (24L * 3600 * 1000)).coerceAtLeast(0)
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column {
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).padding(10.dp).clip(RoundedCornerShape(10.dp))) {
                        PixelThumb(p.width, p.height, Modifier.matchParentSize()) { IntArray(p.width * p.height) }
                        p.thumb?.let { Image(it.asImageBitmap(), p.name, Modifier.matchParentSize(), contentScale = ContentScale.Fit, filterQuality = FilterQuality.None) }
                    }
                    Text(p.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 12.dp))
                    Text("$daysLeft day" + (if (daysLeft == 1L) "" else "s") + " left", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp))
                    Row(Modifier.fillMaxWidth().padding(4.dp)) {
                        TextButton(onClick = { onRestore(p) }, modifier = Modifier.weight(1f)) { Text("Restore") }
                        TextButton(onClick = { onDeleteForever(p) }, modifier = Modifier.weight(1f)) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}
