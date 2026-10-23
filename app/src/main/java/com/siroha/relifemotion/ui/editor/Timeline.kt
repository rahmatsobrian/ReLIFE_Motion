package com.siroha.relifemotion.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.siroha.relifemotion.data.Layer
import com.siroha.relifemotion.data.LayerType
import com.siroha.relifemotion.data.fmtTime
import com.siroha.relifemotion.ui.common.Diamond
import com.siroha.relifemotion.ui.common.Glyph
import com.siroha.relifemotion.ui.common.GlyphIcon
import kotlin.math.roundToInt

fun layerColor(t: LayerType): Color = when (t) {
    LayerType.SHAPE, LayerType.DRAWING -> Color(0xFF52C7C1)
    LayerType.IMAGE -> Color(0xFF7E8CE0)
    LayerType.VIDEO -> Color(0xFF5C9BE0)
    LayerType.AUDIO -> Color(0xFFB070E0)
    LayerType.TEXT -> Color(0xFFE0A050)
    else -> Color(0xFF8A90A8)
}

private val Mint = Color(0xFF7BFBB0)

private fun midEllipsis(name: String, maxChars: Int): String {
    if (name.length <= maxChars || maxChars < 6) return name
    val keep = maxChars - 1
    val h = (keep + 1) / 2
    return name.take(h) + "…" + name.takeLast(keep - h)
}

@Composable
fun Timeline(vm: EditorVM, modifier: Modifier, showFab: Boolean, onFab: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val density = LocalDensity.current
    var dpPerSec by remember { mutableFloatStateOf(47f) }
    val pxPerMs = with(density) { dpPerSec.dp.toPx() } / 1000f
    val pxPerMsS by rememberUpdatedState(pxPerMs)
    val vmS by rememberUpdatedState(vm)
    val multi = vm.multiMode || vm.selected.size > 1
    val single = vm.sel
    val lanes = if (!multi && single != null) listOf(single) else vm.project.layers

    Box(
        modifier.background(cs.surface).pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                do {
                    val ev = awaitPointerEvent()
                    if (ev.changes.any { it.isConsumed }) continue
                    val n = ev.changes.count { it.pressed }
                    if (n >= 2) {
                        dpPerSec = (dpPerSec * ev.calculateZoom()).coerceIn(15f, 600f)
                        ev.changes.forEach { it.consume() }
                    } else if (n == 1) {
                        val c = ev.changes.first()
                        val dx = c.position.x - c.previousPosition.x
                        if (dx != 0f) {
                            vmS.seek(vmS.timeMs - (dx / pxPerMsS).toLong())
                            c.consume()
                        }
                    }
                } while (ev.changes.any { it.pressed })
            }
        }
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wPx = with(density) { maxWidth.toPx() }
            val cx = wPx / 2f

            // penggaris: hanya 0 .. durasi, tick menggantung dari atas
            Canvas(Modifier.fillMaxWidth().height(20.dp)) {
                val total = vm.totalMs
                val tickColor = cs.onSurfaceVariant.copy(alpha = 0.4f)
                val capColor = cs.onSurfaceVariant.copy(alpha = 0.55f)
                val minorOk = pxPerMs * 100f >= 3.dp.toPx()
                val visStart = (vm.timeMs - (cx / pxPerMs).toLong() - 1000L).coerceAtLeast(0L)
                var t = (visStart / 100L) * 100L
                val visEnd = minOf(total, vm.timeMs + (cx / pxPerMs).toLong() + 1000L)
                while (t <= visEnd) {
                    val x = cx + (t - vm.timeMs) * pxPerMs
                    when {
                        t == 0L || t == total -> drawLine(capColor, Offset(x, 0f), Offset(x, size.height), 1.5.dp.toPx())
                        t % 1000L == 0L -> drawLine(tickColor, Offset(x, 0f), Offset(x, 13.dp.toPx()), 1.5.dp.toPx())
                        minorOk -> drawLine(tickColor, Offset(x, 0f), Offset(x, 5.dp.toPx()), 1.dp.toPx())
                    }
                    t += 100L
                }
                if (total % 100L != 0L) {
                    val x = cx + (total - vm.timeMs) * pxPerMs
                    drawLine(capColor, Offset(x, 0f), Offset(x, size.height), 1.5.dp.toPx())
                }
            }

            // lajur layer (jarak antar lajur 32dp)
            Column(Modifier.fillMaxSize().padding(top = 51.dp).verticalScroll(rememberScrollState())) {
                lanes.forEach { l -> Lane(vm, l, cx, pxPerMs, multi) }
                Spacer(Modifier.height(80.dp))
            }

            // playhead dulu, lalu chip waktu DI DEPAN garis putih
            Box(Modifier.offset { IntOffset(cx.roundToInt() - 2, 0) }.width(1.5.dp).fillMaxHeight().background(Color.White))
            Box(
                Modifier.align(Alignment.TopCenter).padding(top = 21.dp).width(84.dp).height(20.dp)
                    .clip(RoundedCornerShape(3.dp)).background(cs.surfaceContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(fmtTime(vm.timeMs, vm.project.fps), fontSize = 15.sp, color = Color.White, maxLines = 1)
            }
        }
        if (showFab) {
            Box(
                Modifier.align(Alignment.BottomEnd).padding(end = 15.dp, bottom = 15.dp).size(54.dp)
                    .clip(CircleShape).background(cs.surfaceContainer)
                    .border(2.5.dp, Color(0xFF69F0AE), CircleShape).clickable { onFab() },
                contentAlignment = Alignment.Center
            ) { GlyphIcon(Glyph.PLUS, tint = Color(0xFF69F0AE), size = 26.dp) }
        }
    }
}

@Composable
private fun Lane(vm: EditorVM, l: Layer, cx: Float, pxPerMs: Float, multi: Boolean) {
    val cs = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val sel = l.id in vm.selected
    val color = layerColor(l.type)
    val lS by rememberUpdatedState(l)
    val ppm by rememberUpdatedState(pxPerMs)
    val x = cx + (l.startMs - vm.timeMs) * pxPerMs
    val w = ((l.endMs - l.startMs) * pxPerMs).coerceAtLeast(12f)
    val wDp = with(density) { w.toDp() }
    val chipColor = when (l.type) {
        LayerType.SHAPE, LayerType.TEXT, LayerType.DRAWING -> Color(l.fill).copy(alpha = 1f)
        else -> color
    }
    val pillShape = RoundedCornerShape(topEnd = 15.dp, bottomEnd = 15.dp)

    Box(Modifier.fillMaxWidth().height(32.dp)) {
        // bar bergaris
        Box(
            Modifier.offset { IntOffset(x.roundToInt(), 0) }.width(wDp).height(26.dp).align(Alignment.CenterStart)
                .clip(RoundedCornerShape(3.dp))
                .background(if (l.visible) color else color.copy(alpha = 0.4f))
                .drawBehind {
                    val sw = 14.dp.toPx()
                    var sx = sw
                    while (sx < size.width) { drawRect(Color.White.copy(alpha = 0.08f), Offset(sx, 0f), Size(sw, size.height)); sx += sw * 2 }
                }
                .then(if (sel && multi) Modifier.border(1.5.dp, Mint, RoundedCornerShape(3.dp)) else Modifier)
                .clickable { vm.select(l.id, additive = multi) }
                .pointerInput(l.id) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        val dMs = (drag.x / ppm).toLong()
                        val cur = lS
                        if (!vm.selected.contains(cur.id)) vm.select(cur.id)
                        val edge = 22.dp.toPx()
                        val local = change.position.x
                        val widthPx = size.width
                        when {
                            local < edge && widthPx > edge * 3 -> vm.moveLayerTimes(cur.id, cur.startMs + dMs, cur.endMs)
                            local > widthPx - edge && widthPx > edge * 3 -> vm.moveLayerTimes(cur.id, cur.startMs, cur.endMs + dMs)
                            else -> vm.moveLayerTimes(cur.id, cur.startMs + dMs, cur.endMs + dMs)
                        }
                    }
                }
        ) {
            Text(
                midEllipsis(l.name, ((wDp.value - 28f) / 6.2f).toInt()), Modifier.align(Alignment.CenterStart).padding(start = 16.dp, end = 6.dp),
                fontSize = 13.sp, color = Color.Black, maxLines = 1, overflow = TextOverflow.Clip
            )
            if (sel && !multi) {
                l.keys.map { it.t }.distinct().forEach { kt ->
                    val kx = (kt - l.startMs) * pxPerMs
                    if (kx in 0f..w) Box(Modifier.offset { IntOffset(kx.roundToInt() - 14, 0) }.align(Alignment.CenterStart)) {
                        Diamond(true, Color.White, 10.dp)
                    }
                }
            }
        }

        // pegangan "‹" dan "›" menempel di kiri/kanan bar saat satu layer dipilih
        if (sel && !multi) {
            Box(
                Modifier.offset { IntOffset(x.roundToInt() - with(density) { 30.dp.roundToPx() }, 0) }.align(Alignment.CenterStart)
                    .width(30.dp).height(26.dp).clip(RoundedCornerShape(topStart = 13.dp, bottomStart = 13.dp)).background(cs.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.ChevronLeft, null, Modifier.size(20.dp), tint = cs.onSurfaceVariant) }
            Box(
                Modifier.offset { IntOffset((x + w).roundToInt(), 0) }.align(Alignment.CenterStart)
                    .width(30.dp).height(26.dp).clip(RoundedCornerShape(topEnd = 13.dp, bottomEnd = 13.dp)).background(cs.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.ChevronRight, null, Modifier.size(20.dp), tint = cs.onSurfaceVariant) }
        }

        // gagang ≡ di kanan (redup)
        Box(Modifier.align(Alignment.CenterEnd).padding(end = 21.dp)) {
            GlyphIcon(Glyph.HAMBURGER, tint = cs.onSurfaceVariant.copy(alpha = 0.35f), size = 12.dp)
        }

        // pil kiri: mata + kotak warna (mode pilih: kotak centang)
        Row(
            Modifier.align(Alignment.CenterStart).height(29.dp).clip(pillShape)
                .background(if (multi && sel) Mint else cs.surfaceContainer)
                .then(if (multi && sel) Modifier else Modifier.border(1.dp, cs.outlineVariant.copy(alpha = 0.5f), pillShape))
                .clickable { if (multi) vm.select(l.id, additive = true) else vm.toggleVisible(l.id) }
                .padding(start = 9.dp, end = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(21.dp), contentAlignment = Alignment.Center) {
                if (!multi) Icon(if (l.visible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff, null, Modifier.size(21.dp), tint = cs.onSurface)
                else if (sel) Box(Modifier.size(18.dp).clip(CircleShape).background(Color(0xFF2A5A52)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Check, null, Modifier.size(12.dp), tint = Color.White)
                }
            }
            Spacer(Modifier.width(11.dp))
            if (l.locked) Icon(Icons.Rounded.Lock, null, Modifier.size(18.dp), tint = cs.onSurfaceVariant)
            else Box(Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)).background(chipColor))
        }
    }
}
