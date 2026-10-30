package com.pomodoro.timer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

object AmbientArt {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val generation = AtomicInteger(0)
    private var cachedUri: String? = null
    private var cachedBitmap: Bitmap? = null

    fun artGeneration(): Int = generation.get()

    fun currentBitmap(): Bitmap? = cachedBitmap

    fun prepareTrackChange(uri: Uri) {
        val key = uri.toString()
        if (key == cachedUri && cachedBitmap != null) return
        cachedUri = null
        cachedBitmap = null
        generation.incrementAndGet()
    }

    fun notifyMetadataChanged() {
        generation.incrementAndGet()
    }

    fun clear() {
        cachedUri = null
        cachedBitmap = null
        generation.incrementAndGet()
    }

    fun reloadSelection(context: Context, onLoaded: ((Bitmap?) -> Unit)? = null) {
        clear()
        loadAmbientSelection(context, onLoaded)
    }

    fun load(context: Context, uri: Uri, onLoaded: ((Bitmap?) -> Unit)? = null) {
        val key = uri.toString()
        if (key == cachedUri && cachedBitmap != null) {
            onLoaded?.invoke(cachedBitmap)
            return
        }
        val app = context.applicationContext
        executor.execute {
            val bitmap = readEmbeddedArt(app, uri)
            main.post {
                cachedBitmap = bitmap
                cachedUri = if (bitmap != null) key else null
                generation.incrementAndGet()
                onLoaded?.invoke(cachedBitmap)
            }
        }
    }

    fun loadAmbientSelection(context: Context, onLoaded: ((Bitmap?) -> Unit)? = null) {
        when (TimerStore.ambientMode(context)) {
            AmbientMode.LIBRARY -> {
                val uri = TimerStore.selectedTrackUri(context)
                    ?: TimerStore.singleTrackUri(context)
                    ?: TimerStore.nowPlayingUri(context)
                if (uri != null) load(context, Uri.parse(uri), onLoaded)
                else {
                    clear()
                    onLoaded?.invoke(null)
                }
            }
            AmbientMode.YOUTUBE -> {
                val id = TimerStore.youtubeVideoId(context)
                if (id != null) {
                    YoutubePlaylist.thumbBitmap(context, id)?.let { bitmap ->
                        cachedBitmap = bitmap
                        cachedUri = "yt:$id"
                        generation.incrementAndGet()
                        onLoaded?.invoke(bitmap)
                        return
                    }
                    loadYoutubeThumbnail(context, id, onLoaded)
                } else {
                    clear()
                    onLoaded?.invoke(null)
                }
            }
            AmbientMode.OFF, AmbientMode.BUILT_IN -> {
                clear()
                onLoaded?.invoke(null)
            }
        }
    }

    fun loadYoutubeThumbnail(context: Context, videoId: String, onLoaded: ((Bitmap?) -> Unit)? = null) {
        val key = "yt:$videoId"
        if (key == cachedUri && cachedBitmap != null) {
            onLoaded?.invoke(cachedBitmap)
            return
        }
        val app = context.applicationContext
        executor.execute {
            val bitmap = runCatching {
                java.net.URL(YouTubeLinks.thumbnailUrl(videoId)).openStream().use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }.getOrNull()
            main.post {
                cachedBitmap = bitmap
                cachedUri = if (bitmap != null) key else null
                generation.incrementAndGet()
                onLoaded?.invoke(cachedBitmap)
            }
        }
    }

    private fun readEmbeddedArt(context: Context, uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val bytes = retriever.embeddedPicture ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val sample = sampleSize(bounds.outWidth, bounds.outHeight, 512)
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    private fun sampleSize(w: Int, h: Int, maxSide: Int): Int {
        var size = 1
        while (w / size > maxSide || h / size > maxSide) size *= 2
        return size
    }
}
