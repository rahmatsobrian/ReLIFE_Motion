package com.siroha.relifemotion.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.siroha.relifemotion.data.LayerType
import com.siroha.relifemotion.data.Project
import com.siroha.relifemotion.data.projectFromJson
import com.siroha.relifemotion.data.toJson
import com.siroha.relifemotion.data.newId
import com.siroha.relifemotion.render.RenderCtx
import com.siroha.relifemotion.render.Renderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min

data class VideoOpts(
    val shortSide: Int = 1080,
    val hevc: Boolean = false,
    val fpsMode: Int = 1,        // 0 rendah (1/4), 1 berkurang (1/2), 2 penuh
    val quality: Float = 0.6f
)

object Exporter {

    fun outFps(p: Project, o: VideoOpts): Int = when (o.fpsMode) {
        0 -> max(p.fps / 4, 12); 1 -> max(p.fps / 2, 15); else -> p.fps
    }

    fun outSize(p: Project, o: VideoOpts): Pair<Int, Int> {
        val s = o.shortSide.toFloat() / min(p.width, p.height)
        fun even(v: Float) = (v.toInt() / 2 * 2).coerceAtLeast(2)
        return even(p.width * s) to even(p.height * s)
    }

    fun bitrate(p: Project, o: VideoOpts): Int {
        val (w, h) = outSize(p, o)
        val bpp = 0.04f + 0.14f * o.quality
        return (w * h * outFps(p, o) * bpp * (if (o.hevc) 0.65f else 1f)).toInt().coerceIn(500_000, 80_000_000)
    }

    fun estimateBytes(p: Project, o: VideoOpts): Long =
        (bitrate(p, o) / 8L) * max(p.durationMs, 1000L) / 1000L

    // ---------------------------------------------------------- MediaStore helpers

    fun saveToGallery(ctx: Context, src: File, mime: String, name: String, isVideo: Boolean, sub: String = ""): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                val base = if (isVideo) "Movies" else "Pictures"
                val cv = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "$base/ReLIFE Motion" + (if (sub.isNotEmpty()) "/$sub" else ""))
                }
                val col = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                val uri = ctx.contentResolver.insert(col, cv) ?: return null
                ctx.contentResolver.openOutputStream(uri)?.use { o -> src.inputStream().use { it.copyTo(o) } }
                uri
            } else {
                val dirType = if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
                val dir = File(ctx.getExternalFilesDir(dirType), "ReLIFE Motion" + (if (sub.isNotEmpty()) "/$sub" else "")).apply { mkdirs() }
                val dest = File(dir, name)
                src.copyTo(dest, true)
                Uri.fromFile(dest)
            }
        } catch (e: Throwable) {
            null
        }
    }

    // ---------------------------------------------------------- video

    private class MuxState { var track = -1; var started = false; var count = 0L }

    suspend fun exportVideo(ctx: Context, p: Project, o: VideoOpts, onProgress: (Float) -> Unit): Uri? =
        withContext(Dispatchers.Default) {
            val (ow, oh) = outSize(p, o)
            val fps = outFps(p, o)
            val total = max(1L, (max(p.durationMs, 1000L) * fps / 1000L))
            val tmp = File(ctx.cacheDir, "export_${newId()}.mp4")
            val mime = if (o.hevc) MediaFormat.MIMETYPE_VIDEO_HEVC else MediaFormat.MIMETYPE_VIDEO_AVC
            val fmt = MediaFormat.createVideoFormat(mime, ow, oh).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate(p, o))
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            var codec: MediaCodec? = null
            var muxer: MediaMuxer? = null
            var result: Uri? = null
            try {
                codec = MediaCodec.createEncoderByType(mime)
                codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                val surface = codec.createInputSurface()
                codec.start()
                muxer = MediaMuxer(tmp.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                val st = MuxState()
                val info = MediaCodec.BufferInfo()
                val bmp = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888)
                val bc = Canvas(bmp)
                val rc = RenderCtx(lowQuality = false, export = true)
                val sx = ow.toFloat() / p.width
                val sy = oh.toFloat() / p.height
                var cancelled = false
                for (i in 0 until total) {
                    if (!coroutineContext.isActive) { cancelled = true; break }
                    val t = i * 1000L / fps
                    bc.save(); bc.scale(sx, sy)
                    Renderer.renderProject(bc, p, t, rc)
                    bc.restore()
                    val sc = surface.lockHardwareCanvas()
                    sc.drawBitmap(bmp, 0f, 0f, null)
                    surface.unlockCanvasAndPost(sc)
                    drain(codec, muxer, st, info, fps, false)
                    onProgress((i + 1f) / total)
                }
                bmp.recycle()
                codec.signalEndOfInputStream()
                drain(codec, muxer, st, info, fps, true)
                if (st.started) muxer.stop()
                if (!cancelled && st.started) {
                    result = saveToGallery(ctx, tmp, "video/mp4", "${p.name}_${System.currentTimeMillis()}.mp4", true)
                }
            } catch (e: Throwable) {
                result = null
            } finally {
                runCatching { codec?.stop() }
                runCatching { codec?.release() }
                runCatching { muxer?.release() }
                tmp.delete()
            }
            result
        }

    private fun drain(codec: MediaCodec, muxer: MediaMuxer, st: MuxState, info: MediaCodec.BufferInfo, fps: Int, eos: Boolean) {
        while (true) {
            val idx = codec.dequeueOutputBuffer(info, 10_000)
            when {
                idx == MediaCodec.INFO_TRY_AGAIN_LATER -> { if (!eos) return }
                idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    st.track = muxer.addTrack(codec.outputFormat)
                    muxer.start(); st.started = true
                }
                idx >= 0 -> {
                    val buf = codec.getOutputBuffer(idx)
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                    if (buf != null && info.size > 0 && st.started) {
                        info.presentationTimeUs = st.count * 1_000_000L / fps
                        st.count++
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        muxer.writeSampleData(st.track, buf, info)
                    }
                    codec.releaseOutputBuffer(idx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    // ---------------------------------------------------------- gambar

    private fun renderFrame(p: Project, t: Long): Bitmap {
        val bmp = Bitmap.createBitmap(p.width, p.height, Bitmap.Config.ARGB_8888)
        Renderer.renderProject(Canvas(bmp), p, t, RenderCtx(lowQuality = false, export = true))
        return bmp
    }

    suspend fun exportFrame(ctx: Context, p: Project, tMs: Long): Uri? = withContext(Dispatchers.Default) {
        val bmp = renderFrame(p, tMs)
        val tmp = File(ctx.cacheDir, "frame_${newId()}.png")
        tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        val u = saveToGallery(ctx, tmp, "image/png", "${p.name}_${tMs}.png", false)
        tmp.delete()
        u
    }

    suspend fun exportSequence(ctx: Context, p: Project, onProgress: (Float) -> Unit): Int = withContext(Dispatchers.Default) {
        val total = max(1L, max(p.durationMs, 1000L) * p.fps / 1000L).toInt()
        var saved = 0
        for (i in 0 until total) {
            if (!coroutineContext.isActive) break
            val bmp = renderFrame(p, i * 1000L / p.fps)
            val tmp = File(ctx.cacheDir, "seq_${newId()}.png")
            tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bmp.recycle()
            if (saveToGallery(ctx, tmp, "image/png", "%s_%05d.png".format(p.name, i + 1), false, p.name) != null) saved++
            tmp.delete()
            onProgress((i + 1f) / total)
        }
        saved
    }

    // ---------------------------------------------------------- paket proyek (.relife)

    suspend fun exportPackage(ctx: Context, p: Project): Uri? = withContext(Dispatchers.IO) {
        try {
            val tmp = File(ctx.cacheDir, "pkg_${newId()}.relife")
            val json = p.toJson()
            val layers = json.getJSONArray("layers")
            ZipOutputStream(tmp.outputStream().buffered()).use { z ->
                var n = 0
                for (i in 0 until layers.length()) {
                    val lo = layers.getJSONObject(i)
                    val uri = lo.optString("uri", "")
                    val type = lo.optString("type")
                    if (uri.isNotEmpty() && (type == LayerType.IMAGE.name || type == LayerType.VIDEO.name || type == LayerType.AUDIO.name)) {
                        val ext = uri.substringAfterLast('.', "bin").takeIf { it.length in 1..4 } ?: "bin"
                        val entry = "media/${n++}.$ext"
                        val ok = try {
                            ctx.contentResolver.openInputStream(Uri.parse(uri))?.use { ins ->
                                z.putNextEntry(ZipEntry(entry)); ins.copyTo(z); z.closeEntry(); true
                            } ?: false
                        } catch (e: Throwable) { false }
                        if (ok) lo.put("uri", "pkg:$entry")
                    }
                }
                z.putNextEntry(ZipEntry("project.json"))
                z.write(json.toString().toByteArray())
                z.closeEntry()
            }
            val name = "${p.name}.relife"
            val result: Uri? = if (Build.VERSION.SDK_INT >= 29) {
                val cv = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/ReLIFE Motion")
                }
                val u = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
                if (u != null) ctx.contentResolver.openOutputStream(u)?.use { o -> tmp.inputStream().use { it.copyTo(o) } }
                u
            } else {
                val dir = File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "ReLIFE Motion").apply { mkdirs() }
                val d = File(dir, name); tmp.copyTo(d, true); Uri.fromFile(d)
            }
            tmp.delete()
            result
        } catch (e: Throwable) {
            null
        }
    }

    /** Impor .relife (zip) atau JSON polos. */
    suspend fun importProject(ctx: Context, uri: Uri): Project? = withContext(Dispatchers.IO) {
        try {
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
            if (bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
                val id = newId()
                val mediaDir = File(ctx.filesDir, "media/$id").apply { mkdirs() }
                var json: String? = null
                ZipInputStream(bytes.inputStream()).use { z ->
                    var e = z.nextEntry
                    while (e != null) {
                        if (e.name == "project.json") json = z.readBytes().toString(Charsets.UTF_8)
                        else if (e.name.startsWith("media/")) File(mediaDir, e.name.removePrefix("media/")).outputStream().use { z.copyTo(it) }
                        e = z.nextEntry
                    }
                }
                val o = JSONObject(json ?: return@withContext null)
                val ls = o.getJSONArray("layers")
                for (i in 0 until ls.length()) {
                    val lo = ls.getJSONObject(i)
                    val u = lo.optString("uri", "")
                    if (u.startsWith("pkg:media/")) lo.put("uri", Uri.fromFile(File(mediaDir, u.removePrefix("pkg:media/"))).toString())
                }
                projectFromJson(o).copy(id = id)
            } else {
                projectFromJson(JSONObject(bytes.toString(Charsets.UTF_8))).copy(id = newId())
            }
        } catch (e: Throwable) {
            null
        }
    }
}
