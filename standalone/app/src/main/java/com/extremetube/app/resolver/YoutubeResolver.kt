package com.extremetube.app.resolver

import com.extremetube.app.model.ResolvedVideo
import com.extremetube.app.model.StreamVariant
import com.extremetube.app.network.StreamSizeProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream

class YoutubeResolver {
    suspend fun resolve(url: String): ResolvedVideo = withContext(Dispatchers.IO) {
        val info = StreamInfo.getInfo(url)
        val audio = chooseAudio(info.audioStreams)
        val duration = info.duration.coerceAtLeast(0L)

        val rawVideos = (info.videoOnlyStreams + info.videoStreams)
            .filter { it.isUrl && it.content.startsWith("http") }
            .distinctBy { "${it.itag}:${it.resolution}:${it.codec}:${it.content}" }
            .sortedWith(
                compareByDescending<VideoStream> { it.height }
                    .thenByDescending { it.fps }
                    .thenByDescending { normalizeBitrate(it.bitrate) }
            )

        val audioUrl = audio?.takeIf { it.isUrl }?.content
        val audioBitrate = audio?.let { normalizeBitrate(maxOf(it.bitrate, it.averageBitrate)) } ?: 0
        val knownAudioBytes = audio?.itagItem?.contentLength?.takeIf { it > 0 }
        val probedAudioBytes = if (audioUrl != null && knownAudioBytes == null) StreamSizeProbe.bytes(audioUrl) else knownAudioBytes

        val limiter = Semaphore(6)
        val variants = coroutineScope {
            rawVideos.map { stream ->
                async {
                    limiter.withPermit {
                        val needsExternalAudio = stream.isVideoOnly
                        val knownVideoBytes = stream.itagItem?.contentLength?.takeIf { it > 0 }
                        val probedVideoBytes = knownVideoBytes ?: StreamSizeProbe.bytes(stream.content)
                        val exact = probedVideoBytes != null && (!needsExternalAudio || probedAudioBytes != null)

                        StreamVariant(
                            id = stream.itag.toString(),
                            videoUrl = stream.content,
                            audioUrl = if (needsExternalAudio) audioUrl else null,
                            resolution = stream.resolution.ifBlank {
                                if (stream.height > 0) "${stream.height}p" else "Video"
                            },
                            width = stream.width,
                            height = stream.height,
                            fps = stream.fps.coerceAtLeast(0),
                            codec = codecLabel(stream.codec),
                            container = stream.format?.suffix?.uppercase() ?: "UNKNOWN",
                            videoBitrate = normalizeBitrate(stream.bitrate),
                            audioBitrate = if (needsExternalAudio) audioBitrate else 0,
                            durationSeconds = duration,
                            videoBytes = probedVideoBytes,
                            audioBytes = if (needsExternalAudio) probedAudioBytes else 0L,
                            sizeExact = exact
                        )
                    }
                }
            }.awaitAll()
        }

        ResolvedVideo(
            title = info.name,
            durationSeconds = duration,
            variants = variants
        )
    }

    private fun chooseAudio(streams: List<AudioStream>): AudioStream? = streams
        .asSequence()
        .filter { it.isUrl && it.content.startsWith("http") }
        .maxByOrNull { normalizeBitrate(maxOf(it.bitrate, it.averageBitrate)) }

    private fun normalizeBitrate(value: Int): Int {
        if (value <= 0) return 0
        return if (value < 10_000) value * 1_000 else value
    }

    private fun codecLabel(codec: String?): String {
        val c = codec.orEmpty().lowercase()
        return when {
            "av01" in c || c.startsWith("av1") -> "AV1"
            "vp09" in c || "vp9" in c -> "VP9"
            "avc1" in c || "h264" in c || "avc" in c -> "AVC/H.264"
            "hev1" in c || "hvc1" in c || "hevc" in c || "h265" in c -> "HEVC/H.265"
            c.isBlank() -> "Unknown codec"
            else -> codec.orEmpty()
        }
    }
}
