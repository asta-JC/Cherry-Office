package com.asta.cherryoffice

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.asta.cherryoffice.data.model.DocumentItem
import com.asta.cherryoffice.ui.home.HomeScreenContent
import com.asta.cherryoffice.ui.home.HomeUiState
import com.asta.cherryoffice.ui.settings.SettingsContent
import com.asta.cherryoffice.ui.settings.SettingsUiState
import com.asta.cherryoffice.ui.theme.CherryTheme
import com.asta.cherryoffice.ui.viewer.ViewerScreen
import com.asta.cherryoffice.ui.viewer.ViewerViewModel
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Opt-in documentation capture. Uses production UI and sample data without editing user history/settings. */
class ReadmeScreenshotsTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun captureReadmeScreens() {
        assertFalse("Capture on a physical device only", android.os.Build.FINGERPRINT.contains("generic"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val file = File(context.cacheDir, "Cherry overview.pptx")
        instrumentation.context.assets.open("cherry-overview.pptx").use { input -> file.outputStream().use(input::copyTo) }
        val output = File(context.getExternalFilesDir(null), "readme").apply { mkdirs() }
        val screen = mutableIntStateOf(0)
        val samples = listOf(
            Triple("Project brief", "pdf", 248_320L), Triple("Meeting notes", "docx", 42_496L),
            Triple("Monthly budget", "xlsx", 85_120L), Triple("Cherry overview", "pptx", 156_672L),
            Triple("Reading list", "pdf", 390_144L), Triple("Ideas for tomorrow", "txt", 3_072L),
            Triple("Team handbook", "docx", 126_976L)
        ).mapIndexed { i, (name, ext, size) -> DocumentItem(id = i.toLong(), uri = "file:///demo/$i.$ext",
            name = "$name.$ext", mimeType = "application/octet-stream", extension = ext, size = size,
            isFavorite = i in listOf(0, 2, 3)) }
        rule.activity.runOnUiThread {
            val viewer = ViewModelProvider(rule.activity)[ViewerViewModel::class.java]
            rule.activity.setContent {
                CherryTheme("LIGHT") {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        when (screen.intValue) {
                            0 -> HomeScreenContent(
                                uiState = HomeUiState(recentDocuments = samples, totalDocumentCount = samples.size),
                                onOpenFileClick = {}, onScanFolderClick = {}, onScanStorageClick = {},
                                onCategorySelect = {}, onToggleFavorites = {}, onSortOrderChange = {},
                                onSearchQueryChange = {}, onToggleSearch = {}, onDocumentClick = {},
                                onFavoriteToggle = { _, _ -> }, onDeleteDocument = {}, onShareDocument = {}, onSettingsClick = {})
                            1 -> ViewerScreen(viewer, Uri.fromFile(file), onBackClick = {})
                            else -> SettingsContent(SettingsUiState(themeMode = "LIGHT", isLoaded = true),
                                onBack = {}, onAppearance = {}, onZoom = {}, onKeepOn = {}, onClear = {})
                        }
                    }
                }
            }
        }
        fun capture(name: String) {
            rule.waitForIdle()
            // AndroidView/WebView paints asynchronously after Compose becomes idle.
            Thread.sleep(1800)
            val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
            File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        capture("library-light")
        rule.runOnIdle { screen.intValue = 1 }
        rule.waitUntil(10000) { !ViewModelProvider(rule.activity)[ViewerViewModel::class.java].uiState.value.isLoading }
        capture("slides-light")
        rule.runOnIdle { screen.intValue = 2 }
        capture("settings-light")
    }
}
