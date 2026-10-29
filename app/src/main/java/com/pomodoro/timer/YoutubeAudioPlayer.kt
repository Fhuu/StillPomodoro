package com.pomodoro.timer

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.util.concurrent.Executors

/** Audio-only YouTube playback (no WebView / video decode). */
object YoutubeAudioPlayer {
    private const val TAG = "YoutubeAudio"
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var player: ExoPlayer? = null
    private var initialized = false
    private var currentVideoId: String? = null
    private val streamUrlCache = HashMap<String, Pair<String, Long>>()
    private const val STREAM_URL_CACHE_MS = 30 * 60 * 1000L

    fun play(context: Context, videoId: String) {
        ensureExtractor()
        if (videoId == currentVideoId && player?.isPlaying == true) return
        currentVideoId = videoId
        executor.execute {
            val streamUrl = resolveAudioUrl(videoId)
            if (streamUrl == null) {
                Log.e(TAG, "No audio stream for $videoId")
                return@execute
            }
            main.post {
                val exo = player(context)
                exo.setMediaItem(MediaItem.fromUri(streamUrl))
                exo.prepare()
                exo.playWhenReady = true
            }
        }
    }

    fun pause() {
        main.post { player?.pause() }
    }

    fun resume() {
        main.post {
            player?.playWhenReady = true
        }
    }

    fun stop() {
        main.post {
            player?.stop()
            player?.clearMediaItems()
            currentVideoId = null
        }
    }

    fun release() {
        main.post {
            player?.release()
            player = null
            currentVideoId = null
            streamUrlCache.clear()
        }
    }

    private fun ensureExtractor() {
        if (initialized) return
        NewPipe.init(YoutubeDownloader.get())
        initialized = true
    }

    private fun resolveAudioUrl(videoId: String): String? {
        val now = System.currentTimeMillis()
        streamUrlCache[videoId]?.let { (url, expires) ->
            if (expires > now) return url
            streamUrlCache.remove(videoId)
        }
        val url = runCatching {
            val link = "https://www.youtube.com/watch?v=$videoId"
            val info = StreamInfo.getInfo(ServiceList.YouTube, link)
            info.audioStreams.maxByOrNull { it.bitrate }?.content
        }.getOrElse {
            Log.e(TAG, "Stream resolve failed", it)
            null
        } ?: return null
        streamUrlCache[videoId] = url to now + STREAM_URL_CACHE_MS
        return url
    }

    private fun player(context: Context): ExoPlayer {
        val existing = player
        if (existing != null) return existing
        val app = context.applicationContext
        return ExoPlayer.Builder(app)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .build()
            .also {
                it.repeatMode = Player.REPEAT_MODE_ONE
                player = it
            }
    }
}
