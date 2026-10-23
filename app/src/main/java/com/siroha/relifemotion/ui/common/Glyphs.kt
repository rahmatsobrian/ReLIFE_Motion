package com.siroha.relifemotion.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class Glyph { UNDO, REDO, SKIP_START, SKIP_END, DUP_ADD, FIT, EXIT, MORE_CIRCLE, HAMBURGER, PLAY, PLUS,
    CLOSE, TRASH, DUP_DASH, DASH_A, DASH_B, DASH_C, ALIGN_L, ALIGN_C, ALIGN_R }

private fun DrawScope.st(w: Float = 2f) = Stroke(w.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

private fun DrawScope.draw(g: Glyph, c: Color) {
    val w = size.width; val h = size.height
    fun p(x: Float, y: Float) = Offset(x * w, y * h)
    when (g) {
        Glyph.UNDO, Glyph.REDO -> {
            val body = Path().apply {
                moveTo(w * 0.2f, h * 0.45f); lineTo(w * 0.62f, h * 0.45f)
                cubicTo(w * 0.9f, h * 0.45f, w * 0.9f, h * 0.8f, w * 0.62f, h * 0.8f)
                lineTo(w * 0.4f, h * 0.8f)
            }
            val head = Path().apply {
                moveTo(w * 0.38f, h * 0.27f); lineTo(w * 0.2f, h * 0.45f); lineTo(w * 0.38f, h * 0.63f)
            }
            if (g == Glyph.REDO) scale(-1f, 1f, Offset(w / 2, h / 2)) { drawPath(body, c, style = st()); drawPath(head, c, style = st()) }
            else { drawPath(body, c, style = st()); drawPath(head, c, style = st()) }
        }
        Glyph.SKIP_START, Glyph.SKIP_END -> {
            val m = g == Glyph.SKIP_END
            scale(if (m) -1f else 1f, 1f, Offset(w / 2, h / 2)) {
                drawLine(c, p(0.14f, 0.24f), p(0.14f, 0.76f), 2.dp.toPx(), StrokeCap.Round)
                drawLine(c, p(0.86f, 0.5f), p(0.3f, 0.5f), 2.dp.toPx(), StrokeCap.Round)
                val head = Path().apply { moveTo(w * 0.52f, h * 0.28f); lineTo(w * 0.3f, h * 0.5f); lineTo(w * 0.52f, h * 0.72f) }
                drawPath(head, c, style = st())
            }
        }
        Glyph.DUP_ADD -> {
            drawRoundRect(c, p(0.36f, 0.1f), Size(w * 0.52f, h * 0.52f), CornerRadius(w * 0.1f), style = st())
            drawRoundRect(c, p(0.12f, 0.34f), Size(w * 0.52f, h * 0.52f), CornerRadius(w * 0.1f), style = st())
            drawLine(c, p(0.08f, 0.9f), p(0.28f, 0.9f), 2.dp.toPx(), StrokeCap.Round)
            drawLine(c, p(0.18f, 0.8f), p(0.18f, 1.0f), 2.dp.toPx(), StrokeCap.Round)
        }
        Glyph.FIT -> {
            val s = 2.dp.toPx()
            val l = w * 0.12f; val r = w * 0.88f; val t = h * 0.18f; val b = h * 0.92f; val k = w * 0.2f
            fun corner(x: Float, y: Float, dx: Float, dy: Float) {
                drawLine(c, Offset(x, y + dy * k), Offset(x, y), s, StrokeCap.Round)
                drawLine(c, Offset(x, y), Offset(x + dx * k, y), s, StrokeCap.Round)
            }
            corner(l, t, 1f, 1f); corner(r, t, -1f, 1f); corner(l, b, 1f, -1f); corner(r, b, -1f, -1f)
            drawRoundRect(c, p(0.3f, 0.36f), Size(w * 0.4f, h * 0.38f), CornerRadius(w * 0.06f), style = st())
            val caret = Path().apply { moveTo(w * 0.44f, h * 0.1f); lineTo(w * 0.56f, h * 0.1f); lineTo(w * 0.5f, h * 0.02f); close() }
            drawPath(caret, c)
        }
        Glyph.EXIT -> {
            val box = Path().apply { moveTo(w * 0.5f, h * 0.2f); lineTo(w * 0.78f, h * 0.2f); lineTo(w * 0.78f, h * 0.8f); lineTo(w * 0.5f, h * 0.8f) }
            drawPath(box, c, style = st())
            drawLine(c, p(0.12f, 0.5f), p(0.62f, 0.5f), 2.dp.toPx(), StrokeCap.Round)
            val head = Path().apply { moveTo(w * 0.32f, h * 0.3f); lineTo(w * 0.12f, h * 0.5f); lineTo(w * 0.32f, h * 0.7f) }
            drawPath(head, c, style = st())
        }
        Glyph.MORE_CIRCLE -> {
            drawCircle(c, w * 0.42f, Offset(w / 2, h / 2), style = st())
            listOf(0.3f, 0.5f, 0.7f).forEach { drawCircle(c, 1.7.dp.toPx(), p(it, 0.5f)) }
        }
        Glyph.HAMBURGER -> listOf(0.3f, 0.5f, 0.7f).forEach { drawLine(c, p(0.15f, it), p(0.85f, it), 1.6.dp.toPx(), StrokeCap.Round) }
        Glyph.PLAY -> drawPath(Path().apply { moveTo(w * 0.22f, h * 0.12f); lineTo(w * 0.86f, h * 0.5f); lineTo(w * 0.22f, h * 0.88f); close() }, c)
        Glyph.PLUS -> {
            drawLine(c, p(0.5f, 0.15f), p(0.5f, 0.85f), 2.2.dp.toPx(), StrokeCap.Round)
            drawLine(c, p(0.15f, 0.5f), p(0.85f, 0.5f), 2.2.dp.toPx(), StrokeCap.Round)
        }
        Glyph.CLOSE -> {
            drawLine(c, p(0.12f, 0.12f), p(0.88f, 0.88f), 2.dp.toPx(), StrokeCap.Round)
            drawLine(c, p(0.88f, 0.12f), p(0.12f, 0.88f), 2.dp.toPx(), StrokeCap.Round)
        }
        Glyph.TRASH -> {
            val s1 = st(1.8f)
            drawLine(c, p(0.14f, 0.24f), p(0.86f, 0.24f), 1.8.dp.toPx(), StrokeCap.Round)
            drawPath(Path().apply { moveTo(w * 0.4f, h * 0.24f); lineTo(w * 0.4f, h * 0.1f); lineTo(w * 0.6f, h * 0.1f); lineTo(w * 0.6f, h * 0.24f) }, c, style = s1)
            drawPath(Path().apply { moveTo(w * 0.22f, h * 0.3f); lineTo(w * 0.29f, h * 0.9f); lineTo(w * 0.71f, h * 0.9f); lineTo(w * 0.78f, h * 0.3f) }, c, style = s1)
            listOf(0.4f, 0.5f, 0.6f).forEach { drawLine(c, p(it, 0.4f), p(it, 0.78f), 1.2.dp.toPx(), StrokeCap.Round) }
        }
        Glyph.DUP_DASH -> {
            val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))
            drawRoundRect(c, p(0.04f, 0.12f), Size(w * 0.54f, h * 0.54f), CornerRadius(w * 0.08f), style = Stroke(1.6.dp.toPx(), pathEffect = dash))
            drawRoundRect(c, p(0.34f, 0.4f), Size(w * 0.54f, h * 0.54f), CornerRadius(w * 0.08f), style = st())
            val bc = Color(0xFF3FD9C0)
            drawCircle(Color(0xFF1B2C34), w * 0.17f, p(0.8f, 0.2f))
            drawCircle(bc, w * 0.17f, p(0.8f, 0.2f), style = Stroke(1.5.dp.toPx()))
            drawCircle(bc, w * 0.07f, p(0.8f, 0.2f), style = Stroke(1.2.dp.toPx()))
        }
        Glyph.DASH_A, Glyph.DASH_B, Glyph.DASH_C -> {
            val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))
            drawRoundRect(c, p(0.05f, 0.05f), Size(w * 0.9f, h * 0.9f), CornerRadius(w * 0.05f), style = Stroke(1.6.dp.toPx(), pathEffect = dash))
            val tri = Path().apply { moveTo(w * 0.36f, h * 0.2f); lineTo(w * 0.2f, h * 0.55f); lineTo(w * 0.52f, h * 0.55f); close() }
            when (g) {
                Glyph.DASH_C -> drawPath(tri, c)
                Glyph.DASH_B -> drawPath(tri, c.copy(alpha = 0.5f), style = st(1.6f))
                else -> drawPath(tri, c, style = st(1.6f))
            }
            drawCircle(c, w * 0.17f, p(0.62f, 0.62f), style = st(1.8f))
            if (g == Glyph.DASH_A) listOf(p(0.05f, 0.05f), p(0.95f, 0.05f), p(0.05f, 0.95f), p(0.95f, 0.95f)).forEach { drawCircle(c, 2.dp.toPx(), it) }
        }
        Glyph.ALIGN_L, Glyph.ALIGN_C, Glyph.ALIGN_R -> {
            val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.5.dp.toPx()))
            val dashed = Stroke(1.6.dp.toPx(), pathEffect = dash)
            val solid = st(1.8f)
            drawRoundRect(c, p(0.04f, 0.34f), Size(w * 0.32f, h * 0.32f), CornerRadius(w * 0.04f), style = if (g == Glyph.ALIGN_L) dashed else solid)
            drawLine(c, p(0.5f, 0.1f), p(0.5f, 0.9f), 2.dp.toPx(), StrokeCap.Round)
            drawRoundRect(c, p(0.6f, 0.26f), Size(w * 0.34f, h * 0.48f), CornerRadius(w * 0.04f), style = if (g == Glyph.ALIGN_R) dashed else solid)
        }
    }
}

@Composable
fun GlyphIcon(g: Glyph, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSurface, size: Dp = 22.dp) {
    Canvas(modifier.size(size)) { draw(g, tint) }
}

@Composable
fun GlyphBtn(g: Glyph, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
             tint: Color = MaterialTheme.colorScheme.onSurface, box: Dp = 46.dp, size: Dp = 22.dp) {
    Box(modifier.size(box).clip(CircleShape).clickable(enabled = enabled) { onClick() }, contentAlignment = Alignment.Center) {
        GlyphIcon(g, tint = if (enabled) tint else tint.copy(alpha = 0.3f), size = size)
    }
}
