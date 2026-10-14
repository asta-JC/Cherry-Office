package com.asta.cherryoffice.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val DarkBackground = Color(0xFF0A0A0F)
val DarkPlumSurface = Color(0xFF1C1520)
val DarkPlumCard = Color(0xFF1C1520)

val CherryPrimary = Color(0xFFFF2D55)
val DeepCherry = Color(0xFF7A0F2A)
val DarkCherryGlow = Color(0xFF5A0A1E)

val CherryBorderTranslucent = Color(0x66FF2D55)
val CherryBorderFaint = Color(0x33FF2D55)

val TextWhite = Color(0xFFFFFFFF)
val TextSoftGreyPurple = Color(0xFF8A7F96)

val GradientHotCherryStart = Color(0xFFFF1F57)
val GradientHotCherryEnd = Color(0xFFFF2D6F)

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
