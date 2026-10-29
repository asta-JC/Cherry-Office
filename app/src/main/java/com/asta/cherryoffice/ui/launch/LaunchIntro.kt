package com.asta.cherryoffice.ui.launch

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.asta.cherryoffice.R

val LaunchRed = Color(0xFFDF2048)

@Composable
fun LaunchIntro(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val finished = rememberUpdatedState(onFinished)
    val owner = LocalLifecycleOwner.current
    BackHandler { finished.value() }
    Box(modifier.fillMaxSize().background(LaunchRed)) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
            LaunchVideoView(context, owner) { finished.value() }
        }, onRelease = { it.release() })
        TextButton(onClick = { finished.value() }, modifier = Modifier.align(Alignment.TopEnd)
            .statusBarsPadding().padding(12.dp)) {
            Text("Skip", color = Color.White.copy(alpha = .9f))
        }
    }
}

/** One hardware-decoded video surface; no bitmap frames, audio, network, or player dependency. */
class LaunchVideoView(context: Context, private val owner: LifecycleOwner, private val onFinished: () -> Unit) :
    TextureView(context), TextureView.SurfaceTextureListener, DefaultLifecycleObserver {
    private var player: MediaPlayer? = null
    private var outputSurface: Surface? = null
    private var prepared = false
    private var finished = false
    private var videoWidth = 720
    private var videoHeight = 1280
    var hasRenderedFrame = false
        private set
    var isReleased = false
        private set
    private val watchdog = Runnable { finish() }

    init {
        contentDescription = "CherryApp launch animation"
        isOpaque = false
        alpha = 0f
        surfaceTextureListener = this
        owner.lifecycle.addObserver(this)
        postDelayed(watchdog, 5000)
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        if (isReleased || finished) return
        try {
            outputSurface = Surface(texture)
            val media = MediaPlayer()
            player = media
            media.also {
                media.setSurface(outputSurface)
                media.setVolume(0f, 0f)
                media.isLooping = false
                media.setOnVideoSizeChangedListener { _, w, h ->
                    videoWidth = w.coerceAtLeast(1); videoHeight = h.coerceAtLeast(1); fitVideo()
                }
                media.setOnInfoListener { _, what, _ ->
                    if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                        hasRenderedFrame = true
                        alpha = 1f
                    }
                    false
                }
                media.setOnPreparedListener {
                    if (!finished && !isReleased) {
                        prepared = true
                        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) it.start()
                    }
                }
                media.setOnCompletionListener { finish() }
                media.setOnErrorListener { _, _, _ -> prepared = false; finish(); true }
                resources.openRawResourceFd(R.raw.cherry_launch).use { descriptor ->
                    media.setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
                }
                media.prepareAsync()
            }
        } catch (_: Exception) { finish() }
    }

    private fun fitVideo() {
        if (width == 0 || height == 0) return
        // Fit the original frame inside the screen; do not crop/enlarge it to fill taller phones.
        val scale = minOf(width.toFloat() / videoWidth, height.toFloat() / videoHeight)
        setTransform(Matrix().apply {
            setScale(videoWidth * scale / width, videoHeight * scale / height, width / 2f, height / 2f)
        })
    }

    private fun finish() {
        if (finished || isReleased) return
        finished = true
        removeCallbacks(watchdog)
        runCatching { if (prepared) player?.pause() }
        // Keep the final decoded frame during Compose's short fade, then release on removal.
        onFinished()
    }

    override fun onResume(owner: LifecycleOwner) {
        if (prepared && !finished && !isReleased) player?.start()
    }
    override fun onPause(owner: LifecycleOwner) {
        if (prepared && !finished && !isReleased) player?.pause()
    }
    override fun onStop(owner: LifecycleOwner) { finish() }
    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) { fitVideo() }
    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        finish()
        release()
        return true
    }

    fun release() {
        if (isReleased) return
        isReleased = true
        removeCallbacks(watchdog)
        owner.lifecycle.removeObserver(this)
        player?.apply {
            setOnPreparedListener(null); setOnCompletionListener(null); setOnErrorListener(null)
            setOnInfoListener(null); setOnVideoSizeChangedListener(null)
            release()
        }
        player = null
        outputSurface?.release()
        outputSurface = null
    }
}
