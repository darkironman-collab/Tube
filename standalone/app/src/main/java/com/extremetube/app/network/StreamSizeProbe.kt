package com.extremetube.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object StreamSizeProbe {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun bytes(url: String): Long? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", SafeDownloader.USER_AGENT)
            .header("Referer", "https://www.youtube.com/")
            .header("Range", "bytes=0-0")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val range = response.header("Content-Range")
                val totalFromRange = range?.substringAfterLast('/')?.toLongOrNull()
                if (totalFromRange != null && totalFromRange > 0) return@withContext totalFromRange

                val len = response.header("Content-Length")?.toLongOrNull()
                if (response.code == 200 && len != null && len > 1) return@withContext len
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
