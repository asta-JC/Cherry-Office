package com.asta.cherryoffice

import com.asta.cherryoffice.ui.settings.SettingsUiState
import org.junit.Test
import org.junit.Assert.assertEquals

class SettingsViewModelTest {

    @Test
    fun defaultState_isCorrect() {
        val state = SettingsUiState()
        assertEquals("DARK_GLOSSY", state.themeMode)
        assertEquals(1.0f, state.defaultZoom)
        assertEquals(false, state.keepScreenOn)
        assertEquals(false, state.showAboutDialog)
        assertEquals(null, state.snackbarMessage)
    }
}
