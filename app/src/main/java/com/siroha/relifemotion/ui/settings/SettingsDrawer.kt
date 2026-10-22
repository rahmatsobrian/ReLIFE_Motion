package com.siroha.relifemotion.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.siroha.relifemotion.Graph
import com.siroha.relifemotion.data.AccessMode
import com.siroha.relifemotion.data.ThemeMode
import com.siroha.relifemotion.priv.Privileged
import com.siroha.relifemotion.ui.common.DropdownField
import com.siroha.relifemotion.ui.common.ReSwitch
import com.siroha.relifemotion.ui.common.Segmented
import com.siroha.relifemotion.ui.common.SwitchRow
import com.siroha.relifemotion.ui.home.toast
import kotlinx.coroutines.launch

@Composable
fun SettingsDrawer() {
    val cs = MaterialTheme.colorScheme
    val s = Graph.settings
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<String?>(null) }
    var log by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { Privileged.refreshShizuku(); if (Privileged.rootAvailable == null) Privileged.probeRoot() }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Text("Pengaturan", Modifier.padding(16.dp), fontSize = 20.sp, fontWeight = FontWeight.Bold)

        SCard {
            SwitchRow("Pratinjau Kualitas Rendah", s.lowPreview, { s.setLowPreview(it) })
            SwitchRow("Mode Demo", s.demoMode, { s.setDemo(it) })
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Durasi Layer Bawaan", Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                val opts = listOf(1, 2, 3, 5, 10)
                DropdownField("${s.defaultDurSec}s", opts.map { "${it}s" }, { s.setDefaultDur(opts[it]) }, Modifier.width(90.dp))
            }
            Column(Modifier.padding(16.dp)) {
                Text("Pengambilan Ulang Audio", fontSize = 12.sp, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Segmented(listOf("RENDAH", "TINGGI", "ULTRA"), s.audioQuality, { s.setAudioQuality(it) })
            }
        }

        Label("Tampilan")
        SCard {
            Column(Modifier.padding(16.dp)) {
                Text("Tema", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Segmented(listOf("Sistem", "Terang", "Gelap", "AMOLED"), s.themeMode.ordinal, { s.setThemeMode(ThemeMode.values()[it]) })
            }
            SwitchRow(
                "Warna Dinamis (Material You)", s.dynamicColor, { s.setDynamic(it) },
                subtitle = if (Build.VERSION.SDK_INT >= 31) "Mengikuti wallpaper" else "Butuh Android 12+"
            )
        }

        Label("Mode Akses")
        SCard {
            Column(Modifier.padding(16.dp)) {
                Segmented(listOf("Non-root", "Shizuku", "Root"), s.accessMode.ordinal, { s.setAccessMode(AccessMode.values()[it]) })
                Spacer(Modifier.height(12.dp))
                StatusRow("Non-root", true, "Selalu tersedia")
                StatusRow("Shizuku", Privileged.shizukuGranted, when {
                    Privileged.shizukuGranted -> "Aktif & diizinkan"
                    Privileged.shizukuRunning -> "Berjalan, izin belum diberikan"
                    else -> "Tidak berjalan"
                })
                StatusRow("Root", Privileged.rootAvailable == true, when (Privileged.rootAvailable) {
                    true -> "Tersedia"; false -> "Tidak tersedia"; null -> "Memeriksa…"
                })
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { Privileged.requestShizuku() }, Modifier.weight(1f)) { Text("Izin Shizuku", fontSize = 12.sp) }
                    OutlinedButton(onClick = { scope.launch { val ok = Privileged.probeRoot(); ctx.toast(if (ok) "Root tersedia" else "Root tidak tersedia") } }, Modifier.weight(1f)) { Text("Cek Root", fontSize = 12.sp) }
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    scope.launch {
                        val r = Privileged.grantMediaPermissions()
                        log = if (r.ok) "Izin media diberikan lewat mode ${s.accessMode.name.lowercase()}" else "Gagal (${r.code}): ${r.out.take(120)}"
                        ctx.toast(log)
                    }
                }, Modifier.fillMaxWidth()) { Text("Beri izin media otomatis") }
                if (log.isNotEmpty()) Text(log, fontSize = 11.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
        }

        Spacer(Modifier.height(12.dp))
        SCard {
            LinkRow("Tentang") { dialog = "about" }
            LinkRow("Pengaturan Privasi") { dialog = "privacy" }
            LinkRow("Perpustakaan Sumber Terbuka") { dialog = "oss" }
            LinkRow("Dapatkan Dukungan") { dialog = "support" }
        }

        Label("Ikuti kami")
        SCard {
            listOf("Twitter" to "https://x.com", "Instagram" to "https://instagram.com", "YouTube" to "https://youtube.com", "TikTok" to "https://tiktok.com").forEach { (n, u) ->
                Row(
                    Modifier.fillMaxWidth().clickable { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(26.dp).clip(CircleShape).background(cs.primaryContainer), contentAlignment = Alignment.Center) {
                        Text(n.take(1), fontWeight = FontWeight.Bold, color = cs.onPrimaryContainer, fontSize = 13.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(n, Modifier.weight(1f), fontSize = 14.sp)
                    Icon(Icons.Rounded.ChevronRight, null, tint = cs.onSurfaceVariant)
                }
            }
        }
    }

    dialog?.let { d ->
        val (t, body) = when (d) {
            "about" -> "ReLIFE Motion" to "Versi 1.0.0\nPackage com.siroha.relifemotion\nEditor video, foto, dan desain bergaya motion graphics.\nAndroid 9–17 • Non-root / Shizuku / Root."
            "privacy" -> "Privasi" to "Semua proyek disimpan lokal di perangkat. Aplikasi tidak mengirim data apa pun ke server."
            "oss" -> "Perpustakaan Sumber Terbuka" to "• Jetpack Compose & Material 3 (Apache-2.0)\n• AndroidX Navigation / Lifecycle / Activity\n• Kotlin Coroutines\n• Shizuku API (Apache-2.0)"
            else -> "Dukungan" to "Laporkan masalah lewat repositori GitHub proyek ini."
        }
        AlertDialog(onDismissRequest = { dialog = null }, title = { Text(t) }, text = { Text(body) },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("OK") } })
    }
}

@Composable
private fun SCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
        content = content
    )
}

@Composable
private fun Label(t: String) {
    Text(t, Modifier.padding(horizontal = 20.dp, vertical = 8.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun LinkRow(t: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(t, Modifier.weight(1f), fontSize = 14.sp)
        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatusRow(name: String, ok: Boolean, text: String) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (ok) cs.primary else cs.outline))
        Spacer(Modifier.width(10.dp))
        Text(name, Modifier.width(80.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(text, fontSize = 12.sp, color = cs.onSurfaceVariant)
    }
}
