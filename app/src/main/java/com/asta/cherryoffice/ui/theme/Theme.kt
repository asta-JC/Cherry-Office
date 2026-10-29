package com.asta.cherryoffice.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import com.asta.cherryoffice.data.repository.AppearanceMode

@Composable
fun CherryTheme(themeMode: String = "SYSTEM", content: @Composable () -> Unit) {
    val mode = AppearanceMode.fromStored(themeMode)
    val dark = when (mode) {
        AppearanceMode.SYSTEM -> isSystemInDarkTheme()
        AppearanceMode.LIGHT -> false
        else -> true
    }
    val scheme = if (dark) darkColorScheme(
        primary = Color(0xFFF2A7B8), onPrimary = Color(0xFF501326),
        primaryContainer = Color(0xFF552735), onPrimaryContainer = Color(0xFFFFD9E1),
        secondary = Color(0xFFD0BCC3), onSecondary = Color(0xFF392B31),
        secondaryContainer = Color(0xFF40323A), onSecondaryContainer = Color(0xFFEEDFE5),
        tertiary = Color(0xFFE5BEA9), onTertiary = Color(0xFF422C20),
        background = if (mode == AppearanceMode.AMOLED) Color.Black else Color(0xFF151318),
        onBackground = Color(0xFFF1EDF0), surface = if (mode == AppearanceMode.AMOLED) Color(0xFF080708) else Color(0xFF151318),
        onSurface = Color(0xFFF1EDF0), surfaceVariant = Color(0xFF332C32), onSurfaceVariant = Color(0xFFCFC3CA),
        surfaceDim = Color(0xFF151318), surfaceBright = Color(0xFF3B363D),
        surfaceContainerLowest = if (mode == AppearanceMode.AMOLED) Color.Black else Color(0xFF100E12),
        surfaceContainerLow = Color(0xFF1D1A1F), surfaceContainer = Color(0xFF211E24),
        surfaceContainerHigh = Color(0xFF2C282E), surfaceContainerHighest = Color(0xFF373239),
        outline = Color(0xFF958990), outlineVariant = Color(0xFF494148)
    ) else lightColorScheme(
        primary = Color(0xFFA82D4B), onPrimary = Color.White,
        primaryContainer = Color(0xFFFFD9E1), onPrimaryContainer = Color(0xFF431021),
        secondary = Color(0xFF715A64), onSecondary = Color.White,
        secondaryContainer = Color(0xFFF0DEE5), onSecondaryContainer = Color(0xFF281820),
        tertiary = Color(0xFF80543C), onTertiary = Color.White,
        background = Color(0xFFFAF8F9), onBackground = Color(0xFF211A20),
        surface = Color(0xFFFAF8F9), onSurface = Color(0xFF211A20),
        surfaceVariant = Color(0xFFEDE4E8), onSurfaceVariant = Color(0xFF635860),
        surfaceDim = Color(0xFFE2DADD), surfaceBright = Color(0xFFFAF8F9),
        surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF5F0F2),
        surfaceContainer = Color(0xFFF0EAED), surfaceContainerHigh = Color(0xFFEAE3E7),
        surfaceContainerHighest = Color(0xFFE4DDE1), outline = Color(0xFF84767F), outlineVariant = Color(0xFFD5C8D0)
    )
    val colors = CherryColors(background = scheme.background, surface = scheme.surface, card = scheme.surfaceContainerLow,
        primary = scheme.primary, deepCherry = scheme.primaryContainer, darkCherryGlow = scheme.surfaceContainer,
        borderTranslucent = scheme.outlineVariant, borderFaint = scheme.outlineVariant.copy(alpha = .5f),
        textWhite = scheme.onSurface, textSoftGreyPurple = scheme.onSurfaceVariant,
        gradientStart = scheme.primary, gradientEnd = scheme.primary)
    CompositionLocalProvider(LocalCherryColors provides colors, LocalCherryTypography provides CherryTypography()) {
        MaterialTheme(colorScheme = scheme, typography = MaterialTypography, content = content)
    }
}

object CherryTheme {
    val colors: CherryColors @Composable @ReadOnlyComposable get() = LocalCherryColors.current
    val typography: CherryTypography @Composable @ReadOnlyComposable get() = LocalCherryTypography.current
}
@Composable fun CherryOfficeTheme(content: @Composable () -> Unit) = CherryTheme(content = content)
