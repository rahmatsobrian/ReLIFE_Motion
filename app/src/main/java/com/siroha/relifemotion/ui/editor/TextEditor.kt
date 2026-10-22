package com.siroha.relifemotion.ui.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.siroha.relifemotion.Graph
import com.siroha.relifemotion.data.Layer
import com.siroha.relifemotion.render.Fonts
import com.siroha.relifemotion.ui.common.DropdownField
import com.siroha.relifemotion.ui.common.IconBtn
import java.io.File

data class FontEntry(val name: String, val file: File?, val category: String, val imported: Boolean)

private fun categorize(n: String): String {
    val l = n.lowercase()
    return when {
        "mono" in l || "code" in l -> "Monospace"
        "script" in l || "hand" in l || "dancing" in l || "cursive" in l || "satisfy" in l || "pacifico" in l -> "Tulisan tangan"
        "black" in l || "display" in l || "condensed" in l || "rounded" in l -> "Tampilan"
        "serif" in l && "sans" !in l -> "Serif"
        "cutive" in l || "notoserif" in l.replace(" ", "") || "droidserif" in l.replace(" ", "") -> "Serif"
        else -> "Sans Serif"
    }
}

fun allFonts(): List<FontEntry> {
    val sys = Fonts.systemFiles().map { FontEntry(Fonts.displayName(it), it, categorize(it.name), false) }
    val dir = File(Graph.app.filesDir, "fonts")
    val imp = (dir.listFiles() ?: emptyArray()).filter { it.extension.lowercase() in setOf("ttf", "otf") }
        .map { FontEntry(Fonts.displayName(it), it, "Diimpor", true) }
    return (imp + sys).distinctBy { it.name }
}

@Composable
fun fontFamilyOf(f: File?): FontFamily = remember(f) { if (f == null) FontFamily.Default else runCatching { FontFamily(Font(f)) }.getOrDefault(FontFamily.Default) }

class TextTarget(val layerId: String?)

@Composable
fun TextEditorDialog(initial: Layer?, onDone: (String, String, String?, Float, Int) -> Unit, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var text by remember { mutableStateOf(initial?.text ?: "") }
    var font by remember { mutableStateOf(initial?.font ?: "Roboto Regular") }
    var fontPath by remember { mutableStateOf(initial?.fontPath) }
    var size by remember { mutableFloatStateOf(initial?.fontSize ?: 18f) }
    var color by remember { mutableIntStateOf(initial?.fill ?: android.graphics.Color.WHITE) }
    var showColor by remember { mutableStateOf(false) }
    var showFonts by remember { mutableStateOf(false) }
    val fonts = remember { allFonts() }
    val focus = remember { FocusRequester() }
    val current = fonts.firstOrNull { it.name == font }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xCC0E0F18)) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().height(50.dp).background(cs.surfaceContainerHigh), verticalAlignment = Alignment.CenterVertically) {
                    IconBtn(Icons.Rounded.Menu, { showFonts = true })
                    Row(Modifier.weight(1f).clickable { showFonts = true }, verticalAlignment = Alignment.CenterVertically) {
                        Text(font, color = cs.primary, fontSize = 14.sp, maxLines = 1, fontWeight = FontWeight.SemiBold)
                        Icon(Icons.Rounded.ArrowDropDown, null, tint = cs.primary)
                    }
                    val sizes = listOf(8, 10, 12, 14, 18, 24, 32, 48, 64, 96)
                    Box(Modifier.width(86.dp)) { DropdownField("${size.toInt()}pt", sizes.map { "${it}pt" }, { size = sizes[it].toFloat() }) }
                    Box(Modifier.padding(horizontal = 8.dp).size(28.dp).clip(RoundedCornerShape(4.dp)).background(Color(color)).border(1.dp, Color.White, RoundedCornerShape(4.dp)).clickable { showColor = !showColor })
                    IconBtn(Icons.Rounded.Check, { onDone(text.ifBlank { "Teks" }, font, fontPath, size, color) }, tint = cs.primary)
                }
                if (showColor) Box(Modifier.fillMaxWidth().background(cs.surfaceContainerHigh).padding(10.dp)) { ColorPicker(color, { color = it }) }
                Row(Modifier.fillMaxWidth().background(cs.surfaceContainerLow).horizontalScroll(rememberScrollState()).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    fonts.take(14).forEach { f ->
                        val sel = f.name == font
                        Column(
                            Modifier.width(72.dp).clip(RoundedCornerShape(8.dp)).background(if (sel) cs.surfaceContainerHighest else Color.Transparent)
                                .border(if (sel) 1.dp else 0.dp, cs.primary, RoundedCornerShape(8.dp))
                                .clickable { font = f.name; fontPath = f.file?.absolutePath }.padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Abc", fontFamily = fontFamilyOf(f.file), fontSize = 20.sp, color = cs.onSurface)
                            Text(f.name, fontSize = 8.sp, maxLines = 1, color = cs.onSurfaceVariant)
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text.ifEmpty { "Pratinjau" }, fontFamily = fontFamilyOf(current?.file ?: fontPath?.let { File(it) }),
                        fontSize = (size * 1.4f).sp, color = Color(color), textAlign = TextAlign.Center
                    )
                }
                BasicTextField(
                    text, { text = it }, Modifier.fillMaxWidth().background(cs.surfaceContainerHigh).padding(14.dp).focusRequester(focus),
                    textStyle = TextStyle(color = cs.onSurface, fontSize = 18.sp), cursorBrush = SolidColor(cs.primary), minLines = 2
                )
            }
        }
    }
    if (showFonts) FontListDialog({ n, p -> font = n; fontPath = p; showFonts = false }, { showFonts = false })
}

@Composable
fun FontListDialog(onPick: (String, String?) -> Unit, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    var fonts by remember { mutableStateOf(allFonts()) }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(false) }
    val cats = listOf("Serif", "Sans Serif", "Tampilan", "Tulisan tangan", "Monospace", "Favorit", "Diimpor")
    val enabled = remember { mutableStateListOf(*cats.toTypedArray()) }
    val favs = remember { mutableStateListOf<String>().also { it.addAll(ctx.getSharedPreferences("fonts", 0).getStringSet("fav", emptySet()) ?: emptySet()) } }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val dir = File(ctx.filesDir, "fonts").apply { mkdirs() }
                val name = (uri.lastPathSegment ?: "font.ttf").substringAfterLast('/').let { if (it.contains('.')) it else "$it.ttf" }
                ctx.contentResolver.openInputStream(uri)?.use { i -> File(dir, name).outputStream().use { o -> i.copyTo(o) } }
                fonts = allFonts()
            }
        }
    }
    val shown = fonts.filter { f ->
        (query.isBlank() || f.name.contains(query, true)) &&
            (f.category in enabled || (f.name in favs && "Favorit" in enabled))
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = cs.surface) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().height(50.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBtn(Icons.Rounded.Menu, { filter = true })
                    if (searching) BasicTextField(query, { query = it }, Modifier.weight(1f), singleLine = true, textStyle = TextStyle(color = cs.onSurface, fontSize = 16.sp), cursorBrush = SolidColor(cs.primary))
                    else Text("Semua Font", Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
                    IconBtn(if (searching) Icons.Rounded.Close else Icons.Rounded.Search, { searching = !searching; if (!searching) query = "" })
                }
                LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(1.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    items(shown, key = { it.name }) { f ->
                        Box(Modifier.fillMaxWidth().height(64.dp).background(cs.surfaceContainerLow).clickable { onPick(f.name, f.file?.absolutePath) }.padding(10.dp)) {
                            Column(Modifier.align(Alignment.CenterStart)) {
                                Text("Abcdefg", fontFamily = fontFamilyOf(f.file), fontSize = 22.sp)
                                Text(f.name, fontSize = 9.sp, color = cs.onSurfaceVariant, maxLines = 1)
                            }
                            Icon(
                                if (f.name in favs) Icons.Rounded.Star else Icons.Rounded.StarBorder, null,
                                Modifier.align(Alignment.TopEnd).size(18.dp).clickable {
                                    if (f.name in favs) favs.remove(f.name) else favs.add(f.name)
                                    ctx.getSharedPreferences("fonts", 0).edit().putStringSet("fav", favs.toSet()).apply()
                                }, tint = cs.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
    if (filter) AlertDialog(
        onDismissRequest = { filter = false },
        title = { Text("Filter Font") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                cats.forEach { c ->
                    Row(Modifier.fillMaxWidth().clickable { if (c in enabled) enabled.remove(c) else enabled.add(c) }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(c in enabled, { if (it) enabled.add(c) else enabled.remove(c) })
                        Text(c)
                    }
                }
                TextButton(onClick = { filter = false; importer.launch(arrayOf("*/*")) }) { Icon(Icons.Rounded.Download, null); Text("  Impor Font") }
            }
        },
        confirmButton = { TextButton(onClick = { filter = false }) { Text("OK") } }
    )
}
