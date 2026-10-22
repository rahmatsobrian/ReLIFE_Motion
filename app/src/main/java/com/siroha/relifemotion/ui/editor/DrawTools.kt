package com.siroha.relifemotion.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.siroha.relifemotion.data.STROKE_BREAK

class DrawState(val vector: Boolean) {
    val pts = mutableStateListOf<Float>()
    var color by mutableIntStateOf(0xFF0000FF.toInt())
    var brush by mutableFloatStateOf(5f)
    var closed by mutableStateOf(false)

    fun addPoint(x: Float, y: Float) { pts.add(x); pts.add(y) }
    fun endStroke() { if (pts.size >= 2 && pts[pts.size - 2] < STROKE_BREAK) { pts.add(STROKE_BREAK); pts.add(STROKE_BREAK) } }

    fun undo() {
        if (vector) { if (pts.size >= 2) { pts.removeAt(pts.size - 1); pts.removeAt(pts.size - 1) }; return }
        while (pts.size >= 2 && pts[pts.size - 2] >= STROKE_BREAK) { pts.removeAt(pts.size - 1); pts.removeAt(pts.size - 1) }
        while (pts.size >= 2 && pts[pts.size - 2] < STROKE_BREAK) { pts.removeAt(pts.size - 1); pts.removeAt(pts.size - 1) }
    }

    val valid: Boolean get() = pts.count { it < STROKE_BREAK } >= (if (vector) 4 else 2)
}

@Composable
fun DrawOverlay(ds: DrawState, vp: Viewport, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Canvas(
        modifier.fillMaxSize().pointerInput(ds.vector) {
            if (ds.vector) {
                detectTapGestures { o -> val p = vp.toProj(o); ds.addPoint(p.x, p.y) }
            } else {
                detectDragGestures(
                    onDragStart = { o -> val p = vp.toProj(o); ds.addPoint(p.x, p.y) },
                    onDragEnd = { ds.endStroke() }, onDragCancel = { ds.endStroke() }
                ) { change, _ -> change.consume(); val p = vp.toProj(change.position); ds.addPoint(p.x, p.y) }
            }
        }
    ) {
        val path = Path()
        var has = false
        var i = 0
        val n = ds.pts.size
        var lx = 0f; var ly = 0f
        while (i + 1 < n) {
            val x = ds.pts[i]; val y = ds.pts[i + 1]
            i += 2
            if (x >= STROKE_BREAK) { if (has) path.lineTo(lx, ly); has = false; continue }
            val s = vp.toScreen(x, y)
            if (!has) { path.moveTo(s.x, s.y); has = true } else path.lineTo(s.x, s.y)
            lx = s.x; ly = s.y
        }
        if (ds.vector && ds.closed && ds.pts.size >= 6) path.close()
        drawPath(path, Color(ds.color), style = Stroke(ds.brush * 3f * vp.scale, cap = StrokeCap.Round, join = StrokeJoin.Round))
        if (ds.vector) {
            var k = 0
            while (k + 1 < n) {
                val s = vp.toScreen(ds.pts[k], ds.pts[k + 1])
                drawCircle(Color.White, 7.dp.toPx(), s)
                drawCircle(if (k == 0) Color(0xFFFFC107) else accent, 4.dp.toPx(), s)
                k += 2
            }
        }
    }
}

@Composable
fun DrawPanel(ds: DrawState, onConfirm: () -> Unit, onCancel: () -> Unit, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    Row(modifier.background(cs.surfaceContainerLow).padding(8.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            Text(if (ds.vector) "Edit Points — ketuk kanvas untuk menambah titik" else "Gambar — seret di kanvas", fontSize = 11.sp, color = cs.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ukuran", fontSize = 11.sp, modifier = Modifier.width(50.dp))
                Slider(ds.brush, { ds.brush = it }, Modifier.weight(1f).height(32.dp), valueRange = 1f..40f)
                Text("${ds.brush.toInt()}", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(26.dp))
            }
            ColorPicker(ds.color, { ds.color = it })
        }
        Column(Modifier.width(52.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
            RailBtn(Icons.Rounded.Undo, { ds.undo() })
            if (ds.vector) RailBtn(Icons.Rounded.CropSquare, { ds.closed = !ds.closed }, ds.closed)
            RailBtn(Icons.Rounded.Check, { if (ds.valid) onConfirm() }, enabled = ds.valid)
            RailBtn(Icons.Rounded.Close, onCancel)
        }
    }
}
