package com.siroha.relifemotion.ui.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.siroha.relifemotion.Graph
import com.siroha.relifemotion.data.AccessMode
import com.siroha.relifemotion.data.AudioItem
import com.siroha.relifemotion.data.LayerType
import com.siroha.relifemotion.data.MediaItem
import com.siroha.relifemotion.data.MediaRepo
import com.siroha.relifemotion.data.Project
import com.siroha.relifemotion.data.ThumbCache
import com.siroha.relifemotion.data.fmtTime
import com.siroha.relifemotion.priv.Privileged
import com.siroha.relifemotion.render.ShapePaths
import com.siroha.relifemotion.ui.common.IconBtn
import com.siroha.relifemotion.ui.home.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun Thumb(uri: Uri, video: Boolean, modifier: Modifier, scale: ContentScale = ContentScale.Crop) {
    val ctx = LocalContext.current
    val bmp by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) { ThumbCache.load(ctx, uri, video, 384)?.asImageBitmap() }
    }
    Box(modifier.background(Color(0xFF2A2E40))) {
        bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = scale) }
    }
}

@Composable
fun AddPanel(
    vm: EditorVM, onClose: () -> Unit, onText: () -> Unit, onFreehand: () -> Unit, onVector: () -> Unit,
    onOpenMedia: (MediaItem) -> Unit, onAudioAll: () -> Unit, modifier: Modifier
) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var permTick by remember { mutableIntStateOf(0) }
    var bucket by remember { mutableStateOf("Semua") }
    var bucketMenu by remember { mutableStateOf(false) }
    var pathDialog by remember { mutableStateOf(false) }
    var elementDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permTick++ }
    val docs = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        uris.forEach { runCatching { ctx.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
        if (uris.isNotEmpty()) vm.addMedia(uris)
    }

    val media by produceState(emptyList<MediaItem>(), permTick, tab) {
        value = if (tab == 1 && MediaRepo.hasVisual(ctx)) withContext(Dispatchers.IO) { MediaRepo.queryMedia(ctx) } else emptyList()
    }
    val audio by produceState(emptyList<AudioItem>(), permTick, tab) {
        value = if (tab == 2 && MediaRepo.hasAudio(ctx)) withContext(Dispatchers.IO) { MediaRepo.queryAudio(ctx) } else emptyList()
    }

    Row(modifier.background(cs.surfaceContainerLow)) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(Modifier.fillMaxWidth().height(50.dp)) {
                listOf("Bentuk" to Icons.Rounded.Category, "Media" to Icons.Rounded.Image, "Audio" to Icons.Rounded.MusicNote,
                    "Objek / Elemen" to Icons.Rounded.Widgets, "Template" to Icons.Rounded.Dashboard).forEachIndexed { i, (n, ic) ->
                    val sel = tab == i
                    Column(Modifier.weight(1f).fillMaxHeight().clickable { tab = i }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(ic, null, Modifier.size(20.dp), tint = if (sel) cs.primary else cs.onSurfaceVariant)
                        Text(n, fontSize = 8.sp, color = if (sel) cs.primary else cs.onSurfaceVariant, maxLines = 1, textAlign = TextAlign.Center)
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    0 -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ShapePaths.all.chunked(5).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { s ->
                                    Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(10.dp)).clickable { vm.addShape(s); onClose() }, contentAlignment = Alignment.Center) {
                                        ShapeIcon(s, Modifier.fillMaxSize().padding(6.dp))
                                    }
                                }
                                repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                    1 -> {
                        if (!MediaRepo.hasVisual(ctx)) PermissionCard("Izinkan akses foto & video untuk menampilkan galeri.") { permLauncher.launch(MediaRepo.visualPermissions()) }
                        else {
                            val buckets = listOf("Semua") + media.map { it.bucket }.distinct().sorted()
                            val shown = if (bucket == "Semua") media else media.filter { it.bucket == bucket }
                            Column(Modifier.fillMaxSize()) {
                                Box {
                                    Row(Modifier.padding(8.dp).clip(RoundedCornerShape(8.dp)).background(cs.surfaceContainerHigh).clickable { bucketMenu = true }.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(bucket, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        Icon(Icons.Rounded.ArrowDropDown, null)
                                    }
                                    DropdownMenu(expanded = bucketMenu, onDismissRequest = { bucketMenu = false }) {
                                        buckets.forEach { b -> DropdownMenuItem(text = { Text(b) }, onClick = { bucket = b; bucketMenu = false }) }
                                        HorizontalDivider()
                                        DropdownMenuItem(text = { Text("Telusuri berkas…") }, onClick = { bucketMenu = false; docs.launch(arrayOf("image/*", "video/*", "audio/*")) })
                                        DropdownMenuItem(text = { Text("Impor dari path (Shizuku/Root)…") }, onClick = { bucketMenu = false; pathDialog = true })
                                    }
                                }
                                LazyVerticalGrid(GridCells.Fixed(4), Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    items(shown, key = { it.uri.toString() }) { m ->
                                        Box(Modifier.aspectRatio(1f).clickable { onOpenMedia(m) }) {
                                            Thumb(m.uri, m.isVideo, Modifier.fillMaxSize())
                                            if (m.isVideo) Text(fmtTime(m.durMs, 30).substring(0, 5), Modifier.align(Alignment.BottomEnd).padding(2.dp).background(Color(0x99000000)).padding(horizontal = 3.dp), color = Color.White, fontSize = 8.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    2 -> {
                        if (!MediaRepo.hasAudio(ctx)) PermissionCard("Izinkan akses audio untuk menampilkan musik perangkat.") { permLauncher.launch(MediaRepo.audioPermissions()) }
                        else LazyHorizontalGrid(GridCells.Fixed(2), Modifier.fillMaxSize().padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            item {
                                Column(Modifier.width(78.dp).fillMaxHeight().clip(RoundedCornerShape(8.dp)).background(cs.surfaceContainerHigh).clickable { onAudioAll() },
                                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Icon(Icons.Rounded.Dashboard, null); Text("Lihat Semua", fontSize = 10.sp)
                                }
                            }
                            items(audio.take(24), key = { it.uri.toString() }) { a ->
                                AudioTile(a, Modifier.width(78.dp)) { vm.addAudio(a.uri, a.title, a.durMs); onClose() }
                            }
                        }
                    }
                    3 -> Row(Modifier.fillMaxSize().padding(10.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        ObjTile("Kamera", Icons.Rounded.Videocam) { vm.addSpecial(LayerType.CAMERA, "Kamera"); onClose() }
                        ObjTile("Nol", Icons.Rounded.CropFree) { vm.addSpecial(LayerType.NULL, "Nol"); onClose() }
                        ObjTile("Grup Kosong", Icons.Rounded.FolderOpen) { vm.addSpecial(LayerType.GROUP, "Grup"); onClose() }
                        ObjTile("Elemen / Proyek", Icons.Rounded.Archive) { elementDialog = true }
                    }
                    else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Belum ada template tersimpan.\nSimpan proyek sebagai elemen untuk memakainya lagi.", fontSize = 12.sp, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        Column(Modifier.width(58.dp).fillMaxHeight().padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
            SideBtn("Gambar\nFreehand", Icons.Rounded.Brush) { onFreehand() }
            SideBtn("Gambar\nVektor", Icons.Rounded.Gesture) { onVector() }
            SideBtn("Teks", Icons.Rounded.TextFields) { onText() }
            IconBtn(Icons.Rounded.Close, onClose)
        }
    }

    if (pathDialog) PathImportDialog({ pathDialog = false }) { f ->
        pathDialog = false
        vm.addMedia(listOf(Uri.fromFile(f)))
    }
    if (elementDialog) {
        val list = Graph.repo.projects.filter { it.id != vm.projectId }
        LaunchedEffect(Unit) { withContext(Dispatchers.IO) { Graph.repo.refresh() } }
        AlertDialog(
            onDismissRequest = { elementDialog = false }, title = { Text("Sisipkan Elemen / Proyek") },
            text = {
                if (list.isEmpty()) Text("Belum ada proyek lain.")
                else LazyColumn { items(list, key = { it.id }) { p ->
                    Text(p.name + if (p.isElement) "  (elemen)" else "", Modifier.fillMaxWidth().clickable {
                        elementDialog = false; vm.insertProject(p); onClose()
                    }.padding(vertical = 10.dp))
                } }
            },
            confirmButton = { TextButton(onClick = { elementDialog = false }) { Text("Tutup") } }
        )
    }
}

@Composable
private fun PermissionCard(msg: String, onRequest: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(msg, fontSize = 12.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Button(onClick = onRequest) { Text("Beri izin") }
        Text("Atau gunakan menu ⋯ ▸ Telusuri berkas / Mode Akses di Pengaturan.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun SideBtn(label: String, icon: ImageVector, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.clip(RoundedCornerShape(8.dp)).clickable { onClick() }.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(22.dp), tint = cs.onSurface)
        Text(label, fontSize = 8.sp, textAlign = TextAlign.Center, lineHeight = 9.sp, color = cs.onSurfaceVariant)
    }
}

@Composable
private fun ObjTile(label: String, icon: ImageVector, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable { onClick() }.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(34.dp))
        Text(label, fontSize = 10.sp, textAlign = TextAlign.Center, color = cs.onSurfaceVariant)
    }
}

@Composable
fun ShapeIcon(id: String, modifier: Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier) {
        val s = size.minDimension
        val path = ShapePaths.build(id, s * 0.9f, s * 0.9f).asComposePath()
        translate(size.width / 2f, size.height / 2f) { drawPath(path, color) }
    }
}

@Composable
fun AudioTile(a: AudioItem, modifier: Modifier, onAdd: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(modifier.fillMaxHeight().clip(RoundedCornerShape(8.dp)).background(Brush.verticalGradient(listOf(Color(0xFF6E8BE0), Color(0xFFB070E0)))).clickable { onAdd() }) {
        Text(a.title, Modifier.align(Alignment.TopStart).padding(4.dp), fontSize = 9.sp, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Icon(Icons.Rounded.Add, null, Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp), tint = Color.White)
    }
}

// ------------------------------------------------------------------ Media picker (layar penuh)

@Composable
fun MediaPickerDialog(initial: MediaItem, onAdd: (List<Uri>) -> Unit, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val all by produceState(listOf(initial)) { value = withContext(Dispatchers.IO) { MediaRepo.queryMedia(ctx).ifEmpty { listOf(initial) } } }
    var bucket by remember { mutableStateOf("Semua") }
    var menu by remember { mutableStateOf(false) }
    var multi by remember { mutableStateOf(false) }
    var current by remember { mutableStateOf(initial) }
    val selected = remember { mutableStateListOf(initial) }
    val buckets = listOf("Semua") + all.map { it.bucket }.distinct().sorted()
    val shown = if (bucket == "Semua") all else all.filter { it.bucket == bucket }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = cs.surface) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().height(52.dp).background(if (multi) cs.primaryContainer else cs.surface), verticalAlignment = Alignment.CenterVertically) {
                    IconBtn(Icons.Rounded.Close, onClose, tint = if (multi) cs.onPrimaryContainer else cs.onSurface)
                    if (multi) Text("${selected.size} dipilih", Modifier.weight(1f), fontWeight = FontWeight.Bold, color = cs.onPrimaryContainer)
                    else Box(Modifier.weight(1f)) {
                        Row(Modifier.clickable { menu = true }, verticalAlignment = Alignment.CenterVertically) {
                            Text(bucket, fontWeight = FontWeight.SemiBold); Icon(Icons.Rounded.ArrowDropDown, null)
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            buckets.forEach { b -> DropdownMenuItem(text = { Text(b) }, onClick = { bucket = b; menu = false }) }
                        }
                    }
                    IconBtn(Icons.Rounded.ContentCopy, { multi = !multi; if (!multi) { selected.clear(); selected.add(current) } }, tint = if (multi) cs.onPrimaryContainer else cs.onSurface)
                    IconBtn(Icons.Rounded.Add, { onAdd((if (selected.isEmpty()) listOf(current) else selected.toList()).map { it.uri }) }, tint = if (multi) cs.onPrimaryContainer else cs.onSurface)
                }
                Box(Modifier.fillMaxWidth().weight(0.9f).background(Color.Black)) {
                    Thumb(current.uri, current.isVideo, Modifier.fillMaxSize(), ContentScale.Fit)
                    Text(current.uri.lastPathSegment ?: "", Modifier.align(Alignment.TopStart).padding(8.dp), color = Color.White, fontSize = 10.sp, maxLines = 1)
                }
                LazyVerticalGrid(GridCells.Fixed(4), Modifier.fillMaxWidth().weight(1.1f), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(shown, key = { it.uri.toString() }) { m ->
                        val idx = selected.indexOfFirst { it.uri == m.uri }
                        Box(Modifier.aspectRatio(1f).clickable {
                            current = m
                            if (multi) { if (idx >= 0) selected.removeAt(idx) else selected.add(m) } else { selected.clear(); selected.add(m) }
                        }.then(if (idx >= 0) Modifier.border(2.dp, cs.primary) else Modifier)) {
                            Thumb(m.uri, m.isVideo, Modifier.fillMaxSize())
                            if (idx >= 0 && multi) Box(Modifier.align(Alignment.TopEnd).padding(3.dp).size(18.dp).clip(CircleShape).background(cs.primary), contentAlignment = Alignment.Center) {
                                Text("${idx + 1}", fontSize = 10.sp, color = cs.onPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Audio: Lihat Semua

@Composable
fun AudioListDialog(onPick: (AudioItem) -> Unit, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val list by produceState(emptyList<AudioItem>()) { value = withContext(Dispatchers.IO) { MediaRepo.queryAudio(ctx) } }
    var cat by remember { mutableIntStateOf(0) }
    var group by remember { mutableStateOf<String?>(null) }
    val albums = list.groupBy { it.album }
    val artists = list.groupBy { it.artist }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = cs.surface) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBtn(Icons.Rounded.Close, onClose)
                    Text("Audio", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.width(130.dp).fillMaxHeight()) {
                        listOf("Lagu" to list.size, "Album" to albums.size, "Artis" to artists.size, "Genre" to 0).forEachIndexed { i, (n, c) ->
                            Row(Modifier.fillMaxWidth().background(if (cat == i) cs.surfaceContainerHigh else Color.Transparent).clickable { cat = i; group = null }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text(n, fontSize = 13.sp, fontWeight = FontWeight.SemiBold); Text("$c", fontSize = 10.sp, color = cs.onSurfaceVariant) }
                            }
                        }
                    }
                    val groupsMap = if (cat == 1) albums else artists
                    LazyColumn(Modifier.weight(1f).fillMaxHeight()) {
                        if ((cat == 1 || cat == 2) && group == null) {
                            items(groupsMap.keys.toList().sorted()) { g ->
                                Text("$g  (${groupsMap[g]?.size ?: 0})", Modifier.fillMaxWidth().clickable { group = g }.padding(14.dp), fontSize = 13.sp)
                            }
                        } else if (cat == 3) {
                            item { Text("Genre tidak tersedia di perangkat ini.", Modifier.padding(16.dp), fontSize = 12.sp, color = cs.onSurfaceVariant) }
                        } else {
                            val songs = if (cat == 0) list else groupsMap[group ?: ""] ?: emptyList()
                            items(songs, key = { it.uri.toString() }) { a ->
                                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)).background(Brush.linearGradient(listOf(Color(0xFF6E8BE0), Color(0xFFE0A050)))))
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(a.title, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${a.artist} • ${fmtTime(a.durMs, 30).substring(0, 5)}", fontSize = 10.sp, color = cs.onSurfaceVariant, maxLines = 1)
                                    }
                                    IconBtn(Icons.Rounded.Add, { onPick(a) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Impor dari path (Shizuku / Root)

@Composable
fun PathImportDialog(onDismiss: () -> Unit, onFile: (java.io.File) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var path by remember { mutableStateOf("/sdcard") }
    var entries by remember { mutableStateOf<List<Pair<String, Boolean>>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(path) { busy = true; entries = Privileged.list(path); busy = false }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Impor dari path") },
        text = {
            Column {
                Text("Mode akses: ${Graph.settings.accessMode.name.lowercase()}", fontSize = 11.sp, color = cs.onSurfaceVariant)
                BasicTextField(path, { path = it }, Modifier.fillMaxWidth().padding(vertical = 6.dp).background(cs.surfaceContainerHigh).padding(8.dp), singleLine = true,
                    textStyle = TextStyle(color = cs.onSurface, fontSize = 13.sp), cursorBrush = SolidColor(cs.primary))
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 260.dp)) {
                    item { Text("⬆  ..", Modifier.fillMaxWidth().clickable { path = path.trimEnd('/').substringBeforeLast('/', "/").ifEmpty { "/" } }.padding(8.dp)) }
                    items(entries) { (name, dir) ->
                        Text((if (dir) "📁  " else "🎞  ") + name, Modifier.fillMaxWidth().clickable {
                            val full = path.trimEnd('/') + "/" + name
                            if (dir) path = full else scope.launch {
                                val f = Privileged.copyToCache(full)
                                if (f != null) onFile(f) else ctx.toast("Gagal membaca berkas (periksa Mode Akses)")
                            }
                        }.padding(8.dp), fontSize = 13.sp)
                    }
                    if (entries.isEmpty() && !busy) item { Text("Kosong / tidak dapat diakses. Ubah Mode Akses di Pengaturan.", Modifier.padding(8.dp), fontSize = 11.sp, color = cs.onSurfaceVariant) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}
