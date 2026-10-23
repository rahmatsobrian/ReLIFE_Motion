package com.siroha.relifemotion.render

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.siroha.relifemotion.data.EffectCatalog
import com.siroha.relifemotion.data.Layer
import com.siroha.relifemotion.data.LayerType
import com.siroha.relifemotion.data.Project
import com.siroha.relifemotion.data.STROKE_BREAK
import com.siroha.relifemotion.data.valueAt
import java.io.File
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

class RenderCtx(
    val lowQuality: Boolean,
    val export: Boolean,
    val invalidate: (() -> Unit)? = null
)

class Mods(val alpha: Float, val cf: ColorFilter?, val blend: String)

class Fx {
    var dx = 0f; var dy = 0f; var drot = 0f; var dsc = 1f; var alphaMul = 1f
    var cm: ColorMatrix? = null
    var blur = 0f; var mosaic = 0f; var motion = 0f; var motionAngle = 0f
    var zoom = 0f; var spin = 0f; var rgb = 0f; var glowR = 0f; var glowI = 0f
    var repKind = 0   // 0 none, 1 linear, 2 grid, 3 radial
    var repN = 1; var repA = 0f; var repB = 0f; var repC = 0f; var repD = 0f
    val needsOff: Boolean get() = blur > 0f || mosaic > 0f || motion > 0f || zoom > 0f || spin > 0f || rgb > 0f || glowR > 0f

    fun addCm(m: ColorMatrix) {
        val c = cm ?: ColorMatrix()
        c.postConcat(m)
        cm = c
    }
}

object BlendModes {
    val groups: List<Pair<String, List<String>>> = listOf(
        "Gelapkan" to listOf("Multiply", "Darken", "Darker Color", "Color Burn", "Linear Burn"),
        "Cerahkan" to listOf("Screen", "Lighten", "Lighter Color", "Color Dodge", "Add"),
        "Kontras" to listOf("Overlay", "Soft Light", "Hard Light", "Difference", "Exclusion")
    )
}

object Fonts {
    private val cache = HashMap<String, Typeface>()

    fun systemFiles(): List<File> =
        listOf("/system/fonts", "/system/product/fonts", "/product/fonts").flatMap { d ->
            File(d).listFiles()?.filter { it.extension.lowercase() in setOf("ttf", "otf") } ?: emptyList()
        }.sortedBy { it.name }

    fun displayName(f: File): String = f.nameWithoutExtension.replace('-', ' ').replace('_', ' ')

    fun typeface(name: String, path: String?): Typeface {
        val key = path ?: name
        synchronized(cache) {
            cache[key]?.let { return it }
        }
        val tf = try {
            if (path != null) Typeface.createFromFile(path)
            else {
                val f = systemFiles().firstOrNull { displayName(it).equals(name, true) }
                if (f != null) Typeface.createFromFile(f) else Typeface.DEFAULT
            }
        } catch (e: Throwable) {
            Typeface.DEFAULT
        }
        synchronized(cache) { cache[key] = tf }
        return tf
    }
}

private object Pool {
    private val ring = arrayOfNulls<Bitmap>(16)
    private var idx = 0

    fun get(w: Int, h: Int, fresh: Boolean): Bitmap {
        if (fresh) return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        idx = (idx + 1) % ring.size
        var b = ring[idx]
        if (b == null || b.isRecycled || b.width != w || b.height != h) {
            b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            ring[idx] = b
        } else {
            b.eraseColor(0)
        }
        return b
    }
}

object Renderer {

    // ------------------------------------------------------------ public API

    fun renderProject(c: Canvas, p: Project, tMs: Long, rc: RenderCtx) {
        c.drawColor(p.bg)
        for (i in p.layers.indices.reversed()) {
            val l = p.layers[i]
            if (!l.visible || tMs < l.startMs || tMs >= l.endMs) continue
            drawLayer(c, p, l, tMs, rc)
        }
    }

    fun layerMatrix(l: Layer, t: Long): Matrix = buildMatrix(l, t, evalFx(l, t))

    fun corners(l: Layer, t: Long): FloatArray {
        val hw = l.w / 2f; val hh = max(l.h / 2f, 6f)
        val pts = floatArrayOf(-hw, -hh, hw, -hh, hw, hh, -hw, hh)
        layerMatrix(l, t).mapPoints(pts)
        return pts
    }

    fun hit(l: Layer, t: Long, px: Float, py: Float): Boolean {
        val inv = Matrix()
        if (!layerMatrix(l, t).invert(inv)) return false
        val pt = floatArrayOf(px, py)
        inv.mapPoints(pt)
        return abs(pt[0]) <= max(l.w / 2f, 20f) && abs(pt[1]) <= max(l.h / 2f, 20f)
    }

    fun center(l: Layer, t: Long): FloatArray {
        val pt = floatArrayOf(0f, 0f)
        layerMatrix(l, t).mapPoints(pt)
        return pt
    }

    fun textLayout(l: Layer, color: Int = l.fill): StaticLayout {
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
        tp.textSize = l.fontSize * 3f
        tp.typeface = Fonts.typeface(l.font, l.fontPath)
        tp.color = color
        val txt = l.text.ifEmpty { " " }
        val w = ceil(txt.split("\n").maxOf { tp.measureText(it) }).toInt().coerceAtLeast(1)
        return StaticLayout.Builder.obtain(txt, 0, txt.length, tp, w)
            .setAlignment(Layout.Alignment.ALIGN_CENTER).build()
    }

    // ------------------------------------------------------------ effects evaluation

    private fun hash(i: Int): Float {
        var x = i * 374761393 + 668265263
        x = (x xor (x ushr 13)) * 1274126177
        return ((x xor (x ushr 16)) and 0xFFFF) / 65535f
    }

    private fun lerp(a: FloatArray, b: FloatArray, t: Float) = FloatArray(20) { a[it] + (b[it] - a[it]) * t }

    private val ident = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)

    private fun hueCm(deg: Float): ColorMatrix {
        val a = Math.toRadians(deg.toDouble())
        val c = cos(a).toFloat(); val s = sin(a).toFloat()
        return ColorMatrix(floatArrayOf(
            0.213f + c * 0.787f - s * 0.213f, 0.715f - c * 0.715f - s * 0.715f, 0.072f - c * 0.072f + s * 0.928f, 0f, 0f,
            0.213f - c * 0.213f + s * 0.143f, 0.715f + c * 0.285f + s * 0.140f, 0.072f - c * 0.072f - s * 0.283f, 0f, 0f,
            0.213f - c * 0.213f - s * 0.787f, 0.715f - c * 0.715f + s * 0.715f, 0.072f + c * 0.928f + s * 0.072f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
    }

    fun evalFx(l: Layer, t: Long): Fx {
        val fx = Fx()
        if (l.effects.isEmpty()) return fx
        val lt = (t - l.startMs) / 1000f
        val dur = (l.endMs - l.startMs) / 1000f
        val twoPi = (2.0 * Math.PI).toFloat()
        for (e in l.effects) {
            if (!e.enabled) continue
            val d = EffectCatalog.get(e.id) ?: continue
            fun g(k: String): Float = e.params[k] ?: d.params.firstOrNull { it.key == k }?.def ?: 0f
            when (e.id) {
                "gaussian", "box", "lens" -> fx.blur = max(fx.blur, g("r"))
                "motion", "directional" -> { fx.motion = g("d"); fx.motionAngle = g("a") }
                "zoomblur" -> fx.zoom = g("amt")
                "spinblur" -> fx.spin = g("amt")
                "mosaic", "pixelate" -> fx.mosaic = g("size")
                "brightness" -> {
                    val b = g("b") * 255f; val c = g("c"); val o = b + 128f * (1f - c)
                    fx.addCm(ColorMatrix(floatArrayOf(c, 0f, 0f, 0f, o, 0f, c, 0f, 0f, o, 0f, 0f, c, 0f, o, 0f, 0f, 0f, 1f, 0f)))
                }
                "saturation" -> fx.addCm(ColorMatrix().also { it.setSaturation(g("s")) })
                "hue" -> fx.addCm(hueCm(g("deg")))
                "invert" -> fx.addCm(ColorMatrix(lerp(ident, floatArrayOf(-1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f, 0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f), g("amt"))))
                "temperature" -> {
                    val k = g("t") * 0.25f
                    fx.addCm(ColorMatrix(floatArrayOf(1f + k, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f - k, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
                }
                "exposure" -> {
                    val s = 2f.pow(g("e"))
                    fx.addCm(ColorMatrix(floatArrayOf(s, 0f, 0f, 0f, 0f, 0f, s, 0f, 0f, 0f, 0f, 0f, s, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
                }
                "threshold" -> {
                    val s = 40f; val o = -s * 255f * g("lvl") + 127.5f
                    fx.addCm(ColorMatrix(floatArrayOf(
                        0.299f * s, 0.587f * s, 0.114f * s, 0f, o,
                        0.299f * s, 0.587f * s, 0.114f * s, 0f, o,
                        0.299f * s, 0.587f * s, 0.114f * s, 0f, o,
                        0f, 0f, 0f, 1f, 0f)))
                }
                "colorize" -> {
                    val col = Color.HSVToColor(floatArrayOf(g("hue"), 0.85f, 1f))
                    val tr = Color.red(col) / 255f; val tg = Color.green(col) / 255f; val tb = Color.blue(col) / 255f
                    val gray = floatArrayOf(
                        0.299f * tr, 0.587f * tr, 0.114f * tr, 0f, 0f,
                        0.299f * tg, 0.587f * tg, 0.114f * tg, 0f, 0f,
                        0.299f * tb, 0.587f * tb, 0.114f * tb, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f)
                    fx.addCm(ColorMatrix(lerp(ident, gray, g("amt"))))
                }
                "solid" -> {
                    val col = Color.HSVToColor(floatArrayOf(g("hue"), g("sat"), 1f))
                    fx.addCm(ColorMatrix(floatArrayOf(
                        0f, 0f, 0f, 0f, Color.red(col).toFloat(),
                        0f, 0f, 0f, 0f, Color.green(col).toFloat(),
                        0f, 0f, 0f, 0f, Color.blue(col).toFloat(),
                        0f, 0f, 0f, 1f, 0f)))
                }
                "rgbsplit" -> fx.rgb = g("d")
                "softglow", "glow" -> { fx.glowR = g("r"); fx.glowI = g("i") }
                // opacity / visibility
                "blink" -> if (floor(lt * g("rate") * 2f).toInt() % 2 == 1) fx.alphaMul = 0f
                "flicker" -> fx.alphaMul *= 1f - g("amt") * hash(floor(lt * 24f).toInt())
                "fade" -> {
                    val fi = g("fin"); val fo = g("fout")
                    var a = 1f
                    if (fi > 0f) a *= min(lt / fi, 1f)
                    if (fo > 0f) a *= min((dur - lt) / fo, 1f)
                    fx.alphaMul *= a.coerceIn(0f, 1f)
                }
                "pulseop" -> fx.alphaMul *= 1f - g("amt") * (0.5f + 0.5f * sin(twoPi * g("rate") * lt))
                // move / transform
                "swing" -> fx.drot += g("ang") * sin(twoPi * g("rate") * lt)
                "spin" -> fx.drot += g("spd") * lt
                "oscillate" -> {
                    val off = g("dist") * sin(twoPi * g("rate") * lt)
                    val a = Math.toRadians(g("dir").toDouble())
                    fx.dx += off * cos(a).toFloat(); fx.dy += off * sin(a).toFloat()
                }
                "pulsesize" -> fx.dsc *= 1f + g("amt") * sin(twoPi * g("rate") * lt)
                "autoshake", "jitter" -> {
                    val step = floor(lt * g("rate")).toInt()
                    fx.dx += (hash(step * 2) - 0.5f) * 2f * g("amt")
                    fx.dy += (hash(step * 2 + 1) - 0.5f) * 2f * g("amt")
                }
                "offset" -> { fx.dx += g("x"); fx.dy += g("y") }
                // repeat
                "replinear", "repeat" -> { fx.repKind = 1; fx.repN = g("n").roundToInt().coerceIn(1, 24); fx.repA = g("dx"); fx.repB = g("dy") }
                "repgrid" -> { fx.repKind = 2; fx.repN = g("cols").roundToInt().coerceAtLeast(1); fx.repC = g("rows"); fx.repA = g("gx"); fx.repB = g("gy") }
                "repradial" -> { fx.repKind = 3; fx.repN = g("n").roundToInt().coerceIn(1, 32); fx.repA = g("rad") }
            }
        }
        return fx
    }

    private fun buildMatrix(l: Layer, t: Long, fx: Fx): Matrix {
        val x = l.valueAt("x", t) + fx.dx
        val y = l.valueAt("y", t) + fx.dy
        val rot = l.valueAt("rot", t) + fx.drot
        val sx = l.valueAt("sx", t) * fx.dsc
        val sy = l.valueAt("sy", t) * fx.dsc
        val kx = l.valueAt("kx", t).coerceIn(-80f, 80f)
        val ky = l.valueAt("ky", t).coerceIn(-80f, 80f)
        val m = Matrix()
        m.postScale(sx, sy)
        if (kx != 0f || ky != 0f) {
            m.postSkew(tan(Math.toRadians(kx.toDouble())).toFloat(), tan(Math.toRadians(ky.toDouble())).toFloat())
        }
        m.postRotate(rot)
        m.postTranslate(x, y)
        return m
    }

    private fun copyMatrices(fx: Fx, m: Matrix): List<Matrix> {
        if (fx.repKind == 0) return listOf(m)
        val out = ArrayList<Matrix>()
        when (fx.repKind) {
            1 -> for (i in 0 until fx.repN) out.add(Matrix(m).also { it.postTranslate(fx.repA * i, fx.repB * i) })
            2 -> {
                val rows = fx.repC.roundToInt().coerceAtLeast(1)
                for (r in 0 until rows) for (c in 0 until fx.repN) {
                    val ox = fx.repA * (c - (fx.repN - 1) / 2f)
                    val oy = fx.repB * (r - (rows - 1) / 2f)
                    out.add(Matrix(m).also { it.postTranslate(ox, oy) })
                }
            }
            else -> for (i in 0 until fx.repN) {
                val a = 2.0 * Math.PI * i / fx.repN
                out.add(Matrix(m).also { it.postTranslate((cos(a) * fx.repA).toFloat(), (sin(a) * fx.repA).toFloat()) })
            }
        }
        return out
    }

    // ------------------------------------------------------------ layer drawing

    private fun drawLayer(c: Canvas, p: Project, l: Layer, t: Long, rc: RenderCtx) {
        when (l.type) {
            LayerType.AUDIO, LayerType.GROUP, LayerType.NULL, LayerType.CAMERA -> return
            else -> {}
        }
        val fx = evalFx(l, t)
        val alpha = (l.valueAt("op", t) * fx.alphaMul).coerceIn(0f, 1f)
        if (alpha <= 0.001f) return
        val m = buildMatrix(l, t, fx)
        val mods = Mods(alpha, fx.cm?.let { ColorMatrixColorFilter(it) }, l.blend)
        val copies = copyMatrices(fx, m)

        if (!fx.needsOff) {
            for (mm in copies) {
                c.save(); c.concat(mm)
                drawContent(c, l, t, mods, rc)
                c.restore()
            }
            return
        }

        val base = if (rc.lowQuality && !rc.export) 0.5f else 1f
        val ks = when {
            fx.mosaic > 0f -> (1f / fx.mosaic.coerceAtLeast(1f)).coerceIn(1f / 128f, base)
            fx.blur > 0f -> (base / (1f + fx.blur / 3f)).coerceIn(1f / 64f, base)
            else -> base
        }
        val bw = max(1, (p.width * ks).roundToInt())
        val bh = max(1, (p.height * ks).roundToInt())
        val fresh = rc.export
        val src = Pool.get(bw, bh, fresh)
        val sc = Canvas(src)
        sc.scale(ks, ks)
        val plain = Mods(1f, null, "Normal")
        for (mm in copies) {
            sc.save(); sc.concat(mm)
            drawContent(sc, l, t, plain, rc)
            sc.restore()
        }

        val ctr = center(l, t)
        val cx = ctr[0] * ks; val cy = ctr[1] * ks
        var out = src
        var tmpAcc: Bitmap? = null

        if (fx.motion > 0f || fx.zoom > 0f || fx.spin > 0f || fx.rgb > 0f) {
            val acc = Pool.get(bw, bh, fresh)
            tmpAcc = acc
            val ac = Canvas(acc)
            val n = 10
            val pp = Paint(Paint.FILTER_BITMAP_FLAG)
            if (fx.rgb > 0f) {
                val d = fx.rgb * ks
                val chans = arrayOf(
                    floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f),
                    floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f),
                    floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)
                )
                for (i in 0 until 3) {
                    pp.colorFilter = ColorMatrixColorFilter(ColorMatrix(chans[i]))
                    pp.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
                    ac.drawBitmap(src, (i - 1) * d, 0f, pp)
                }
            } else {
                for (i in 0 until n) {
                    val f = i / (n - 1f) - 0.5f
                    pp.alpha = (255f / (i + 1)).toInt()
                    ac.save()
                    when {
                        fx.motion > 0f -> {
                            val a = Math.toRadians(fx.motionAngle.toDouble())
                            ac.translate((cos(a) * fx.motion * f * ks).toFloat(), (sin(a) * fx.motion * f * ks).toFloat())
                        }
                        fx.zoom > 0f -> { val s = 1f + fx.zoom * 0.01f * (i / (n - 1f)); ac.scale(s, s, cx, cy) }
                        else -> ac.rotate(fx.spin * f * 2f, cx, cy)
                    }
                    ac.drawBitmap(src, 0f, 0f, pp)
                    ac.restore()
                }
            }
            out = acc
        }

        val fp = Paint(Paint.ANTI_ALIAS_FLAG)
        fp.isFilterBitmap = fx.mosaic <= 0f
        fp.alpha = (alpha * 255).toInt().coerceIn(0, 255)
        fp.colorFilter = mods.cf
        applyBlend(fp, l.blend)
        c.save()
        c.scale(1f / ks, 1f / ks)
        c.drawBitmap(out, 0f, 0f, fp)
        c.restore()

        if (fx.glowR > 0f) {
            val gw = max(1, (bw / (1f + fx.glowR / 2f)).toInt())
            val gh = max(1, (bh / (1f + fx.glowR / 2f)).toInt())
            val small = Bitmap.createScaledBitmap(out, gw, gh, true)
            val gp = Paint(Paint.FILTER_BITMAP_FLAG)
            gp.xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
            val passes = if (fx.glowI > 1f) 2 else 1
            gp.alpha = (alpha * min(fx.glowI, 1f) * 255).toInt().coerceIn(0, 255)
            c.save()
            c.scale(1f / ks, 1f / ks)
            repeat(passes) { c.drawBitmap(small, null, RectF(0f, 0f, bw.toFloat(), bh.toFloat()), gp) }
            c.restore()
            if (rc.export) small.recycle()
        }
        if (rc.export) {
            src.recycle()
            tmpAcc?.recycle()
        }
    }

    // ------------------------------------------------------------ content

    private fun applyBlend(p: Paint, name: String) {
        if (name == "Normal") return
        if (Build.VERSION.SDK_INT >= 29) {
            val bm = when (name) {
                "Multiply" -> BlendMode.MULTIPLY
                "Darken", "Darker Color" -> BlendMode.DARKEN
                "Color Burn", "Linear Burn" -> BlendMode.COLOR_BURN
                "Screen" -> BlendMode.SCREEN
                "Lighten", "Lighter Color" -> BlendMode.LIGHTEN
                "Color Dodge" -> BlendMode.COLOR_DODGE
                "Add" -> BlendMode.PLUS
                "Overlay" -> BlendMode.OVERLAY
                "Soft Light" -> BlendMode.SOFT_LIGHT
                "Hard Light" -> BlendMode.HARD_LIGHT
                "Difference" -> BlendMode.DIFFERENCE
                "Exclusion" -> BlendMode.EXCLUSION
                else -> null
            }
            if (bm != null) p.blendMode = bm
        } else {
            val pm = when (name) {
                "Multiply" -> PorterDuff.Mode.MULTIPLY
                "Darken", "Darker Color" -> PorterDuff.Mode.DARKEN
                "Screen" -> PorterDuff.Mode.SCREEN
                "Lighten", "Lighter Color" -> PorterDuff.Mode.LIGHTEN
                "Overlay" -> PorterDuff.Mode.OVERLAY
                "Add" -> PorterDuff.Mode.ADD
                else -> null
            }
            if (pm != null) p.xfermode = PorterDuffXfermode(pm)
        }
    }

    private fun mkPaint(m: Mods, color: Int, style: Paint.Style, extraAlpha: Float = 1f): Paint {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.style = style
        p.color = color
        p.alpha = (Color.alpha(color) * m.alpha * extraAlpha).toInt().coerceIn(0, 255)
        p.colorFilter = m.cf
        applyBlend(p, m.blend)
        return p
    }

    private fun drawShadow(c: Canvas, path: Path, l: Layer, m: Mods) {
        val s = l.shadow ?: return
        val steps = 6
        for (k in 1..steps) {
            val pa = mkPaint(m, s.color, Paint.Style.FILL_AND_STROKE, s.alpha * 0.28f * (1f - (k - 1) / steps.toFloat()))
            pa.strokeWidth = s.size * 2f * k / steps
            pa.strokeJoin = Paint.Join.ROUND
            c.drawPath(path, pa)
        }
    }

    private fun drawBorders(c: Canvas, path: Path, l: Layer, m: Mods) {
        for (b in l.borders) {
            val pa = mkPaint(m, b.color, Paint.Style.STROKE)
            when (b.pos) {
                0 -> { c.save(); c.clipPath(path); pa.strokeWidth = b.width * 2f; c.drawPath(path, pa); c.restore() }
                1 -> { pa.strokeWidth = b.width; c.drawPath(path, pa) }
                else -> { c.save(); c.clipOutPath(path); pa.strokeWidth = b.width * 2f; c.drawPath(path, pa); c.restore() }
            }
        }
    }

    private fun strokeOutline(c: Canvas, path: Path, l: Layer, m: Mods) {
        if (!l.strokeOn) return
        val pa = mkPaint(m, l.fill, Paint.Style.STROKE)
        pa.strokeWidth = l.strokeW
        pa.strokeCap = when (l.cap) { 0 -> Paint.Cap.BUTT; 2 -> Paint.Cap.SQUARE; else -> Paint.Cap.ROUND }
        pa.strokeJoin = when (l.join) { 0 -> Paint.Join.MITER; 2 -> Paint.Join.BEVEL; else -> Paint.Join.ROUND }
        c.drawPath(path, pa)
    }

    private fun drawContent(c: Canvas, l: Layer, t: Long, m: Mods, rc: RenderCtx) {
        when (l.type) {
            LayerType.SHAPE -> {
                val path = ShapePaths.build(l.shape, l.w, l.h)
                drawShadow(c, path, l, m)
                if (l.fillOn) c.drawPath(path, mkPaint(m, l.fill, Paint.Style.FILL))
                drawBorders(c, path, l, m)
                strokeOutline(c, path, l, m)
            }
            LayerType.IMAGE, LayerType.VIDEO -> {
                val bmp: Bitmap? = if (l.type == LayerType.IMAGE) {
                    l.uri?.let { MediaCache.image(it) }
                } else {
                    val srcMs = ((t - l.startMs) * l.speed).toLong().coerceAtLeast(0L) + l.inMs
                    l.uri?.let { VideoFrames.frame(it, srcMs, rc.export, rc.invalidate) }
                }
                val rect = Path().also { it.addRect(-l.w / 2, -l.h / 2, l.w / 2, l.h / 2, Path.Direction.CW) }
                drawShadow(c, rect, l, m)
                if (bmp != null) {
                    val pa = mkPaint(m, Color.WHITE, Paint.Style.FILL)
                    pa.isFilterBitmap = true
                    c.drawBitmap(bmp, null, RectF(-l.w / 2, -l.h / 2, l.w / 2, l.h / 2), pa)
                } else {
                    c.drawPath(rect, mkPaint(m, 0x55888888, Paint.Style.FILL))
                }
                drawBorders(c, rect, l, m)
            }
            LayerType.TEXT -> {
                val layout = textLayout(l)
                val tp = layout.paint
                tp.alpha = (Color.alpha(l.fill) * m.alpha).toInt().coerceIn(0, 255)
                tp.colorFilter = m.cf
                applyBlend(tp, m.blend)
                l.shadow?.let { tp.setShadowLayer(it.size, 0f, 0f, it.color) }
                c.save()
                c.translate(-layout.width / 2f, -layout.height / 2f)
                for (b in l.borders) {
                    val ol = textLayout(l, b.color)
                    ol.paint.style = Paint.Style.STROKE
                    ol.paint.strokeWidth = b.width * 2f
                    ol.paint.strokeJoin = Paint.Join.ROUND
                    ol.paint.alpha = (Color.alpha(b.color) * m.alpha).toInt().coerceIn(0, 255)
                    ol.draw(c)
                }
                layout.draw(c)
                c.restore()
            }
            LayerType.DRAWING -> drawDrawing(c, l, m)
            else -> {}
        }
    }

    fun drawingPath(l: Layer): Path {
        val path = Path()
        val pts = l.points
        var px = 0f; var py = 0f; var has = false
        var i = 0
        while (i + 1 < pts.size) {
            val x = pts[i]; val y = pts[i + 1]
            i += 2
            if (x >= STROKE_BREAK) {
                if (has) path.lineTo(px, py)
                has = false
                continue
            }
            if (!has) {
                path.moveTo(x, y); px = x; py = y; has = true
            } else {
                val mx = (px + x) / 2f; val my = (py + y) / 2f
                path.quadTo(px, py, mx, my)
                px = x; py = y
            }
        }
        if (has) path.lineTo(px, py)
        if (l.closed) path.close()
        return path
    }

    private fun drawDrawing(c: Canvas, l: Layer, m: Mods) {
        val path = drawingPath(l)
        drawShadow(c, path, l, m)
        if (l.closed && l.fillOn) c.drawPath(path, mkPaint(m, l.fill, Paint.Style.FILL))
        val pa = mkPaint(m, l.fill, Paint.Style.STROKE)
        pa.strokeWidth = if (l.strokeOn) l.strokeW else l.brush * 3f
        pa.strokeCap = Paint.Cap.ROUND
        pa.strokeJoin = Paint.Join.ROUND
        c.drawPath(path, pa)
        drawBorders(c, path, l, m)
    }
}
