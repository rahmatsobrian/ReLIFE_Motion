package com.siroha.relifemotion.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.floor

enum class LayerType { SHAPE, IMAGE, VIDEO, AUDIO, TEXT, DRAWING, GROUP, NULL, CAMERA }

fun newId(): String = UUID.randomUUID().toString().take(8)

/** Kurva easing antar keyframe. */
data class Ease(
    val type: String = "linear",       // linear | bezier | bounce | steps | hold
    val a: Float = 0.25f, val b: Float = 0.25f, val c: Float = 0.75f, val d: Float = 0.75f,
    val steps: Int = 4
) {
    fun apply(t0: Float): Float {
        val t = t0.coerceIn(0f, 1f)
        return when (type) {
            "bezier" -> cubic(a, b, c, d, t)
            "bounce" -> bounceOut(t)
            "steps" -> floor(t * steps) / steps
            "hold" -> if (t < 1f) 0f else 1f
            else -> t
        }
    }

    fun toJson(): JSONObject = JSONObject().put("t", type).put("a", a.toDouble()).put("b", b.toDouble())
        .put("c", c.toDouble()).put("d", d.toDouble()).put("s", steps)

    companion object {
        fun fromJson(o: JSONObject?): Ease = if (o == null) Ease() else Ease(
            o.optString("t", "linear"),
            o.optDouble("a", 0.25).toFloat(), o.optDouble("b", 0.25).toFloat(),
            o.optDouble("c", 0.75).toFloat(), o.optDouble("d", 0.75).toFloat(), o.optInt("s", 4)
        )

        fun cubic(x1: Float, y1: Float, x2: Float, y2: Float, x: Float): Float {
            var lo = 0f
            var hi = 1f
            var t = x
            repeat(22) {
                val cx = 3 * (1 - t) * (1 - t) * t * x1 + 3 * (1 - t) * t * t * x2 + t * t * t
                if (cx < x) lo = t else hi = t
                t = (lo + hi) / 2f
            }
            return 3 * (1 - t) * (1 - t) * t * y1 + 3 * (1 - t) * t * t * y2 + t * t * t
        }

        fun bounceOut(t: Float): Float {
            val n = 7.5625f
            val d = 2.75f
            return when {
                t < 1 / d -> n * t * t
                t < 2 / d -> { val u = t - 1.5f / d; n * u * u + 0.75f }
                t < 2.5f / d -> { val u = t - 2.25f / d; n * u * u + 0.9375f }
                else -> { val u = t - 2.625f / d; n * u * u + 0.984375f }
            }
        }
    }
}

data class Key(val prop: String, val t: Long, val v: Float, val ease: Ease = Ease())

/** pos: 0 = di dalam, 1 = tengah, 2 = di luar */
data class StrokeSpec(val pos: Int = 0, val color: Int = -1, val width: Float = 6f)

data class ShadowSpec(val color: Int = -16777216, val size: Float = 4f, val alpha: Float = 1f)

data class EffectInst(
    val uid: String = newId(),
    val id: String,
    val params: Map<String, Float> = emptyMap(),
    val enabled: Boolean = true
)

data class Layer(
    val id: String = newId(),
    val type: LayerType,
    val name: String,
    val startMs: Long = 0L,
    val endMs: Long = 3000L,
    val shape: String = "rect",
    val w: Float = 300f,
    val h: Float = 300f,
    val fill: Int = 0xFFD9EE8B.toInt(),
    val fillOn: Boolean = true,
    val opacity: Float = 1f,
    val blend: String = "Normal",
    val x: Float = 0f, val y: Float = 0f, val rot: Float = 0f,
    val sx: Float = 1f, val sy: Float = 1f, val kx: Float = 0f, val ky: Float = 0f,
    val keys: List<Key> = emptyList(),
    val strokeOn: Boolean = false, val strokeW: Float = 4f, val cap: Int = 1, val join: Int = 1,
    val borders: List<StrokeSpec> = emptyList(),
    val shadow: ShadowSpec? = null,
    val effects: List<EffectInst> = emptyList(),
    val text: String = "", val font: String = "Roboto Regular", val fontPath: String? = null, val fontSize: Float = 18f,
    val uri: String? = null,
    val volume: Float = 1f, val speed: Float = 1f,
    val points: List<Float> = emptyList(), val closed: Boolean = false, val brush: Float = 5f,
    val visible: Boolean = true, val locked: Boolean = false,
    val mediaDurationMs: Long = 0L
)

const val STROKE_BREAK = 1e9f

fun Layer.base(prop: String): Float = when (prop) {
    "x" -> x; "y" -> y; "rot" -> rot; "sx" -> sx; "sy" -> sy; "kx" -> kx; "ky" -> ky
    "op" -> opacity; "vol" -> volume
    else -> 0f
}

fun Layer.withBase(prop: String, v: Float): Layer = when (prop) {
    "x" -> copy(x = v); "y" -> copy(y = v); "rot" -> copy(rot = v)
    "sx" -> copy(sx = v); "sy" -> copy(sy = v); "kx" -> copy(kx = v); "ky" -> copy(ky = v)
    "op" -> copy(opacity = v); "vol" -> copy(volume = v)
    else -> this
}

fun Layer.hasKeys(prop: String): Boolean = keys.any { it.prop == prop }

fun Layer.valueAt(prop: String, t: Long): Float {
    var first: Key? = null
    var last: Key? = null
    var prev: Key? = null
    var next: Key? = null
    for (k in keys) {
        if (k.prop != prop) continue
        if (first == null || k.t < first.t) first = k
        if (last == null || k.t > last.t) last = k
        if (k.t <= t && (prev == null || k.t > prev.t)) prev = k
        if (k.t > t && (next == null || k.t < next.t)) next = k
    }
    if (first == null || last == null) return base(prop)
    if (t <= first.t) return first.v
    if (t >= last.t) return last.v
    val a = prev ?: return first.v
    val b = next ?: return last.v
    val f = (t - a.t).toFloat() / (b.t - a.t).toFloat()
    return a.v + (b.v - a.v) * a.ease.apply(f)
}

data class Project(
    val id: String = newId(),
    val name: String = "Proyek Baru",
    val width: Int = 1080,
    val height: Int = 1920,
    val fps: Int = 60,
    val bg: Int = -16777216,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
    val isElement: Boolean = false,
    val layers: List<Layer> = emptyList()   // indeks 0 = layer paling atas
) {
    val durationMs: Long get() = (layers.maxOfOrNull { it.endMs } ?: 0L)
}

// ---------------------------------------------------------------- JSON

private fun JSONObject.f(k: String, d: Float) = optDouble(k, d.toDouble()).toFloat()

fun Layer.toJson(): JSONObject = JSONObject().apply {
    put("id", id); put("type", type.name); put("name", name)
    put("start", startMs); put("end", endMs)
    put("shape", shape); put("w", w.toDouble()); put("h", h.toDouble())
    put("fill", fill); put("fillOn", fillOn); put("opacity", opacity.toDouble()); put("blend", blend)
    put("x", x.toDouble()); put("y", y.toDouble()); put("rot", rot.toDouble())
    put("sx", sx.toDouble()); put("sy", sy.toDouble()); put("kx", kx.toDouble()); put("ky", ky.toDouble())
    put("keys", JSONArray().also { a ->
        keys.forEach { k -> a.put(JSONObject().put("p", k.prop).put("t", k.t).put("v", k.v.toDouble()).put("e", k.ease.toJson())) }
    })
    put("strokeOn", strokeOn); put("strokeW", strokeW.toDouble()); put("cap", cap); put("join", join)
    put("borders", JSONArray().also { a ->
        borders.forEach { b -> a.put(JSONObject().put("pos", b.pos).put("color", b.color).put("w", b.width.toDouble())) }
    })
    shadow?.let { put("shadow", JSONObject().put("c", it.color).put("s", it.size.toDouble()).put("a", it.alpha.toDouble())) }
    put("effects", JSONArray().also { a ->
        effects.forEach { e ->
            val po = JSONObject()
            e.params.forEach { (k, v) -> po.put(k, v.toDouble()) }
            a.put(JSONObject().put("uid", e.uid).put("id", e.id).put("on", e.enabled).put("p", po))
        }
    })
    put("text", text); put("font", font); fontPath?.let { put("fontPath", it) }; put("fontSize", fontSize.toDouble())
    uri?.let { put("uri", it) }
    put("volume", volume.toDouble()); put("speed", speed.toDouble())
    put("points", JSONArray().also { a -> points.forEach { a.put(it.toDouble()) } })
    put("closed", closed); put("brush", brush.toDouble())
    put("visible", visible); put("locked", locked); put("mediaDur", mediaDurationMs)
}

fun layerFromJson(o: JSONObject): Layer {
    val keys = ArrayList<Key>()
    o.optJSONArray("keys")?.let { a ->
        for (i in 0 until a.length()) {
            val k = a.getJSONObject(i)
            keys.add(Key(k.getString("p"), k.getLong("t"), k.f("v", 0f), Ease.fromJson(k.optJSONObject("e"))))
        }
    }
    val borders = ArrayList<StrokeSpec>()
    o.optJSONArray("borders")?.let { a ->
        for (i in 0 until a.length()) {
            val b = a.getJSONObject(i)
            borders.add(StrokeSpec(b.optInt("pos", 0), b.optInt("color", -1), b.f("w", 6f)))
        }
    }
    val effects = ArrayList<EffectInst>()
    o.optJSONArray("effects")?.let { a ->
        for (i in 0 until a.length()) {
            val e = a.getJSONObject(i)
            val pm = HashMap<String, Float>()
            e.optJSONObject("p")?.let { po -> po.keys().forEach { k -> pm[k] = po.optDouble(k, 0.0).toFloat() } }
            effects.add(EffectInst(e.optString("uid", newId()), e.getString("id"), pm, e.optBoolean("on", true)))
        }
    }
    val pts = ArrayList<Float>()
    o.optJSONArray("points")?.let { a -> for (i in 0 until a.length()) pts.add(a.optDouble(i, 0.0).toFloat()) }
    val sh = o.optJSONObject("shadow")?.let { ShadowSpec(it.optInt("c", -16777216), it.f("s", 4f), it.f("a", 1f)) }
    return Layer(
        id = o.optString("id", newId()),
        type = runCatching { LayerType.valueOf(o.optString("type", "SHAPE")) }.getOrDefault(LayerType.SHAPE),
        name = o.optString("name", "Layer"),
        startMs = o.optLong("start", 0), endMs = o.optLong("end", 3000),
        shape = o.optString("shape", "rect"), w = o.f("w", 300f), h = o.f("h", 300f),
        fill = o.optInt("fill", 0xFFD9EE8B.toInt()), fillOn = o.optBoolean("fillOn", true),
        opacity = o.f("opacity", 1f), blend = o.optString("blend", "Normal"),
        x = o.f("x", 0f), y = o.f("y", 0f), rot = o.f("rot", 0f),
        sx = o.f("sx", 1f), sy = o.f("sy", 1f), kx = o.f("kx", 0f), ky = o.f("ky", 0f),
        keys = keys,
        strokeOn = o.optBoolean("strokeOn", false), strokeW = o.f("strokeW", 4f),
        cap = o.optInt("cap", 1), join = o.optInt("join", 1),
        borders = borders, shadow = sh, effects = effects,
        text = o.optString("text", ""), font = o.optString("font", "Roboto Regular"),
        fontPath = if (o.has("fontPath")) o.optString("fontPath") else null,
        fontSize = o.f("fontSize", 18f),
        uri = if (o.has("uri")) o.optString("uri") else null,
        volume = o.f("volume", 1f), speed = o.f("speed", 1f),
        points = pts, closed = o.optBoolean("closed", false), brush = o.f("brush", 5f),
        visible = o.optBoolean("visible", true), locked = o.optBoolean("locked", false),
        mediaDurationMs = o.optLong("mediaDur", 0)
    )
}

fun Project.toJson(): JSONObject = JSONObject().apply {
    put("v", 1); put("id", id); put("name", name)
    put("w", width); put("h", height); put("fps", fps); put("bg", bg)
    put("created", createdAt); put("modified", modifiedAt); put("element", isElement)
    put("layers", JSONArray().also { a -> layers.forEach { a.put(it.toJson()) } })
}

fun projectFromJson(o: JSONObject): Project {
    val ls = ArrayList<Layer>()
    o.optJSONArray("layers")?.let { a -> for (i in 0 until a.length()) ls.add(layerFromJson(a.getJSONObject(i))) }
    return Project(
        id = o.optString("id", newId()), name = o.optString("name", "Proyek"),
        width = o.optInt("w", 1080), height = o.optInt("h", 1920), fps = o.optInt("fps", 60),
        bg = o.optInt("bg", -16777216), createdAt = o.optLong("created", System.currentTimeMillis()),
        modifiedAt = o.optLong("modified", System.currentTimeMillis()),
        isElement = o.optBoolean("element", false), layers = ls
    )
}

fun fmtTime(ms: Long, fps: Int): String {
    val totalSec = ms / 1000
    val frames = ((ms % 1000) * fps / 1000).toInt()
    val m = totalSec / 60
    val s = totalSec % 60
    return "%02d:%02d:%02d".format(m, s, frames)
}

fun aspectLabel(w: Int, h: Int): String {
    fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
    val g = gcd(w, h).coerceAtLeast(1)
    val a = w / g
    val b = h / g
    return if (a <= 32 && b <= 32) "$a:$b" else "%.2f:1".format(w.toFloat() / h)
}
