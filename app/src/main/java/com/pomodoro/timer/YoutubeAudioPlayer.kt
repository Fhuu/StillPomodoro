package com.pomodoro.timer

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import java.util.concurrent.Executors

/** Audio-only YouTube playback (no WebView / video decode). */
object YoutubeAudioPlayer {
    private const val TAG = "YoutubeAudio"
    private val ytHeaders = mapOf(
        "Referer" to "https://www.youtube.com/",
        "Origin" to "https://www.youtube.com",
    )
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var player: ExoPlayer? = null
    private var currentVideoId: String? = null
    private val streamUrlCache = HashMap<String, Pair<String, Long>>()
    private const val STREAM_URL_CACHE_MS = 45 * 60 * 1000L

    fun prefetch(context: Context, videoId: String) {
        val app = context.applicationContext
        executor.execute { resolvePlayUrl(app, videoId, bustCache = false) }
    }

    fun play(context: Context, videoId: String) {
        val app = context.applicationContext
        if (videoId == currentVideoId &&
            player?.isPlaying == true &&
            player?.playbackState == Player.STATE_READY
        ) {
            return
        }
        currentVideoId = videoId
        TimerStore.setYoutubePlaybackError(app, null)
        executor.execute {
            val streamUrl = resolvePlayUrl(app, videoId, bustCache = false)
            main.post {
                if (currentVideoId != videoId) return@post
                if (streamUrl == null) {
                    TimerStore.setYoutubePlaybackError(app, "Could not load YouTube audio for this video")
                    PomodoroService.refreshNotification(app)
                    Log.e(TAG, "No stream URL for $videoId")
                    return@post
                }
                val exo = player(app)
                exo.stop()
                exo.clearMediaItems()
                exo.setMediaItem(MediaItem.fromUri(streamUrl))
                exo.prepare()
                exo.playWhenReady = true
                Log.i(TAG, "Playing $videoId")
            }
        }
    }

    fun pause() {
        main.post { player?.pause() }
    }

    fun resume() {
        main.post { player?.playWhenReady = true }
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

    private fun resolvePlayUrl(context: Context, videoId: String, bustCache: Boolean): String? {
        val now = System.currentTimeMillis()
        if (!bustCache) {
            streamUrlCache[videoId]?.let { (url, expires) ->
                if (expires > now) return url
                streamUrlCache.remove(videoId)
            }
        } else {
            streamUrlCache.remove(videoId)
        }
        val resolved = YoutubeStreamResolver.resolve(videoId) ?: return null
        streamUrlCache[videoId] = resolved.url to now + STREAM_URL_CACHE_MS
        Log.d(TAG, "${resolved.log} for $videoId")
        return resolved.url
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun player(context: Context): ExoPlayer {
        val existing = player
        if (existing != null) return existing
        val app = context.applicationContext
        val http = OkHttpDataSource.Factory(YoutubeDownloader.httpClient())
            .setUserAgent(YoutubeDownloader.USER_AGENT)
            .setDefaultRequestProperties(ytHeaders)
        return ExoPlayer.Builder(app)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                false,
            )
            .build()
            .also { exo ->
                exo.repeatMode = Player.REPEAT_MODE_ONE
                exo.volume = 1f
                exo.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        val videoId = currentVideoId ?: return
                        Log.e(TAG, "ExoPlayer error for $videoId", error)
                        executor.execute {
                            val fresh = resolvePlayUrl(app, videoId, bustCache = true)
                            main.post {
                                if (currentVideoId != videoId) return@post
                                if (fresh == null) {
                                    TimerStore.setYoutubePlaybackError(
                                        app,
                                        "Playback failed — try another video",
                                    )
                                    PomodoroService.refreshNotification(app)
                                    return@post
                                }
                                exo.setMediaItem(MediaItem.fromUri(fresh))
                                exo.prepare()
                                exo.playWhenReady = true
                            }
                        }
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY && exo.isPlaying) {
                            TimerStore.setYoutubePlaybackError(app, null)
                        }
                    }
                })
                player = exo
            }
    }
}
