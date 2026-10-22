package com.siroha.relifemotion.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.core.content.ContextCompat
import com.siroha.relifemotion.render.MediaCache

data class MediaItem(val uri: Uri, val isVideo: Boolean, val bucket: String, val durMs: Long, val date: Long)
data class AudioItem(val uri: Uri, val title: String, val artist: String, val album: String, val durMs: Long)

object MediaRepo {
    fun visualPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    fun audioPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    private fun granted(ctx: Context, p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    fun hasVisual(ctx: Context): Boolean {
        if (visualPermissions().all { granted(ctx, it) }) return true
        return Build.VERSION.SDK_INT >= 34 && granted(ctx, "android.permission.READ_MEDIA_VISUAL_USER_SELECTED")
    }

    fun hasAudio(ctx: Context): Boolean = audioPermissions().all { granted(ctx, it) }

    fun queryMedia(ctx: Context): List<MediaItem> {
        val out = ArrayList<MediaItem>()
        fun q(base: Uri, video: Boolean) {
            val cols = if (video) arrayOf("_id", "bucket_display_name", "date_added", "duration") else arrayOf("_id", "bucket_display_name", "date_added")
            try {
                ctx.contentResolver.query(base, cols, null, null, "date_added DESC")?.use { c ->
                    var n = 0
                    while (c.moveToNext() && n < 500) {
                        val id = c.getLong(0)
                        out.add(MediaItem(ContentUris.withAppendedId(base, id), video, c.getString(1) ?: "Lainnya", if (video) c.getLong(3) else 0L, c.getLong(2)))
                        n++
                    }
                }
            } catch (e: Throwable) { }
        }
        q(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false)
        q(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true)
        return out.sortedByDescending { it.date }
    }

    fun queryAudio(ctx: Context): List<AudioItem> {
        val out = ArrayList<AudioItem>()
        val base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        try {
            ctx.contentResolver.query(base, arrayOf("_id", "title", "artist", "album", "duration"), "duration > 0", null, "date_added DESC")?.use { c ->
                while (c.moveToNext() && out.size < 800) {
                    out.add(AudioItem(ContentUris.withAppendedId(base, c.getLong(0)), c.getString(1) ?: "Audio", c.getString(2) ?: "<unknown>", c.getString(3) ?: "<unknown>", c.getLong(4)))
                }
            }
        } catch (e: Throwable) { }
        return out
    }
}

object ThumbCache {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun load(ctx: Context, uri: Uri, video: Boolean, px: Int = 256): Bitmap? {
        val key = "$uri@$px"
        cache.get(key)?.let { return it }
        val b: Bitmap? = try {
            if (Build.VERSION.SDK_INT >= 29) ctx.contentResolver.loadThumbnail(uri, Size(px, px), null)
            else if (!video) MediaCache.decode(uri, px)
            else {
                val r = MediaMetadataRetriever()
                r.setDataSource(ctx, uri)
                val f = r.getFrameAtTime(0)
                r.release()
                f
            }
        } catch (e: Throwable) {
            try { if (!video) MediaCache.decode(uri, px) else null } catch (e2: Throwable) { null }
        }
        if (b != null) cache.put(key, b)
        return b
    }
}
