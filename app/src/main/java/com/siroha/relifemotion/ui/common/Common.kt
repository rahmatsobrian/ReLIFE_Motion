package com.siroha.relifemotion.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/** Switch Material 3 dengan indikator (ikon centang / silang di dalam thumb). */
@Composable
fun ReSwitch(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        modifier = modifier,
        thumbContent = {
            Icon(
                imageVector = if (checked) Icons.Rounded.Check else Icons.Rounded.Close,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
        }
    )
}

@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, subtitle: String? = null) {
    Row(
        modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ReSwitch(checked, onChange)
    }
}

/** Segmented control sederhana (RENDAH | TINGGI | ULTRA). */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, height: Dp = 38.dp) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier.height(height).clip(RoundedCornerShape(12.dp)).background(cs.surfaceContainerHighest)
    ) {
        options.forEachIndexed { i, label ->
            val sel = i == selected
            Box(
                Modifier.weight(1f).fillMaxHeight().padding(3.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (sel) cs.inverseSurface else Color.Transparent)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                    color = if (sel) cs.primaryContainer else cs.onSurfaceVariant, maxLines = 1
                )
            }
        }
    }
}

/** Dropdown field (label + panah) berbasis DropdownMenu. */
@Composable
fun DropdownField(
    value: String, options: List<String>, onSelect: (Int) -> Unit, modifier: Modifier = Modifier,
    leading: @Composable (() -> Unit)? = null
) {
    var open by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(10.dp)).background(cs.surfaceContainerHighest)
                .clickable { open = true }.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leading != null) { leading(); Spacer(Modifier.width(8.dp)) }
            Text(value, Modifier.weight(1f), fontSize = 14.sp, maxLines = 1)
            Icon(Icons.Rounded.KeyboardArrowDown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { i, o ->
                DropdownMenuItem(text = { Text(o) }, onClick = { open = false; onSelect(i) })
            }
        }
    }
}

@Composable
fun RIcon(icon: ImageVector, tint: Color = MaterialTheme.colorScheme.onSurface, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Icon(icon, null, modifier.size(size), tint = tint)
}

@Composable
fun IconBtn(icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSurface,
            enabled: Boolean = true, box: Dp = 44.dp, size: Dp = 22.dp, bg: Color = Color.Transparent) {
    Box(
        modifier.size(box).clip(CircleShape).background(bg).clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, Modifier.size(size), tint = if (enabled) tint else tint.copy(alpha = 0.35f))
    }
}

/** Ikon keyframe belah-ketupat. */
@Composable
fun Diamond(filled: Boolean, color: Color, size: Dp = 18.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val p = Path().apply {
            moveTo(s / 2, s * 0.06f); lineTo(s * 0.94f, s / 2); lineTo(s / 2, s * 0.94f); lineTo(s * 0.06f, s / 2); close()
        }
        if (filled) drawPath(p, color) else drawPath(p, color, style = Stroke(width = s * 0.12f))
    }
}

/** Pil "label" kecil. */
@Composable
fun Pill(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier) {
    Surface(modifier, color = bg, shape = RoundedCornerShape(50)) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 2.dp), color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * Slider gaya penggaris (ala Alight Motion). Geser horizontal untuk mengubah nilai.
 * [sens] = unit nilai per piksel.
 */
@Composable
fun RulerSlider(
    value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier,
    sens: Float = 0.5f, min: Float = -1e9f, max: Float = 1e9f, onStart: () -> Unit = {}
) {
    val cs = MaterialTheme.colorScheme
    val cur by rememberUpdatedState(value)
    val onChangeS by rememberUpdatedState(onChange)
    val onStartS by rememberUpdatedState(onStart)
    val tick = cs.onSurfaceVariant.copy(alpha = 0.55f)
    val accent = cs.primary
    Canvas(
        modifier.height(54.dp).pointerInput(sens, min, max) {
            detectHorizontalDragGestures(onDragStart = { onStartS() }) { change, dx ->
                change.consume()
                onChangeS((cur - dx * sens).coerceIn(min, max))
            }
        }
    ) {
        val spacing = 10.dp.toPx()
        val offset = ((cur / sens) % spacing)
        val cx = size.width / 2
        var x = cx - floor(cx / spacing) * spacing + offset
        while (x < size.width) {
            val d = abs(x - cx)
            val a = (1f - d / cx).coerceIn(0.1f, 1f)
            val major = ((x - cx) / spacing).roundToInt() % 5 == 0
            val h = size.height * (if (major) 0.8f else 0.55f)
            drawLine(tick.copy(alpha = tick.alpha * a), Offset(x, (size.height - h) / 2), Offset(x, (size.height + h) / 2), strokeWidth = 1.5.dp.toPx())
            x += spacing
        }
        drawLine(accent, Offset(cx, 0f), Offset(cx, size.height), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
    }
}

/** Slider vertikal bergaya penggaris (untuk jarak grid). */
@Composable
fun VerticalBar(value: Float, onChange: (Float) -> Unit, min: Float, max: Float, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val onChangeS by rememberUpdatedState(onChange)
    Canvas(
        modifier.pointerInput(min, max) {
            detectDragGestures { change, _ ->
                change.consume()
                val f = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                onChangeS(min + (max - min) * f)
            }
        }
    ) {
        val n = 22
        for (i in 0 until n) {
            val y = size.height * i / (n - 1f)
            drawLine(cs.onSurfaceVariant.copy(alpha = 0.6f), Offset(size.width * 0.15f, y), Offset(size.width * (if (i % 5 == 0) 0.85f else 0.6f), y), 1.5.dp.toPx())
        }
        val fy = size.height * (1f - (value - min) / (max - min))
        drawLine(cs.primary, Offset(0f, fy), Offset(size.width, fy), 3.dp.toPx(), StrokeCap.Round)
    }
}

/** Kotak angka kecil bergaya Alight (label di bawah). */
@Composable
fun ValueBox(text: String, label: String, modifier: Modifier = Modifier, accent: Boolean = true) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier.clip(RoundedCornerShape(8.dp)).background(cs.surfaceContainerHighest).padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text, color = if (accent) cs.primary else cs.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(label, color = cs.onSurfaceVariant, fontSize = 9.sp)
    }
}

fun Modifier.dashedBorder(color: Color, radius: Dp = 12.dp): Modifier = this.border(1.5.dp, color, RoundedCornerShape(radius))

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary)
}
