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
    // Monospace styles for branding, headers, chips, labels, footers
    val appTitle: TextStyle = TextStyle(
        fontFamily = MonospaceFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        letterSpacing = 2.sp,
        color = TextWhite
    ),
    val sectionHeader: TextStyle = TextStyle(
        fontFamily = MonospaceFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        letterSpacing = 1.5.sp,
        color = TextWhite
    ),
    val chipText: TextStyle = TextStyle(
        fontFamily = MonospaceFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 1.sp,
        color = TextWhite
    ),
    val labelMonospace: TextStyle = TextStyle(
        fontFamily = MonospaceFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        letterSpacing = 1.2.sp,
        color = TextSoftGreyPurple
    ),
    val footerMonospace: TextStyle = TextStyle(
        fontFamily = MonospaceFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        letterSpacing = 1.5.sp,
        color = TextSoftGreyPurple
    ),
    // Sans-serif styles for document text and clean UI content
    val documentTitle: TextStyle = TextStyle(
        fontFamily = SansSerifFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        color = TextWhite
    ),
    val documentBody: TextStyle = TextStyle(
        fontFamily = SansSerifFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = TextWhite
    ),
    val documentMeta: TextStyle = TextStyle(
        fontFamily = SansSerifFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        color = TextSoftGreyPurple
    )
)

val LocalCherryTypography = staticCompositionLocalOf { CherryTypography() }

// Material 3 Typography integration
val MaterialTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = MonospaceFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        letterSpacing = 2.sp,
        color = TextWhite
    ),
    titleLarge = TextStyle(
        fontFamily = MonospaceFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        letterSpacing = 1.5.sp,
        color = TextWhite
    ),
    titleMedium = TextStyle(
        fontFamily = MonospaceFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        letterSpacing = 1.2.sp,
        color = TextWhite
    ),
    bodyLarge = TextStyle(
        fontFamily = SansSerifFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        color = TextWhite
    ),
    bodyMedium = TextStyle(
        fontFamily = SansSerifFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = TextWhite
    ),
    labelSmall = TextStyle(
        fontFamily = MonospaceFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 1.sp,
        color = TextSoftGreyPurple
    )
)
