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
    private val client = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val url = request.url()
        val builder = okhttp3.Request.Builder().url(url)
        for ((key, values) in request.headers()) {
            for (value in values) {
                builder.addHeader(key, value)
            }
        }
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
        private val instance = YoutubeDownloader()

        fun get(): Downloader = instance
    }
}
