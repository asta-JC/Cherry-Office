package com.asta.cherryoffice.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.asta.cherryoffice.ui.theme.CherryTheme
import com.asta.cherryoffice.ui.theme.DarkBackground
import com.asta.cherryoffice.ui.theme.DarkCherryGlow
import com.asta.cherryoffice.ui.theme.DeepCherry

@Composable
fun GlowBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val darkBg = CherryTheme.colors.background
    val primaryCherry = CherryTheme.colors.primary
    val deepCherry = CherryTheme.colors.deepCherry
    val glowCherry = CherryTheme.colors.darkCherryGlow

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(darkBg)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // Top-right soft cherry glow blob
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryCherry.copy(alpha = 0.28f),
                        deepCherry.copy(alpha = 0.15f),
                        glowCherry.copy(alpha = 0.05f),
                        Color.Transparent
                    ),
                    center = Offset(width * 0.85f, height * 0.12f),
                    radius = width * 0.65f
                ),
                radius = width * 0.65f,
                center = Offset(width * 0.85f, height * 0.12f)
            )

            // Center-left deep cherry glow blob
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        deepCherry.copy(alpha = 0.22f),
                        glowCherry.copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    center = Offset(width * 0.1f, height * 0.48f),
                    radius = width * 0.55f
                ),
                radius = width * 0.55f,
                center = Offset(width * 0.1f, height * 0.48f)
            )

            // Bottom-right hot cherry glow blob
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryCherry.copy(alpha = 0.20f),
                        glowCherry.copy(alpha = 0.10f),
                        Color.Transparent
                    ),
                    center = Offset(width * 0.8f, height * 0.88f),
                    radius = width * 0.70f
                ),
                radius = width * 0.70f,
                center = Offset(width * 0.8f, height * 0.88f)
            )
        }

        content()
    }
}
