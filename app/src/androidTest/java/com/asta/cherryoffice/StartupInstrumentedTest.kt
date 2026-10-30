package com.asta.cherryoffice

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlinx.coroutines.runBlocking
import androidx.room.Room
import com.asta.cherryoffice.data.local.AppDatabase

@RunWith(AndroidJUnit4::class)
class StartupInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    private fun contains(node: AccessibilityNodeInfo?, label: String): Boolean {
        if (node == null) return false
        if (node.text?.toString() == label || node.contentDescription?.toString() == label) return true
        return (0 until node.childCount).any { contains(node.getChild(it), label) }
    }

    private fun waitFor(label: String) {
        val deadline = SystemClock.uptimeMillis() + 5000
        while (SystemClock.uptimeMillis() < deadline) {
            if (contains(instrumentation.uiAutomation.rootInActiveWindow, label)) return
            SystemClock.sleep(50)
        }
        fail("Expected app content: $label")
    }

    private fun hasVideo(view: View): Boolean = view is TextureView ||
        (view is ViewGroup && (0 until view.childCount).any { hasVideo(view.getChildAt(it)) })

    private fun checkDirectContent(scenario: ActivityScenario<MainActivity>, label: String) {
        waitFor(label)
        assertFalse("No Skip overlay should obscure content", contains(instrumentation.uiAutomation.rootInActiveWindow, "Skip"))
        scenario.onActivity { activity ->
            val root = activity.window.decorView
            assertFalse("App content must not be covered by a launch-video surface", hasVideo(root))
            val insets = root.rootWindowInsets
            assertNotNull(insets)
            val bars = WindowInsetsCompat.toWindowInsetsCompat(insets, root)
            assertTrue("Startup must leave the status bar visible", bars.isVisible(WindowInsetsCompat.Type.statusBars()))
            assertTrue("Startup must leave the navigation bar visible", bars.isVisible(WindowInsetsCompat.Type.navigationBars()))
        }
    }

    @Test fun launcherRecreationAndExternalDocumentsOpenWithoutIntro() {
        assertFalse("Physical device required", android.os.Build.FINGERPRINT.contains("generic"))
        val launcher = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        repeat(2) {
            ActivityScenario.launch<MainActivity>(launcher).use { scenario ->
                checkDirectContent(scenario, "Recent files")
                scenario.recreate()
                checkDirectContent(scenario, "Recent files")
            }
        }
        val file = File(context.cacheDir, "direct-startup-check.txt").apply { writeText("Cherry Office startup check") }
        try {
            listOf(Intent.ACTION_VIEW, Intent.ACTION_SEND).forEach { action ->
                val intent = Intent(context, MainActivity::class.java).apply {
                    this.action = action
                    type = "text/plain"
                    if (action == Intent.ACTION_VIEW) data = Uri.fromFile(file)
                    else putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file))
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                    checkDirectContent(scenario, "Document options")
                    waitFor(file.name)
                }
            }
        } finally {
            val database = Room.databaseBuilder(context, AppDatabase::class.java, "cherry_office_db").build()
            try {
                runBlocking {
                    val documents = database.documentDao()
                    documents.getDocumentByUri(Uri.fromFile(file).toString())?.let { documents.deleteDocument(it) }
                }
            } finally {
                database.close()
                file.delete()
            }
        }
    }
}
