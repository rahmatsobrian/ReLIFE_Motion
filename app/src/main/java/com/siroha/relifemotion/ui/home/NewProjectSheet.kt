package com.siroha.relifemotion.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.siroha.relifemotion.Graph
import com.siroha.relifemotion.data.Project
import com.siroha.relifemotion.ui.common.DropdownField
import com.siroha.relifemotion.ui.common.IconBtn

val RESOLUTIONS = listOf(
    "2160p (4k)" to 2160, "1440p (QHD)" to 1440, "1080p (FHD)" to 1080, "720p (HD)" to 720,
    "540p (SD)" to 540, "360p" to 360, "270p" to 270
)
val FPS_LIST = listOf(12, 15, 18, 20, 24, 25, 30, 48, 50, 60)
val BG_COLORS = listOf(
    "Hitam" to 0xFF000000.toInt(), "Putih" to 0xFFFFFFFF.toInt(), "Abu-abu Muda" to 0xFFD0D0D0.toInt(),
    "Hijau" to 0xFF1DB954.toInt(), "Biru" to 0xFF2979FF.toInt(), "Merah" to 0xFFE53935.toInt(),
    "Kuning" to 0xFFFFC107.toInt(), "Transparan" to 0x00000000
)
private val RATIOS = listOf("16:9" to (16 to 9), "9:16" to (9 to 16), "4:5" to (4 to 5), "1:1" to (1 to 1), "4:3" to (4 to 3))

@Composable
fun NewProjectSheet(onDismiss: () -> Unit, onCreate: (Project) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var element by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("Proyek Baru") }
    var ratio by remember { mutableIntStateOf(1) }
    var custom by remember { mutableStateOf(false) }
    var cw by remember { mutableStateOf("1080") }
    var ch by remember { mutableStateOf("1920") }
    var res by remember { mutableIntStateOf(2) }
    var fps by remember { mutableIntStateOf(FPS_LIST.indexOf(60)) }
    var bg by remember { mutableIntStateOf(0) }

    fun dims(): Pair<Int, Int> {
        if (custom) return (cw.toIntOrNull() ?: 1080).coerceIn(16, 4096) to (ch.toIntOrNull() ?: 1920).coerceIn(16, 4096)
        val s = RESOLUTIONS[res].second
        val (rw, rh) = RATIOS[ratio].second
        fun ev(v: Float) = (v.toInt() / 2 * 2).coerceAtLeast(2)
        return if (rw >= rh) ev(s * rw / rh.toFloat()) to s else s to ev(s * rh / rw.toFloat())
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = cs.surfaceContainerLow, dragHandle = null,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().imePadding()) {
            Spacer(Modifier.height(8.dp))
            // Tab PROYEK | ELEMEN
            Row(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(cs.surfaceContainerHighest)) {
                listOf("PROYEK", "ELEMEN").forEachIndexed { i, t ->
                    val sel = (i == 1) == element
                    Box(
                        Modifier.weight(1f).fillMaxHeight().padding(3.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (sel) cs.inverseSurface else Color.Transparent).clickable { element = i == 1; if (element) name = "Elemen Baru" else name = "Proyek Baru" },
                        contentAlignment = Alignment.Center
                    ) { Text(t, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (sel) cs.primaryContainer else cs.onSurfaceVariant) }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = name, onValueChange = { name = it }, singleLine = true, modifier = Modifier.weight(1f),
                    textStyle = TextStyle(color = cs.onSurface, fontSize = 16.sp), cursorBrush = SolidColor(cs.primary)
                )
                Icon(Icons.Rounded.Close, null, Modifier.size(20.dp).clickable { name = "" }, tint = cs.onSurfaceVariant)
            }
            HorizontalDivider(color = cs.outline)
            Spacer(Modifier.height(14.dp))
            // Rasio
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                RATIOS.forEachIndexed { i, (label, _) ->
                    val sel = !custom && ratio == i
                    Box(
                        Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(6.dp))
                            .background(if (sel) cs.primaryContainer else Color.White).border(1.dp, if (sel) cs.primary else Color.Gray, RoundedCornerShape(6.dp))
                            .clickable { custom = false; ratio = i },
                        contentAlignment = Alignment.Center
                    ) { Text(label, fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.SemiBold) }
                }
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(6.dp)).background(if (custom) cs.primaryContainer else Color.White)
                        .border(1.dp, Color.Gray, RoundedCornerShape(6.dp)).clickable { custom = true },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp), tint = Color.Black) }
            }
            Spacer(Modifier.height(16.dp))
            if (custom) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Resolusi", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Ukuran Komposisi:", fontSize = 10.sp, color = cs.onSurfaceVariant)
                    }
                    NumBox(cw, { cw = it }, Modifier.width(88.dp))
                    Box(Modifier.padding(horizontal = 8.dp).size(26.dp).clip(CircleShape).background(cs.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Link, null, Modifier.size(16.dp))
                    }
                    NumBox(ch, { ch = it }, Modifier.width(88.dp))
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Resolusi", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        val (w, h) = dims()
                        Text("Ukuran Komposisi:\n$w × $h", fontSize = 10.sp, color = cs.onSurfaceVariant)
                    }
                    DropdownField(RESOLUTIONS[res].first, RESOLUTIONS.map { it.first }, { res = it }, Modifier.width(170.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Frame Rate", Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                DropdownField("${FPS_LIST[fps]} fps", FPS_LIST.map { "$it fps" }, { fps = it }, Modifier.width(170.dp))
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Latar Belakang", Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = if (element) cs.onSurfaceVariant else cs.onSurface)
                DropdownField(
                    BG_COLORS[bg].first, BG_COLORS.map { it.first }, { if (!element) bg = it }, Modifier.width(170.dp),
                    leading = { Box(Modifier.size(18.dp).background(Color(BG_COLORS[bg].second)).border(1.dp, Color.Gray)) }
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    val (w, h) = dims()
                    onCreate(Project(
                        name = name.ifBlank { if (element) "Elemen Baru" else "Proyek Baru" },
                        width = w, height = h, fps = FPS_LIST[fps], bg = BG_COLORS[bg].second, isElement = element
                    ))
                }) { Text(if (element) "BUAT ELEMEN" else "BUAT PROYEK", fontWeight = FontWeight.Bold, color = cs.onSurface) }
            }
            Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                IconBtn(Icons.Rounded.Close, onDismiss, size = 26.dp)
            }
        }
    }
}

@Composable
private fun NumBox(v: String, onChange: (String) -> Unit, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    Box(modifier.height(42.dp).clip(RoundedCornerShape(8.dp)).background(cs.surfaceContainerHighest), contentAlignment = Alignment.Center) {
        BasicTextField(
            value = v, onValueChange = { onChange(it.filter(Char::isDigit).take(4)) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = TextStyle(color = cs.onSurface, fontSize = 15.sp, textAlign = TextAlign.Center),
            cursorBrush = SolidColor(cs.primary)
        )
    }
}
