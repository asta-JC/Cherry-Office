package com.asta.cherryoffice

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.asta.cherryoffice.data.repository.SettingsRepository
import com.asta.cherryoffice.excel.OfficeSpreadsheetReader
import com.asta.cherryoffice.pdf.*
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import kotlinx.coroutines.runBlocking
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ReadingInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun fixture(rotation: Int = 0): File {
        PDFBoxResourceLoader.init(context)
        val file = File(context.cacheDir, "selection-$rotation.pdf")
        PDDocument().use { doc ->
            repeat(2) { index ->
                val page = PDPage(PDRectangle.LETTER).apply { this.rotation = rotation }
                doc.addPage(page)
                PDPageContentStream(doc, page).use { out ->
                    out.beginText(); out.setFont(PDType1Font.HELVETICA, 18f); out.newLineAtOffset(72f, 720f)
                    out.showText(if (index == 0) "Hello Cherry Office" else "Second page selectable text")
                    out.endText()
                    val image = Bitmap.createBitmap(80, 50, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(230, 40, 70)) }
                    out.drawImage(LosslessFactory.createFromImage(doc, image), 72f, 600f, 80f, 50f)
                    image.recycle()
                }
            }
            doc.save(file)
        }
        return file
    }
    @Test fun pdfTextExtractionRotationsScansAndCopyPermissions() {
        listOf(0, 90, 180, 270).forEach { rotation ->
            PdfBoxTextReader(fixture(rotation)).use { reader ->
                val page = reader.page(0)
                assertTrue(page.text.contains("Hello Cherry Office"))
                assertTrue(page.glyphs.isNotEmpty())
                val first = page.glyphs.first()
                assertTrue(first.right > first.left && first.bottom > first.top)
                when (rotation) {
                    0 -> { assertEquals(72f, first.left, 2f); assertTrue(first.top in 50f..73f) }
                    90 -> { assertTrue(first.left in 715f..725f); assertEquals(72f, first.top, 2f) }
                    180 -> { assertTrue(first.right in 535f..545f); assertEquals(720f, first.top, 2f) }
                    270 -> { assertTrue(first.right in 68f..75f); assertTrue(first.top in 515f..540f) }
                }
            }
        }
        val scanned = File(context.cacheDir, "scan.pdf")
        PDDocument().use { it.addPage(PDPage()); it.save(scanned) }
        PdfBoxTextReader(scanned).use { assertTrue(it.page(0).glyphs.isEmpty()); assertNotNull(it.page(0).message) }
        val restricted = File(context.cacheDir, "restricted.pdf")
        PDDocument.load(fixture()).use { doc ->
            val permission = com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission().apply { setCanExtractContent(false) }
            doc.protect(com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy("owner-secret", "", permission))
            doc.save(restricted)
        }
        PdfBoxTextReader(restricted).use { assertFalse(it.page(0).copyAllowed) }
    }
    @Test fun xlsReaderRunsOnAndroidWithoutDesktopApis() {
        val file = File(context.cacheDir, "android-compat.xls")
        HSSFWorkbook().use { book ->
            val sheet = book.createSheet("Android XLS")
            sheet.createRow(0).createCell(0).setCellValue("Real workbook")
            file.outputStream().use(book::write)
        }
        file.inputStream().use { assertEquals("Real workbook", OfficeSpreadsheetReader.read(it, file.name).sheets.single().rows.single().cells.single().value) }
    }
    private fun find(view: View, test: (View) -> Boolean): View? {
        if (test(view)) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i), test)?.let { return it }
        return null
    }
    private fun waitForPdf(scenario: ActivityScenario<MainActivity>): PdfReadingView {
        var result: PdfReadingView? = null
        val deadline = SystemClock.uptimeMillis() + 12000
        while (result == null && SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { result = find(it.window.decorView) { view -> view is PdfReadingView } as? PdfReadingView }
            if (result == null) SystemClock.sleep(100)
        }
        return requireNotNull(result) { "PDF surface did not appear" }
    }
    private fun screenshot(name: String) {
        instrumentation.waitForIdleSync()
        SystemClock.sleep(120)
        val directory = File(context.getExternalFilesDir(null), "verification").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot().let { image ->
            File(directory, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }; image.recycle()
        }
    }
    @Test fun longPressSelectionCopiesAndPreservesPageColorsAcrossThemes() {
        val file = fixture()
        val settings = SettingsRepository(context)
        runBlocking { settings.setThemeMode("DARK"); settings.setDefaultZoom(1f) }
        val intent = Intent(context, MainActivity::class.java).apply { action = Intent.ACTION_VIEW; data = Uri.fromFile(file); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            val view = waitForPdf(scenario)
            SystemClock.sleep(2500)
            screenshot("pdf-dark")
            var viewWidth = 0
            instrumentation.runOnMainSync { viewWidth = view.width }
            val d = context.resources.displayMetrics.density
            val pageWidth = viewWidth - 24 * d
            val location = IntArray(2)
            instrumentation.runOnMainSync { view.getLocationOnScreen(location) }
            val photoX = (location[0] + 12 * d + 100f / 612 * pageWidth).toInt()
            val photoY = (location[1] + 12 * d + 160f / 612 * pageWidth).toInt()
            val darkShot = instrumentation.uiAutomation.takeScreenshot()
            val photoBefore = darkShot.getPixel(photoX, photoY); darkShot.recycle()
            val textX = 12 * d + 86f / 612f * pageWidth
            val textY = 12 * d + 65f / 612f * pageWidth
            instrumentation.runOnMainSync {
                val now = SystemClock.uptimeMillis()
                view.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, textX, textY, 0))
            }
            SystemClock.sleep(800)
            instrumentation.runOnMainSync {
                val now = SystemClock.uptimeMillis()
                view.dispatchTouchEvent(MotionEvent.obtain(now - 800, now, MotionEvent.ACTION_UP, textX, textY, 0))
            }
            SystemClock.sleep(500)
            screenshot("pdf-selection")
            // Drag the end handle to expand the same selection across the line.
            val textPage = PdfBoxTextReader(file).use { it.page(0) }
            val lastHello = textPage.glyphs.last { it.end <= 5 }
            val lastLine = textPage.glyphs.last()
            val factor = pageWidth / 612f
            val startX = 12 * d + lastHello.right * factor
            val startY = 12 * d + lastHello.bottom * factor + 8 * d
            val endX = 12 * d + lastLine.right * factor + 3
            instrumentation.runOnMainSync {
                val now = SystemClock.uptimeMillis()
                view.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, startX, startY, 0))
                view.dispatchTouchEvent(MotionEvent.obtain(now, now + 80, MotionEvent.ACTION_MOVE, endX, startY, 0))
                view.dispatchTouchEvent(MotionEvent.obtain(now, now + 100, MotionEvent.ACTION_UP, endX, startY, 0))
            }
            screenshot("pdf-expanded-selection")
            instrumentation.runOnMainSync { assertTrue(view.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_COPY, null)) }
            SystemClock.sleep(800)
            var copied = ""
            scenario.onActivity { activity -> copied = (activity.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).primaryClip?.getItemAt(0)?.text?.toString().orEmpty() }
            assertEquals("Hello Cherry Office", copied)
            runBlocking { settings.setThemeMode("LIGHT") }
            SystemClock.sleep(800); screenshot("pdf-light")
            val lightShot = instrumentation.uiAutomation.takeScreenshot()
            assertEquals("Pictures must have identical pixels in both themes", photoBefore, lightShot.getPixel(photoX, photoY))
            lightShot.recycle()
            // Appearance must not alter the session's original page bitmap or images.
            PdfSession(context, Uri.fromFile(file)).use { session ->
                val image = session.render(0, 612)
                assertEquals(Color.rgb(230, 40, 70), image.getPixel(100, 160))
            }
        }
    }
    private fun clickDescription(description: String) {
        fun findNode(node: android.view.accessibility.AccessibilityNodeInfo?): android.view.accessibility.AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.contentDescription?.toString()?.equals(description, true) == true || node.text?.toString()?.equals(description, true) == true) return node
            for (i in 0 until node.childCount) findNode(node.getChild(i))?.let { return it }
            return null
        }
        val automation = instrumentation.uiAutomation
        val service = automation.serviceInfo
        service.flags = service.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo = service
        var candidate: android.view.accessibility.AccessibilityNodeInfo? = null
        val deadline = SystemClock.uptimeMillis() + 2500
        while (candidate == null && SystemClock.uptimeMillis() < deadline) {
            candidate = findNode(automation.rootInActiveWindow)
                ?: automation.windows.firstNotNullOfOrNull { findNode(it.root) }
            if (candidate == null) SystemClock.sleep(100)
        }
        val node = candidate ?: error("Missing accessibility action: $description")
        var clickable: android.view.accessibility.AccessibilityNodeInfo? = node
        while (clickable != null && !clickable.isClickable) clickable = clickable.parent
        if (clickable != null) assertTrue(clickable.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
        else {
            val rect = android.graphics.Rect(); node.getBoundsInScreen(rect)
            val now = SystemClock.uptimeMillis()
            instrumentation.sendPointerSync(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, rect.centerX().toFloat(), rect.centerY().toFloat(), 0))
            instrumentation.sendPointerSync(MotionEvent.obtain(now, now + 80, MotionEvent.ACTION_UP, rect.centerX().toFloat(), rect.centerY().toFloat(), 0))
        }
        instrumentation.waitForIdleSync()
    }
    @Test fun captureHomeAndSettingsAcrossAppearanceModes() {
        val settings = SettingsRepository(context)
        runBlocking { settings.setThemeMode("DARK") }
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)).use { scenario ->
            SystemClock.sleep(1200)
            listOf("DARK", "LIGHT", "AMOLED", "SYSTEM").forEach { mode ->
                runBlocking { settings.setThemeMode(mode) }
                SystemClock.sleep(500); screenshot("home-${mode.lowercase()}")
            }
            clickDescription("Settings")
            listOf("DARK", "LIGHT", "AMOLED", "SYSTEM").forEach { mode ->
                runBlocking { settings.setThemeMode(mode) }
                SystemClock.sleep(500); screenshot("settings-${mode.lowercase()}")
            }
        }
    }

    @Test fun multiPageCopyAndCropCoordinates() {
        val file = fixture()
        PdfSession(context, Uri.fromFile(file)).use { session ->
            val first = session.extract(0)
            val second = session.extract(1)
            val copied = session.copy(PdfSelection(PdfTextPoint(0, 0), PdfTextPoint(1, second.text.trimEnd().length)))
            assertTrue(copied.startsWith("Hello Cherry Office"))
            assertTrue(copied.endsWith("Second page selectable text"))
        }
        val crop = File(context.cacheDir, "crop.pdf")
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle.LETTER).apply { cropBox = PDRectangle(20f, 40f, 400f, 600f) }
            doc.addPage(page)
            PDPageContentStream(doc, page).use { out ->
                out.beginText(); out.setFont(PDType1Font.HELVETICA, 18f); out.newLineAtOffset(70f, 540f); out.showText("Crop text"); out.endText()
            }
            doc.save(crop)
        }
        PdfBoxTextReader(crop).use { reader ->
            val glyph = reader.page(0).glyphs.first()
            assertEquals(50f, glyph.left, 2f)
            assertTrue(glyph.top in 80f..102f)
        }
    }

    @Test fun wordUsesNativeLongPressSelectionAndCopy() {
        val file = File(context.cacheDir, "word-selection.docx")
        instrumentation.context.assets.open("reading-sample.docx").use { input -> file.outputStream().use(input::copyTo) }
        runBlocking { SettingsRepository(context).setThemeMode("DARK") }
        val intent = Intent(context, MainActivity::class.java).apply { action = Intent.ACTION_VIEW; data = Uri.fromFile(file); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            var web: android.webkit.WebView? = null
            val deadline = SystemClock.uptimeMillis() + 12000
            while (web == null && SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity { web = find(it.window.decorView) { view -> view is android.webkit.WebView } as? android.webkit.WebView }
                if (web == null) SystemClock.sleep(100)
            }
            val view = requireNotNull(web)
            SystemClock.sleep(1800)
            fun evaluate(script: String): String {
                val latch = java.util.concurrent.CountDownLatch(1)
                var result = "null"
                instrumentation.runOnMainSync { view.evaluateJavascript(script) { result = it; latch.countDown() } }
                assertTrue(latch.await(3, java.util.concurrent.TimeUnit.SECONDS))
                return result
            }
            val coords = org.json.JSONArray(evaluate("(function(){var p=document.querySelector('#pages .sheet-body p');var w=document.createTreeWalker(p,NodeFilter.SHOW_TEXT);var n=w.nextNode();while(n&&!n.textContent.trim())n=w.nextNode();var range=document.createRange();range.setStart(n,0);range.setEnd(n,Math.min(n.textContent.length,4));var r=range.getBoundingClientRect();return [r.left+r.width/2,r.top+r.height/2];})()"))
            var scale = 1f
            instrumentation.runOnMainSync { @Suppress("DEPRECATION") run { scale = view.scale } }
            val x = coords.getDouble(0).toFloat() * scale
            val y = coords.getDouble(1).toFloat() * scale
            val location = IntArray(2)
            instrumentation.runOnMainSync { view.getLocationOnScreen(location); view.requestFocus() }
            val now = SystemClock.uptimeMillis()
            fun touch(action: Int) {
                val properties = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER })
                val coordinates = arrayOf(MotionEvent.PointerCoords().apply { this.x = x + location[0]; this.y = y + location[1]; pressure = 1f; size = .1f })
                val event = MotionEvent.obtain(now, SystemClock.uptimeMillis(), action, 1, properties, coordinates, 0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0)
                instrumentation.sendPointerSync(event); event.recycle()
            }
            touch(MotionEvent.ACTION_DOWN)
            SystemClock.sleep(android.view.ViewConfiguration.getLongPressTimeout().toLong() + 400)
            touch(MotionEvent.ACTION_UP)
            SystemClock.sleep(500)
            val selected = org.json.JSONTokener(evaluate("window.getSelection().toString()")).nextValue().toString()
            screenshot("word-selection")
            assertTrue("Word text must be selectable by long press at $x,$y (scale $scale)", selected.isNotBlank())
            clickDescription("Copy")
            var copied = ""
            val copyDeadline = SystemClock.uptimeMillis() + 2500
            while (copied != selected && SystemClock.uptimeMillis() < copyDeadline) {
                scenario.onActivity { activity -> copied = (activity.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).primaryClip?.getItemAt(0)?.text?.toString().orEmpty() }
                if (copied != selected) SystemClock.sleep(100)
            }
            assertEquals(selected, copied)
        }
    }

    @Test fun movingPinchKeepsTheSameWordUnderTheFingers() {
        val file = fixture()
        runBlocking { SettingsRepository(context).setDefaultZoom(1f) }
        val intent = Intent(context, MainActivity::class.java).apply { action = Intent.ACTION_VIEW; data = Uri.fromFile(file); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            val view = waitForPdf(scenario)
            SystemClock.sleep(1800)
            val glyph = PdfBoxTextReader(file).use { it.page(0).glyphs.first { glyph -> glyph.start == 6 } }
            var w = 0
            instrumentation.runOnMainSync { w = view.width }
            val gap = 12 * context.resources.displayMetrics.density
            val factor = (w - 2 * gap) / 612f
            val cx = gap + (glyph.left + glyph.right) / 2 * factor
            val cy = gap + (glyph.top + glyph.bottom) / 2 * factor
            val down = SystemClock.uptimeMillis()
            fun event(action: Int, count: Int, span: Float, dx: Float, dy: Float): MotionEvent {
                val props = Array(count) { i -> MotionEvent.PointerProperties().apply { id = i; toolType = MotionEvent.TOOL_TYPE_FINGER } }
                val coords = Array(count) { i -> MotionEvent.PointerCoords().apply {
                    x = cx + dx + if (i == 0) -span / 2 else span / 2; y = cy + dy; pressure = 1f; size = .1f
                } }
                return MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, count, props, coords, 0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0)
            }
            instrumentation.runOnMainSync {
                event(MotionEvent.ACTION_DOWN, 1, 160f, 0f, 0f).also { view.dispatchTouchEvent(it); it.recycle() }
                event(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, 160f, 0f, 0f).also { view.dispatchTouchEvent(it); it.recycle() }
            }
            for (i in 1..12) {
                instrumentation.runOnMainSync { event(MotionEvent.ACTION_MOVE, 2, 160f + i * 16f, i * 2f, i.toFloat()).also { view.dispatchTouchEvent(it); it.recycle() } }
                SystemClock.sleep(20)
            }
            instrumentation.runOnMainSync {
                event(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, 352f, 24f, 12f).also { view.dispatchTouchEvent(it); it.recycle() }
                event(MotionEvent.ACTION_UP, 1, 352f, 24f, 12f).also { view.dispatchTouchEvent(it); it.recycle() }
            }
            SystemClock.sleep(400)
            instrumentation.runOnMainSync {
                val now = SystemClock.uptimeMillis()
                view.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, cx + 24f, cy + 12f, 0))
            }
            SystemClock.sleep(750)
            instrumentation.runOnMainSync {
                val now = SystemClock.uptimeMillis()
                view.dispatchTouchEvent(MotionEvent.obtain(now - 750, now, MotionEvent.ACTION_UP, cx + 24f, cy + 12f, 0))
            }
            SystemClock.sleep(300); screenshot("pdf-pinch-anchor")
            instrumentation.runOnMainSync { view.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_COPY, null) }
            SystemClock.sleep(500)
            var copied = ""
            scenario.onActivity { copied = (it.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).primaryClip?.getItemAt(0)?.text?.toString().orEmpty() }
            assertEquals("Cherry", copied)
        }
    }

    @Test fun recordDarkForwardAndBackNavigation() {
        runBlocking { SettingsRepository(context).setThemeMode("DARK") }
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)).use { scenario ->
            SystemClock.sleep(1000)
            val started = SystemClock.uptimeMillis()
            val recorder = if (android.os.Build.VERSION.SDK_INT >= 35) instrumentation.uiAutomation.executeShellCommand("screenrecord --time-limit 8 /sdcard/cherry-navigation.mp4") else null
            try {
                SystemClock.sleep(700)
                clickDescription("Settings"); SystemClock.sleep(400); screenshot("navigation-settings")
                clickDescription("Back"); SystemClock.sleep(400); screenshot("navigation-home")
                clickDescription("Settings"); SystemClock.sleep(400)
                clickDescription("Back"); SystemClock.sleep(400)
                val remaining = 8500 - (SystemClock.uptimeMillis() - started)
                if (recorder != null && remaining > 0) SystemClock.sleep(remaining)
            } finally { recorder?.close() }
        }
    }

    @Test fun rapidExternalDocumentSwitchingKeepsViewersBoundToTheirFiles() {
        val pdf = fixture()
        val word = File(context.cacheDir, "switch.docx")
        instrumentation.context.assets.open("reading-sample.docx").use { input -> word.outputStream().use(input::copyTo) }
        val xls = File(context.cacheDir, "switch.xls")
        HSSFWorkbook().use { book -> book.createSheet("Switch").createRow(0).createCell(0).setCellValue("XLS switch"); xls.outputStream().use(book::write) }
        fun intent(file: File) = Intent(context, MainActivity::class.java).apply { action = Intent.ACTION_VIEW; data = Uri.fromFile(file); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP) }
        ActivityScenario.launch<MainActivity>(intent(pdf)).use { scenario ->
            waitForPdf(scenario); SystemClock.sleep(500)
            repeat(3) {
                listOf(xls, word, pdf).forEach { file ->
                    scenario.onActivity { it.startActivity(intent(file)) }
                    SystemClock.sleep(120)
                }
            }
            SystemClock.sleep(1800)
            assertNotNull(waitForPdf(scenario))
            screenshot("rapid-file-switching")
        }
    }

}
