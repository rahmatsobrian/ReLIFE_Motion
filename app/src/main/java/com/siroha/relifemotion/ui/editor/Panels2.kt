package com.siroha.relifemotion.ui.editor

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.siroha.relifemotion.data.EffectCatalog
import com.siroha.relifemotion.data.EffectInst
import com.siroha.relifemotion.data.Layer
import com.siroha.relifemotion.data.LayerType
import com.siroha.relifemotion.data.ShadowSpec
import com.siroha.relifemotion.data.StrokeSpec
import com.siroha.relifemotion.data.valueAt
import com.siroha.relifemotion.render.BlendModes
import com.siroha.relifemotion.ui.common.Diamond
import com.siroha.relifemotion.ui.common.IconBtn
import com.siroha.relifemotion.ui.common.ReSwitch
import com.siroha.relifemotion.ui.common.RulerSlider
import com.siroha.relifemotion.ui.common.Segmented
import com.siroha.relifemotion.ui.common.ValueBox
import kotlin.math.roundToInt

@Composable
private fun SubPanel(
    onBack: () -> Unit, modifier: Modifier = Modifier,
    leftExtra: @Composable ColumnScope.() -> Unit = {},
    rightRail: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(modifier.fillMaxSize()) {
        Column(Modifier.width(48.dp).fillMaxHeight().padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            RailBtn(Icons.Rounded.ArrowBack, onBack)
            leftExtra()
        }
        Box(Modifier.weight(1f).fillMaxHeight().padding(vertical = 6.dp).clip(RoundedCornerShape(10.dp)).background(cs.surfaceContainerHigh), content = content)
        if (rightRail != null) Column(Modifier.width(48.dp).fillMaxHeight().padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, content = rightRail)
        else Spacer(Modifier.width(8.dp))
    }
}

@Composable
private fun SwatchRow(label: String, color: Int, onClick: () -> Unit, trailing: String = "") {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(64.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = cs.primary)
        Text(trailing, Modifier.weight(1f), fontSize = 10.sp, color = cs.onSurfaceVariant, textAlign = TextAlign.End)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(28.dp).clip(RoundedCornerShape(5.dp)).background(Color(color)).border(1.dp, cs.outline, RoundedCornerShape(5.dp)).clickable { onClick() })
    }
}

@Composable
private fun RulerRow(label: String, value: Float, text: String, onChange: (Float) -> Unit, sens: Float, min: Float, max: Float) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(64.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = cs.primary)
        RulerSlider(value, onChange, Modifier.weight(1f), sens, min, max)
        ValueBox(text, "", Modifier.width(54.dp), accent = false)
    }
}

// ------------------------------------------------------------------ Border & Bayangan

@Composable
fun BorderPanel(vm: EditorVM, l: Layer, onPage: (Page) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var tab by remember { mutableIntStateOf(1) }
    var pickIdx by remember { mutableIntStateOf(-1) }   // -1 none, 0..n border, 100 shadow
    SubPanel(
        onBack = { onPage(Page.MAIN) },
        rightRail = {
            RailBtn(Icons.Rounded.Gesture, { tab = 0 }, tab == 0)
            RailBtn(Icons.Rounded.CropSquare, { tab = 1 }, tab == 1)
            RailBtn(Icons.Rounded.FilterNone, { tab = 2 }, tab == 2)
        }
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp)) {
            when (tab) {
                0 -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Stroke", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        ReSwitch(l.strokeOn, { v -> vm.updateLayer(l.id) { it.copy(strokeOn = v) } })
                    }
                    RulerRow("Lebar", l.strokeW, f1(l.strokeW), { v -> vm.updateLayer(l.id, "sw") { it.copy(strokeW = v) } }, 0.1f, 0.5f, 200f)
                    Text("Ujung", fontSize = 11.sp, color = cs.onSurfaceVariant)
                    Segmented(listOf("Rata", "Bulat", "Persegi"), l.cap, { v -> vm.updateLayer(l.id) { it.copy(cap = v) } })
                    Spacer(Modifier.height(6.dp))
                    Text("Sambungan", fontSize = 11.sp, color = cs.onSurfaceVariant)
                    Segmented(listOf("Runcing", "Bulat", "Miring"), l.join, { v -> vm.updateLayer(l.id) { it.copy(join = v) } })
                }
                1 -> {
                    OutlinedButton(onClick = {
                        vm.updateLayer(l.id) { it.copy(borders = it.borders + StrokeSpec(0, -1, 6f)) }
                    }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Add, null); Text(" Tambahkan Border") }
                    l.borders.forEachIndexed { i, b ->
                        Column(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(10.dp)).background(cs.surfaceContainerHighest).padding(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Border", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                IconBtn(Icons.Rounded.Delete, { vm.updateLayer(l.id) { it.copy(borders = it.borders.filterIndexed { j, _ -> j != i }) } }, box = 32.dp, size = 18.dp)
                            }
                            Segmented(listOf("DI DALAM", "TENGAH", "DI LUAR"), b.pos, { v ->
                                vm.updateLayer(l.id) { it.copy(borders = it.borders.mapIndexed { j, x -> if (j == i) x.copy(pos = v) else x }) }
                            }, height = 32.dp)
                            SwatchRow("Warna", b.color, { pickIdx = if (pickIdx == i) -1 else i }, "%d %d %d".format(android.graphics.Color.red(b.color), android.graphics.Color.green(b.color), android.graphics.Color.blue(b.color)))
                            if (pickIdx == i) ColorPicker(b.color, { c -> vm.updateLayer(l.id, "bc$i") { it.copy(borders = it.borders.mapIndexed { j, x -> if (j == i) x.copy(color = c) else x }) } })
                            RulerRow("Ukuran", b.width, b.width.roundToInt().toString(), { v ->
                                vm.updateLayer(l.id, "bw$i") { it.copy(borders = it.borders.mapIndexed { j, x -> if (j == i) x.copy(width = v) else x }) }
                            }, 0.1f, 0.5f, 200f)
                        }
                    }
                }
                else -> {
                    val sh = l.shadow
                    if (sh == null) {
                        OutlinedButton(onClick = { vm.updateLayer(l.id) { it.copy(shadow = ShadowSpec()) } }, Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.Add, null); Text(" Tambahkan Bayangan")
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Bayangan", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            IconBtn(Icons.Rounded.Delete, { vm.updateLayer(l.id) { it.copy(shadow = null) } }, box = 32.dp, size = 18.dp)
                        }
                        SwatchRow("Warna", sh.color, { pickIdx = if (pickIdx == 100) -1 else 100 })
                        if (pickIdx == 100) ColorPicker(sh.color, { c -> vm.updateLayer(l.id, "shc") { it.copy(shadow = sh.copy(color = c)) } })
                        RulerRow("Ukuran", sh.size, sh.size.roundToInt().toString(), { v -> vm.updateLayer(l.id, "shs") { it.copy(shadow = sh.copy(size = v)) } }, 0.1f, 0f, 120f)
                        RulerRow("Alpha", sh.alpha, "${(sh.alpha * 100).roundToInt()}%", { v -> vm.updateLayer(l.id, "sha") { it.copy(shadow = sh.copy(alpha = v)) } }, 0.005f, 0f, 1f)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Blending & Opacity

@Composable
fun BlendPanel(vm: EditorVM, l: Layer, onPage: (Page) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val t = vm.timeMs
    val op = l.valueAt("op", t)
    var open by remember { mutableStateOf<String?>(null) }
    val hasKey = vm.hasKeyAtPlayhead(l, listOf("op"))
    SubPanel(
        onBack = { onPage(Page.MAIN) },
        leftExtra = {
            Box(Modifier.size(42.dp).clip(CircleShape).clickable { vm.toggleKey(l.id, listOf("op")) }, contentAlignment = Alignment.Center) {
                Diamond(hasKey, if (hasKey) cs.primary else cs.onSurface, 20.dp)
            }
        }
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(op, { vm.setProp(l.id, "op", it) }, Modifier.weight(1f), valueRange = 0f..1f)
                Text("${(op * 100).roundToInt()}%", Modifier.width(48.dp), textAlign = TextAlign.End, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Row(Modifier.fillMaxWidth().clickable { vm.updateLayer(l.id) { it.copy(blend = "Normal") } }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Normal", Modifier.weight(1f), fontSize = 13.sp)
                if (l.blend == "Normal") Icon(Icons.Rounded.Check, null, tint = cs.primary)
            }
            BlendModes.groups.forEach { (g, modes) ->
                Row(Modifier.fillMaxWidth().clickable { open = if (open == g) null else g }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (open == g) Icons.Rounded.ExpandMore else Icons.Rounded.ChevronRight, null, Modifier.size(18.dp))
                    Text(g, Modifier.weight(1f).padding(start = 6.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                if (open == g) Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    modes.take(4).forEach { m -> BlendTile(m, l.blend == m, Modifier.weight(1f)) { vm.updateLayer(l.id) { it.copy(blend = m) } } }
                }
                if (open == g && modes.size > 4) Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    modes.drop(4).forEach { m -> BlendTile(m, l.blend == m, Modifier.weight(1f)) { vm.updateLayer(l.id) { it.copy(blend = m) } } }
                    repeat(4 - (modes.size - 4)) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun BlendTile(name: String, sel: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(modifier.clickable { onClick() }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(8.dp))
                .background(Brush.linearGradient(listOf(Color(0xFFE05C8A), Color(0xFF5C8AE0), Color(0xFF5CE0A8))))
                .border(if (sel) 2.dp else 0.dp, cs.primary, RoundedCornerShape(8.dp))
        )
        Text(name, fontSize = 9.sp, color = if (sel) cs.primary else cs.onSurfaceVariant, maxLines = 1, textAlign = TextAlign.Center)
    }
}

// ------------------------------------------------------------------ Color & Fill

@Composable
fun FillPanel(vm: EditorVM, l: Layer, onPage: (Page) -> Unit) {
    val cs = MaterialTheme.colorScheme
    SubPanel(onBack = { onPage(Page.MAIN) }) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp)) {
            if (l.type == LayerType.IMAGE || l.type == LayerType.VIDEO || l.type == LayerType.AUDIO) {
                Text("Layer media tidak memiliki warna isi. Gunakan Efek ▸ Warna & Cahaya.", fontSize = 12.sp, color = cs.onSurfaceVariant)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (l.type == LayerType.TEXT) "Warna Teks" else "Isi", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    ReSwitch(l.fillOn, { v -> vm.updateLayer(l.id) { it.copy(fillOn = v) } })
                }
                Spacer(Modifier.height(6.dp))
                ColorPicker(l.fill, { c -> vm.updateLayer(l.id, "fill") { x -> x.copy(fill = c) } })
            }
        }
    }
}

// ------------------------------------------------------------------ Edit Bentuk

@Composable
fun ShapePanel(vm: EditorVM, l: Layer, onPage: (Page) -> Unit) {
    var link by remember { mutableStateOf(true) }
    val cs = MaterialTheme.colorScheme
    SubPanel(onBack = { onPage(Page.MAIN) }) {
        Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ukuran", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Box(Modifier.size(28.dp).clip(CircleShape).background(if (link) cs.outline else cs.surfaceContainerHighest).clickable { link = !link }, contentAlignment = Alignment.Center) {
                    Icon(if (link) Icons.Rounded.Link else Icons.Rounded.LinkOff, null, Modifier.size(16.dp), tint = Color.White)
                }
            }
            RulerRow("Lebar", l.w, l.w.roundToInt().toString(), { v ->
                vm.updateLayer(l.id, "w") { x -> if (link && x.w > 0f) x.copy(w = v, h = x.h * v / x.w) else x.copy(w = v) }
            }, 1f, 4f, 8000f)
            RulerRow("Tinggi", l.h, l.h.roundToInt().toString(), { v ->
                vm.updateLayer(l.id, "h") { x -> if (link && x.h > 0f) x.copy(h = v, w = x.w * v / x.h) else x.copy(h = v) }
            }, 1f, 4f, 8000f)
        }
    }
}

// ------------------------------------------------------------------ Presets

@Composable
fun PresetsPanel(vm: EditorVM, l: Layer, onPage: (Page) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val presets: List<Pair<String, (Layer) -> Layer>> = listOf(
        "Neon" to { x -> x.copy(fill = 0xFF1DE9B6.toInt(), shadow = ShadowSpec(0xFF1DE9B6.toInt(), 14f, 1f)) },
        "Bayangan Lembut" to { x -> x.copy(shadow = ShadowSpec(0xFF000000.toInt(), 10f, 0.6f)) },
        "Garis Putih" to { x -> x.copy(borders = listOf(StrokeSpec(1, -1, 6f))) },
        "Kaca" to { x -> x.copy(opacity = 0.6f, borders = listOf(StrokeSpec(0, -1, 3f))) },
        "Blok Gelap" to { x -> x.copy(fill = 0xFF1B1E2B.toInt(), borders = listOf(StrokeSpec(2, 0xFF1DE9B6.toInt(), 5f))) },
        "Reset Gaya" to { x -> x.copy(borders = emptyList(), shadow = null, opacity = 1f, blend = "Normal", effects = emptyList()) }
    )
    SubPanel(onBack = { onPage(Page.MAIN) }) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (n, f) ->
                        Box(
                            Modifier.weight(1f).height(60.dp).clip(RoundedCornerShape(10.dp)).background(cs.surfaceContainerHighest).clickable { vm.updateLayer(l.id) { x -> f(x) } },
                            contentAlignment = Alignment.Center
                        ) { Text(n, fontSize = 11.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Efek

@Composable
fun EffectsPanel(vm: EditorVM, l: Layer, onPage: (Page) -> Unit, onAdd: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    SubPanel(onBack = { onPage(Page.MAIN) }) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp)) {
            OutlinedButton(onClick = onAdd, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Add, null); Text(" Tambahkan Efek") }
            l.effects.forEach { e ->
                val def = EffectCatalog.get(e.id)
                Column(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(10.dp)).background(cs.surfaceContainerHighest).padding(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(def?.nameId ?: e.id, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(def?.nameEn ?: "", fontSize = 10.sp, color = cs.onSurfaceVariant)
                        }
                        ReSwitch(e.enabled, { v -> vm.updateLayer(l.id) { x -> x.copy(effects = x.effects.map { if (it.uid == e.uid) it.copy(enabled = v) else it }) } })
                        IconBtn(Icons.Rounded.Delete, { vm.updateLayer(l.id) { x -> x.copy(effects = x.effects.filterNot { it.uid == e.uid }) } }, box = 36.dp, size = 18.dp)
                    }
                    if (def != null && !def.implemented) {
                        Text("Efek ini belum dirender oleh mesin ReLIFE Motion (ditandai untuk rilis berikutnya).", fontSize = 10.sp, color = cs.onSurfaceVariant)
                    }
                    def?.params?.forEach { pd ->
                        val v = e.params[pd.key] ?: pd.def
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(pd.label, Modifier.width(78.dp), fontSize = 11.sp, color = cs.onSurfaceVariant)
                            Slider(v, { nv ->
                                vm.updateLayer(l.id, "fx${e.uid}${pd.key}") { x ->
                                    x.copy(effects = x.effects.map { if (it.uid == e.uid) it.copy(params = it.params + (pd.key to nv)) else it })
                                }
                            }, Modifier.weight(1f).height(30.dp), valueRange = pd.min..pd.max)
                            Text(f1(v), Modifier.width(40.dp), fontSize = 11.sp, textAlign = TextAlign.End)
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Pemetaan Waktu

@Composable
fun TimePanel(vm: EditorVM, l: Layer, onPage: (Page) -> Unit) {
    val cs = MaterialTheme.colorScheme
    SubPanel(onBack = { onPage(Page.MAIN) }) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.Center) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf(0.5f, 1f, 2f).forEach { s ->
                    Text(
                        "${s}x", Modifier.clip(RoundedCornerShape(8.dp)).background(if (l.speed == s) cs.inverseSurface else cs.surfaceContainerHighest)
                            .clickable { vm.updateLayer(l.id) { it.copy(speed = s) } }.padding(horizontal = 18.dp, vertical = 8.dp),
                        color = if (l.speed == s) cs.inverseOnSurface else cs.onSurface, fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("%.2fx".format(java.util.Locale.US, l.speed), Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
            Slider(l.speed, { v -> vm.updateLayer(l.id, "spd") { it.copy(speed = (v * 20).roundToInt() / 20f) } }, valueRange = 0.1f..4f)
            Text("Kecepatan memengaruhi layer video dan audio.", fontSize = 10.sp, color = cs.onSurfaceVariant)
        }
    }
}
