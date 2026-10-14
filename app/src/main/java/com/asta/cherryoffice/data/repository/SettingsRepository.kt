package com.asta.cherryoffice.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore by preferencesDataStore(name = "cherry_office_settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode") // "DARK_GLOSSY", "AMOLED_BLACK", "LIGHT_CHERRY"
        val DEFAULT_ZOOM = floatPreferencesKey("default_zoom")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
    }

    val themeModeFlow: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.THEME_MODE] ?: "DARK_GLOSSY"
        }

    val defaultZoomFlow: Flow<Float> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.DEFAULT_ZOOM] ?: 1.0f
        }

    val keepScreenOnFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.KEEP_SCREEN_ON] ?: false
        }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = mode
        }
    }

    suspend fun setDefaultZoom(zoom: Float) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEFAULT_ZOOM] = zoom
        }
    }

    suspend fun setKeepScreenOn(keepOn: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEEP_SCREEN_ON] = keepOn
        }
    }
}
