package com.asta.cherryoffice.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Small, open-stroke cherries keep the brand light beside document content. */
@Composable
fun CherryMark(modifier: Modifier = Modifier) {
    val berry = MaterialTheme.colorScheme.primary.copy(alpha = .72f)
    val stem = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .65f)
    Canvas(modifier.size(28.dp)) {
        val unit = size.minDimension / 28f
        fun point(x: Float, y: Float) = Offset(x * unit, y * unit)
        drawCircle(berry, 4.7f * unit, point(8f, 20f), style = Stroke(1.3f * unit))
        drawCircle(berry, 4.4f * unit, point(20f, 21f), style = Stroke(1.3f * unit))
        val stems = Path().apply {
            moveTo(8f*unit, 15.5f*unit); cubicTo(8f*unit, 9f*unit, 16f*unit, 10f*unit, 16f*unit, 4f*unit)
            moveTo(16f*unit, 5f*unit); cubicTo(17f*unit, 9f*unit, 20f*unit, 10f*unit, 20f*unit, 16.5f*unit)
            moveTo(16f*unit, 6f*unit); cubicTo(20f*unit, 2f*unit, 24f*unit, 5f*unit, 24f*unit, 5f*unit)
            cubicTo(22f*unit, 8f*unit, 18f*unit, 8f*unit, 16f*unit, 6f*unit)
        }
        drawPath(stems, stem, style = Stroke(1.15f*unit, cap = StrokeCap.Round))
    }
}
