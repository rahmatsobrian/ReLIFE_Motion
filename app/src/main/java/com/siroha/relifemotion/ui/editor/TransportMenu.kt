package com.siroha.relifemotion.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

private val MenuBg = Color(0xFF343650)
private val MenuOn = Color(0xFFF2F3FA)
private val MenuOff = Color(0xFF8B8EA8)

private fun DrawScope.arrow(tint: Color, cx: Float, cy: Float, r: Float, out: Boolean) {
    val sw = 1.5.dp.toPx()
    val a = Offset(cx - r, cy + r); val b = Offset(cx + r, cy - r)
    if (out) {
        drawLine(tint, a, b, sw, StrokeCap.Round)
        drawLine(tint, b, Offset(b.x - r * 0.9f, b.y), sw, StrokeCap.Round)
        drawLine(tint, b, Offset(b.x, b.y + r * 0.9f), sw, StrokeCap.Round)
    } else {
        drawLine(tint, b, a, sw, StrokeCap.Round)
        drawLine(tint, a, Offset(a.x + r * 0.9f, a.y), sw, StrokeCap.Round)
        drawLine(tint, a, Offset(a.x, a.y - r * 0.9f), sw, StrokeCap.Round)
    }
}

@Composable
private fun MIcon(kind: Int, tint: Color) {
    Box(Modifier.size(23.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val st = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            if (kind == 0) {
                drawLine(tint, Offset(w * 0.1f, h * 0.22f), Offset(w * 0.78f, h * 0.22f), st.width, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.1f, h * 0.45f), Offset(w * 0.5f, h * 0.45f), st.width, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.1f, h * 0.68f), Offset(w * 0.4f, h * 0.68f), st.width, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.52f, h * 0.76f), Offset(w * 0.64f, h * 0.88f), st.width, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.64f, h * 0.88f), Offset(w * 0.9f, h * 0.58f), st.width, StrokeCap.Round)
            } else {
                drawRoundRect(tint, Offset(w * 0.36f, h * 0.04f), Size(w * 0.6f, h * 0.6f), CornerRadius(w * 0.1f), style = st)
                drawRoundRect(MenuBg, Offset(w * 0.04f, h * 0.36f), Size(w * 0.6f, h * 0.6f), CornerRadius(w * 0.1f))
                drawRoundRect(tint, Offset(w * 0.04f, h * 0.36f), Size(w * 0.6f, h * 0.6f), CornerRadius(w * 0.1f), style = st)
                when (kind) {
                    1 -> {
                        drawLine(tint, Offset(w * 0.66f, h * 0.18f), Offset(w * 0.66f, h * 0.5f), st.width, StrokeCap.Round)
                        drawLine(tint, Offset(w * 0.5f, h * 0.34f), Offset(w * 0.82f, h * 0.34f), st.width, StrokeCap.Round)
                    }
                    2 -> { arrow(tint, w * 0.66f, h * 0.34f, w * 0.12f, true); drawCircle(tint, w * 0.07f, Offset(w * 0.2f, h * 0.8f), style = Stroke(1.3.dp.toPx())) }
                    3 -> arrow(tint, w * 0.66f, h * 0.34f, w * 0.12f, true)
                    4 -> arrow(tint, w * 0.34f, h * 0.66f, w * 0.12f, false)
                    5 -> arrow(tint, w * 0.34f, h * 0.66f, w * 0.12f, false)
                }
            }
        }
        if (kind == 3 || kind == 5) Text("S", Modifier.align(if (kind == 3) Alignment.BottomStart else Alignment.TopEnd).padding(start = 4.dp, bottom = 2.dp, end = 3.dp), color = tint, fontSize = 9.sp)
    }
}

@Composable
private fun MRow(kind: Int, label: String, sub: String? = null, enabled: Boolean = true, badge: Boolean = false, chevron: Boolean = false, onClick: () -> Unit) {
    val c = if (enabled) MenuOn else MenuOff
    Row(
        Modifier.fillMaxWidth().height(if (sub != null) 48.dp else 40.dp).clickable(enabled = enabled) { onClick() }.padding(start = 16.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MIcon(kind, c)
        Spacer(Modifier.width(15.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = c, fontSize = 12.5.sp, maxLines = 1)
            if (sub != null) Text(sub, color = c, fontSize = 8.5.sp, maxLines = 1)
        }
        if (badge) Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFFF7CF55)).padding(horizontal = 5.dp, vertical = 1.dp)) {
            Text("NEW", color = Color(0xFF2B2A20), fontSize = 9.5.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        }
        if (chevron) Canvas(Modifier.size(12.dp)) {
            val p = androidx.compose.ui.graphics.Path().apply { moveTo(size.width * 0.3f, size.height * 0.1f); lineTo(size.width * 0.75f, size.height * 0.5f); lineTo(size.width * 0.3f, size.height * 0.9f) }
            drawPath(p, c, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** Menu popup yang menempel di tepi kanan layar, tepat di bawah baris transport. */
@Composable
fun TransportMenu(vm: EditorVM, offsetYPx: Int, onDismiss: () -> Unit) {
    val n = vm.selected.size
    Popup(alignment = Alignment.TopEnd, offset = IntOffset(0, offsetYPx), onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Column(
            Modifier.width(250.dp).clip(RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp)).background(MenuBg).padding(vertical = 3.dp)
        ) {
            MRow(0, "Pilih Semua Layer") { onDismiss(); vm.selectAll() }
            MRow(1, "Gandakan $n Layer", enabled = n > 0) { onDismiss(); vm.duplicateSelected() }
            MRow(2, "Simpan preset", enabled = false, badge = true) { onDismiss() }
            MRow(3, "Salin $n Layer", sub = "(termasuk gaya)", enabled = n > 0) { onDismiss(); vm.copySelected() }
            MRow(4, "Tempel ${vm.clipboard.size} Layer", enabled = vm.clipboard.isNotEmpty()) { onDismiss(); vm.paste() }
            MRow(5, "Tempel Gaya", enabled = vm.clipboard.isNotEmpty(), chevron = true) { onDismiss(); vm.pasteStyle() }
        }
    }
}
