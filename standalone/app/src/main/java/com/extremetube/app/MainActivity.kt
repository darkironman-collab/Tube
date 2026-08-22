package com.extremetube.app

import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.extremetube.app.model.StreamVariant
import com.extremetube.app.playback.PlaybackService
import com.extremetube.app.resolver.YoutubeResolver
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val resolver = YoutubeResolver()
    private var variants: List<StreamVariant> = emptyList()
    private var currentTitle: String = "Extreme Tube"
    private lateinit var playerView: PlayerView
    private lateinit var urlInput: EditText
    private lateinit var qualityButton: Button
    private lateinit var status: TextView
    private lateinit var controllerFuture: ListenableFuture<MediaController>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        connectController()

        intent?.dataString?.let {
            urlInput.setText(it)
            resolve(it)
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }

        playerView = PlayerView(this).apply {
            useController = true
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(230)
            )
        }
        root.addView(playerView)

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(6))
        }

        urlInput = EditText(this).apply {
            hint = "Paste YouTube link"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setSingleLine(true)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val loadButton = Button(this).apply {
            text = "Load"
            setOnClickListener {
                val url = urlInput.text.toString().trim()
                if (url.isNotEmpty()) resolve(url)
            }
        }
        inputRow.addView(urlInput)
        inputRow.addView(loadButton)
        root.addView(inputRow)

        qualityButton = Button(this).apply {
            text = "Quality · codec · size"
            isEnabled = false
            setOnClickListener { showQualityDialog() }
        }
        root.addView(qualityButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(dp(12), dp(6), dp(12), dp(6)) })

        status = TextView(this).apply {
            text = "Independent Extreme Tube player"
            setTextColor(Color.LTGRAY)
            setPadding(dp(16), dp(8), dp(16), dp(16))
        }
        root.addView(status)

        setContentView(root)
    }

    private fun connectController() {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
        controllerFuture.addListener({
            try {
                playerView.player = controllerFuture.get()
            } catch (_: Exception) {
                runOnUiThread { status.text = "Player service connection failed" }
            }
        }, MoreExecutors.directExecutor())
    }

    private fun resolve(url: String) {
        qualityButton.isEnabled = false
        status.text = "Loading formats…"
        lifecycleScope.launch {
            try {
                val result = resolver.resolve(url)
                currentTitle = result.title
                variants = result.variants
                status.text = "${result.title}\n${variants.size} codec/quality variants"
                qualityButton.isEnabled = variants.isNotEmpty()

                val default = variants.firstOrNull { it.height in 720..1080 }
                    ?: variants.firstOrNull()
                if (default != null) play(default)
            } catch (t: Throwable) {
                variants = emptyList()
                qualityButton.isEnabled = false
                status.text = "Could not resolve video: ${t.message ?: t.javaClass.simpleName}"
            }
        }
    }

    private fun showQualityDialog() {
        if (variants.isEmpty()) return
        val labels = variants.map { it.displayLabel() }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Video quality · codec · size")
            .setItems(labels) { dialog, which ->
                variants.getOrNull(which)?.let { play(it) }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun play(variant: StreamVariant) {
        qualityButton.text = variant.displayLabel()
        val intent = Intent(this, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_PLAY_VARIANT
            putExtra(PlaybackService.EXTRA_VIDEO_URL, variant.videoUrl)
            putExtra(PlaybackService.EXTRA_AUDIO_URL, variant.audioUrl)
            putExtra(PlaybackService.EXTRA_TITLE, currentTitle)
        }
        startService(intent)
    }

    override fun onDestroy() {
        playerView.player = null
        if (::controllerFuture.isInitialized) {
            MediaController.releaseFuture(controllerFuture)
        }
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
