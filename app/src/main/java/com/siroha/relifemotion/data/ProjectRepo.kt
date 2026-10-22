package com.siroha.relifemotion.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.runtime.mutableStateListOf
import com.siroha.relifemotion.render.RenderCtx
import com.siroha.relifemotion.render.Renderer
import org.json.JSONObject
import java.io.File

class ProjectRepo(private val ctx: Context) {
    private val dir = File(ctx.filesDir, "projects").apply { mkdirs() }
    private val thumbDir = File(ctx.filesDir, "thumbs").apply { mkdirs() }

    /** Daftar proyek untuk UI (diisi lewat [refresh]). */
    val projects = mutableStateListOf<Project>()

    fun file(id: String) = File(dir, "$id.json")
    fun thumb(id: String) = File(thumbDir, "$id.png")
    fun sizeOf(id: String): Long = file(id).length()

    fun refresh() {
        val list = (dir.listFiles() ?: emptyArray())
            .filter { it.extension == "json" }
            .mapNotNull { f -> runCatching { projectFromJson(JSONObject(f.readText())) }.getOrNull() }
            .sortedByDescending { it.modifiedAt }
        projects.clear()
        projects.addAll(list)
    }

    fun load(id: String): Project? =
        runCatching { projectFromJson(JSONObject(file(id).readText())) }.getOrNull()

    fun save(p: Project) {
        val saved = p.copy(modifiedAt = System.currentTimeMillis())
        val tmp = File(dir, "${p.id}.tmp")
        tmp.writeText(saved.toJson().toString())
        tmp.renameTo(file(p.id))
        runCatching { writeThumb(saved) }
    }

    fun delete(id: String) {
        file(id).delete(); thumb(id).delete()
    }

    fun duplicate(id: String): Project? {
        val p = load(id) ?: return null
        val copy = p.copy(id = newId(), name = p.name + " (Salinan)", createdAt = System.currentTimeMillis())
        save(copy)
        return copy
    }

    fun importJson(text: String): Project? = runCatching {
        val p = projectFromJson(JSONObject(text)).copy(id = newId())
        save(p); p
    }.getOrNull()

    private fun writeThumb(p: Project) {
        val tw = 270
        val th = (tw.toFloat() * p.height / p.width).toInt().coerceIn(48, 480)
        val bmp = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(tw.toFloat() / p.width, th.toFloat() / p.height)
        Renderer.renderProject(c, p, 0L, RenderCtx(lowQuality = true, export = true))
        thumb(p.id).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
        bmp.recycle()
    }
}
