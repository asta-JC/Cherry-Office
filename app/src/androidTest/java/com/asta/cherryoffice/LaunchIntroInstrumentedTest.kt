package com.asta.cherryoffice

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.asta.cherryoffice.ui.launch.LaunchVideoView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LaunchIntroInstrumentedTest {
    private fun find(view: View): LaunchVideoView? {
        if (view is LaunchVideoView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
        return null
    }

    @Test fun realVideoPlaysThenReleasesWithoutDelayingExternalDocumentsOrReplaying() {
        assertFalse("Physical device required", android.os.Build.FINGERPRINT.contains("generic"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        context.resources.openRawResourceFd(R.raw.cherry_launch).use { descriptor ->
            val metadata = MediaMetadataRetriever()
            try {
                metadata.setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
                assertNull("Clip must contain no audio track", metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
                assertTrue(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong() in 2700..2900)
                assertEquals("720", metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH))
                assertEquals("1280", metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT))
            } finally { metadata.release() }
        }
        val document = File(context.cacheDir, "intro-document.txt").apply { writeText("Open this document immediately.") }
        val external = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW; data = Uri.fromFile(document); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ActivityScenario.launch<MainActivity>(external).use { scenario ->
            SystemClock.sleep(400)
            scenario.onActivity { assertNull("External document launch must skip the intro", find(it.window.decorView)) }
        }
        val launcher = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN; addCategory(Intent.CATEGORY_LAUNCHER); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ActivityScenario.launch<MainActivity>(launcher).use { scenario ->
            var video: LaunchVideoView? = null
            val deadline = SystemClock.uptimeMillis() + 2500
            while (video == null && SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity { activity -> video = find(activity.window.decorView)?.takeIf { it.hasRenderedFrame } }
                if (video == null) SystemClock.sleep(40)
            }
            val surface = requireNotNull(video) { "Launch animation did not render a video frame" }
            scenario.onActivity {
                val values = FloatArray(9)
                surface.getTransform(Matrix()).getValues(values)
                val displayedWidth = surface.width * values[Matrix.MSCALE_X]
                val displayedHeight = surface.height * values[Matrix.MSCALE_Y]
                assertTrue("Video must fit without cropping the sides", displayedWidth <= surface.width + 1)
                assertTrue("Video must fit without cropping the top/bottom", displayedHeight <= surface.height + 1)
                assertEquals("Video aspect ratio must stay original", 720f / 1280f, displayedWidth / displayedHeight, .01f)
            }
            SystemClock.sleep(1800)
            val directory = File(context.getExternalFilesDir(null), "verification").apply { mkdirs() }
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            File(directory, "launch-video.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG,100,it) }
            screenshot.recycle()
            SystemClock.sleep(1500)
            scenario.onActivity {
                assertNull("Intro must finish automatically", find(it.window.decorView))
                assertTrue("Video decoder must be released after the intro", surface.isReleased)
            }
            scenario.recreate()
            SystemClock.sleep(300)
            scenario.onActivity { assertNull("Recreation must not replay the intro", find(it.window.decorView)) }
        }
        ActivityScenario.launch<MainActivity>(launcher).use { scenario ->
            SystemClock.sleep(300)
            scenario.onActivity { assertNull("Warm launch must not replay the intro", find(it.window.decorView)) }
        }
    }
}
