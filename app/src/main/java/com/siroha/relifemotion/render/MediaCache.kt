package com.siroha.relifemotion.render

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import com.siroha.relifemotion.Graph
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.max

object MediaCache {
    private val images = object : LruCache<String, Bitmap>(64 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun image(uri: String): Bitmap? {
        images.get(uri)?.let { return it }
        val b = decode(Uri.parse(uri), 2048) ?: return null
        images.put(uri, b)
        return b
    }

    fun decode(uri: Uri, maxSide: Int): Bitmap? = try {
        val src = ImageDecoder.createSource(Graph.app.contentResolver, uri)
        ImageDecoder.decodeBitmap(src) { dec, info, _ ->
            dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val s = max(info.size.width, info.size.height)
            if (s > maxSide) {
                val f = maxSide.toFloat() / s
                dec.setTargetSize((info.size.width * f).toInt().coerceAtLeast(1), (info.size.height * f).toInt().coerceAtLeast(1))
            }
        }
    } catch (e: Throwable) {
        null
    }

    /** Ukuran asli gambar tanpa decode penuh. */
    fun imageSize(uri: Uri): Pair<Int, Int>? = try {
        val b = decode(uri, 2048)
        if (b != null) b.width to b.height else null
    } catch (e: Throwable) { null }
}

object VideoFrames {
    private val cache = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val last = ConcurrentHashMap<String, Bitmap>()
    private val retrievers = HashMap<String, MediaMetadataRetriever>()
    private val pending = HashSet<String>()
    private val exec = Executors.newSingleThreadExecutor()

    private fun retriever(uri: String): MediaMetadataRetriever = synchronized(retrievers) {
        retrievers.getOrPut(uri) {
            MediaMetadataRetriever().also { it.setDataSource(Graph.app, Uri.parse(uri)) }
        }
    }

    private fun fetch(uri: String, ms: Long, precise: Boolean): Bitmap? = try {
        val opt = if (precise) MediaMetadataRetriever.OPTION_CLOSEST else MediaMetadataRetriever.OPTION_CLOSEST_SYNC
        val raw = synchronized(retrievers) { retriever(uri).getFrameAtTime(ms * 1000L, opt) }
        if (raw != null && max(raw.width, raw.height) > 1280) {
            val f = 1280f / max(raw.width, raw.height)
            Bitmap.createScaledBitmap(raw, (raw.width * f).toInt(), (raw.height * f).toInt(), true)
        } else raw
    } catch (e: Throwable) {
        null
    }

    fun frame(uri: String, srcMs: Long, sync: Boolean, onReady: (() -> Unit)?): Bitmap? {
        val bucket = if (sync) srcMs else srcMs / 66
        val key = "$uri@$bucket"
        cache.get(key)?.let { last[uri] = it; return it }
        if (sync) {
            val b = fetch(uri, srcMs, true)
            if (b != null) { cache.put(key, b); last[uri] = b }
            return b ?: last[uri]
        }
        val start = synchronized(pending) { pending.add(key) }
        if (start) {
            exec.execute {
                try {
                    val b = fetch(uri, srcMs, false)
                    if (b != null) { cache.put(key, b); last[uri] = b }
                } finally {
                    synchronized(pending) { pending.remove(key) }
                }
                onReady?.invoke()
            }
        }
        return last[uri]
    }

    fun release() {
        synchronized(retrievers) {
            retrievers.values.forEach { runCatching { it.release() } }
            retrievers.clear()
        }
    }

    fun durationMs(uri: Uri): Long = try {
        val r = MediaMetadataRetriever()
        r.setDataSource(Graph.app, uri)
        val d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        r.release(); d
    } catch (e: Throwable) { 0L }

    fun size(uri: Uri): Pair<Int, Int>? = try {
        val r = MediaMetadataRetriever()
        r.setDataSource(Graph.app, uri)
        var w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        var h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        r.release()
        if (rot == 90 || rot == 270) { val t = w; w = h; h = t }
        if (w > 0 && h > 0) w to h else null
    } catch (e: Throwable) { null }
}
