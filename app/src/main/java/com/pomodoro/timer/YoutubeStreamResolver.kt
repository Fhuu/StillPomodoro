package com.pomodoro.timer

import android.util.Log
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo

object YoutubeStreamResolver {
    private const val TAG = "YoutubeStream"
    @Volatile private var initialized = false

    data class Result(val url: String, val log: String)

    fun resolve(videoId: String): Result? {
        ensureInit()
        return runCatching {
            val link = "https://www.youtube.com/watch?v=$videoId"
            val info = StreamInfo.getInfo(ServiceList.YouTube, link)
            val audioCount = info.audioStreams.size
            val videoCount = info.videoStreams.size
            for (stream in info.audioStreams.sortedByDescending { bitrateOf(it) }) {
                streamUrl(stream)?.let { url ->
                    return Result(url, "audio stream ($audioCount audio, $videoCount video)")
                }
            }
            for (stream in info.videoStreams.sortedBy { it.height.coerceAtLeast(1) }) {
                streamUrl(stream)?.let { url ->
                    return Result(url, "video stream fallback ($audioCount audio, $videoCount video)")
                }
            }
            Log.e(TAG, "No playable URL for $videoId (audio=$audioCount video=$videoCount)")
            null
        }.getOrElse {
            Log.e(TAG, "Extract failed for $videoId", it)
            null
        }
    }

    private fun bitrateOf(stream: Stream): Int =
        when (stream) {
            is org.schabi.newpipe.extractor.stream.AudioStream -> {
                val avg = stream.averageBitrate
                if (avg > 0) avg else stream.bitrate
            }
            is org.schabi.newpipe.extractor.stream.VideoStream -> stream.bitrate
            else -> 0
        }

    private fun streamUrl(stream: Stream): String? =
        stream.content?.takeIf { it.isNotBlank() }
            ?: stream.url?.takeIf { it.isNotBlank() }

    private fun ensureInit() {
        if (initialized) return
        NewPipe.init(YoutubeDownloader.get(), Localization.DEFAULT, ContentCountry.DEFAULT)
        initialized = true
    }
}
