package com.asta.cherryoffice.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asta.cherryoffice.ui.theme.CherryTheme
import com.asta.cherryoffice.ui.theme.MonospaceFontFamily

enum class CherryButtonVariant {
    MAIN,   // Hot cherry gradient (#FF1F57 to #FF2D6F) with soft glow
    ACTION, // Red-tinted fill + red border
    NORMAL  // Dark with faint border
}

@Composable
fun CherryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: CherryButtonVariant = CherryButtonVariant.MAIN,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
) {
    val colors = CherryTheme.colors

    when (variant) {
        CherryButtonVariant.MAIN -> {
            val gradientBrush = Brush.horizontalGradient(
                colors = listOf(colors.gradientStart, colors.gradientEnd)
            )
            val alpha = if (enabled) 1f else 0.5f

            Box(
                modifier = modifier
                    .shadow(
                        elevation = if (enabled) 10.dp else 0.dp,
                        shape = shape,
                        spotColor = colors.primary,
                        ambientColor = colors.primary
                    )
                    .clip(shape)
                    .background(gradientBrush)
                    .clickable(enabled = enabled, onClick = onClick)
                    .padding(contentPadding),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = colors.textWhite.copy(alpha = alpha),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = text,
                        style = CherryTheme.typography.chipText.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        ),
                        color = colors.textWhite.copy(alpha = alpha),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        CherryButtonVariant.ACTION -> {
            val containerColor = colors.deepCherry.copy(alpha = 0.35f)
            val borderStroke = BorderStroke(1.dp, colors.primary)

            Surface(
                onClick = onClick,
                enabled = enabled,
                modifier = modifier,
                shape = shape,
                color = containerColor,
                border = borderStroke
            ) {
                Row(
                    modifier = Modifier.padding(contentPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = text,
                        style = CherryTheme.typography.chipText.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = colors.textWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        CherryButtonVariant.NORMAL -> {
            val containerColor = colors.surface
            val borderStroke = BorderStroke(1.dp, colors.borderFaint)

            Surface(
                onClick = onClick,
                enabled = enabled,
                modifier = modifier,
                shape = shape,
                color = containerColor,
                border = borderStroke
            ) {
                Row(
                    modifier = Modifier.padding(contentPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = colors.textSoftGreyPurple,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = text,
                        style = CherryTheme.typography.chipText,
                        color = colors.textWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
