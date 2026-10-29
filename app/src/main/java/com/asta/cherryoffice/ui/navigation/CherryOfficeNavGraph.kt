package com.asta.cherryoffice.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.asta.cherryoffice.ui.home.HomeScreen
import com.asta.cherryoffice.ui.home.HomeViewModel
import com.asta.cherryoffice.ui.settings.SettingsScreen
import com.asta.cherryoffice.ui.settings.SettingsViewModel
import com.asta.cherryoffice.ui.viewer.ViewerScreen
import com.asta.cherryoffice.ui.viewer.ViewerViewModel
import kotlinx.serialization.Serializable

@Serializable
sealed interface CherryRoute {
    @Serializable
    data object Home : CherryRoute

    @Serializable
    data object Settings : CherryRoute

    @Serializable
    data class Viewer(val uriString: String) : CherryRoute
}

@Composable
fun CherryOfficeNavGraph(
    homeViewModel: HomeViewModel,
    settingsViewModel: SettingsViewModel,
    viewerViewModel: ViewerViewModel,
    initialUriString: String? = null,
    externalOpenRequestId: Int = 0
) {
    val backStack = remember {
        mutableStateListOf<Any>(
            CherryRoute.Home
        )
    }

    val settings by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val rootView = LocalView.current
    val reading = backStack.lastOrNull() is CherryRoute.Viewer
    SideEffect { rootView.keepScreenOn = reading && settings.keepScreenOn }
    DisposableEffect(rootView) { onDispose { rootView.keepScreenOn = false } }

    LaunchedEffect(initialUriString, externalOpenRequestId) {
        initialUriString?.let { uri ->
            val route = CherryRoute.Viewer(uri)
            if (backStack.lastOrNull() != route) {
                if (backStack.lastOrNull() is CherryRoute.Viewer) backStack[backStack.lastIndex] = route
                else backStack.add(route)
            }
        }
    }

    NavDisplay(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) },
        popTransitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) },
        predictivePopTransitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) },
        backStack = backStack,
        onBack = {
            if (backStack.size > 1) {
                backStack.removeLastOrNull()
            }
        },
        entryProvider = { key ->
            when (key) {
                is CherryRoute.Home -> NavEntry(key) {
                    HomeScreen(
                        viewModel = homeViewModel,
                        onDocumentOpenRequested = { doc ->
                            backStack.add(CherryRoute.Viewer(doc.uri))
                        },
                        onSettingsClick = {
                            backStack.add(CherryRoute.Settings)
                        }
                    )
                }
                is CherryRoute.Settings -> NavEntry(key) {
                    SettingsScreen(
                        viewModel = settingsViewModel,
                        onBackClick = {
                            backStack.removeLastOrNull()
                        }
                    )
                }
                is CherryRoute.Viewer -> NavEntry(key) {
                    ViewerScreen(
                        viewModel = viewerViewModel,
                        documentUri = Uri.parse(key.uriString),
                        onBackClick = {
                            if (backStack.size > 1) backStack.removeLastOrNull()
                        }
                    )
                }
                else -> error("Unknown route: $key")
            }
        }
    )
}
