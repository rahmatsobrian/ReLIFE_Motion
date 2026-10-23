package com.siroha.relifemotion.ui.editor

import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.siroha.relifemotion.Graph
import com.siroha.relifemotion.data.*
import com.siroha.relifemotion.render.MediaCache
import com.siroha.relifemotion.render.Renderer
import com.siroha.relifemotion.render.ShapePaths
import com.siroha.relifemotion.render.VideoFrames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class EditorVM(val projectId: String) : ViewModel() {
    var project by mutableStateOf(Graph.repo.load(projectId) ?: Project(id = projectId))
        private set
    var timeMs by mutableLongStateOf(0L)
    var playing by mutableStateOf(false)
    var loop by mutableStateOf(false)
    var selected by mutableStateOf<List<String>>(emptyList())
    var multiMode by mutableStateOf(false)
    var redrawTick by mutableIntStateOf(0)
    var canUndo by mutableStateOf(false); private set
    var canRedo by mutableStateOf(false); private set
    var clipboard by mutableStateOf<List<Layer>>(emptyList()); private set
    private var styleClip: Layer? = null
    private var easeClip: Ease? = null

    private val undoStack = ArrayDeque<Project>()
    private val redoStack = ArrayDeque<Project>()
    private var lastTag = ""
    private var lastTagTime = 0L
    private var saveJob: Job? = null

    val sel: Layer? get() = project.layers.firstOrNull { it.id == selected.firstOrNull() }
    val selLayers: List<Layer> get() = project.layers.filter { it.id in selected }
    val totalMs: Long get() = max(project.durationMs, 1000L)
    private val tol: Long get() = 1000L / max(project.fps, 1) / 2 + 1

    // ------------------------------------------------------------ edit core

    fun edit(tag: String = "", f: (Project) -> Project) {
        val now = System.currentTimeMillis()
        if (tag.isEmpty() || tag != lastTag || now - lastTagTime > 900) {
            undoStack.addLast(project)
            if (undoStack.size > 60) undoStack.removeFirst()
            redoStack.clear()
        }
        lastTag = tag; lastTagTime = now
        project = f(project)
        canUndo = undoStack.isNotEmpty(); canRedo = redoStack.isNotEmpty()
        scheduleSave()
    }

    fun undo() {
        val p = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(project); project = p; lastTag = ""
        canUndo = undoStack.isNotEmpty(); canRedo = redoStack.isNotEmpty(); scheduleSave()
    }

    fun redo() {
        val p = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(project); project = p; lastTag = ""
        canUndo = undoStack.isNotEmpty(); canRedo = redoStack.isNotEmpty(); scheduleSave()
    }

    fun renameProject(n: String) = edit("name") { it.copy(name = n) }

    fun updateLayer(id: String, tag: String = "", f: (Layer) -> Layer) =
        edit(tag) { p -> p.copy(layers = p.layers.map { if (it.id == id) f(it) else it }) }

    fun updateSelected(tag: String = "", f: (Layer) -> Layer) {
        val ids = selected.toSet()
        edit(tag) { p -> p.copy(layers = p.layers.map { if (it.id in ids) f(it) else it }) }
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(700)
            val p = project
            withContext(Dispatchers.IO) { Graph.repo.save(p) }
        }
    }

    override fun onCleared() {
        stopAudio()
        runCatching { Graph.repo.save(project) }
        super.onCleared()
    }

    // ------------------------------------------------------------ properties + keyframes

    fun setProp(id: String, prop: String, v: Float, tag: String = prop) {
        updateLayer(id, tag) { l ->
            if (l.hasKeys(prop)) upsertKey(l, prop, v) else l.withBase(prop, v)
        }
    }

    private fun upsertKey(l: Layer, prop: String, v: Float): Layer {
        val ex = l.keys.firstOrNull { it.prop == prop && abs(it.t - timeMs) <= tol }
        val nk = Key(prop, timeMs, v, ex?.ease ?: Ease())
        return l.copy(keys = l.keys.filterNot { it.prop == prop && abs(it.t - timeMs) <= tol } + nk)
    }

    fun hasKeyAtPlayhead(l: Layer, props: List<String>): Boolean =
        l.keys.any { it.prop in props && abs(it.t - timeMs) <= tol }

    fun toggleKey(id: String, props: List<String>) {
        updateLayer(id) { l ->
            if (hasKeyAtPlayhead(l, props)) {
                l.copy(keys = l.keys.filterNot { it.prop in props && abs(it.t - timeMs) <= tol })
            } else {
                var out = l
                props.forEach { pr -> out = upsertKey(out, pr, l.valueAt(pr, timeMs)) }
                out
            }
        }
    }

    fun easeAtPlayhead(l: Layer, props: List<String>): Ease? =
        l.keys.firstOrNull { it.prop in props && abs(it.t - timeMs) <= tol }?.ease

    fun setEase(id: String, props: List<String>, e: Ease) {
        updateLayer(id, "ease") { l ->
            l.copy(keys = l.keys.map { if (it.prop in props && abs(it.t - timeMs) <= tol) it.copy(ease = e) else it })
        }
    }

    fun copyEase(e: Ease) { easeClip = e }
    fun pasteEase(): Ease? = easeClip

    fun applyEaseToAllKeys(id: String, props: List<String>, e: Ease) {
        updateLayer(id) { l -> l.copy(keys = l.keys.map { if (it.prop in props) it.copy(ease = e) else it }) }
    }

    // ------------------------------------------------------------ selection

    fun select(id: String, additive: Boolean = false) {
        if (additive) multiMode = true
        selected = if (additive) (if (id in selected) selected - id else selected + id) else listOf(id)
        if (additive && selected.isEmpty()) multiMode = false
    }
    /** Tahan lama pada pil mata: masuk mode pilih dan tambahkan layer ke seleksi. */
    fun beginMulti(id: String) { multiMode = true; if (id !in selected) selected = selected + id }
    fun clearSelection() { selected = emptyList(); multiMode = false }
    fun selectAll() { multiMode = true; selected = project.layers.map { it.id } }

    // ------------------------------------------------------------ layer creation

    private fun uniqueName(base: String): String {
        val n = project.layers.count { it.name.startsWith(base) } + 1
        return "$base $n"
    }

    private fun unit(): Float = min(project.width, project.height) / 1080f

    private fun defaultEnd(start: Long) = start + Graph.settings.defaultDurSec * 1000L

    fun addLayer(l: Layer) {
        edit { it.copy(layers = listOf(l) + it.layers) }
        selected = listOf(l.id)
    }

    fun addShape(shape: String) {
        val u = unit()
        val base = ShapePaths.nameOf(shape)
        val line = shape == "line"
        addLayer(Layer(
            type = LayerType.SHAPE, name = uniqueName(base), shape = shape,
            startMs = timeMs, endMs = defaultEnd(timeMs),
            w = (if (line) 500f else 300f) * u, h = 300f * u,
            x = project.width / 2f, y = project.height / 2f,
            fill = if (shape == "rect") 0xFFD9EE8B.toInt() else 0xFF3F9BE8.toInt()
        ))
    }

    fun addText(text: String, font: String, fontPath: String?, size: Float, color: Int) {
        val tmp = Layer(type = LayerType.TEXT, name = uniqueName("Teks"), text = text, font = font, fontPath = fontPath,
            fontSize = size, fill = color, startMs = timeMs, endMs = defaultEnd(timeMs),
            x = project.width / 2f, y = project.height / 2f)
        val lay = Renderer.textLayout(tmp)
        addLayer(tmp.copy(w = lay.width.toFloat(), h = lay.height.toFloat()))
    }

    fun updateText(id: String, text: String, font: String, fontPath: String?, size: Float, color: Int) {
        updateLayer(id, "text") { l ->
            val t = l.copy(text = text, font = font, fontPath = fontPath, fontSize = size, fill = color)
            val lay = Renderer.textLayout(t)
            t.copy(w = lay.width.toFloat(), h = lay.height.toFloat(), name = if (text.isNotBlank()) text.take(24) else l.name)
        }
    }

    fun addMedia(uris: List<Uri>) {
        viewModelScope.launch {
            for (u in uris) {
                val mime = Graph.app.contentResolver.getType(u) ?: ""
                val isVideo = mime.startsWith("video")
                val isAudio = mime.startsWith("audio")
                val layer: Layer? = withContext(Dispatchers.IO) {
                    val name = u.lastPathSegment?.substringAfterLast('/')?.take(28) ?: "Media"
                    when {
                        isAudio -> {
                            val d = VideoFrames.durationMs(u)
                            Layer(type = LayerType.AUDIO, name = name, uri = u.toString(), startMs = 0, endMs = d.coerceAtLeast(1000L),
                                w = 100f, h = 100f, mediaDurationMs = d)
                        }
                        isVideo -> {
                            val sz = VideoFrames.size(u) ?: (1080 to 1920)
                            val d = VideoFrames.durationMs(u).coerceAtLeast(500L)
                            val f = min(project.width * 0.9f / sz.first, project.height * 0.9f / sz.second)
                            Layer(type = LayerType.VIDEO, name = name, uri = u.toString(), startMs = 0, endMs = d,
                                w = sz.first * f, h = sz.second * f, mediaDurationMs = d,
                                x = project.width / 2f, y = project.height / 2f)
                        }
                        else -> {
                            MediaCache.image(u.toString())
                            val b = MediaCache.image(u.toString())
                            if (b == null) null else {
                                val f = min(project.width * 0.8f / b.width, project.height * 0.8f / b.height)
                                Layer(type = LayerType.IMAGE, name = name, uri = u.toString(), startMs = 0, endMs = 3000,
                                    w = b.width * f, h = b.height * f, x = project.width / 2f, y = project.height / 2f)
                            }
                        }
                    }
                }
                if (layer != null) {
                    val start = timeMs
                    val dur = layer.endMs - layer.startMs
                    addLayer(layer.copy(startMs = start, endMs = start + if (layer.type == LayerType.IMAGE) Graph.settings.defaultDurSec * 1000L else dur))
                }
            }
        }
    }

    fun addAudio(uri: Uri, name: String, dur: Long) {
        addLayer(Layer(type = LayerType.AUDIO, name = name, uri = uri.toString(), startMs = timeMs, endMs = timeMs + dur.coerceAtLeast(1000L), w = 100f, h = 100f, mediaDurationMs = dur))
    }

    fun addSpecial(type: LayerType, name: String) {
        addLayer(Layer(type = type, name = uniqueName(name), startMs = timeMs, endMs = defaultEnd(timeMs), w = 200f, h = 200f,
            x = project.width / 2f, y = project.height / 2f))
    }

    fun addDrawing(points: List<Float>, color: Int, brush: Float, closed: Boolean, vector: Boolean) {
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        var i = 0
        while (i + 1 < points.size) {
            if (points[i] < STROKE_BREAK) {
                minX = min(minX, points[i]); maxX = max(maxX, points[i]); minY = min(minY, points[i + 1]); maxY = max(maxY, points[i + 1])
            }
            i += 2
        }
        if (minX == Float.MAX_VALUE) return
        val cx = (minX + maxX) / 2f; val cy = (minY + maxY) / 2f
        val rel = ArrayList<Float>(points.size)
        i = 0
        while (i + 1 < points.size) {
            if (points[i] >= STROKE_BREAK) { rel.add(STROKE_BREAK); rel.add(STROKE_BREAK) }
            else { rel.add(points[i] - cx); rel.add(points[i + 1] - cy) }
            i += 2
        }
        addLayer(Layer(
            type = LayerType.DRAWING, name = uniqueName(if (vector) "Bentuk" else "Gambar"),
            points = rel, closed = closed, brush = brush, fill = color, fillOn = closed,
            w = (maxX - minX) + brush * 6f, h = (maxY - minY) + brush * 6f, x = cx, y = cy,
            startMs = timeMs, endMs = defaultEnd(timeMs)
        ))
    }

    fun insertProject(other: Project) {
        val copies = other.layers.map { it.copy(id = newId(), startMs = it.startMs + timeMs, endMs = it.endMs + timeMs, keys = it.keys.map { k -> k.copy(t = k.t + timeMs) }) }
        edit { it.copy(layers = copies + it.layers) }
        selected = copies.map { it.id }
    }

    // ------------------------------------------------------------ layer ops

    fun deleteSelected() {
        val ids = selected.toSet()
        edit { p -> p.copy(layers = p.layers.filterNot { it.id in ids }) }
        selected = emptyList()
    }

    fun duplicateSelected() {
        val src = selLayers
        if (src.isEmpty()) return
        val copies = src.map { it.copy(id = newId(), name = it.name + " Salinan", x = it.x + 40, y = it.y + 40) }
        edit { p -> p.copy(layers = copies + p.layers) }
        selected = copies.map { it.id }
    }

    fun copySelected() { clipboard = selLayers; styleClip = selLayers.firstOrNull() }

    fun paste() {
        if (clipboard.isEmpty()) return
        val copies = clipboard.map { it.copy(id = newId(), name = it.name + " Salinan", startMs = timeMs, endMs = timeMs + (it.endMs - it.startMs), keys = emptyList()) }
        edit { p -> p.copy(layers = copies + p.layers) }
        selected = copies.map { it.id }
    }

    fun pasteStyle() {
        val s = styleClip ?: return
        updateSelected { l -> l.copy(fill = s.fill, opacity = s.opacity, blend = s.blend, borders = s.borders, shadow = s.shadow, effects = s.effects) }
    }

    fun toggleVisible(id: String) = updateLayer(id) { it.copy(visible = !it.visible) }
    fun toggleLock(id: String) = updateLayer(id) { it.copy(locked = !it.locked) }

    /** Potong layer terpilih di posisi playhead menjadi dua bagian. */
    fun splitAtPlayhead() {
        val t = timeMs
        val ids = selected.toSet()
        val hit = project.layers.filter { it.id in ids && t > it.startMs + 50 && t < it.endMs - 50 }.map { it.id }.toSet()
        if (hit.isEmpty()) return
        edit { p ->
            p.copy(layers = p.layers.flatMap { l ->
                if (l.id in hit) {
                    val right = l.copy(id = newId(), startMs = t, inMs = l.inMs + ((t - l.startMs) * l.speed).toLong())
                    listOf(right, l.copy(endMs = t))
                } else listOf(l)
            })
        }
        clearSelection()
    }

    /** Buang bagian kiri layer (sebelum playhead). */
    fun trimStartToPlayhead() {
        val t = timeMs
        updateSelected { l ->
            if (t > l.startMs + 50 && t < l.endMs) l.copy(startMs = t, inMs = l.inMs + ((t - l.startMs) * l.speed).toLong()) else l
        }
    }

    /** Buang bagian kanan layer (setelah playhead). */
    fun trimEndToPlayhead() {
        val t = timeMs
        updateSelected { l -> if (t > l.startMs + 50 && t < l.endMs) l.copy(endMs = t) else l }
    }

    /** Batas waktu (awal & akhir) semua layer terpilih. */
    private fun selBounds(): List<Long> = selLayers.flatMap { listOf(it.startMs, it.endMs) }.distinct().sorted()

    /** Panah kiri: ke batas layer terpilih terdekat sebelum playhead; kalau tidak ada, ke awal video. */
    fun jumpToStart() {
        val prev = selBounds().lastOrNull { it < timeMs - 1 }
        seek(prev ?: 0L)
    }

    /** Panah kanan: ke batas layer terpilih terdekat setelah playhead; kalau tidak ada, ke akhir video. */
    fun jumpToEnd() {
        val next = selBounds().firstOrNull { it > timeMs + 1 }
        seek(next ?: totalMs)
    }

    fun moveLayerTimes(id: String, start: Long, end: Long) {
        updateLayer(id, "time-$id") { it.copy(startMs = max(0L, start), endMs = max(start + 100L, end)) }
    }

    fun align(kind: Int) {
        val ls = selLayers
        if (ls.isEmpty()) return
        fun ext(l: Layer) = floatArrayOf(l.w * l.sx / 2f, l.h * l.sy / 2f)
        val W = project.width.toFloat(); val H = project.height.toFloat()
        val single = ls.size == 1
        val left = if (single) 0f else ls.minOf { it.x - ext(it)[0] }
        val right = if (single) W else ls.maxOf { it.x + ext(it)[0] }
        val top = if (single) 0f else ls.minOf { it.y - ext(it)[1] }
        val bottom = if (single) H else ls.maxOf { it.y + ext(it)[1] }
        updateSelected { l ->
            val e = ext(l)
            when (kind) {
                0 -> l.copy(x = left + e[0]); 1 -> l.copy(x = (left + right) / 2f); 2 -> l.copy(x = right - e[0])
                3 -> l.copy(y = top + e[1]); 4 -> l.copy(y = (top + bottom) / 2f); else -> l.copy(y = bottom - e[1])
            }
        }
    }

    // ------------------------------------------------------------ playback

    fun seek(t: Long) { timeMs = t.coerceIn(0L, totalMs) }
    fun togglePlay() {
        if (!playing && timeMs >= totalMs) timeMs = 0L
        playing = !playing
        if (!playing) stopAudio()
    }

    private val players = HashMap<String, MediaPlayer>()

    fun audioTick() {
        if (!playing) return
        for (l in project.layers) {
            if ((l.type != LayerType.AUDIO && l.type != LayerType.VIDEO) || l.uri == null) continue
            val active = l.visible && timeMs >= l.startMs && timeMs < l.endMs
            val has = players.containsKey(l.id)
            if (active && !has) startPlayer(l)
            else if (!active && has) { players.remove(l.id)?.let { runCatching { it.release() } } }
        }
    }

    private fun startPlayer(l: Layer) {
        try {
            val mp = MediaPlayer()
            players[l.id] = mp
            mp.setDataSource(Graph.app, Uri.parse(l.uri))
            mp.setOnPreparedListener {
                runCatching {
                    it.seekTo((((timeMs - l.startMs) * l.speed).toInt().coerceAtLeast(0) + l.inMs).toInt())
                    val v = l.valueAt("vol", timeMs).coerceIn(0f, 1f)
                    it.setVolume(v, v)
                    if (playing) it.start()
                }
            }
            mp.setOnErrorListener { m, _, _ -> runCatching { m.release() }; players.remove(l.id); true }
            mp.prepareAsync()
        } catch (e: Throwable) {
            players.remove(l.id)
        }
    }

    fun stopAudio() {
        players.values.forEach { runCatching { it.release() } }
        players.clear()
    }
}
