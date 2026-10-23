package com.siroha.relifemotion.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.siroha.relifemotion.data.Ease
import com.siroha.relifemotion.data.Layer
import com.siroha.relifemotion.data.LayerType
import com.siroha.relifemotion.data.valueAt
import com.siroha.relifemotion.ui.common.Diamond
import com.siroha.relifemotion.ui.common.IconBtn
import com.siroha.relifemotion.ui.common.RulerSlider
import com.siroha.relifemotion.ui.common.ValueBox
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

enum class Page { MAIN, TRANSFORM, EASING, BORDER, BLEND, FILL, SHAPE, PRESETS, EFFECTS, TIME }

fun f2(v: Float): String = "%.2f".format(Locale.US, v)
fun f1(v: Float): String = "%.1f".format(Locale.US, v)

@Composable
fun NumInput(title: String, value: Float, onDismiss: () -> Unit, onOk: (Float) -> Unit) {
    var s by remember { mutableStateOf(f2(value)) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = { OutlinedTextField(s, { s = it }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)) },
        confirmButton = { TextButton(onClick = { s.replace(',', '.').toFloatOrNull()?.let(onOk); onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
fun EditableValue(text: String, label: String, value: Float, onCommit: (Float) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    ValueBox(text, label, modifier.clickable { open = true })
    if (open) NumInput(label, value, { open = false }, onCommit)
}

@Composable
fun RailBtn(icon: ImageVector, onClick: () -> Unit, selected: Boolean = false, enabled: Boolean = true) {
    val cs = MaterialTheme.colorScheme
    IconBtn(icon, onClick, tint = if (selected) cs.primary else cs.onSurface, enabled = enabled, box = 42.dp, size = 22.dp,
        bg = if (selected) cs.surfaceContainerHighest else Color.Transparent)
}

@Composable
fun PanelHost(vm: EditorVM, vp: Viewport, page: Page, onPage: (Page) -> Unit, onEffects: () -> Unit, onEditText: () -> Unit, modifier: Modifier) {
    val l = vm.sel
    if (l == null) { Box(modifier); return }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainer)) {
        when (page) {
            Page.MAIN -> MainPanel(vm, l, onPage, onEditText)
            Page.TRANSFORM, Page.EASING -> TransformPanel(vm, vp, l, page, onPage)
            Page.BORDER -> BorderPanel(vm, l, onPage)
            Page.BLEND -> BlendPanel(vm, l, onPage)
            Page.FILL -> FillPanel(vm, l, onPage)
            Page.SHAPE -> ShapePanel(vm, l, onPage)
            Page.PRESETS -> PresetsPanel(vm, l, onPage)
            Page.EFFECTS -> EffectsPanel(vm, l, onPage, onEffects)
            Page.TIME -> TimePanel(vm, l, onPage)
        }
    }
}

@Composable
private fun PTile(
    label: String?, icon: ImageVector?, modifier: Modifier, glyph: com.siroha.relifemotion.ui.common.Glyph? = null,
    labelSize: androidx.compose.ui.unit.TextUnit = 10.5.sp, dim: Boolean = false, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(4.dp), onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val tint = cs.onSurfaceVariant.copy(alpha = if (dim) 0.45f else 1f)
    val body: @Composable ColumnScope.() -> Unit = {
        if (glyph != null) com.siroha.relifemotion.ui.common.GlyphIcon(glyph, tint = cs.onSurface, size = 24.dp)
        else if (icon != null) Icon(icon, null, Modifier.size(23.dp), tint = tint)
        if (label != null) Text(label, fontSize = labelSize, color = tint, textAlign = TextAlign.Center, maxLines = 1)
    }
    if (label == null) Column(
        modifier.clip(shape).background(cs.surfaceContainerHigh).clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, content = body
    ) else Column(
        modifier.clip(shape).background(cs.surfaceContainerHigh).clickable { onClick() }.padding(top = 7.dp, bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween, content = body
    )
}

@Composable
private fun MainPanel(vm: EditorVM, l: Layer, onPage: (Page) -> Unit, onEditText: () -> Unit) {
    val hasAudio = l.type == LayerType.AUDIO || l.type == LayerType.VIDEO
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp).padding(top = 15.dp, bottom = 17.dp)) {
        Row(Modifier.fillMaxWidth().height(38.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PTile(null, Icons.Rounded.Speed, Modifier.weight(1f).fillMaxHeight()) { onPage(Page.TIME) }
            Row(Modifier.weight(3f).fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                PTile(null, null, Modifier.weight(1f).fillMaxHeight(), glyph = com.siroha.relifemotion.ui.common.Glyph.ALIGN_L, shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)) { vm.trimStartToPlayhead() }
                PTile(null, null, Modifier.weight(1f).fillMaxHeight(), glyph = com.siroha.relifemotion.ui.common.Glyph.ALIGN_C, shape = RoundedCornerShape(0.dp)) { vm.splitAtPlayhead() }
                PTile(null, null, Modifier.weight(1f).fillMaxHeight(), glyph = com.siroha.relifemotion.ui.common.Glyph.ALIGN_R, shape = RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)) { vm.trimEndToPlayhead() }
            }
            PTile(null, if (l.volume > 0f) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff, Modifier.weight(1f).fillMaxHeight(), dim = !hasAudio) {
                if (hasAudio) vm.setProp(l.id, "vol", if (l.volume > 0f) 0f else 1f)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().height(57.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PTile("Color & Fill", Icons.Rounded.Palette, Modifier.weight(1f).fillMaxHeight()) { onPage(Page.FILL) }
            PTile("Border & Bayangan", Icons.Rounded.CropSquare, Modifier.weight(1f).fillMaxHeight()) { onPage(Page.BORDER) }
            PTile("Blending & Opacity", Icons.Rounded.Layers, Modifier.weight(1f).fillMaxHeight()) { onPage(Page.BLEND) }
        }
        Spacer(Modifier.height(7.dp))
        Row(Modifier.fillMaxWidth().height(57.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PTile("Move & Transform", Icons.Rounded.OpenWith, Modifier.weight(1f).fillMaxHeight(), labelSize = 8.sp) { onPage(Page.TRANSFORM) }
            if (l.type == LayerType.TEXT) PTile("Teks", Icons.Rounded.TextFields, Modifier.weight(1f).fillMaxHeight()) { onEditText() }
            PTile("Edit Bentuk", Icons.Rounded.Edit, Modifier.weight(1f).fillMaxHeight()) { onPage(Page.SHAPE) }
            PTile("Presets", Icons.Rounded.Star, Modifier.weight(1f).fillMaxHeight()) { onPage(Page.PRESETS) }
            PTile("Efek", Icons.Rounded.AutoAwesome, Modifier.weight(1f).fillMaxHeight()) { onPage(Page.EFFECTS) }
        }
    }
}

private fun toolProps(tool: Int): List<String> = when (tool) {
    0 -> listOf("x", "y"); 1 -> listOf("rot"); 2 -> listOf("sx", "sy"); else -> listOf("kx", "ky")
}

@Composable
private fun TransformPanel(vm: EditorVM, vp: Viewport, l: Layer, page: Page, onPage: (Page) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var tool by remember { mutableIntStateOf(0) }
    var link by remember { mutableStateOf(true) }
    var menu by remember { mutableStateOf(false) }
    var overshoot by remember { mutableStateOf(false) }
    val props = toolProps(tool)
    val t = vm.timeMs
    val hasKey = vm.hasKeyAtPlayhead(l, props)

    Row(Modifier.fillMaxSize()) {
        // rel kiri
        Column(Modifier.width(48.dp).fillMaxHeight().padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            RailBtn(Icons.Rounded.ArrowBack, { if (page == Page.EASING) onPage(Page.TRANSFORM) else onPage(Page.MAIN) })
            Box(Modifier.size(42.dp).clip(CircleShape).clickable { vm.toggleKey(l.id, props) }, contentAlignment = Alignment.Center) {
                Diamond(hasKey, if (hasKey) cs.primary else cs.onSurface, 20.dp)
            }
            if (page == Page.TRANSFORM) RailBtn(Icons.Rounded.Timeline, { onPage(Page.EASING) }, enabled = true)
            else RailBtn(Icons.Rounded.ContentPaste, { vm.pasteEase()?.let { vm.setEase(l.id, props, it) } })
            Box {
                RailBtn(Icons.Rounded.MoreHoriz, { menu = true })
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (page == Page.TRANSFORM) {
                        DropdownMenuItem(text = { Text("Setel Ulang Kemiringan") }, onClick = { menu = false; vm.updateLayer(l.id) { it.copy(kx = 0f, ky = 0f, keys = it.keys.filterNot { k -> k.prop == "kx" || k.prop == "ky" }) } })
                        DropdownMenuItem(text = { Text("Atur Ulang Transform") }, onClick = {
                            menu = false
                            vm.updateLayer(l.id) { it.copy(rot = 0f, sx = 1f, sy = 1f, kx = 0f, ky = 0f, keys = emptyList()) }
                        })
                    } else {
                        val e = vm.easeAtPlayhead(l, props)
                        DropdownMenuItem(text = { Text("Salin Kurva") }, onClick = { menu = false; e?.let { vm.copyEase(it) } })
                        DropdownMenuItem(text = { Text("Tempel Kurva") }, enabled = vm.pasteEase() != null, onClick = { menu = false; vm.pasteEase()?.let { vm.setEase(l.id, props, it) } })
                        DropdownMenuItem(text = { Text("Tempel Kurva ke Semua Bingkai Utama") }, enabled = vm.pasteEase() != null, onClick = { menu = false; vm.pasteEase()?.let { vm.applyEaseToAllKeys(l.id, props, it) } })
                        DropdownMenuItem(text = { Text(if (overshoot) "Matikan Overshoot" else "Aktifkan Overshoot") }, onClick = { menu = false; overshoot = !overshoot })
                    }
                }
            }
        }
        // tengah
        Box(Modifier.weight(1f).fillMaxHeight().padding(vertical = 6.dp).clip(RoundedCornerShape(10.dp)).background(cs.surfaceContainerHigh)) {
            if (page == Page.EASING) {
                val e = vm.easeAtPlayhead(l, props)
                if (e == null) {
                    Text("Tambahkan keyframe (◆) di posisi playhead untuk mengatur easing.", Modifier.align(Alignment.Center).padding(16.dp),
                        fontSize = 12.sp, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
                } else EasingEditor(e, { vm.setEase(l.id, props, it) }, overshoot)
            } else when (tool) {
                0 -> MoveTool(vm, vp, l)
                1 -> RotateTool(vm, l)
                2 -> ScaleTool(vm, l, link) { link = !link }
                else -> SkewTool(vm, l)
            }
        }
        // rel kanan
        Column(Modifier.width(48.dp).fillMaxHeight().padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (page == Page.TRANSFORM) {
                RailBtn(Icons.Rounded.OpenWith, { tool = 0 }, tool == 0)
                RailBtn(Icons.Rounded.RotateRight, { tool = 1 }, tool == 1)
                RailBtn(Icons.Rounded.AspectRatio, { tool = 2 }, tool == 2)
                RailBtn(Icons.Rounded.Straighten, { tool = 3 }, tool == 3)
            } else {
                val e = vm.easeAtPlayhead(l, props)
                fun set(x: Ease) = vm.setEase(l.id, props, x)
                RailBtn(Icons.Rounded.Timeline, { if (e != null) set(Ease("bezier", 0.25f, 0.25f, 0.75f, 0.75f)) }, e?.type == "bezier" || e?.type == "linear")
                RailBtn(Icons.Rounded.Speed, { if (e != null) set(Ease("bounce")) }, e?.type == "bounce")
                RailBtn(Icons.Rounded.Insights, { if (e != null) set(Ease("steps", steps = 4)) }, e?.type == "steps")
                RailBtn(Icons.Rounded.Pause, { if (e != null) set(Ease("hold")) }, e?.type == "hold")
            }
        }
    }
}

@Composable
private fun MoveTool(vm: EditorVM, vp: Viewport, l: Layer) {
    val cs = MaterialTheme.colorScheme
    val t = vm.timeMs
    val lS by rememberUpdatedState(l)
    val x = l.valueAt("x", t); val y = l.valueAt("y", t)
    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EditableValue(f2(x), "X", x, { vm.setProp(l.id, "x", it) }, Modifier.weight(1f))
            EditableValue(f2(y), "Y", y, { vm.setProp(l.id, "y", it) }, Modifier.weight(1f))
            ValueBox("0.00", "Z", Modifier.weight(1f), accent = false)
        }
        Box(
            Modifier.fillMaxSize().padding(top = 8.dp).pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    val cur = lS
                    val s = vp.scale.coerceAtLeast(0.01f)
                    vm.setProp(cur.id, "x", cur.valueAt("x", vm.timeMs) + drag.x / s * 1.0f, "mv")
                    vm.setProp(cur.id, "y", cur.valueAt("y", vm.timeMs) + drag.y / s * 1.0f, "mv")
                }
            },
            contentAlignment = Alignment.Center
        ) {
            Text("Geser ke sini untuk memindahkan layer", fontSize = 12.sp, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun RotateTool(vm: EditorVM, l: Layer) {
    val cs = MaterialTheme.colorScheme
    val rot = l.valueAt("rot", vm.timeMs)
    val rotS by rememberUpdatedState(rot)
    var last by remember { mutableFloatStateOf(0f) }
    val accent = cs.primary
    val ring = cs.outlineVariant
    Box(
        Modifier.fillMaxSize().pointerInput(l.id) {
            detectDragGestures(onDragStart = { o -> last = Math.toDegrees(atan2((o.y - size.height / 2f).toDouble(), (o.x - size.width / 2f).toDouble())).toFloat() }) { change, _ ->
                change.consume()
                val a = Math.toDegrees(atan2((change.position.y - size.height / 2f).toDouble(), (change.position.x - size.width / 2f).toDouble())).toFloat()
                var d = a - last
                while (d > 180f) d -= 360f
                while (d < -180f) d += 360f
                last = a
                vm.setProp(l.id, "rot", rotS + d)
            }
        },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize().padding(8.dp)) {
            val r = min(size.width, size.height) / 2f - 14.dp.toPx()
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(ring, r, c, style = Stroke(3.dp.toPx()))
            val a = Math.toRadians((rotS - 90f).toDouble())
            val k = Offset(c.x + (cos(a) * r).toFloat(), c.y + (sin(a) * r).toFloat())
            drawCircle(Color.White, 14.dp.toPx(), k)
            drawCircle(accent, 14.dp.toPx(), k, style = Stroke(2.dp.toPx()))
        }
        var edit by remember { mutableStateOf(false) }
        Text("${rot.roundToInt()}°", Modifier.clickable { edit = true }, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        if (edit) NumInput("Rotasi (°)", rot, { edit = false }) { vm.setProp(l.id, "rot", it) }
    }
}

@Composable
private fun ScaleTool(vm: EditorVM, l: Layer, link: Boolean, onLink: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val t = vm.timeMs
    val sx = l.valueAt("sx", t); val sy = l.valueAt("sy", t)
    val w = l.w * sx; val h = l.h * sy
    Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.Center) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            EditableValue(f1(w), "Lebar", w, { nw ->
                val f = nw / l.w
                vm.setProp(l.id, "sx", f); if (link && sx != 0f) vm.setProp(l.id, "sy", f * sy / sx)
            }, Modifier.weight(1f))
            Box(Modifier.padding(horizontal = 6.dp).size(28.dp).clip(RoundedCornerShape(6.dp)).background(if (link) cs.outline else cs.surfaceContainerHighest).clickable { onLink() }, contentAlignment = Alignment.Center) {
                Icon(if (link) Icons.Rounded.Link else Icons.Rounded.LinkOff, null, Modifier.size(16.dp), tint = Color.White)
            }
            EditableValue(f1(h), "Tinggi", h, { nh ->
                val f = nh / l.h
                vm.setProp(l.id, "sy", f); if (link && sy != 0f) vm.setProp(l.id, "sx", f * sx / sy)
            }, Modifier.weight(1f))
        }
        RulerSlider(w, { nw ->
            val f = nw / l.w
            vm.setProp(l.id, "sx", f); if (link && sx != 0f) vm.setProp(l.id, "sy", f * sy / sx)
        }, Modifier.fillMaxWidth(), sens = 1f, min = 1f, max = 20000f)
        if (!link) RulerSlider(h, { nh -> vm.setProp(l.id, "sy", nh / l.h) }, Modifier.fillMaxWidth(), sens = 1f, min = 1f, max = 20000f)
    }
}

@Composable
private fun SkewTool(vm: EditorVM, l: Layer) {
    val t = vm.timeMs
    val kx = l.valueAt("kx", t); val ky = l.valueAt("ky", t)
    Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.SpaceEvenly) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EditableValue("${f1(kx)}°", "X SKEW", kx, { vm.setProp(l.id, "kx", it.coerceIn(-80f, 80f)) })
            RulerSlider(kx, { vm.setProp(l.id, "kx", it) }, Modifier.weight(1f), sens = 0.3f, min = -80f, max = 80f)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            EditableValue("${f1(ky)}°", "Y SKEW", ky, { vm.setProp(l.id, "ky", it.coerceIn(-80f, 80f)) })
            RulerSlider(ky, { vm.setProp(l.id, "ky", it) }, Modifier.weight(1f), sens = 0.3f, min = -80f, max = 80f)
        }
    }
}

// ---------------------------------------------------------------- easing

@Composable
fun EasingEditor(ease: Ease, onChange: (Ease) -> Unit, overshoot: Boolean) {
    val cs = MaterialTheme.colorScheme
    val easeS by rememberUpdatedState(ease)
    val yMin = if (overshoot) -0.5f else 0f
    val yMax = if (overshoot) 1.5f else 1f
    var grab by remember { mutableIntStateOf(-1) }
    val types = listOf("bezier", "bounce", "steps", "hold")
    val labels = listOf("CUBIC BEZIER EASING", "BOUNCE", "STEPS", "HOLD")
    val idx = types.indexOf(if (ease.type == "linear") "bezier" else ease.type).coerceAtLeast(0)
    val e = if (ease.type == "linear") Ease("bezier", 0.25f, 0.25f, 0.75f, 0.75f) else ease
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                Ease("bezier", 0.25f, 0.25f, 0.75f, 0.75f), Ease("bezier", 0.42f, 0f, 1f, 1f),
                Ease("bezier", 0f, 0f, 0.58f, 1f), Ease("bezier", 0.42f, 0f, 0.58f, 1f)
            ).forEach { p ->
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(6.dp)).background(cs.surfaceContainerHighest).clickable { onChange(p) }) {
                    Canvas(Modifier.fillMaxSize().padding(6.dp)) {
                        val path = Path()
                        for (i in 0..20) {
                            val x = i / 20f; val y = p.apply(x)
                            val o = Offset(x * size.width, size.height - y * size.height)
                            if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
                        }
                        drawPath(path, cs.onSurface, style = Stroke(1.5.dp.toPx()))
                    }
                }
            }
        }
        Canvas(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp).pointerInput(overshoot) {
                detectDragGestures(
                    onDragStart = { o ->
                        val cur = if (easeS.type == "linear") Ease("bezier", 0.25f, 0.25f, 0.75f, 0.75f) else easeS
                        if (cur.type != "bezier") { grab = -1; return@detectDragGestures }
                        fun sx(x: Float) = x * size.width
                        fun sy(y: Float) = size.height - (y - yMin) / (yMax - yMin) * size.height
                        val d1 = (o - Offset(sx(cur.a), sy(cur.b))).getDistance()
                        val d2 = (o - Offset(sx(cur.c), sy(cur.d))).getDistance()
                        grab = if (d1 < d2) 0 else 1
                    },
                    onDragEnd = { grab = -1 }
                ) { change, _ ->
                    change.consume()
                    val cur = if (easeS.type == "linear") Ease("bezier", 0.25f, 0.25f, 0.75f, 0.75f) else easeS
                    if (cur.type != "bezier" || grab < 0) return@detectDragGestures
                    val nx = (change.position.x / size.width).coerceIn(0f, 1f)
                    val ny = (yMin + (1f - change.position.y / size.height) * (yMax - yMin)).coerceIn(yMin, yMax)
                    onChange(if (grab == 0) cur.copy(type = "bezier", a = nx, b = ny) else cur.copy(type = "bezier", c = nx, d = ny))
                }
            }
        ) {
            fun px(x: Float) = x * size.width
            fun py(y: Float) = size.height - (y - yMin) / (yMax - yMin) * size.height
            for (i in 0..4) {
                val gx = size.width * i / 4f
                drawLine(cs.outlineVariant.copy(alpha = 0.6f), Offset(gx, 0f), Offset(gx, size.height), 1f)
                val gy = size.height * i / 4f
                drawLine(cs.outlineVariant.copy(alpha = 0.6f), Offset(0f, gy), Offset(size.width, gy), 1f)
            }
            drawLine(cs.onSurfaceVariant, Offset(px(0f), py(0f)), Offset(px(1f), py(0f)), 1.5f)
            drawLine(cs.onSurfaceVariant, Offset(px(0f), py(1f)), Offset(px(1f), py(1f)), 1.5f)
            val path = Path()
            for (i in 0..80) {
                val x = i / 80f
                val y = e.apply(x)
                if (i == 0) path.moveTo(px(x), py(y)) else path.lineTo(px(x), py(y))
            }
            drawPath(path, cs.primary, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            if (e.type == "bezier") {
                val p0 = Offset(px(0f), py(0f)); val p3 = Offset(px(1f), py(1f))
                val h1 = Offset(px(e.a), py(e.b)); val h2 = Offset(px(e.c), py(e.d))
                drawLine(Color.White, p0, h1, 2.dp.toPx()); drawLine(Color.White, p3, h2, 2.dp.toPx())
                drawCircle(Color.White, 9.dp.toPx(), h1); drawCircle(Color.White, 9.dp.toPx(), h2)
                drawCircle(cs.primary, 5.dp.toPx(), p0); drawCircle(cs.primary, 5.dp.toPx(), p3)
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            IconBtn(Icons.Rounded.ChevronLeft, {
                val n = (idx + types.size - 1) % types.size
                onChange(defaultOf(types[n]))
            }, box = 34.dp)
            Text(labels[idx], fontSize = 10.sp, fontWeight = FontWeight.Bold, color = cs.onSurfaceVariant, modifier = Modifier.width(150.dp), textAlign = TextAlign.Center)
            IconBtn(Icons.Rounded.ChevronRight, {
                val n = (idx + 1) % types.size
                onChange(defaultOf(types[n]))
            }, box = 34.dp)
        }
    }
}

private fun defaultOf(type: String): Ease = when (type) {
    "bezier" -> Ease("bezier", 0.25f, 0.25f, 0.75f, 0.75f)
    "steps" -> Ease("steps", steps = 4)
    else -> Ease(type)
}
