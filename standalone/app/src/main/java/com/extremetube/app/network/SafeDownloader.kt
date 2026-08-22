package com.extremetube.app.network

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Network gate for extraction. Requests to unrelated third-party hosts are rejected.
 * No analytics, telemetry, remote config or updater endpoint is present here.
 */
class SafeDownloader : Downloader() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val exactHosts = setOf(
        "youtu.be",
        "youtubei.googleapis.com",
        "jnn-pa.googleapis.com"
    )

    private val suffixHosts = setOf(
        ".youtube.com",
        ".googlevideo.com",
        ".ytimg.com",
        ".youtube-nocookie.com",
        ".googleusercontent.com"
    )

    override fun execute(request: Request): Response {
        val url = request.url()
        enforceAllowedHost(url)

        val builder = okhttp3.Request.Builder().url(url)
        request.headers().forEach { (name, values) ->
            values.forEach { value -> builder.addHeader(name, value) }
        }
        if (builder.build().header("User-Agent") == null) {
            builder.header("User-Agent", USER_AGENT)
        }

        when (request.httpMethod().uppercase()) {
            "GET" -> builder.get()
            "HEAD" -> builder.head()
            "POST" -> {
                val contentType = request.headers()["Content-Type"]?.firstOrNull()?.toMediaTypeOrNull()
                builder.post((request.dataToSend() ?: ByteArray(0)).toRequestBody(contentType))
            }
            else -> throw IOException("Blocked unsupported HTTP method: ${request.httpMethod()}")
        }

        client.newCall(builder.build()).execute().use { response ->
            val length = response.body?.contentLength() ?: 0L
            if (length > MAX_METADATA_BODY_BYTES) {
                throw IOException("Blocked oversized extractor response: $length bytes")
            }
            val headers = response.headers.toMultimap()
            val body = if (request.httpMethod().equals("HEAD", true)) "" else response.body?.string().orEmpty()
            return Response(
                response.code,
                response.message,
                headers,
                body,
                response.request.url.toString()
            )
        }
    }

    private fun enforceAllowedHost(url: String) {
        val host = try {
            URI(url).host?.lowercase()
        } catch (_: Exception) {
            null
        } ?: throw IOException("Blocked malformed URL")

        val allowed = host in exactHosts || suffixHosts.any { suffix -> host.endsWith(suffix) }
        if (!allowed) throw IOException("Blocked non-YouTube host: $host")
    }

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/131 Mobile Safari/537.36"
        private const val MAX_METADATA_BODY_BYTES = 12L * 1024L * 1024L
    }
}
