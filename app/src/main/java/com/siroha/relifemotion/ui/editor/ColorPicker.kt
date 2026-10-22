package com.siroha.relifemotion.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val SWATCHES = listOf(
    0xFFFF3B30, 0xFFFF9500, 0xFFFFCC00, 0xFF34C759, 0xFF00C7BE, 0xFF007AFF, 0xFF5856D6, 0xFFFF2D92,
    0xFFFFFFFF, 0xFFC7C7CC, 0xFF8E8E93, 0xFF48484A, 0xFF000000, 0xFF64D2FF, 0xFFA2D86A, 0xFFD9EE8B,
    0xFFFFD6D6, 0xFFFFE9C7, 0xFFFFF7C2, 0xFFC7F2D4, 0xFFC2F2EE, 0xFFC7DEFF, 0xFFD9D7FF, 0xFFFFD1E6
).map { it.toInt() }

fun hex(c: Int): String = "#%06X (%d%%)".format(c and 0xFFFFFF, (android.graphics.Color.alpha(c) / 2.55f).toInt())

/** Pemilih warna: header hex, kisi swatch, dan slider HSV + alpha. */
@Composable
fun ColorPicker(color: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    var advanced by remember { mutableStateOf(false) }
    val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(color, it) }
    val alpha = android.graphics.Color.alpha(color)
    fun set(h: Float, s: Float, v: Float, a: Int) {
        onChange(android.graphics.Color.HSVToColor(a, floatArrayOf(h, s, v)))
    }
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF0000FF).copy(alpha = 0f).let { Color(color).copy(alpha = 1f) })
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val lum = (0.299f * android.graphics.Color.red(color) + 0.587f * android.graphics.Color.green(color) + 0.114f * android.graphics.Color.blue(color)) / 255f
            Text(hex(color), Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (lum > 0.6f) Color.Black else Color.White)
            Text(if (advanced) "Swatch" else "HSV", Modifier.clickable { advanced = !advanced }.padding(4.dp), fontSize = 11.sp,
                fontWeight = FontWeight.Bold, color = if (lum > 0.6f) Color.Black else Color.White)
        }
        Spacer(Modifier.height(6.dp))
        if (!advanced) {
            SWATCHES.chunked(8).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { c ->
                        Box(
                            Modifier.weight(1f).height(26.dp).clip(RoundedCornerShape(5.dp)).background(Color(c))
                                .border(if (c == (color or (0xFF shl 24)) && alpha == 255) 2.dp else 0.5.dp, if (c == color) cs.primary else cs.outlineVariant, RoundedCornerShape(5.dp))
                                .clickable { onChange(c) }
                        )
                    }
                }
            }
        } else {
            Text("Hue", fontSize = 10.sp, color = cs.onSurfaceVariant)
            Slider(hsv[0], { set(it, hsv[1], hsv[2], alpha) }, valueRange = 0f..360f, modifier = Modifier.height(28.dp))
            Text("Saturasi", fontSize = 10.sp, color = cs.onSurfaceVariant)
            Slider(hsv[1], { set(hsv[0], it, hsv[2], alpha) }, valueRange = 0f..1f, modifier = Modifier.height(28.dp))
            Text("Nilai", fontSize = 10.sp, color = cs.onSurfaceVariant)
            Slider(hsv[2], { set(hsv[0], hsv[1], it, alpha) }, valueRange = 0f..1f, modifier = Modifier.height(28.dp))
            Text("Alpha", fontSize = 10.sp, color = cs.onSurfaceVariant)
            Slider(alpha / 255f, { set(hsv[0], hsv[1], hsv[2], (it * 255).toInt()) }, valueRange = 0f..1f, modifier = Modifier.height(28.dp))
        }
    }
}
