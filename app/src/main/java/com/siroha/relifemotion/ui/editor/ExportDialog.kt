package com.siroha.relifemotion.ui.editor

import android.media.MediaCodecList
import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.siroha.relifemotion.export.Exporter
import com.siroha.relifemotion.export.VideoOpts
import com.siroha.relifemotion.ui.common.DropdownField
import com.siroha.relifemotion.ui.common.IconBtn
import com.siroha.relifemotion.ui.common.ReSwitch
import com.siroha.relifemotion.ui.common.Segmented
import com.siroha.relifemotion.ui.home.RESOLUTIONS
import com.siroha.relifemotion.ui.home.toast
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private fun human(b: Long): String = when {
    b >= 1L shl 30 -> "%.1fGB".format(java.util.Locale.US, b / (1024.0 * 1024 * 1024))
    b >= 1L shl 20 -> "%.1fMB".format(java.util.Locale.US, b / (1024.0 * 1024))
    else -> "%.0fKB".format(java.util.Locale.US, b / 1024.0)
}

@Composable
fun ExportDialog(vm: EditorVM, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val p = vm.project
    var page by remember { mutableIntStateOf(0) }
    var opt by remember { mutableIntStateOf(0) }
    var vo by remember {
        val short = minOf(p.width, p.height)
        mutableStateOf(VideoOpts(shortSide = RESOLUTIONS.map { it.second }.minByOrNull { kotlin.math.abs(it - short) } ?: 1080))
    }
    var progress by remember { mutableStateOf<Float?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var asTemplate by remember { mutableStateOf(false) }
    val hevc = remember { runCatching { MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { it.isEncoder && it.supportedTypes.any { t -> t.equals("video/hevc", true) } } }.getOrDefault(false) }
    val free = remember { runCatching { StatFs(Environment.getDataDirectory().path).availableBytes }.getOrDefault(0L) }
    val resName = RESOLUTIONS.firstOrNull { it.second == vo.shortSide }?.first ?: "${vo.shortSide}p"
    val fpsOut = Exporter.outFps(p, vo)
    val est = when (opt) {
        0 -> Exporter.estimateBytes(p, vo)
        3 -> p.width.toLong() * p.height * 2
        4 -> p.width.toLong() * p.height * 2 * (p.durationMs.coerceAtLeast(1000) * p.fps / 1000)
        else -> 200_000L
    }

    fun run() {
        job = scope.launch {
            progress = 0f
            try {
                when (opt) {
                    0 -> {
                        val u = Exporter.exportVideo(ctx, vm.project, vo) { progress = it }
                        ctx.toast(if (u != null) "Video disimpan di Movies/ReLIFE Motion" else "Ekspor video gagal / dibatalkan")
                    }
                    1 -> ctx.toast("Unggah ke Cloud tidak tersedia (mode offline)")
                    2 -> {
                        val u = Exporter.exportPackage(ctx, vm.project)
                        ctx.toast(if (u != null) "Paket proyek disimpan di Download/ReLIFE Motion" else "Gagal membuat paket")
                    }
                    3 -> {
                        val u = Exporter.exportFrame(ctx, vm.project, vm.timeMs)
                        ctx.toast(if (u != null) "Frame disimpan di Pictures/ReLIFE Motion" else "Gagal menyimpan frame")
                    }
                    else -> {
                        val n = Exporter.exportSequence(ctx, vm.project) { progress = it }
                        ctx.toast("$n gambar disimpan di Pictures/ReLIFE Motion")
                    }
                }
            } finally {
                progress = null
            }
            onClose()
        }
    }

    Dialog(onDismissRequest = { if (progress == null) onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = cs.surface) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBtn(if (page == 1) Icons.Rounded.ArrowBack else Icons.Rounded.Close, { if (page == 1) page = 0 else onClose() })
                    Text(if (page == 1) "Video" else "Ekspor & Bagikan", Modifier.weight(1f), textAlign = if (page == 1) TextAlign.Center else TextAlign.Start, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    if (page == 1) Spacer(Modifier.width(48.dp))
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                    Row { Text("Ruang yang Tersedia", Modifier.weight(1f), fontSize = 13.sp); Text(human(free), fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    Spacer(Modifier.height(8.dp))
                    Row { Text("Ukuran file (perkiraan)", Modifier.weight(1f), fontSize = 13.sp); Text(human(est), fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    Spacer(Modifier.height(14.dp))
                    if (page == 0) {
                        ExportOption(opt == 0, Icons.Rounded.Movie, "Video", "MP4 $resName ${fpsOut}fps", true, { opt = 0 }, { page = 1 })
                        Box(Modifier.padding(vertical = 6.dp)) {
                            DropdownField(resName, RESOLUTIONS.map { it.first }, { vo = vo.copy(shortSide = RESOLUTIONS[it].second) }, Modifier.fillMaxWidth())
                        }
                        ExportOption(opt == 1, Icons.Rounded.CloudUpload, "Unggah ke Cloud", "Penyimpanan cloud (offline)", false, { opt = 1 }, {})
                        ExportOption(opt == 2, Icons.Rounded.Archive, "Paket Proyek", "Termasuk media (.relife)", true, { opt = 2 }, {})
                        ExportOption(opt == 3, Icons.Rounded.Image, "Frame Saat Ini sebagai PNG", "", false, { opt = 3 }, {})
                        ExportOption(opt == 4, Icons.Rounded.Collections, "Urutan Gambar", "PNG ${p.fps}fps", true, { opt = 4 }, {})
                    } else {
                        Text("Resolusi Ekspor", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        DropdownField(resName, RESOLUTIONS.map { it.first }, { vo = vo.copy(shortSide = RESOLUTIONS[it].second) }, Modifier.fillMaxWidth().padding(vertical = 6.dp))
                        Text("Codec", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                        Segmented(listOf("H.264/AVC", "H.265/HEVC"), if (vo.hevc) 1 else 0, { if (it == 0 || hevc) vo = vo.copy(hevc = it == 1) }, Modifier.padding(vertical = 6.dp))
                        if (!hevc) Text("Encoder HEVC tidak tersedia di perangkat ini.", fontSize = 10.sp, color = cs.onSurfaceVariant)
                        Row(Modifier.padding(top = 8.dp)) { Text("Frame Rate", Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold); Text("${fpsOut}fps", fontSize = 12.sp, color = cs.onSurfaceVariant) }
                        Segmented(listOf("RENDAH", "BERKURANG", "PENUH"), vo.fpsMode, { vo = vo.copy(fpsMode = it) }, Modifier.padding(vertical = 6.dp))
                        Row(Modifier.padding(top = 8.dp)) {
                            Text("Kualitas", Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("%.1fMbps".format(java.util.Locale.US, Exporter.bitrate(p, vo) / 1_000_000f), fontSize = 12.sp, color = cs.onSurfaceVariant)
                        }
                        Slider(vo.quality, { vo = vo.copy(quality = it) }, valueRange = 0f..1f)
                        Row { Text("Rendah", Modifier.weight(1f), fontSize = 10.sp, color = cs.onSurfaceVariant); Text("Tinggi", fontSize = 10.sp, color = cs.onSurfaceVariant) }
                        Text("Catatan: ekspor video menyimpan gambar saja (audio belum di-mix).", fontSize = 10.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
                    }
                }
                if (page == 0) Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Ekspor juga sebagai Template", Modifier.weight(1f), fontSize = 13.sp)
                    ReSwitch(asTemplate, { asTemplate = it })
                }
                Button(
                    onClick = { run() }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(52.dp),
                    shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = cs.inverseSurface, contentColor = cs.inverseOnSurface)
                ) { Text("Ekspor", fontWeight = FontWeight.Bold) }
            }
        }
    }

    progress?.let { pr ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Mengekspor…") },
            text = {
                Column {
                    LinearWavyProgressIndicator(progress = { pr }, modifier = Modifier.fillMaxWidth())
                    Text("${(pr * 100).toInt()}%", Modifier.padding(top = 10.dp), fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = { TextButton(onClick = { job?.cancel(); progress = null }) { Text("Batal") } }
        )
    }
}

@Composable
private fun ExportOption(sel: Boolean, icon: ImageVector, title: String, sub: String, chevron: Boolean, onSelect: () -> Unit, onChevron: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(cs.surfaceContainerLowest).clickable { onSelect() }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(if (sel) cs.primaryContainer else cs.surface).border(2.dp, if (sel) cs.primaryContainer else cs.outline, CircleShape))
        Spacer(Modifier.width(12.dp))
        Icon(icon, null, Modifier.size(24.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            if (sub.isNotEmpty()) Text(sub, fontSize = 10.sp, color = cs.onSurfaceVariant)
        }
        if (chevron) Icon(Icons.Rounded.ChevronRight, null, Modifier.clickable { onChevron() }, tint = cs.onSurfaceVariant)
    }
}
