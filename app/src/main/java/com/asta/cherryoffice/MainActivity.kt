package com.asta.cherryoffice

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.core.view.WindowInsetsCompat
import com.asta.cherryoffice.ui.launch.LaunchIntro
import com.asta.cherryoffice.ui.launch.LaunchRed
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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

    private var incomingUri by mutableStateOf<String?>(null)
    private var launchIntroVisible by mutableStateOf(false)
    private var incomingRequestId by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        incomingUri = extractUriFromIntent(intent)?.toString()
        launchIntroVisible = savedInstanceState == null && incomingUri == null && intent.action == Intent.ACTION_MAIN &&
            (application as CherryOfficeApplication).consumeLaunchIntro()

        setContent {
            val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()

            CherryTheme(themeMode = if (settingsState.isLoaded) settingsState.themeMode else "DARK") {
                val background = if (launchIntroVisible) LaunchRed else MaterialTheme.colorScheme.background
                SideEffect {
                    window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(background.toArgb()))
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        if (launchIntroVisible) hide(WindowInsetsCompat.Type.systemBars())
                        else show(WindowInsetsCompat.Type.systemBars())
                        isAppearanceLightStatusBars = background.luminance() > .5f
                        isAppearanceLightNavigationBars = background.luminance() > .5f
                    }
                }
                Box(Modifier.fillMaxSize()) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        if (!settingsState.isLoaded) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        } else {
                            CherryOfficeNavGraph(
                                homeViewModel = homeViewModel,
                                settingsViewModel = settingsViewModel,
                                viewerViewModel = viewerViewModel,
                                initialUriString = incomingUri,
                                externalOpenRequestId = incomingRequestId
                            )
                        }
                    }
                    AnimatedVisibility(visible = launchIntroVisible, exit = fadeOut(tween(180))) {
                        LaunchIntro(onFinished = { launchIntroVisible = false })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractUriFromIntent(intent)?.let { uri ->
            launchIntroVisible = false
            incomingUri = uri.toString()
            incomingRequestId++
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
