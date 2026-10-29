package com.pomodoro.timer

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.util.concurrent.Executors

data class YoutubePlaylistEntry(
    val videoId: String,
    val title: String,
    val link: String,
    val thumbPath: String?,
)

/** Local YouTube playlist (links + cached thumbnails in app storage). */
object YoutubePlaylist {
    private const val PREFS_KEY = "youtube_playlist_entries"
    private const val DIR = "youtube_playlist"
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun entries(context: Context): List<YoutubePlaylistEntry> {
        migrateLegacyVideoSync(context)
        val raw = TimerStore.prefs(context).getString(PREFS_KEY, null) ?: return emptyList()
        return runCatching {
            JSONArray(raw).let { array ->
                buildList {
                    for (i in 0 until array.length()) {
                        add(entryFromJson(array.getJSONObject(i)))
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun add(context: Context, input: String, onDone: (YoutubePlaylistEntry?) -> Unit) {
        val id = YouTubeLinks.parseVideoId(input)
        if (id == null) {
            onDone(null)
            return
        }
        if (entries(context).any { it.videoId == id }) {
            onDone(entries(context).first { it.videoId == id })
            return
        }
        val app = context.applicationContext
        val link = canonicalLink(id)
        executor.execute {
            val thumbPath = cacheThumbnail(app, id)
            val title = displayTitle(id)
            val entry = YoutubePlaylistEntry(id, title, link, thumbPath)
            val list = entries(app).toMutableList()
            list.add(entry)
            save(app, list)
            main.post { onDone(entry) }
        }
    }

    fun remove(context: Context, videoId: String) {
        val app = context.applicationContext
        val list = entries(app).filterNot { it.videoId == videoId }
        entryThumbFile(app, videoId).delete()
        save(app, list)
        if (TimerStore.youtubeVideoId(app) == videoId) {
            TimerStore.clearYoutubeSelection(app)
        }
    }

    fun thumbBitmap(entry: YoutubePlaylistEntry): android.graphics.Bitmap? {
        val path = entry.thumbPath ?: return null
        return runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
    }

    fun thumbBitmap(context: Context, videoId: String): android.graphics.Bitmap? {
        val fromEntry = entries(context).firstOrNull { it.videoId == videoId }?.let { thumbBitmap(it) }
        if (fromEntry != null) return fromEntry
        val file = entryThumbFile(context.applicationContext, videoId)
        return if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
    }

    private fun save(context: Context, list: List<YoutubePlaylistEntry>) {
        val array = JSONArray()
        list.forEach { array.put(it.toJson()) }
        TimerStore.prefs(context).edit().putString(PREFS_KEY, array.toString()).apply()
    }

    private fun migrateLegacyVideoSync(context: Context) {
        val prefs = TimerStore.prefs(context)
        if (prefs.getString(PREFS_KEY, null) != null) return
        val id = prefs.getString("youtube_video_id", null)?.takeIf { it.isNotBlank() } ?: return
        val app = context.applicationContext
        val thumbPath = cacheThumbnail(app, id)
        save(app, listOf(YoutubePlaylistEntry(id, displayTitle(id), canonicalLink(id), thumbPath)))
    }

    private fun cacheThumbnail(context: Context, videoId: String): String? {
        val file = entryThumbFile(context, videoId)
        file.parentFile?.mkdirs()
        return runCatching {
            URL(YouTubeLinks.thumbnailUrl(videoId)).openStream().use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            file.absolutePath
        }.getOrNull()
    }

    private fun entryThumbFile(context: Context, videoId: String): File =
        File(File(context.filesDir, DIR), "$videoId.jpg")

    private fun canonicalLink(videoId: String): String = "https://youtu.be/$videoId"

    private fun displayTitle(videoId: String): String = "YouTube · $videoId"

    private fun YoutubePlaylistEntry.toJson(): JSONObject = JSONObject().apply {
        put("videoId", videoId)
        put("title", title)
        put("link", link)
        put("thumbPath", thumbPath)
    }

    private fun entryFromJson(o: JSONObject): YoutubePlaylistEntry = YoutubePlaylistEntry(
        videoId = o.getString("videoId"),
        title = o.getString("title"),
        link = o.getString("link"),
        thumbPath = o.optString("thumbPath").takeIf { it.isNotBlank() },
    )
}
