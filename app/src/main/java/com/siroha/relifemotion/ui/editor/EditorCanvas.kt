package com.siroha.relifemotion.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.siroha.relifemotion.Graph
import com.siroha.relifemotion.data.LayerType
import com.siroha.relifemotion.data.valueAt
import com.siroha.relifemotion.render.RenderCtx
import com.siroha.relifemotion.render.Renderer
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class Viewport {
    var zoom by mutableFloatStateOf(1f)
    var pan by mutableStateOf(Offset.Zero)
    var box by mutableStateOf(IntSize.Zero)
    var pw by mutableIntStateOf(1080)
    var ph by mutableIntStateOf(1920)

    val fit: Float get() = if (box.width == 0) 1f else min(box.width * 0.98f / pw, box.height * 0.98f / ph)
    val scale: Float get() = fit * zoom
    val offX: Float get() = box.width / 2f + pan.x - pw * scale / 2f
    val offY: Float get() = box.height / 2f + pan.y - ph * scale / 2f
    fun toProj(o: Offset) = Offset((o.x - offX) / scale, (o.y - offY) / scale)
    fun toScreen(x: Float, y: Float) = Offset(x * scale + offX, y * scale + offY)
    fun reset() { zoom = 1f; pan = Offset.Zero }
}

private val Mint = Color(0xFF1DE9B6)

@Composable
fun EditorCanvas(
    vm: EditorVM, vp: Viewport, gridOn: Boolean, gridSize: Float, modifier: Modifier = Modifier,
    interactive: Boolean = true, showSelection: Boolean = true
) {
    val lowQ = Graph.settings.lowPreview
    val rc = remember(lowQ) { RenderCtx(lowQuality = lowQ, export = false, invalidate = { vm.redrawTick++ }) }
    vp.pw = vm.project.width
    vp.ph = vm.project.height
    val slop = LocalViewConfiguration.current.touchSlop
    val vmS by rememberUpdatedState(vm)

    Box(
        modifier.fillMaxSize().onSizeChanged { vp.box = it }.pointerInput(interactive) {
            if (!interactive) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val v = vmS
                val t = v.timeMs
                val pj = vp.toProj(down.position)
                var handle = -1
                var hitId: String? = null
                val s0 = v.sel
                if (s0 != null && v.selected.size == 1 && !s0.locked && s0.type != LayerType.AUDIO) {
                    val c = Renderer.corners(s0, t)
                    for (i in 0 until 4) {
                        val sp = vp.toScreen(c[i * 2], c[i * 2 + 1])
                        if (hypot(sp.x - down.position.x, sp.y - down.position.y) < 36.dp.toPx()) { handle = i; break }
                    }
                }
                if (handle < 0) {
                    hitId = v.project.layers.firstOrNull {
                        it.visible && !it.locked && t >= it.startMs && t < it.endMs &&
                            it.type != LayerType.AUDIO && it.type != LayerType.GROUP && it.type != LayerType.CAMERA &&
                            Renderer.hit(it, t, pj.x, pj.y)
                    }?.id
                }
                val startSx = s0?.valueAt("sx", t) ?: 1f
                val startSy = s0?.valueAt("sy", t) ?: 1f
                val ctr = s0?.let { Renderer.center(it, t) }
                val ctrScr = ctr?.let { vp.toScreen(it[0], it[1]) }
                val startDist = if (ctrScr != null) hypot(down.position.x - ctrScr.x, down.position.y - ctrScr.y).coerceAtLeast(1f) else 1f
                var moved = false
                var multi = false
                do {
                    val ev = awaitPointerEvent()
                    val pressed = ev.changes.count { it.pressed }
                    if (pressed >= 2) {
                        multi = true
                        val z = ev.calculateZoom()
                        val p = ev.calculatePan()
                        vp.zoom = (vp.zoom * z).coerceIn(0.2f, 12f)
                        vp.pan = vp.pan + p
                        ev.changes.forEach { it.consume() }
                    } else if (pressed == 1 && !multi) {
                        val c = ev.changes.first { it.pressed }
                        if (!moved && (c.position - down.position).getDistance() > slop) moved = true
                        if (moved) {
                            val d = c.position - c.previousPosition
                            if (handle >= 0 && s0 != null && ctrScr != null) {
                                val dist = hypot(c.position.x - ctrScr.x, c.position.y - ctrScr.y)
                                val r = (dist / startDist).coerceIn(0.05f, 20f)
                                v.setProp(s0.id, "sx", startSx * r)
                                v.setProp(s0.id, "sy", startSy * r)
                                c.consume()
                            } else if (hitId != null) {
                                if (hitId !in v.selected) v.select(hitId)
                                val dx = d.x / vp.scale
                                val dy = d.y / vp.scale
                                for (l in v.selLayers) {
                                    if (l.locked) continue
                                    v.setProp(l.id, "x", l.valueAt("x", t) + dx, "mv")
                                    v.setProp(l.id, "y", l.valueAt("y", t) + dy, "mv")
                                }
                                c.consume()
                            }
                        }
                    }
                } while (ev.changes.any { it.pressed })
                if (!moved && !multi) {
                    if (hitId != null) v.select(hitId) else v.clearSelection()
                }
            }
        }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_VARIABLE") val tick = vm.redrawTick
            val p = vm.project
            val t = vm.timeMs
            val s = vp.scale
            val ox = vp.offX
            val oy = vp.offY
            drawIntoCanvas { cv ->
                val nc = cv.nativeCanvas
                nc.save()
                nc.translate(ox, oy)
                nc.scale(s, s)
                nc.clipRect(0f, 0f, p.width.toFloat(), p.height.toFloat())
                Renderer.renderProject(nc, p, t, rc)
                nc.restore()
            }
            drawRect(Color.White.copy(alpha = 0.25f), Offset(ox, oy), androidx.compose.ui.geometry.Size(p.width * s, p.height * s), style = Stroke(1.dp.toPx()))
            if (gridOn && gridSize * s > 5f) {
                val gc = Color.White.copy(alpha = 0.22f)
                var x = 0f
                while (x <= p.width) { drawLine(gc, Offset(ox + x * s, oy), Offset(ox + x * s, oy + p.height * s), 1f); x += gridSize }
                var y = 0f
                while (y <= p.height) { drawLine(gc, Offset(ox, oy + y * s), Offset(ox + p.width * s, oy + y * s), 1f); y += gridSize }
            }
            if (showSelection) {
                for (l in vm.selLayers) {
                    if (l.type == LayerType.AUDIO) continue
                    val c = Renderer.corners(l, t)
                    val pts = (0 until 4).map { vp.toScreen(c[it * 2], c[it * 2 + 1]) }
                    for (i in 0 until 4) drawLine(Mint, pts[i], pts[(i + 1) % 4], 2.dp.toPx())
                    if (vm.selected.size == 1 && !l.locked) pts.forEach {
                        drawCircle(Color.White, 7.dp.toPx(), it)
                        drawCircle(Mint, 7.dp.toPx(), it, style = Stroke(2.dp.toPx()))
                    }
                }
            }
        }
    }
}
