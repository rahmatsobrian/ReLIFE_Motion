package com.siroha.relifemotion.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.siroha.relifemotion.Graph
import com.siroha.relifemotion.ui.common.IconBtn

@Composable
fun AccountScreen(onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val s = Graph.settings
    var editName by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(cs.surface).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Icons.Rounded.Close, onClose)
            Text("Akun Saya", Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Box {
                IconBtn(Icons.Rounded.MoreVert, { menu = true })
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Ubah nama") }, onClick = { menu = false; editName = true })
                }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().clickable { editName = true }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(72.dp).clip(CircleShape).background(Color(0xFF8D6E63)), contentAlignment = Alignment.Center) {
                    Text(s.userName.take(1).uppercase().ifEmpty { "M" }, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.userName, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("Akun lokal • tanpa login", fontSize = 12.sp, color = cs.onSurfaceVariant)
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = cs.onSurfaceVariant)
            }
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 0.dp).background(cs.surfaceContainerHigh).clickable { info = true }.padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) { Text("INFO LISENSI", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
            Spacer(Modifier.height(20.dp))
            Box(Modifier.padding(horizontal = 16.dp)) {
                Column(
                    Modifier.fillMaxWidth().padding(top = 12.dp).border(2.dp, cs.onSurface, RoundedCornerShape(16.dp)).padding(16.dp).padding(top = 8.dp)
                ) {
                    Text("Manfaat:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    listOf(
                        "Tanpa Tanda Air", "Semua Paket Efek Terbuka", "Ekspor & Impor Paket Proyek", "Easing Tingkat Lanjut",
                        "Induk Layer & Objek Kamera", "Mode Akses Shizuku / Root", "Penyimpanan lokal tanpa batas langganan"
                    ).forEach { Text("•  $it", fontSize = 13.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp)) }
                    Spacer(Modifier.height(10.dp))
                    Text("ReLIFE Motion bebas langganan. Seluruh fitur tersedia secara lokal di perangkat.", fontSize = 9.sp, color = cs.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Text("KELOLA LISENSI", Modifier.align(Alignment.End).clickable { info = true }, color = cs.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Text(
                    "Keanggotaan ReLIFE Penuh", Modifier.padding(start = 14.dp).background(cs.surface).padding(horizontal = 6.dp),
                    fontWeight = FontWeight.Bold, fontSize = 14.sp
                )
            }
            Spacer(Modifier.height(24.dp))
            Text("Offline: Sambungkan ke Internet untuk mendapatkan informasi terbaru", Modifier.padding(horizontal = 16.dp), fontSize = 10.sp, color = cs.onSurfaceVariant)
        }
    }
    if (editName) {
        var v by remember { mutableStateOf(s.userName) }
        AlertDialog(
            onDismissRequest = { editName = false }, title = { Text("Ubah nama") },
            text = { OutlinedTextField(v, { v = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { if (v.isNotBlank()) s.applyUserName(v.trim()); editName = false }) { Text("Simpan") } },
            dismissButton = { TextButton(onClick = { editName = false }) { Text("Batal") } }
        )
    }
    if (info) {
        AlertDialog(
            onDismissRequest = { info = false }, title = { Text("Lisensi") },
            text = { Text("ReLIFE Motion v1.0.0\nPackage: com.siroha.relifemotion\nSemua fitur terbuka, tanpa langganan.") },
            confirmButton = { TextButton(onClick = { info = false }) { Text("OK") } }
        )
    }
}
