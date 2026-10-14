package com.asta.cherryoffice

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.asta.cherryoffice.ui.home.HomeViewModel
import com.asta.cherryoffice.ui.navigation.CherryOfficeNavGraph
import com.asta.cherryoffice.ui.settings.SettingsViewModel
import com.asta.cherryoffice.ui.theme.CherryTheme
import com.asta.cherryoffice.ui.viewer.ViewerViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val homeViewModel: HomeViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val viewerViewModel: ViewerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val initialUri = extractUriFromIntent(intent)

        setContent {
            val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()

            // Handle Keep Screen On preference
            if (settingsState.keepScreenOn) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }

            CherryTheme(themeMode = settingsState.themeMode) {
                CherryOfficeNavGraph(
                    homeViewModel = homeViewModel,
                    settingsViewModel = settingsViewModel,
                    viewerViewModel = viewerViewModel,
                    initialUriString = initialUri?.toString()
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractUriFromIntent(intent)?.let { uri ->
            homeViewModel.processIntentUri(uri)
        }
    }

    private fun extractUriFromIntent(intent: Intent?): Uri? {
        if (intent == null) return null
        val action = intent.action
        return when (action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
                } ?: intent.data
            }
            else -> intent.data
        }
    }
}
