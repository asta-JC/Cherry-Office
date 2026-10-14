package com.asta.cherryoffice.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
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
    initialUriString: String? = null
) {
    val backStack = remember {
        mutableStateListOf<Any>(
            if (initialUriString != null) CherryRoute.Viewer(initialUriString) else CherryRoute.Home
        )
    }

    NavDisplay(
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
                            backStack.removeLastOrNull()
                        }
                    )
                }
                else -> error("Unknown route: $key")
            }
        }
    )
}
