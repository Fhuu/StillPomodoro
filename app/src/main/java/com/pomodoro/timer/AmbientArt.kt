package com.pomodoro.timer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

object AmbientArt {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var cachedUri: String? = null
    private var cachedBitmap: Bitmap? = null

    fun currentBitmap(): Bitmap? = cachedBitmap

    fun clear() {
        cachedUri = null
        cachedBitmap = null
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
                onLoaded?.invoke(cachedBitmap)
            }
        }
    }

    fun loadAmbientSelection(context: Context, onLoaded: ((Bitmap?) -> Unit)? = null) {
        when (TimerStore.ambientMode(context)) {
            AmbientMode.FILE -> TimerStore.ambientUri(context)?.let { load(context, Uri.parse(it), onLoaded) }
                ?: run { clear(); onLoaded?.invoke(null) }
            AmbientMode.FOLDER -> {
                val playing = TimerStore.nowPlayingUri(context)
                if (playing != null) load(context, Uri.parse(playing), onLoaded)
                else {
                    clear()
                    onLoaded?.invoke(null)
                }
            }
            AmbientMode.OFF -> {
                clear()
                onLoaded?.invoke(null)
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
