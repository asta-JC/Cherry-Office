package com.asta.cherryoffice.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

val AmoledBackground = Color(0xFF000000)
val AmoledSurface = Color(0xFF0D0A0C)
val AmoledCard = Color(0xFF140E12)

val LightBackground = Color(0xFFFFF5F7)
val LightSurface = Color(0xFFFFFFFF)
val LightCard = Color(0xFFFFFFFF)
val LightTextPrimary = Color(0xFF1A0A0E)
val LightTextSecondary = Color(0xFF6E555C)
val LightBorder = Color(0x33FF2D55)

@Composable
fun CherryTheme(
    themeMode: String = "DARK_GLOSSY",
    content: @Composable () -> Unit
) {
    val cherryColors = when (themeMode) {
        "AMOLED_BLACK" -> CherryColors(
            background = AmoledBackground,
            surface = AmoledSurface,
            card = AmoledCard,
            primary = CherryPrimary,
            deepCherry = DeepCherry,
            darkCherryGlow = DarkCherryGlow,
            borderTranslucent = CherryBorderTranslucent,
            borderFaint = CherryBorderFaint,
            textWhite = TextWhite,
            textSoftGreyPurple = TextSoftGreyPurple,
            gradientStart = GradientHotCherryStart,
            gradientEnd = GradientHotCherryEnd
        )
        "LIGHT_CHERRY" -> CherryColors(
            background = LightBackground,
            surface = LightSurface,
            card = LightCard,
            primary = CherryPrimary,
            deepCherry = DeepCherry,
            darkCherryGlow = DarkCherryGlow,
            borderTranslucent = LightBorder,
            borderFaint = LightBorder,
            textWhite = LightTextPrimary,
            textSoftGreyPurple = LightTextSecondary,
            gradientStart = GradientHotCherryStart,
            gradientEnd = GradientHotCherryEnd
        )
        else -> CherryColors() // DARK_GLOSSY
    }

    val materialColorScheme = if (themeMode == "LIGHT_CHERRY") {
        lightColorScheme(
            primary = CherryPrimary,
            onPrimary = TextWhite,
            primaryContainer = DeepCherry,
            onPrimaryContainer = TextWhite,
            secondary = DeepCherry,
            onSecondary = TextWhite,
            tertiary = DarkCherryGlow,
            onTertiary = TextWhite,
            background = LightBackground,
            onBackground = LightTextPrimary,
            surface = LightSurface,
            onSurface = LightTextPrimary,
            surfaceVariant = LightCard,
            onSurfaceVariant = LightTextSecondary,
            outline = LightBorder
        )
    } else {
        darkColorScheme(
            primary = CherryPrimary,
            onPrimary = TextWhite,
            primaryContainer = DeepCherry,
            onPrimaryContainer = TextWhite,
            secondary = DeepCherry,
            onSecondary = TextWhite,
            tertiary = DarkCherryGlow,
            onTertiary = TextWhite,
            background = cherryColors.background,
            onBackground = TextWhite,
            surface = cherryColors.surface,
            onSurface = TextWhite,
            surfaceVariant = cherryColors.card,
            onSurfaceVariant = TextSoftGreyPurple,
            outline = CherryBorderTranslucent
        )
    }

    CompositionLocalProvider(
        LocalCherryColors provides cherryColors,
        LocalCherryTypography provides CherryTypography()
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            typography = MaterialTypography,
            content = content
        )
    }
}

object CherryTheme {
    val colors: CherryColors
        @Composable
        @ReadOnlyComposable
        get() = LocalCherryColors.current

    val typography: CherryTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalCherryTypography.current
}

// Backwards compatibility alias for default template setup
@Composable
fun CherryOfficeTheme(
    content: @Composable () -> Unit
) {
    CherryTheme(themeMode = "DARK_GLOSSY", content = content)
}
