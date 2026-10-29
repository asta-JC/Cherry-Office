package com.asta.cherryoffice.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val DarkBackground = Color(0xFF151318)
val DarkPlumSurface = Color(0xFF211E24)
val DarkPlumCard = Color(0xFF211E24)

val CherryPrimary = Color(0xFFBE3655)
val DeepCherry = Color(0xFF752C40)
val DarkCherryGlow = Color(0xFF35232B)

val CherryBorderTranslucent = Color(0xFF51474C)
val CherryBorderFaint = Color(0xFF393238)

val TextWhite = Color(0xFFF1EDF0)
val TextSoftGreyPurple = Color(0xFFBEB4BB)

val GradientHotCherryStart = Color(0xFFBE3655)
val GradientHotCherryEnd = Color(0xFFBE3655)

@Immutable
data class CherryColors(
    val background: Color = DarkBackground,
    val surface: Color = DarkPlumSurface,
    val card: Color = DarkPlumCard,
    val primary: Color = CherryPrimary,
    val deepCherry: Color = DeepCherry,
    val darkCherryGlow: Color = DarkCherryGlow,
    val borderTranslucent: Color = CherryBorderTranslucent,
    val borderFaint: Color = CherryBorderFaint,
    val textWhite: Color = TextWhite,
    val textSoftGreyPurple: Color = TextSoftGreyPurple,
    val gradientStart: Color = GradientHotCherryStart,
    val gradientEnd: Color = GradientHotCherryEnd
)

val LocalCherryColors = staticCompositionLocalOf { CherryColors() }
