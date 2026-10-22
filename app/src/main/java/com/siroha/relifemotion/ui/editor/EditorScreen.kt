package com.siroha.relifemotion.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.siroha.relifemotion.Graph
import com.siroha.relifemotion.data.EffectCatalog
import com.siroha.relifemotion.data.EffectInst
import com.siroha.relifemotion.data.LayerType
import com.siroha.relifemotion.data.MediaItem
import com.siroha.relifemotion.data.fmtTime
import com.siroha.relifemotion.render.Renderer
import com.siroha.relifemotion.ui.common.DropdownField
import com.siroha.relifemotion.ui.common.IconBtn
import com.siroha.relifemotion.ui.home.BG_COLORS
import com.siroha.relifemotion.ui.home.FPS_LIST

private enum class DrawMode { NONE, FREEHAND, VECTOR }

private fun pageTitle(p: Page): String = when (p) {
    Page.TRANSFORM, Page.EASING -> "Move & Transform"
    Page.BORDER -> "Border & Bayangan"
    Page.BLEND -> "Blending & Opacity"
    Page.FILL -> "Color & Fill"
    Page.SHAPE -> "Edit Bentuk"
    Page.PRESETS -> "Presets"
    Page.EFFECTS -> "Efek"
    Page.TIME -> "Pemetaan Waktu"
    Page.MAIN -> ""
}

@Composable
fun EditorScreen(id: String, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val vm: EditorVM = viewModel(key = id, factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = EditorVM(id) as T
    })
    val vp = remember { Viewport() }
    var page by remember { mutableStateOf(Page.MAIN) }
    var addOpen by remember { mutableStateOf(false) }
    var gridOn by remember { mutableStateOf(false) }
    var gridSize by remember { mutableFloatStateOf(40f) }
    var showSel by remember { mutableStateOf(true) }
    var effectsOpen by remember { mutableStateOf(false) }
    var textTarget by remember { mutableStateOf<TextTarget?>(null) }
    var drawMode by remember { mutableStateOf(DrawMode.NONE) }
    var drawState by remember { mutableStateOf(DrawState(false)) }
    var exportOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var mediaPick by remember { mutableStateOf<MediaItem?>(null) }
    var audioAll by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }

    val sel = vm.sel
    val multi = vm.selected.size > 1

    LaunchedEffect(vm.selected.firstOrNull()) { page = Page.MAIN }
    DisposableEffect(Unit) { onDispose { vm.playing = false; vm.stopAudio() } }

    LaunchedEffect(vm.playing) {
        if (vm.playing) {
            var last = withFrameNanos { it }
            while (vm.playing) {
                val now = withFrameNanos { it }
                val dt = (now - last) / 1_000_000L
                last = now
                var t = vm.timeMs + dt
                if (t >= vm.totalMs) {
                    if (vm.loop) { t = 0L; vm.stopAudio() }
                    else { vm.timeMs = vm.totalMs; vm.playing = false; vm.stopAudio(); break }
                }
                vm.timeMs = t
                vm.audioTick()
            }
        }
    }

    BackHandler {
        when {
            drawMode != DrawMode.NONE -> drawMode = DrawMode.NONE
            addOpen -> addOpen = false
            page == Page.EASING -> page = Page.TRANSFORM
            page != Page.MAIN -> page = Page.MAIN
            vm.selected.isNotEmpty() -> vm.clearSelection()
            else -> onBack()
        }
    }

    Column(Modifier.fillMaxSize().background(cs.surface).statusBarsPadding().navigationBarsPadding()) {
        // ------------------------------------------------ top bar
        when {
            multi -> Row(Modifier.fillMaxWidth().height(52.dp).background(cs.primaryContainer), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Icons.Rounded.Close, { vm.clearSelection() }, tint = cs.onPrimaryContainer)
                Text("Memilih ${vm.selected.size} layer", Modifier.weight(1f), fontWeight = FontWeight.Bold, color = cs.onPrimaryContainer)
                IconBtn(Icons.Rounded.ContentCopy, { vm.duplicateSelected() }, tint = cs.onPrimaryContainer)
                IconBtn(Icons.Rounded.Delete, { vm.deleteSelected() }, tint = cs.onPrimaryContainer)
                IconBtn(if (vm.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, { vm.togglePlay() }, tint = cs.onPrimaryContainer)
            }
            sel != null && page != Page.MAIN -> Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Icons.Rounded.ArrowBack, { page = if (page == Page.EASING) Page.TRANSFORM else Page.MAIN })
                Text(pageTitle(page), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
            sel != null -> Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Icons.Rounded.ArrowBack, { vm.clearSelection() })
                BasicTextField(
                    sel.name, { n -> vm.updateLayer(sel.id, "nm") { it.copy(name = n) } }, Modifier.weight(1f).padding(horizontal = 6.dp), singleLine = true,
                    textStyle = TextStyle(color = cs.onSurface, fontSize = 15.sp), cursorBrush = SolidColor(cs.primary)
                )
                IconBtn(Icons.Rounded.ContentCopy, { vm.duplicateSelected() })
                IconBtn(Icons.Rounded.Delete, { vm.deleteSelected() })
                Box {
                    IconBtn(Icons.Rounded.MoreVert, { moreMenu = true })
                    DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                        DropdownMenuItem(text = { Text("Salin") }, onClick = { moreMenu = false; vm.copySelected() })
                        DropdownMenuItem(text = { Text(if (sel.locked) "Buka kunci" else "Kunci layer") }, onClick = { moreMenu = false; vm.toggleLock(sel.id) })
                        DropdownMenuItem(text = { Text("Atur ke playhead") }, onClick = {
                            moreMenu = false
                            val d = sel.endMs - sel.startMs
                            vm.moveLayerTimes(sel.id, vm.timeMs, vm.timeMs + d)
                        })
                    }
                }
            }
            else -> Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Icons.Rounded.ArrowBack, onBack)
                var rename by remember { mutableStateOf(false) }
                Text(vm.project.name, Modifier.weight(1f).clickable { rename = true }, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconBtn(Icons.Rounded.Settings, { settingsOpen = true })
                IconBtn(Icons.Rounded.Share, { vm.playing = false; exportOpen = true })
                if (rename) {
                    var v by remember { mutableStateOf(vm.project.name) }
                    AlertDialog(
                        onDismissRequest = { rename = false }, title = { Text("Nama proyek") },
                        text = { OutlinedTextField(v, { v = it }, singleLine = true) },
                        confirmButton = { TextButton(onClick = { if (v.isNotBlank()) vm.renameProject(v.trim()); rename = false }) { Text("OK") } },
                        dismissButton = { TextButton(onClick = { rename = false }) { Text("Batal") } }
                    )
                }
            }
        }

        // ------------------------------------------------ canvas
        Box(Modifier.weight(1f).fillMaxWidth().background(cs.surfaceContainerLowest)) {
            EditorCanvas(vm, vp, gridOn, gridSize, interactive = drawMode == DrawMode.NONE, showSelection = showSel && drawMode == DrawMode.NONE)
            if (drawMode != DrawMode.NONE) DrawOverlay(drawState, vp)

            if (sel != null && drawMode == DrawMode.NONE) {
                Box(
                    Modifier.padding(8.dp).size(40.dp).clip(CircleShape).background(cs.surfaceContainerHigh).clickable {
                        if (vp.zoom > 1.2f) vp.reset() else {
                            val c = Renderer.center(sel, vm.timeMs)
                            vp.zoom = 2.5f
                            val s = vp.scale
                            vp.pan = Offset((vp.pw / 2f - c[0]) * s, (vp.ph / 2f - c[1]) * s)
                        }
                    },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.ZoomIn, null, tint = cs.onSurface) }
            }

            val showRail = drawMode == DrawMode.NONE && (sel == null || page == Page.MAIN || page == Page.EFFECTS || page == Page.FILL || page == Page.PRESETS || page == Page.TIME)
            if (showRail) {
                Row(Modifier.align(Alignment.TopEnd).padding(end = 4.dp, top = 4.dp)) {
                    if (gridOn) {
                        Box(Modifier.padding(end = 6.dp, top = 4.dp).width(44.dp).height(150.dp).clip(RoundedCornerShape(10.dp)).background(cs.surfaceContainerHigh)) {
                            com.siroha.relifemotion.ui.common.VerticalBar(gridSize, { gridSize = it.toInt().toFloat().coerceAtLeast(5f) }, 10f, 200f, Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 6.dp))
                            Text("${gridSize.toInt()}", Modifier.align(Alignment.BottomCenter), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Column(Modifier.clip(RoundedCornerShape(10.dp)).background(cs.surfaceContainerHigh.copy(alpha = 0.92f)), horizontalAlignment = Alignment.CenterHorizontally) {
                        RailBtn(Icons.Rounded.CropSquare, { showSel = !showSel }, showSel)
                        RailBtn(Icons.Rounded.GridOn, { gridOn = !gridOn }, gridOn)
                        RailBtn(Icons.Rounded.Layers, { Graph.settings.setLowPreview(!Graph.settings.lowPreview) }, !Graph.settings.lowPreview)
                        RailBtn(Icons.Rounded.FitScreen, { vp.reset() })
                        RailBtn(Icons.Rounded.Add, { vp.zoom = (vp.zoom * 1.25f).coerceAtMost(12f) })
                        Text("${(vp.zoom * 100).toInt()}%", fontSize = 9.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 2.dp))
                        RailBtn(Icons.Rounded.Remove, { vp.zoom = (vp.zoom / 1.25f).coerceAtLeast(0.2f) })
                    }
                }
            }
        }

        // ------------------------------------------------ transport
        Row(Modifier.fillMaxWidth().height(46.dp).background(cs.surfaceContainerLow), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
            IconBtn(Icons.Rounded.Undo, { vm.undo() }, enabled = vm.canUndo)
            IconBtn(Icons.Rounded.Redo, { vm.redo() }, enabled = vm.canRedo)
            IconBtn(Icons.Rounded.SkipPrevious, { vm.seek(0L) })
            IconBtn(if (vm.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, { vm.togglePlay() }, size = 28.dp)
            IconBtn(Icons.Rounded.SkipNext, { vm.seek(vm.totalMs) })
            IconBtn(Icons.Rounded.Repeat, { vm.loop = !vm.loop }, tint = if (vm.loop) cs.primary else cs.onSurface)
            IconBtn(Icons.Rounded.FitScreen, { vp.reset() })
        }

        // ------------------------------------------------ area bawah
        val bottomMod = Modifier.fillMaxWidth()
        when {
            drawMode != DrawMode.NONE -> DrawPanel(
                drawState,
                onConfirm = {
                    vm.addDrawing(drawState.pts.toList(), drawState.color, drawState.brush, drawState.closed, drawState.vector)
                    drawMode = DrawMode.NONE
                },
                onCancel = { drawMode = DrawMode.NONE },
                modifier = bottomMod.weight(0.9f)
            )
            addOpen -> AddPanel(
                vm, { addOpen = false },
                onText = { addOpen = false; textTarget = TextTarget(null) },
                onFreehand = { addOpen = false; drawState = DrawState(false); drawMode = DrawMode.FREEHAND },
                onVector = { addOpen = false; drawState = DrawState(true); drawMode = DrawMode.VECTOR },
                onOpenMedia = { mediaPick = it }, onAudioAll = { audioAll = true },
                modifier = bottomMod.weight(0.9f)
            )
            sel != null && !multi -> {
                Timeline(vm, bottomMod.height(96.dp), showFab = false, onFab = {})
                PanelHost(
                    vm, vp, page, { page = it }, { effectsOpen = true },
                    { textTarget = TextTarget(sel.id) }, bottomMod.weight(0.8f)
                )
            }
            multi -> {
                Timeline(vm, bottomMod.weight(0.6f), showFab = false, onFab = {})
                Row(Modifier.fillMaxWidth().height(52.dp).background(cs.surfaceContainerLow), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    IconBtn(Icons.Rounded.AlignHorizontalLeft, { vm.align(0) })
                    IconBtn(Icons.Rounded.AlignHorizontalCenter, { vm.align(1) })
                    IconBtn(Icons.Rounded.AlignHorizontalRight, { vm.align(2) })
                    IconBtn(Icons.Rounded.AlignVerticalTop, { vm.align(3) })
                    IconBtn(Icons.Rounded.AlignVerticalCenter, { vm.align(4) })
                    IconBtn(Icons.Rounded.AlignVerticalBottom, { vm.align(5) })
                }
            }
            else -> Box(bottomMod.weight(0.9f)) {
                Timeline(vm, Modifier.fillMaxSize(), showFab = true, onFab = { addOpen = true })
                Box(Modifier.align(Alignment.TopEnd)) {
                    IconBtn(Icons.Rounded.MoreVert, { menu = true }, size = 20.dp, box = 36.dp)
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Pilih Semua Layer") }, onClick = { menu = false; vm.selectAll() })
                        DropdownMenuItem(text = { Text("Gandakan ${vm.selected.size} Layer") }, enabled = vm.selected.isNotEmpty(), onClick = { menu = false; vm.duplicateSelected() })
                        DropdownMenuItem(text = { Text("Salin ${vm.selected.size} Layer") }, enabled = vm.selected.isNotEmpty(), onClick = { menu = false; vm.copySelected() })
                        DropdownMenuItem(text = { Text("Tempel ${vm.clipboard.size} Layer") }, enabled = vm.clipboard.isNotEmpty(), onClick = { menu = false; vm.paste() })
                        DropdownMenuItem(text = { Text("Tempel Gaya") }, enabled = vm.clipboard.isNotEmpty(), onClick = { menu = false; vm.pasteStyle() })
                    }
                }
            }
        }
    }

    // ------------------------------------------------ dialog & overlay
    if (effectsOpen) EffectBrowser(
        onPick = { d ->
            val l = vm.sel
            if (l != null) vm.updateLayer(l.id) { it.copy(effects = it.effects + EffectInst(id = d.id, params = EffectCatalog.defaults(d.id))) }
            effectsOpen = false; page = Page.EFFECTS
        },
        onClose = { effectsOpen = false }
    )
    textTarget?.let { tt ->
        val existing = tt.layerId?.let { lid -> vm.project.layers.firstOrNull { it.id == lid } }
        TextEditorDialog(existing, { text, font, path, size, color ->
            if (existing == null) vm.addText(text, font, path, size, color) else vm.updateText(existing.id, text, font, path, size, color)
            textTarget = null
        }, { textTarget = null })
    }
    mediaPick?.let { m ->
        MediaPickerDialog(m, { uris -> vm.addMedia(uris); mediaPick = null; addOpen = false }, { mediaPick = null })
    }
    if (audioAll) AudioListDialog({ a -> vm.addAudio(a.uri, a.title, a.durMs); audioAll = false; addOpen = false }, { audioAll = false })
    if (exportOpen) ExportDialog(vm) { exportOpen = false }
    if (settingsOpen) {
        val p = vm.project
        var fpsIdx by remember { mutableIntStateOf(FPS_LIST.indexOf(p.fps).coerceAtLeast(0)) }
        var bgIdx by remember { mutableIntStateOf(BG_COLORS.indexOfFirst { it.second == p.bg }.coerceAtLeast(0)) }
        AlertDialog(
            onDismissRequest = { settingsOpen = false },
            title = { Text("Pengaturan Proyek") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Ukuran: ${p.width} × ${p.height}", fontSize = 13.sp)
                    DropdownField("${FPS_LIST[fpsIdx]} fps", FPS_LIST.map { "$it fps" }, { fpsIdx = it })
                    DropdownField(BG_COLORS[bgIdx].first, BG_COLORS.map { it.first }, { bgIdx = it })
                    Text("Total waktu pengeditan: ${fmtTime(vm.totalMs, p.fps)}", fontSize = 11.sp, color = cs.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = { vm.edit { it.copy(fps = FPS_LIST[fpsIdx], bg = BG_COLORS[bgIdx].second) }; settingsOpen = false }) { Text("Simpan") } },
            dismissButton = { TextButton(onClick = { settingsOpen = false }) { Text("Batal") } }
        )
    }
}
