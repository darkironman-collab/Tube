package com.extremetube.app.playback

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.extremetube.app.network.SafeDownloader

class PlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(AudioAttributes.DEFAULT, true)
            setWakeMode(C.WAKE_MODE_NETWORK)
            repeatMode = Player.REPEAT_MODE_OFF
        }
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_PLAY_VARIANT) {
            val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL)
            if (!videoUrl.isNullOrBlank()) {
                playVariant(
                    videoUrl = videoUrl,
                    audioUrl = intent.getStringExtra(EXTRA_AUDIO_URL),
                    title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
                )
            }
            return START_STICKY
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun playVariant(videoUrl: String, audioUrl: String?, title: String) {
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(SafeDownloader.USER_AGENT)
            .setDefaultRequestProperties(
                mapOf(
                    "Referer" to "https://www.youtube.com/",
                    "Origin" to "https://www.youtube.com"
                )
            )

        val factory = ProgressiveMediaSource.Factory(dataSourceFactory)
        val metadata = MediaMetadata.Builder().setTitle(title.ifBlank { "Extreme Tube" }).build()
        val videoItem = MediaItem.Builder()
            .setUri(videoUrl)
            .setMediaMetadata(metadata)
            .build()
        val videoSource = factory.createMediaSource(videoItem)

        val finalSource: MediaSource = if (!audioUrl.isNullOrBlank()) {
            val audioSource = factory.createMediaSource(MediaItem.fromUri(audioUrl))
            MergingMediaSource(videoSource, audioSource)
        } else {
            videoSource
        }

        player.setMediaSource(finalSource)
        player.prepare()
        player.playWhenReady = true
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Playback intentionally continues after the UI task is dismissed.
    }

    override fun onDestroy() {
        mediaSession.release()
        player.release()
        super.onDestroy()
    }

    companion object {
        const val ACTION_PLAY_VARIANT = "com.extremetube.app.PLAY_VARIANT"
        const val EXTRA_VIDEO_URL = "video_url"
        const val EXTRA_AUDIO_URL = "audio_url"
        const val EXTRA_TITLE = "title"
    }
}
