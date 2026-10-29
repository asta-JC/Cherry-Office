package com.asta.cherryoffice.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val MonospaceFontFamily = FontFamily.Monospace
val SansSerifFontFamily = FontFamily.SansSerif

@Immutable
data class CherryTypography(
    // Modern rounded sans-serif styles for friendly branding and clear UI hierarchy
    val appTitle: TextStyle = TextStyle(
        fontFamily = SansSerifFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        letterSpacing = 0.5.sp,
        color = androidx.compose.ui.graphics.Color.Unspecified
    ),
    val sectionHeader: TextStyle = TextStyle(
        fontFamily = SansSerifFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        letterSpacing = 0.2.sp,
        color = androidx.compose.ui.graphics.Color.Unspecified
    ),
    val chipText: TextStyle = TextStyle(
        fontFamily = SansSerifFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        letterSpacing = 0.2.sp,
        color = androidx.compose.ui.graphics.Color.Unspecified
    ),
    val labelMonospace: TextStyle = TextStyle(
        fontFamily = SansSerifFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.3.sp,
        color = androidx.compose.ui.graphics.Color.Unspecified
    ),
    val footerMonospace: TextStyle = TextStyle(
        fontFamily = SansSerifFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.4.sp,
        color = androidx.compose.ui.graphics.Color.Unspecified
    ),
    // Sans-serif styles for document text and clean UI content
    val documentTitle: TextStyle = TextStyle(
        fontFamily = SansSerifFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        color = androidx.compose.ui.graphics.Color.Unspecified
    ),
    val documentBody: TextStyle = TextStyle(
        fontFamily = SansSerifFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = androidx.compose.ui.graphics.Color.Unspecified
    ),
    val documentMeta: TextStyle = TextStyle(
        fontFamily = SansSerifFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        color = androidx.compose.ui.graphics.Color.Unspecified
    )
)

val LocalCherryTypography = staticCompositionLocalOf { CherryTypography() }

// Material 3 Typography integration
val MaterialTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = SansSerifFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        letterSpacing = 0.sp,
        color = androidx.compose.ui.graphics.Color.Unspecified
    ),
    titleLarge = TextStyle(
        fontFamily = SansSerifFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        letterSpacing = 0.15.sp,
        color = androidx.compose.ui.graphics.Color.Unspecified
    ),
    titleMedium = TextStyle(
        fontFamily = SansSerifFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        letterSpacing = 0.15.sp,
        color = androidx.compose.ui.graphics.Color.Unspecified
    ),
    bodyLarge = TextStyle(
        fontFamily = SansSerifFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        color = androidx.compose.ui.graphics.Color.Unspecified
    ),
    bodyMedium = TextStyle(
        fontFamily = SansSerifFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = androidx.compose.ui.graphics.Color.Unspecified
    ),
    labelSmall = TextStyle(
        fontFamily = SansSerifFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.3.sp,
        color = androidx.compose.ui.graphics.Color.Unspecified
    )
)
