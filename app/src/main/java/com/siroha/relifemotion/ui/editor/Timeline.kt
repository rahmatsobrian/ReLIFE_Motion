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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
import kotlin.math.roundToInt

fun layerColor(t: LayerType): Color = when (t) {
    LayerType.SHAPE, LayerType.DRAWING -> Color(0xFF3CCFC0)
    LayerType.IMAGE -> Color(0xFF7E8CE0)
    LayerType.VIDEO -> Color(0xFF5C9BE0)
    LayerType.AUDIO -> Color(0xFFB070E0)
    LayerType.TEXT -> Color(0xFFE0A050)
    else -> Color(0xFF8A90A8)
}

@Composable
fun Timeline(vm: EditorVM, modifier: Modifier, showFab: Boolean, onFab: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val density = LocalDensity.current
    var dpPerSec by remember { mutableFloatStateOf(80f) }
    val pxPerMs = with(density) { dpPerSec.dp.toPx() } / 1000f
    val pxPerMsS by rememberUpdatedState(pxPerMs)
    val vmS by rememberUpdatedState(vm)

    Box(
        modifier.background(cs.surfaceContainerLow).pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                do {
                    val ev = awaitPointerEvent()
                    if (ev.changes.any { it.isConsumed }) continue
                    val n = ev.changes.count { it.pressed }
                    if (n >= 2) {
                        dpPerSec = (dpPerSec * ev.calculateZoom()).coerceIn(20f, 600f)
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
            Column(Modifier.fillMaxSize()) {
                Text(
                    fmtTime(vm.timeMs, vm.project.fps), Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp),
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = cs.onSurface
                )
                // penggaris
                Canvas(Modifier.fillMaxWidth().height(14.dp)) {
                    val stepMs = if (dpPerSec >= 120f) 250L else if (dpPerSec >= 50f) 500L else 1000L
                    val t0 = vm.timeMs - (cx / pxPerMs).toLong() - stepMs
                    var t = (t0 / stepMs) * stepMs
                    val end = vm.timeMs + (cx / pxPerMs).toLong() + stepMs
                    while (t <= end) {
                        if (t >= 0) {
                            val x = cx + (t - vm.timeMs) * pxPerMs
                            val major = t % 1000L == 0L
                            drawLine(cs.onSurfaceVariant.copy(alpha = if (major) 0.8f else 0.4f), Offset(x, size.height), Offset(x, size.height * (if (major) 0.2f else 0.55f)), 1.dp.toPx())
                        }
                        t += stepMs
                    }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    vm.project.layers.forEach { l ->
                        Lane(vm, l, cx, pxPerMs)
                    }
                    Spacer(Modifier.height(60.dp))
                }
            }
            // playhead
            Box(Modifier.offset { IntOffset(cx.roundToInt() - 1, 0) }.width(2.dp).fillMaxHeight().background(Color.White))
        }
        if (showFab) {
            Box(
                Modifier.align(Alignment.BottomEnd).padding(14.dp).size(52.dp).clip(CircleShape).background(cs.surface)
                    .border(2.dp, cs.primary, CircleShape).clickable { onFab() },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.Add, "Tambah layer", tint = cs.primary, modifier = Modifier.size(30.dp)) }
        }
    }
}

@Composable
private fun Lane(vm: EditorVM, l: Layer, cx: Float, pxPerMs: Float) {
    val cs = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val sel = l.id in vm.selected
    val laneH = 34.dp
    val color = layerColor(l.type)
    val lS by rememberUpdatedState(l)
    val ppm by rememberUpdatedState(pxPerMs)
    val x = cx + (l.startMs - vm.timeMs) * pxPerMs
    val w = ((l.endMs - l.startMs) * pxPerMs).coerceAtLeast(12f)

    Box(Modifier.fillMaxWidth().height(laneH).padding(vertical = 2.dp)) {
        Box(
            Modifier.offset { IntOffset(x.roundToInt(), 0) }.width(with(density) { w.toDp() }).fillMaxHeight()
                .clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = if (l.visible) 0.95f else 0.4f))
                .then(if (sel) Modifier.border(2.dp, Color.White, RoundedCornerShape(6.dp)) else Modifier)
                .clickable { vm.select(l.id, additive = false) }
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
                l.name, Modifier.align(Alignment.CenterStart).padding(start = 30.dp, end = 6.dp), fontSize = 11.sp,
                color = Color.Black, maxLines = 1, overflow = TextOverflow.Clip, fontWeight = FontWeight.Medium
            )
            if (sel) {
                l.keys.map { it.t }.distinct().forEach { kt ->
                    val kx = (kt - l.startMs) * pxPerMs
                    if (kx in 0f..w) Box(Modifier.offset { IntOffset(kx.roundToInt() - 20, 0) }.align(Alignment.CenterStart)) {
                        Diamond(true, Color.White, 10.dp)
                    }
                }
            }
        }
        // ikon mata + tipe di tepi kiri
        Row(
            Modifier.align(Alignment.CenterStart).clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)).background(cs.surfaceContainerHigh.copy(alpha = 0.92f)).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (l.visible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff, null,
                Modifier.size(16.dp).clickable { vm.toggleVisible(l.id) }, tint = cs.onSurface
            )
            Spacer(Modifier.width(4.dp))
            if (l.locked) Icon(Icons.Rounded.Lock, null, Modifier.size(12.dp), tint = cs.onSurfaceVariant)
            else Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
        }
    }
}
