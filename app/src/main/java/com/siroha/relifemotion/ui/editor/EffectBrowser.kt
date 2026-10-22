package com.siroha.relifemotion.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.siroha.relifemotion.data.EffectCatalog
import com.siroha.relifemotion.data.EffectDef
import com.siroha.relifemotion.ui.common.IconBtn

private val catColors = listOf(
    Color(0xFFE0A050), Color(0xFF8A70E0), Color(0xFF50A0E0), Color(0xFF50C0A0), Color(0xFFE06090), Color(0xFF8A90A8),
    Color(0xFF70B0E0), Color(0xFFA070E0), Color(0xFF60C080), Color(0xFFE08070), Color(0xFF9090C0), Color(0xFF606880)
)

@Composable
fun EffectBrowser(onPick: (EffectDef) -> Unit, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var cat by remember { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = cs.surfaceContainerLow) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBtn(if (cat != null || searching) Icons.Rounded.ArrowBack else Icons.Rounded.Close, {
                        if (searching) { searching = false; query = "" } else if (cat != null) cat = null else onClose()
                    })
                    if (searching) {
                        BasicTextField(
                            query, { query = it }, Modifier.weight(1f).padding(horizontal = 8.dp), singleLine = true,
                            textStyle = TextStyle(color = cs.onSurface, fontSize = 16.sp), cursorBrush = SolidColor(cs.primary)
                        )
                    } else {
                        Text(cat ?: "Browser Efek", Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        IconBtn(Icons.Rounded.Search, { searching = true })
                    }
                }
                when {
                    searching -> EffectGrid(EffectCatalog.search(query), onPick)
                    cat != null -> EffectGrid(EffectCatalog.inCategory(cat!!), onPick)
                    else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        Text("Populer", Modifier.padding(horizontal = 16.dp, vertical = 6.dp), fontSize = 12.sp, color = cs.onSurfaceVariant)
                        EffectCatalog.popular.chunked(4).forEach { row ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                                row.forEach { d -> Box(Modifier.weight(1f)) { EffectTile(d) { onPick(d) } } }
                                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        EffectCatalog.categories.chunked(2).forEachIndexed { r, pair ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)) {
                                pair.forEachIndexed { i, c ->
                                    val col = catColors[(r * 2 + i) % catColors.size]
                                    Box(
                                        Modifier.weight(1f).padding(2.dp).height(78.dp).clip(RoundedCornerShape(4.dp))
                                            .background(Brush.linearGradient(listOf(col.copy(alpha = 0.55f), cs.surfaceContainerHigh)))
                                            .clickable { cat = c },
                                        contentAlignment = Alignment.Center
                                    ) { Text(c, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center) }
                                }
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun EffectGrid(list: List<EffectDef>, onPick: (EffectDef) -> Unit) {
    LazyVerticalGrid(GridCells.Fixed(3), Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(list, key = { it.id }) { d -> EffectTile(d) { onPick(d) } }
    }
}

@Composable
fun EffectTile(d: EffectDef, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.clickable { onClick() }.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(Color.White)) {
            Canvas(Modifier.fillMaxSize().padding(14.dp)) {
                val seed = (d.id.hashCode() and 0x7FFFFFFF)
                val c1 = Color.hsv((seed % 360).toFloat(), 0.55f, 0.95f)
                val c2 = Color.hsv(((seed / 7) % 360).toFloat(), 0.6f, 0.9f)
                drawCircle(Brush.linearGradient(listOf(c1, c2)), size.minDimension / 2f, center)
                drawCircle(Color.White.copy(alpha = 0.35f), size.minDimension / 5f, Offset(center.x - size.width / 8f, center.y - size.height / 8f))
            }
            if (d.implemented) Box(
                Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp).clip(CircleShape).background(cs.primaryContainer),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.Check, null, Modifier.size(12.dp), tint = cs.onPrimaryContainer) }
        }
        Text(d.nameId, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis, lineHeight = 12.sp)
        Text(d.nameEn, fontSize = 8.sp, color = cs.onSurfaceVariant, maxLines = 1, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
    }
}
