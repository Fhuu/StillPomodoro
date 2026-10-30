package com.pomodoro.timer

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Minimal HTTP client for NewPipe Extractor (audio stream URLs only). */
class YoutubeDownloader private constructor() : Downloader() {
    val client = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val url = request.url()
        val builder = okhttp3.Request.Builder().url(url)
        var hasUserAgent = false
        var hasReferer = false
        for ((key, values) in request.headers()) {
            if (key.equals("User-Agent", ignoreCase = true)) hasUserAgent = true
            if (key.equals("Referer", ignoreCase = true)) hasReferer = true
            for (value in values) {
                builder.addHeader(key, value)
            }
        }
        if (!hasUserAgent) builder.header("User-Agent", USER_AGENT)
        if (!hasReferer) builder.header("Referer", "https://www.youtube.com/")
        val body = request.dataToSend()?.toRequestBody()
        builder.method(request.httpMethod(), body)
        val response = client.newCall(builder.build()).execute()
        val responseBody = response.body?.string() ?: ""
        val headers = mutableMapOf<String, List<String>>()
        for (name in response.headers.names()) {
            headers[name] = response.headers.values(name)
        }
        return Response(response.code, response.message, headers, responseBody, response.request.url.toString())
    }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
        private val instance = YoutubeDownloader()

        fun get(): Downloader = instance

        fun httpClient(): OkHttpClient = instance.client
    }
}
