package com.pomodoro.update

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

internal data class ReleaseApk(
    val tag: String,
    val version: ReleaseVersion,
    val url: String,
    val size: Long,
    val sha256: String?,
)

internal class GitHubReleaseClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.MINUTES)
        .followSslRedirects(false)
        .build(),
) {
    fun check(currentVersion: String): ReleaseApk? {
        val request = Request.Builder().url(RELEASES_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "StillPomodoro-UpdateCheck")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException(if (response.code == 403 || response.code == 429) {
                "GitHub is limiting update checks. Try again later."
            } else "Couldn't check GitHub releases. Try again later.")
            val source = response.body?.source()
                ?: throw IOException("GitHub returned an empty response.")
            if (source.request(MAX_METADATA_BYTES + 1)) throw IOException("The release response is too large.")
            return select(source.readUtf8(), currentVersion)
        }
    }

    fun select(json: String, currentVersion: String): ReleaseApk? {
        val current = ReleaseVersion.parse(currentVersion) ?: throw IOException("Couldn't read the installed version.")
        val releases = JSONArray(json)
        return (0 until releases.length()).mapNotNull { index ->
            val release = releases.optJSONObject(index) ?: return@mapNotNull null
            if (release.optBoolean("draft")) return@mapNotNull null
            val tag = release.optString("tag_name")
            val version = ReleaseVersion.parse(tag) ?: return@mapNotNull null
            if (version <= current) return@mapNotNull null
            val assets = release.optJSONArray("assets") ?: return@mapNotNull null
            val apks = (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }.filter {
                it.optString("name").endsWith(".apk", ignoreCase = true) && it.optLong("size") in 1..MAX_APK_BYTES
            }
            val asset = apks.singleOrNull() ?: apks.filter { it.optString("name").contains("universal", ignoreCase = true) }.singleOrNull()
                ?: return@mapNotNull null
            val url = asset.optString("browser_download_url")
            if (!isReleaseAsset(url, tag)) return@mapNotNull null
            val digest = asset.optString("digest").takeIf { it.isNotEmpty() && it != "null" }
            if (digest != null && !Regex("sha256:[a-fA-F0-9]{64}").matches(digest)) return@mapNotNull null
            ReleaseApk(tag, version, url, asset.getLong("size"), digest?.substringAfter(':')?.lowercase())
        }.maxByOrNull { it.version }
    }

    fun download(release: ReleaseApk, directory: File, progress: (Int) -> Unit): File {
        if (!isReleaseAsset(release.url, release.tag) || release.size !in 1..MAX_APK_BYTES) throw IOException("Invalid update download.")
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Couldn't create the update folder.")
        val partial = File(directory, "download.part")
        val destination = File.createTempFile("update-", ".apk", directory)
        var complete = false
        try {
            val request = Request.Builder().url(release.url).header("User-Agent", "StillPomodoro-UpdateDownload").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Couldn't download the update. Try again.")
                val body = response.body ?: throw IOException("The update download is empty.")
                val digest = MessageDigest.getInstance("SHA-256")
                var received = 0L
                var lastPercent = -1
                body.byteStream().use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            received += count
                            if (received > release.size || received > MAX_APK_BYTES) throw IOException("The update size doesn't match the release.")
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                            val percent = (received * 100 / release.size).toInt()
                            if (percent != lastPercent) {
                                progress(percent)
                                lastPercent = percent
                            }
                        }
                    }
                }
                if (received != release.size) throw IOException("The update download was incomplete. Try again.")
                val actualDigest = digest.digest().joinToString("") { "%02x".format(it) }
                if (release.sha256 != null && actualDigest != release.sha256) throw IOException("The update couldn't be verified. Try downloading it again.")
            }
            Files.move(partial.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            complete = true
            return destination
        } finally {
            partial.delete()
            if (!complete) destination.delete()
        }
    }

    private fun isReleaseAsset(value: String, tag: String): Boolean {
        val url = value.toHttpUrlOrNull() ?: return false
        return url.isHttps && url.host == "github.com" && url.username.isEmpty() && url.password.isEmpty() &&
            url.pathSegments.take(4) == listOf("Fhuu", "StillPomodoro", "releases", "download") &&
            url.pathSegments.getOrNull(4) == tag && url.pathSegments.size == 6
    }

    companion object {
        const val RELEASES_URL = "https://api.github.com/repos/Fhuu/StillPomodoro/releases?per_page=100"
        private const val MAX_APK_BYTES = 150L * 1024 * 1024
        private const val MAX_METADATA_BYTES = 2L * 1024 * 1024
    }
}
