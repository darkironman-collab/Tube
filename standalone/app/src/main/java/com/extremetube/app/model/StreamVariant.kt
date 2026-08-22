package com.extremetube.app.model

data class StreamVariant(
    val id: String,
    val videoUrl: String,
    val audioUrl: String?,
    val resolution: String,
    val width: Int,
    val height: Int,
    val fps: Int,
    val codec: String,
    val container: String,
    val videoBitrate: Int,
    val audioBitrate: Int,
    val durationSeconds: Long,
    val videoBytes: Long? = null,
    val audioBytes: Long? = null,
    val sizeExact: Boolean = false
) {
    val totalBytes: Long?
        get() {
            val video = videoBytes ?: return null
            return video + (audioBytes ?: 0L)
        }

    fun displayLabel(): String {
        val fpsLabel = if (fps > 30 && !resolution.contains(fps.toString())) "$resolution${fps}" else resolution
        val size = formatSize(totalBytes ?: estimatedBytes())
        val prefix = if (totalBytes != null && sizeExact) "" else "~"
        return "$fpsLabel · $codec · $container · $prefix$size"
    }

    private fun estimatedBytes(): Long {
        if (durationSeconds <= 0) return 0L
        val bps = videoBitrate.toLong().coerceAtLeast(0) + audioBitrate.toLong().coerceAtLeast(0)
        return (bps * durationSeconds) / 8L
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "size unknown"
        val mib = bytes / (1024.0 * 1024.0)
        val gib = mib / 1024.0
        return if (gib >= 1.0) String.format("%.2f GB", gib) else String.format("%.0f MB", mib)
    }
}
