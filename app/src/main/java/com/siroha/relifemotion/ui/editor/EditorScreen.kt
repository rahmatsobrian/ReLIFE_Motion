package com.siroha.relifemotion.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideocamOff
import androidx.compose.material.icons.rounded.*
import com.siroha.relifemotion.ui.common.Glyph
import com.siroha.relifemotion.ui.common.GlyphBtn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
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

@Composable
private fun UnderlineField(value: String, onChange: (String) -> Unit, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(modifier) {
        BasicTextField(value, onChange, Modifier.fillMaxWidth(), singleLine = true,
            textStyle = TextStyle(color = cs.onSurface, fontSize = 13.sp), cursorBrush = SolidColor(cs.primary))
        Box(Modifier.fillMaxWidth().padding(top = 3.dp).height(1.dp).background(cs.onSurface.copy(alpha = 0.3f)))
    }
}

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
            vm.selected.isNotEmpty() || vm.multiMode -> vm.clearSelection()
            else -> onBack()
        }
    }

    val mint = Color(0xFF7BFBB0)
    val multi = vm.multiMode || vm.selected.size > 1
    val density = androidx.compose.ui.platform.LocalDensity.current
    var railOpen by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(cs.surface).statusBarsPadding().navigationBarsPadding()) {
        // ------------------------------------------------ top bar (tinggi 48dp)
        when {
            multi -> Row(Modifier.fillMaxWidth().height(42.dp).background(mint), verticalAlignment = Alignment.CenterVertically) {
                GlyphBtn(Glyph.CLOSE, { vm.clearSelection() }, tint = Color.Black, size = 16.dp)
                Text("Memilih ${vm.selected.size} layer", Modifier.weight(1f), color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                GlyphBtn(Glyph.DASH_A, { vm.duplicateSelected() }, tint = Color.Black, size = 23.dp)
                GlyphBtn(Glyph.DASH_B, { vm.copySelected() }, tint = Color.Black, size = 23.dp)
                GlyphBtn(Glyph.DASH_C, { vm.paste() }, tint = Color.Black, size = 23.dp)
                GlyphBtn(Glyph.TRASH, { vm.deleteSelected() }, tint = Color.Black, size = 22.dp)
                if (vm.playing) IconBtn(Icons.Rounded.Pause, { vm.togglePlay() }, tint = Color.Black, size = 22.dp, box = 46.dp)
                else GlyphBtn(Glyph.PLAY, { vm.togglePlay() }, tint = Color.Black, size = 20.dp)
            }
            sel != null && page != Page.MAIN -> Row(Modifier.fillMaxWidth().height(42.dp).background(cs.surfaceContainer), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Icons.Rounded.ArrowBack, { page = if (page == Page.EASING) Page.TRANSFORM else Page.MAIN }, tint = cs.onSurface, box = 40.dp)
                Text(pageTitle(page), fontWeight = FontWeight.Normal, fontSize = 15.sp, color = cs.onSurface)
            }
            sel != null -> Row(Modifier.fillMaxWidth().height(42.dp).background(cs.surfaceContainer), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Icons.Rounded.ArrowBack, { vm.clearSelection() }, tint = cs.onSurface, box = 40.dp)
                UnderlineField(sel.name, { n -> vm.updateLayer(sel.id, "nm") { it.copy(name = n) } }, Modifier.weight(1f).padding(horizontal = 4.dp))
                GlyphBtn(Glyph.DUP_DASH, { vm.duplicateSelected() }, size = 23.dp)
                GlyphBtn(Glyph.TRASH, { vm.deleteSelected() }, size = 22.dp)
                Box {
                    GlyphBtn(Glyph.MORE_CIRCLE, { moreMenu = true }, size = 24.dp)
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
            else -> Row(Modifier.fillMaxWidth().height(42.dp), verticalAlignment = Alignment.CenterVertically) {
                GlyphBtn(Glyph.EXIT, onBack, size = 20.dp)
                var rename by remember { mutableStateOf(false) }
                Column(Modifier.weight(1f).padding(horizontal = 4.dp).clickable { rename = true }) {
                    Text(vm.project.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Box(Modifier.fillMaxWidth().padding(top = 3.dp).height(1.dp).background(cs.onSurface.copy(alpha = 0.25f)))
                }
                IconBtn(Icons.Outlined.Settings, { settingsOpen = true }, size = 22.dp, box = 40.dp)
                IconBtn(Icons.Outlined.OpenInNew, { vm.playing = false; exportOpen = true }, size = 22.dp, box = 40.dp)
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

        // ------------------------------------------------ canvas (tanpa margin, area samping lebih gelap)
        Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF151722))) {
            EditorCanvas(vm, vp, gridOn, gridSize, interactive = drawMode == DrawMode.NONE, showSelection = showSel && drawMode == DrawMode.NONE)
            if (drawMode != DrawMode.NONE) DrawOverlay(drawState, vp)

            if ((sel != null || multi) && drawMode == DrawMode.NONE && !railOpen) {
                Box(
                    Modifier.padding(16.dp).size(28.dp).clip(RoundedCornerShape(5.dp)).background(cs.surfaceContainerHigh).clickable {
                        val s0 = sel
                        if (vp.zoom > 1.2f || s0 == null) vp.reset() else {
                            val c = Renderer.center(s0, vm.timeMs)
                            vp.zoom = 2.5f
                            val s = vp.scale
                            vp.pan = Offset((vp.pw / 2f - c[0]) * s, (vp.ph / 2f - c[1]) * s)
                        }
                    },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.ZoomIn, null, Modifier.size(20.dp), tint = cs.onSurface) }
            }

            if (railOpen && drawMode == DrawMode.NONE) {
                if (gridOn) {
                    Box(Modifier.align(Alignment.TopEnd).padding(end = 57.dp, top = 8.dp).width(44.dp).height(150.dp).clip(RoundedCornerShape(10.dp)).background(cs.surfaceContainerHigh)) {
                        com.siroha.relifemotion.ui.common.VerticalBar(gridSize, { gridSize = it.toInt().toFloat().coerceAtLeast(5f) }, 10f, 200f, Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 6.dp))
                        Text("${gridSize.toInt()}", Modifier.align(Alignment.BottomCenter), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = cs.onSurface)
                    }
                }
                Box(Modifier.align(Alignment.TopEnd).fillMaxHeight().width(51.dp).background(cs.surfaceContainerHighest)) {
                    Column(Modifier.fillMaxWidth()) {
                        @Composable fun R(icon: androidx.compose.ui.graphics.vector.ImageVector, on: Boolean, click: () -> Unit) {
                            Box(Modifier.fillMaxWidth().height(63.dp).clickable { click() }, contentAlignment = Alignment.Center) {
                                Icon(icon, null, Modifier.size(26.dp), tint = if (on) cs.primaryContainer else Color.White)
                            }
                        }
                        R(Icons.Outlined.CropSquare, showSel) { showSel = !showSel }
                        R(Icons.Outlined.GridOn, gridOn) { gridOn = !gridOn }
                        R(Icons.Outlined.Layers, !Graph.settings.lowPreview) { Graph.settings.applyLowPreview(!Graph.settings.lowPreview) }
                        R(Icons.Outlined.VideocamOff, false) { vp.reset() }
                    }
                    Column(
                        Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp).width(32.dp).height(104.dp)
                            .clip(RoundedCornerShape(8.dp)).background(cs.surface),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(20.dp).clickable { vp.zoom = (vp.zoom * 1.25f).coerceAtMost(12f) }, tint = Color.White)
                        Text("${(vp.zoom * 100).toInt()}%", fontSize = 12.sp, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.clickable { vp.reset() })
                        Icon(Icons.Rounded.Remove, null, Modifier.size(20.dp).clickable { vp.zoom = (vp.zoom / 1.25f).coerceAtLeast(0.2f) }, tint = Color.White)
                    }
                }
            }
        }

        // ------------------------------------------------ transport (7 kolom sama lebar)
        Box(Modifier.fillMaxWidth().height(46.dp).background(cs.surface)) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                @Composable fun Cell(content: @Composable () -> Unit) { Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) { content() } }
                Cell { GlyphBtn(Glyph.UNDO, { vm.undo() }, enabled = vm.canUndo, size = 24.dp) }
                Cell { GlyphBtn(Glyph.REDO, { vm.redo() }, enabled = vm.canRedo, size = 24.dp) }
                Cell { GlyphBtn(Glyph.SKIP_START, { vm.jumpToStart() }, size = 24.dp) }
                Cell {
                    Box(
                        Modifier.size(42.dp).clip(CircleShape).pointerInput(Unit) {
                            detectTapGestures(onTap = { vm.togglePlay() }, onLongPress = { vm.loop = !vm.loop })
                        },
                        contentAlignment = Alignment.Center
                    ) {
                        if (vm.playing) Icon(Icons.Rounded.Pause, null, Modifier.size(28.dp), tint = cs.onSurface)
                        else com.siroha.relifemotion.ui.common.GlyphIcon(Glyph.PLAY, tint = if (vm.loop) cs.primaryContainer else cs.onSurface, size = 24.dp)
                    }
                }
                Cell { GlyphBtn(Glyph.SKIP_END, { vm.jumpToEnd() }, size = 24.dp) }
                Cell { GlyphBtn(Glyph.DUP_ADD, { menu = true }, size = 24.dp) }
                Cell { GlyphBtn(Glyph.FIT, { railOpen = !railOpen }, size = 24.dp, tint = if (railOpen) cs.primaryContainer else cs.onSurface) }
            }
            if (menu) TransportMenu(vm, with(density) { 46.dp.roundToPx() }) { menu = false }
        }

        // ------------------------------------------------ area bawah (319dp = timeline 91 + panel 228)
        val bottomMod = Modifier.fillMaxWidth()
        val bottomH = 280.dp
        when {
            drawMode != DrawMode.NONE -> DrawPanel(
                drawState,
                onConfirm = {
                    vm.addDrawing(drawState.pts.toList(), drawState.color, drawState.brush, drawState.closed, drawState.vector)
                    drawMode = DrawMode.NONE
                },
                onCancel = { drawMode = DrawMode.NONE },
                modifier = bottomMod.height(bottomH)
            )
            addOpen -> AddPanel(
                vm, { addOpen = false },
                onText = { addOpen = false; textTarget = TextTarget(null) },
                onFreehand = { addOpen = false; drawState = DrawState(false); drawMode = DrawMode.FREEHAND },
                onVector = { addOpen = false; drawState = DrawState(true); drawMode = DrawMode.VECTOR },
                onOpenMedia = { mediaPick = it }, onAudioAll = { audioAll = true },
                modifier = bottomMod.height(bottomH)
            )
            multi -> Box(bottomMod.height(bottomH)) {
                Timeline(vm, Modifier.fillMaxSize(), showFab = false, onFab = {})
                Row(
                    Modifier.align(Alignment.BottomCenter).padding(start = 6.dp, end = 6.dp, bottom = 6.dp).fillMaxWidth().height(42.dp)
                        .clip(RoundedCornerShape(12.dp)).background(cs.surfaceContainerHighest),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlyphBtn(Glyph.ALIGN_L, { vm.trimStartToPlayhead() }, size = 24.dp)
                    GlyphBtn(Glyph.ALIGN_C, { vm.splitAtPlayhead() }, size = 24.dp)
                    GlyphBtn(Glyph.ALIGN_R, { vm.trimEndToPlayhead() }, size = 24.dp)
                    Spacer(Modifier.weight(1f))
                    for (k in 0..2) {
                        Box(Modifier.size(40.dp).clickable { vm.align(k) }, contentAlignment = Alignment.Center) {
                            androidx.compose.foundation.Canvas(Modifier.size(width = 26.dp, height = 22.dp)) {
                                drawRoundRect(Color(0xFF6C7089), size = size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
                                val bw = listOf(0.8f, 0.55f, 0.35f)
                                for (r in 0..2) {
                                    val wBar = size.width * bw[r]
                                    val x = when (k) { 0 -> size.width * 0.1f; 1 -> (size.width - wBar) / 2f; else -> size.width * 0.9f - wBar }
                                    drawRoundRect(Color.White, androidx.compose.ui.geometry.Offset(x, size.height * (0.17f + r * 0.27f)), androidx.compose.ui.geometry.Size(wBar, 3.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()))
                                }
                            }
                        }
                    }
                }
            }
            sel != null -> {
                Timeline(vm, bottomMod.height(80.dp), showFab = false, onFab = {})
                PanelHost(
                    vm, vp, page, { page = it }, { effectsOpen = true },
                    { textTarget = TextTarget(sel.id) }, bottomMod.height(bottomH - 80.dp)
                )
            }
            else -> Timeline(vm, bottomMod.height(bottomH), showFab = true, onFab = { addOpen = true })
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
