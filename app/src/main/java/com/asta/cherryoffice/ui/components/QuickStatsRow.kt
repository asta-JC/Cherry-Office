package com.asta.cherryoffice.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asta.cherryoffice.ui.theme.CherryTheme
import com.asta.cherryoffice.utils.DocumentCategory

data class CategoryStat(
    val category: DocumentCategory,
    val count: Int,
    val icon: ImageVector,
    val tintColor: Color,
    val containerColor: Color
)

@Composable
fun QuickStatsRow(
    pdfCount: Int,
    wordCount: Int,
    excelCount: Int,
    pptCount: Int,
    selectedCategory: DocumentCategory,
    onCategoryClick: (DocumentCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    val stats = listOf(
        CategoryStat(
            category = DocumentCategory.PDF,
            count = pdfCount,
            icon = Icons.Default.PictureAsPdf,
            tintColor = Color(0xFFFF2D55),
            containerColor = Color(0x28FF2D55)
        ),
        CategoryStat(
            category = DocumentCategory.WORD,
            count = wordCount,
            icon = Icons.AutoMirrored.Filled.Article,
            tintColor = Color(0xFF3B82F6),
            containerColor = Color(0x283B82F6)
        ),
        CategoryStat(
            category = DocumentCategory.EXCEL,
            count = excelCount,
            icon = Icons.Default.GridOn,
            tintColor = Color(0xFF10B981),
            containerColor = Color(0x2810B981)
        ),
        CategoryStat(
            category = DocumentCategory.POWERPOINT,
            count = pptCount,
            icon = Icons.Default.Slideshow,
            tintColor = Color(0xFFF59E0B),
            containerColor = Color(0x28F59E0B)
        )
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        stats.forEach { stat ->
            val isSelected = (selectedCategory == stat.category)
            StatCard(
                stat = stat,
                isSelected = isSelected,
                onClick = { onCategoryClick(stat.category) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatCard(
    stat: CategoryStat,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = CherryTheme.colors
    val borderColor = if (isSelected) stat.tintColor else colors.borderFaint
    val bgColor = if (isSelected) stat.tintColor.copy(alpha = 0.15f) else colors.card

    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = bgColor,
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(stat.containerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = stat.icon,
                    contentDescription = stat.category.displayName,
                    tint = stat.tintColor,
                    modifier = Modifier.size(17.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stat.count.toString(),
                style = CherryTheme.typography.appTitle.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = colors.textWhite
            )

            Text(
                text = stat.category.displayName,
                style = CherryTheme.typography.labelMonospace.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = if (isSelected) stat.tintColor else colors.textSoftGreyPurple
            )
        }
    }
}
