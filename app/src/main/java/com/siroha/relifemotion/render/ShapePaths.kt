package com.siroha.relifemotion.render

import android.graphics.Path
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

object ShapePaths {
    val all = listOf(
        "circle", "rounded", "plus", "ring", "triangle",
        "pie", "hexagon", "flower", "arrow", "quad",
        "rect", "star", "line", "diamond", "heart"
    )

    fun nameOf(id: String): String = when (id) {
        "circle" -> "Lingkaran"; "rounded" -> "Persegi Panjang Bulat"; "plus" -> "Plus"
        "ring" -> "Cincin"; "triangle" -> "Segitiga"; "pie" -> "Pai"; "hexagon" -> "Segi Enam"
        "flower" -> "Bunga"; "arrow" -> "Panah"; "quad" -> "Segi Empat"; "rect" -> "Persegi panjang"
        "star" -> "Bintang"; "line" -> "Garis"; "diamond" -> "Berlian"; "heart" -> "Hati"
        else -> "Bentuk"
    }

    private fun poly(p: Path, f: FloatArray, w: Float, h: Float) {
        p.moveTo(f[0] * w, f[1] * h)
        var i = 2
        while (i < f.size) { p.lineTo(f[i] * w, f[i + 1] * h); i += 2 }
        p.close()
    }

    private fun star(p: Path, n: Int, inner: Float, w: Float, h: Float) {
        for (i in 0 until n * 2) {
            val r = if (i % 2 == 0) 0.5f else 0.5f * inner
            val a = Math.toRadians((-90.0 + i * 180.0 / n))
            val x = (cos(a) * r).toFloat() * w
            val y = (sin(a) * r).toFloat() * h
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
    }

    fun build(id: String, w: Float, h: Float): Path {
        val p = Path()
        val l = -w / 2; val t = -h / 2; val r = w / 2; val b = h / 2
        when (id) {
            "circle" -> p.addOval(RectF(l, t, r, b), Path.Direction.CW)
            "rounded" -> { val rad = min(w, h) * 0.2f; p.addRoundRect(RectF(l, t, r, b), rad, rad, Path.Direction.CW) }
            "plus" -> poly(p, floatArrayOf(-.17f, -.5f, .17f, -.5f, .17f, -.17f, .5f, -.17f, .5f, .17f, .17f, .17f,
                .17f, .5f, -.17f, .5f, -.17f, .17f, -.5f, .17f, -.5f, -.17f, -.17f, -.17f), w, h)
            "ring" -> {
                p.fillType = Path.FillType.EVEN_ODD
                p.addOval(RectF(l, t, r, b), Path.Direction.CW)
                p.addOval(RectF(l * 0.62f, t * 0.62f, r * 0.62f, b * 0.62f), Path.Direction.CW)
            }
            "triangle" -> poly(p, floatArrayOf(0f, -.5f, .5f, .5f, -.5f, .5f), w, h)
            "pie" -> { p.moveTo(0f, 0f); p.arcTo(RectF(l, t, r, b), -90f, 270f); p.close() }
            "hexagon" -> {
                for (i in 0 until 6) {
                    val a = Math.toRadians(-90.0 + i * 60.0)
                    val x = (cos(a) * 0.5).toFloat() * w; val y = (sin(a) * 0.5).toFloat() * h
                    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                p.close()
            }
            "flower" -> star(p, 8, 0.72f, w, h)
            "arrow" -> poly(p, floatArrayOf(-.5f, -.15f, .1f, -.15f, .1f, -.4f, .5f, 0f, .1f, .4f, .1f, .15f, -.5f, .15f), w, h)
            "quad" -> poly(p, floatArrayOf(-.5f, .35f, -.2f, -.35f, .5f, -.35f, .2f, .35f), w, h)
            "star" -> star(p, 5, 0.42f, w, h)
            "line" -> p.addRect(l, -6f, r, 6f, Path.Direction.CW)
            "diamond" -> poly(p, floatArrayOf(0f, -.5f, .5f, 0f, 0f, .5f, -.5f, 0f), w, h)
            "heart" -> {
                p.moveTo(0f, h * 0.5f)
                p.cubicTo(-w * 0.9f, h * 0.0f, -w * 0.5f, -h * 0.6f, 0f, -h * 0.2f)
                p.cubicTo(w * 0.5f, -h * 0.6f, w * 0.9f, h * 0.0f, 0f, h * 0.5f)
                p.close()
            }
            else -> p.addRect(l, t, r, b, Path.Direction.CW)
        }
        return p
    }
}
