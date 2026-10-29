package com.asta.cherryoffice

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PresentationInstrumentedTest {
    @Test fun picturesChartsAndVerticalScrollOnPhysicalDevice() {
        assertFalse("Use a physical device only", android.os.Build.FINGERPRINT.contains("generic"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val file = File(context.cacheDir, "slide-preview.pptx")
        instrumentation.context.assets.open(file.name).use { input -> file.outputStream().use(input::copyTo) }
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW; data = Uri.fromFile(file); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        fun find(view: View): WebView? {
            if (view is WebView) return view
            if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
            return null
        }
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            var web: WebView? = null
            val deadline = SystemClock.uptimeMillis() + 15000
            while (web == null && SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity { web = find(it.window.decorView) }
                if (web == null) SystemClock.sleep(100)
            }
            val view = requireNotNull(web) { "Slide viewer did not appear" }
            SystemClock.sleep(1800)
            val directory = File(context.getExternalFilesDir(null), "verification").apply { mkdirs() }
            val shot = instrumentation.uiAutomation.takeScreenshot()
            var imagePixels = 0; var chartPixels = 0
            for (y in 0 until shot.height step 3) for (x in 0 until shot.width step 3) {
                val c = shot.getPixel(x,y)
                if (Color.red(c) in 200..220 && Color.green(c) in 35..55 && Color.blue(c) in 80..100) imagePixels++
                if (Color.red(c) in 58..78 && Color.green(c) in 104..124 && Color.blue(c) in 186..206) chartPixels++
            }
            File(directory,"presentation-top.png").outputStream().use { shot.compress(Bitmap.CompressFormat.PNG,100,it) }; shot.recycle()
            assertTrue("Embedded picture must be visibly rendered", imagePixels > 80)
            assertTrue("Chart bars must be visibly rendered", chartPixels > 80)
            var before = 0
            instrumentation.runOnMainSync { before = view.scrollY; view.scrollBy(0, view.height) }
            SystemClock.sleep(400)
            instrumentation.runOnMainSync { assertTrue("Slides must scroll vertically", view.scrollY > before); assertFalse(view.settings.javaScriptEnabled) }
            val scrolled = instrumentation.uiAutomation.takeScreenshot()
            File(directory,"presentation-scrolled.png").outputStream().use { scrolled.compress(Bitmap.CompressFormat.PNG,100,it) }; scrolled.recycle()
        }
    }
    @Test fun multilineMixedSizesAndBottomAlignedTextStayInsideTheirBoxes() {
        assertFalse("Use a physical device only", android.os.Build.FINGERPRINT.contains("generic"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val file = File(context.cacheDir, "text-clipping.pptx")
        instrumentation.context.assets.open(file.name).use { input -> file.outputStream().use(input::copyTo) }
        fun find(view: View): WebView? {
            if (view is WebView) return view
            if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
            return null
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW; data = Uri.fromFile(file); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            var web: WebView? = null
            val deadline = SystemClock.uptimeMillis() + 15000
            while (web == null && SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity { web = find(it.window.decorView) }
                if (web == null) SystemClock.sleep(100)
            }
            val view = requireNotNull(web)
            SystemClock.sleep(1500)
            var result = ""
            val latch = java.util.concurrent.CountDownLatch(1)
            instrumentation.runOnMainSync {
                assertFalse(view.settings.javaScriptEnabled)
                assertEquals(100, view.settings.textZoom)
                view.settings.javaScriptEnabled = true
                view.evaluateJavascript("""
                    (() => {
                      const boxes = Array.from(document.querySelectorAll('[data-slide-text]'));
                      return {
                        count: boxes.length,
                        shrunk: boxes.filter(b => Number(b.dataset.fitScale) < 1).length,
                        clipped: boxes.filter(b => {
                          const text = b.querySelector('[data-slide-content]').getBoundingClientRect();
                          const frame = b.getBoundingClientRect();
                          return text.top < frame.top - 1 || text.bottom > frame.bottom + 1 ||
                            text.left < frame.left - 1 || text.right > frame.right + 1;
                        }).length
                      };
                    })()
                """.trimIndent()) {
                    result = it; view.settings.javaScriptEnabled = false; latch.countDown()
                }
            }
            assertTrue(latch.await(5, java.util.concurrent.TimeUnit.SECONDS))
            val metrics = org.json.JSONObject(result)
            assertEquals(6, metrics.getInt("count"))
            assertTrue("Tight boxes must shrink to fit", metrics.getInt("shrunk") >= 4)
            assertEquals("No text box should clip the last line", 0, metrics.getInt("clipped"))
            val shot = instrumentation.uiAutomation.takeScreenshot()
            val directory = File(context.getExternalFilesDir(null), "verification").apply { mkdirs() }
            File(directory,"presentation-text-fit.png").outputStream().use { shot.compress(Bitmap.CompressFormat.PNG,100,it) }
            shot.recycle()
        }
    }

}
