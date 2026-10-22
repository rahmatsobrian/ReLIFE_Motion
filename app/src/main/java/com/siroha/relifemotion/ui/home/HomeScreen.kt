package com.siroha.relifemotion.ui.home

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.siroha.relifemotion.Graph
import com.siroha.relifemotion.data.Project
import com.siroha.relifemotion.data.aspectLabel
import com.siroha.relifemotion.data.fmtTime
import com.siroha.relifemotion.export.Exporter
import com.siroha.relifemotion.ui.common.*
import com.siroha.relifemotion.ui.settings.SettingsDrawer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Context.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

@Composable
fun ReLifeLogo(fontSize: TextUnit = 20.sp) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("ReLIFE Motion", fontSize = fontSize, fontWeight = FontWeight.Bold, color = cs.onSurface, letterSpacing = 0.5.sp)
        Spacer(Modifier.width(4.dp))
        val accent = cs.primary
        Canvas(Modifier.size(22.dp)) {
            val s = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
            drawArc(accent, 200f, 220f, false, Offset(2.dp.toPx(), 2.dp.toPx()), androidx.compose.ui.geometry.Size(size.width - 4.dp.toPx(), size.height - 4.dp.toPx()), style = s)
            drawArc(accent.copy(alpha = 0.55f), 20f, 180f, false, Offset(6.dp.toPx(), 6.dp.toPx()), androidx.compose.ui.geometry.Size(size.width - 12.dp.toPx(), size.height - 12.dp.toPx()), style = s)
        }
    }
}

private data class Template(val name: String, val w: Int, val h: Int, val c1: Color, val c2: Color)

private val templates = listOf(
    Template("Kosong 16:9", 1920, 1080, Color(0xFF2BC9A6), Color(0xFF4F9BE0)),
    Template("Reels 9:16", 1080, 1920, Color(0xFF9B6BE0), Color(0xFFE06BA8)),
    Template("Kotak 1:1", 1080, 1080, Color(0xFFE0A06B), Color(0xFFE06B6B)),
    Template("Potret 4:5", 1080, 1350, Color(0xFF6BA0E0), Color(0xFF6BE0C1))
)

@Composable
fun HomeScreen(onOpenProject: (String) -> Unit, onAccount: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val repo = Graph.repo
    var tab by rememberSaveable { mutableStateOf(0) }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    var showNew by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() }
    var selecting by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var tutorial by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { repo.refresh() } }

    fun createFrom(t: Template) {
        val p = Project(name = t.name.substringBefore(' ') + " Baru", width = t.w, height = t.h)
        scope.launch { withContext(Dispatchers.IO) { repo.save(p); repo.refresh() }; onOpenProject(p.id) }
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val p = Exporter.importProject(ctx, uri)
            if (p == null) ctx.toast("Berkas proyek tidak valid") else {
                withContext(Dispatchers.IO) { repo.save(p); repo.refresh() }
                ctx.toast("Proyek diimpor: ${p.name}")
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = drawer.isOpen || tab != 99,
        drawerContent = {
            ModalDrawerSheet(Modifier.fillMaxWidth(0.88f), drawerContainerColor = cs.surface) {
                SettingsDrawer()
            }
        }
    ) {
        Scaffold(
            containerColor = cs.surface,
            bottomBar = {
                if (selecting) {
                    SelectionBar(
                        onExport = {
                            val ids = selected.toList()
                            scope.launch {
                                ids.forEach { id -> repo.load(id)?.let { Exporter.exportPackage(ctx, it) } }
                                ctx.toast("Paket proyek disimpan di Download/ReLIFE Motion")
                            }
                        },
                        onDuplicate = {
                            val ids = selected.toList()
                            scope.launch { withContext(Dispatchers.IO) { ids.forEach { repo.duplicate(it) }; repo.refresh() } }
                            selecting = false; selected.clear()
                        },
                        onDelete = { confirmDelete = true }
                    )
                } else HomeBottomBar(tab, { tab = it }, { showNew = true })
            }
        ) { pad ->
            Column(Modifier.padding(pad).fillMaxSize()) {
                Column(Modifier.statusBarsPadding()) {
                    HomeTopBar(onMenu = { scope.launch { drawer.open() } }, onAvatar = onAccount)
                }
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                    label = "tabs"
                ) { t ->
                    when (t) {
                        0 -> BerandaTab(
                            onNew = { showNew = true }, onTemplate = ::createFrom,
                            onAll = { tab = 2 }, onAllTemplates = { tab = 3 }, onOpen = onOpenProject,
                            onMenu = { action, p -> projectAction(ctx, scope, action, p) }
                        )
                        1 -> TutorialTab { tutorial = it }
                        2 -> ProyekTab(
                            selecting = selecting, selected = selected,
                            onSelecting = { selecting = it }, onOpen = onOpenProject,
                            onUpload = { importer.launch(arrayOf("*/*")) },
                            onMenu = { action, p ->
                                if (action == "Pilih") { selecting = true; selected.clear(); selected.add(p.id) }
                                else projectAction(ctx, scope, action, p)
                            }
                        )
                        else -> TemplatTab(onTemplate = ::createFrom)
                    }
                }
            }
        }
    }

    if (showNew) {
        NewProjectSheet(
            onDismiss = { showNew = false },
            onCreate = { p ->
                showNew = false
                scope.launch { withContext(Dispatchers.IO) { repo.save(p); repo.refresh() }; onOpenProject(p.id) }
            }
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Hapus ${selected.size} proyek?") },
            text = { Text("Tindakan ini tidak dapat dibatalkan.") },
            confirmButton = {
                TextButton(onClick = {
                    val ids = selected.toList()
                    scope.launch { withContext(Dispatchers.IO) { ids.forEach { repo.delete(it) }; repo.refresh() } }
                    selected.clear(); selecting = false; confirmDelete = false
                }) { Text("Hapus") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Batal") } }
        )
    }
    tutorial?.let { t ->
        AlertDialog(
            onDismissRequest = { tutorial = null },
            title = { Text(t) },
            text = {
                Text(
                    if (t == "Memulai")
                        "1. Tekan tombol + untuk membuat proyek.\n2. Pilih rasio, resolusi, dan frame rate.\n3. Tekan + di editor untuk menambah bentuk, media, audio, atau teks.\n4. Pilih layer lalu atur Move & Transform, Efek, dan Blending."
                    else
                        "• Tekan ◆ di panel Move & Transform untuk menambah keyframe.\n• Buka ikon kurva untuk mengatur easing.\n• Tambahkan efek lewat Efek ▸ Tambahkan Efek.\n• Ekspor video lewat ikon bagikan di kanan atas."
                )
            },
            confirmButton = { TextButton(onClick = { tutorial = null }) { Text("Mengerti") } }
        )
    }
}

private fun projectAction(ctx: Context, scope: kotlinx.coroutines.CoroutineScope, action: String, p: Project) {
    val repo = Graph.repo
    when (action) {
        "Ekspor" -> scope.launch {
            val u = Exporter.exportPackage(ctx, p)
            ctx.toast(if (u != null) "Paket proyek disimpan di Download/ReLIFE Motion" else "Gagal mengekspor")
        }
        "Gandakan" -> scope.launch { withContext(Dispatchers.IO) { repo.duplicate(p.id); repo.refresh() } }
        "Hapus" -> scope.launch { withContext(Dispatchers.IO) { repo.delete(p.id); repo.refresh() } }
        "Unggah ke Cloud" -> ctx.toast("Cloud tidak tersedia (mode offline)")
    }
}

@Composable
private fun HomeTopBar(onMenu: () -> Unit, onAvatar: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBtn(Icons.Rounded.Menu, onMenu)
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { ReLifeLogo() }
        Box(
            Modifier.padding(end = 12.dp).size(34.dp).clip(CircleShape).background(Color(0xFF8D6E63)).clickable { onAvatar() },
            contentAlignment = Alignment.Center
        ) {
            Text(Graph.settings.userName.take(1).uppercase().ifEmpty { "M" }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

@Composable
private fun HomeBottomBar(tab: Int, onTab: (Int) -> Unit, onFab: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().background(cs.surfaceContainerLowest).navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            NavItem("Beranda", Icons.Rounded.Home, tab == 0, Modifier.weight(1f)) { onTab(0) }
            NavItem("Tutorial", Icons.Rounded.School, tab == 1, Modifier.weight(1f)) { onTab(1) }
            Spacer(Modifier.weight(1f))
            NavItem("Proyek", Icons.Rounded.Movie, tab == 2, Modifier.weight(1f), badge = "NEW") { onTab(2) }
            NavItem("Templat", Icons.Rounded.Dashboard, tab == 3, Modifier.weight(1f)) { onTab(3) }
        }
        Box(
            Modifier.align(Alignment.TopCenter).offset(y = (-16).dp).size(60.dp)
                .shadow(8.dp, CircleShape).clip(CircleShape).background(cs.primaryContainer).clickable { onFab() },
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.Add, "Buat proyek", Modifier.size(34.dp), tint = cs.onPrimaryContainer) }
    }
}

@Composable
private fun NavItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, sel: Boolean, modifier: Modifier, badge: String? = null, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val tint = if (sel) cs.onSurface else cs.onSurfaceVariant
    Column(modifier.fillMaxHeight().clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box {
            Icon(icon, null, Modifier.size(if (sel) 28.dp else 24.dp), tint = tint)
            if (badge != null) Pill(badge, Color(0xFFFFC107), Color.Black, Modifier.align(Alignment.TopEnd).offset(x = 16.dp, y = (-8).dp))
        }
        Text(label, fontSize = 11.sp, color = tint, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun SelectionBar(onExport: () -> Unit, onDuplicate: () -> Unit, onDelete: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().background(cs.secondaryContainer).navigationBarsPadding().height(64.dp)) {
        SelAction("Ekspor", Icons.Rounded.FileUpload, Modifier.weight(1f), onExport)
        SelAction("Gandakan", Icons.Rounded.ContentCopy, Modifier.weight(1f), onDuplicate)
        SelAction("Hapus", Icons.Rounded.Delete, Modifier.weight(1f), onDelete)
    }
}

@Composable
private fun SelAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme.onSecondaryContainer
    Column(modifier.fillMaxHeight().clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, null, tint = c); Text(label, fontSize = 11.sp, color = c)
    }
}

@Composable
private fun SectionHeader(title: String, action: String?, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.Bold)
        if (action != null) Text(action, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clickable { onAction() })
    }
}

@Composable
private fun BerandaTab(
    onNew: () -> Unit, onTemplate: (Template) -> Unit, onAll: () -> Unit, onAllTemplates: () -> Unit,
    onOpen: (String) -> Unit, onMenu: (String, Project) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val projects = Graph.repo.projects
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { SectionHeader("Templat Baru", "Lihat semua", onAllTemplates) }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(templates) { t -> TemplateCard(t, Modifier.width(120.dp).height(150.dp)) { onTemplate(t) } }
            }
        }
        item { Spacer(Modifier.height(8.dp)); SectionHeader("Proyek terbaru", "Lihat semua", onAll) }
        item {
            Row(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(56.dp).clip(RoundedCornerShape(12.dp))
                    .border(1.5.dp, cs.outlineVariant, RoundedCornerShape(12.dp)).clickable { onNew() },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
            ) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(cs.primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(20.dp), tint = cs.onPrimaryContainer)
                }
                Spacer(Modifier.width(10.dp))
                Text("Buat Proyek Baru", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }
        items(projects.take(6), key = { it.id }) { p ->
            ProjectCard(p, selecting = false, selected = false, modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
                onClick = { onOpen(p.id) }, onMenu = { onMenu(it, p) })
        }
    }
}

@Composable
private fun TemplateCard(t: Template, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(t.c1, t.c2))).clickable { onClick() },
        contentAlignment = Alignment.BottomStart
    ) {
        Text(t.name, Modifier.padding(10.dp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
fun ProjectThumb(p: Project, modifier: Modifier) {
    val bmp by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, p.id, p.modifiedAt) {
        value = withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(Graph.repo.thumb(p.id).absolutePath)?.asImageBitmap() }.getOrNull()
        }
    }
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(Color.Black)) {
        bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
        Text(
            fmtTime(p.durationMs, p.fps), Modifier.align(Alignment.BottomStart).padding(3.dp).clip(RoundedCornerShape(4.dp))
                .background(Color(0xAA000000)).padding(horizontal = 4.dp, vertical = 1.dp),
            color = Color.White, fontSize = 9.sp
        )
    }
}

@Composable
fun ProjectCard(p: Project, selecting: Boolean, selected: Boolean, modifier: Modifier, onClick: () -> Unit, onMenu: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    val kb = Graph.repo.sizeOf(p.id) / 1024f
    val size = if (kb >= 1024) "%.1fMB".format(kb / 1024f) else "%.1fKB".format(kb)
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(cs.surfaceContainerLowest).clickable { onClick() }.padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selecting) {
            RadioButton(selected = selected, onClick = onClick)
        }
        ProjectThumb(p, Modifier.size(76.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Row {
                Meta(Icons.Rounded.AspectRatio, aspectLabel(p.width, p.height), Modifier.weight(1f))
                Meta(Icons.Rounded.CropSquare, "${minOf(p.width, p.height)}p", Modifier.weight(1f))
            }
            Spacer(Modifier.height(4.dp))
            Row {
                Meta(Icons.Rounded.Folder, size, Modifier.weight(1f))
                Meta(Icons.Rounded.Speed, "${p.fps}fps", Modifier.weight(1f))
            }
        }
        if (!selecting) Box {
            IconBtn(Icons.Rounded.MoreHoriz, { menu = true })
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                listOf("Pilih", "Ekspor", "Gandakan", "Unggah ke Cloud", "Hapus").forEach { a ->
                    DropdownMenuItem(text = { Text(a) }, onClick = { menu = false; onMenu(a) })
                }
            }
        }
    }
}

@Composable
private fun Meta(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        Text(text, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TutorialTab(onOpen: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(16.dp).fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("Memulai" to listOf(Color(0xFF2BC9A6), Color(0xFF4FC3A1)), "Langkah Berikutnya" to listOf(Color(0xFF4F9BE0), Color(0xFFB06BE0))).forEach { (n, c) ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(c)).clickable { onOpen(n) },
                    contentAlignment = Alignment.Center
                ) { Text(n, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center) }
            }
        }
        Column(Modifier.weight(1f).fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Rounded.WifiOff, null, Modifier.size(56.dp), tint = cs.onSurface)
            Spacer(Modifier.height(10.dp))
            Text("Sambungkan ke internet untuk berita, informasi, dan tutorial ReLIFE Motion terbaru.", fontSize = 12.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun TemplatTab(onTemplate: (Template) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Templates", Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Your Templates", fontSize = 12.sp, color = cs.onSurfaceVariant)
            Icon(Icons.Rounded.ChevronRight, null, Modifier.size(18.dp), tint = cs.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(cs.surfaceContainerHigh).padding(16.dp)) {
            Text("Templat online belum tersedia (offline). Gunakan templat bawaan di bawah.", fontSize = 12.sp, color = cs.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
        templates.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { t -> TemplateCard(t, Modifier.weight(1f).height(130.dp)) { onTemplate(t) } }
            }
        }
    }
}

@Composable
private fun ProyekTab(
    selecting: Boolean, selected: SnapshotStateList<String>, onSelecting: (Boolean) -> Unit,
    onOpen: (String) -> Unit, onUpload: () -> Unit, onMenu: (String, Project) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var chip by remember { mutableIntStateOf(0) }
    var sortByName by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    val all = Graph.repo.projects
    val list = when (chip) {
        0 -> all.filter { !it.isElement }
        2 -> all.filter { it.isElement }
        else -> emptyList()
    }.let { l -> if (sortByName) l.sortedBy { it.name.lowercase() } else l.sortedByDescending { it.modifiedAt } }
    val fmt = remember { SimpleDateFormat("d MMMM yyyy", Locale("id", "ID")) }

    Column(Modifier.fillMaxSize()) {
        if (selecting) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${selected.size} dipilih", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text("Batal", Modifier.clickable { onSelecting(false); selected.clear() }.padding(8.dp), fontWeight = FontWeight.SemiBold)
            }
        }
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(cs.surfaceContainerLowest).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.FileUpload, null, Modifier.size(36.dp), tint = cs.onSurface)
            Spacer(Modifier.width(12.dp))
            Text("Unggah file proyek", Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(
                "Unggah", Modifier.clip(RoundedCornerShape(10.dp)).background(cs.secondaryContainer).clickable { onUpload() }.padding(horizontal = 16.dp, vertical = 8.dp),
                color = cs.onSecondaryContainer, fontWeight = FontWeight.SemiBold
            )
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Proyek", "Template Anda", "Elemen", "Cloud").forEachIndexed { i, n ->
                val sel = chip == i
                Text(
                    n, Modifier.clip(RoundedCornerShape(10.dp)).background(if (sel) cs.inverseSurface else cs.surfaceContainerLowest)
                        .clickable { chip = i }.padding(horizontal = 14.dp, vertical = 8.dp),
                    color = if (sel) cs.inverseOnSurface else cs.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 13.sp
                )
            }
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Proyek Anda", Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Box {
                Row(Modifier.clickable { sortMenu = true }, verticalAlignment = Alignment.CenterVertically) {
                    Text("Urutkan menurut ${if (sortByName) "Nama" else "Tanggal"}", fontSize = 12.sp)
                    Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.size(18.dp))
                }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    listOf("Nama" to true, "Tanggal" to false).forEach { (n, v) ->
                        DropdownMenuItem(
                            text = { Text(n) }, onClick = { sortByName = v; sortMenu = false },
                            trailingIcon = { RadioButton(selected = sortByName == v, onClick = { sortByName = v; sortMenu = false }) }
                        )
                    }
                }
            }
        }
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (chip == 3) "Cloud tidak tersedia saat offline" else "Belum ada item", color = cs.onSurfaceVariant, fontSize = 13.sp)
            }
        } else {
            val groups = if (sortByName) listOf("" to list) else list.groupBy { fmt.format(Date(it.modifiedAt)) }.toList()
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                groups.forEach { (date, ps) ->
                    if (date.isNotEmpty()) item(key = "h$date") { Text(date, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                    items(ps, key = { it.id }) { p ->
                        ProjectCard(
                            p, selecting, p.id in selected, Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
                            onClick = {
                                if (selecting) { if (p.id in selected) selected.remove(p.id) else selected.add(p.id) }
                                else onOpen(p.id)
                            },
                            onMenu = { onMenu(it, p) }
                        )
                    }
                }
            }
        }
    }
}
