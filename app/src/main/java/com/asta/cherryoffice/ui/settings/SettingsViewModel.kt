package com.asta.cherryoffice.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asta.cherryoffice.data.repository.DocumentRepository
import com.asta.cherryoffice.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val themeMode: String = "DARK_GLOSSY", // "DARK_GLOSSY", "AMOLED_BLACK", "LIGHT_CHERRY"
    val defaultZoom: Float = 1.0f,
    val keepScreenOn: Boolean = false,
    val showAboutDialog: Boolean = false,
    val snackbarMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val documentRepository: DocumentRepository
) : ViewModel() {

    private val _showAboutDialog = MutableStateFlow(false)
    private val _snackbarMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.themeModeFlow,
        settingsRepository.defaultZoomFlow,
        settingsRepository.keepScreenOnFlow,
        _showAboutDialog,
        _snackbarMessage
    ) { themeMode, defaultZoom, keepScreenOn, showAbout, snackbarMsg ->
        SettingsUiState(
            themeMode = themeMode,
            defaultZoom = defaultZoom,
            keepScreenOn = keepScreenOn,
            showAboutDialog = showAbout,
            snackbarMessage = snackbarMsg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }

    fun setDefaultZoom(zoom: Float) {
        viewModelScope.launch {
            settingsRepository.setDefaultZoom(zoom)
        }
    }

    fun setKeepScreenOn(keepOn: Boolean) {
        viewModelScope.launch {
            settingsRepository.setKeepScreenOn(keepOn)
        }
    }

    fun clearRecentHistory() {
        viewModelScope.launch {
            try {
                documentRepository.clearAll()
                _snackbarMessage.value = "Recent files history cleared successfully."
            } catch (e: Exception) {
                _snackbarMessage.value = "Failed to clear history: ${e.localizedMessage}"
            }
        }
    }

    fun setShowAboutDialog(show: Boolean) {
        _showAboutDialog.value = show
    }

    fun clearSnackbarMessage() {
        _snackbarMessage.value = null
    }
}
