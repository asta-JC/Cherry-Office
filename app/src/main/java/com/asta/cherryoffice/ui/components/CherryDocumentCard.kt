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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asta.cherryoffice.data.model.DocumentItem
import com.asta.cherryoffice.ui.theme.CherryTheme
import com.asta.cherryoffice.utils.DocumentCategory
import com.asta.cherryoffice.utils.DocumentUtils

data class CategoryStyle(
    val icon: ImageVector,
    val primaryColor: Color,
    val containerColor: Color,
    val label: String
)

@Composable
fun getCategoryStyle(category: DocumentCategory, extension: String): CategoryStyle {
    return when (category) {
        DocumentCategory.PDF -> CategoryStyle(
            icon = Icons.Default.PictureAsPdf,
            primaryColor = Color(0xFFFF2D55),
            containerColor = Color(0x28FF2D55),
            label = "PDF"
        )
        DocumentCategory.WORD -> CategoryStyle(
            icon = Icons.AutoMirrored.Filled.Article,
            primaryColor = Color(0xFF3B82F6),
            containerColor = Color(0x283B82F6),
            label = extension.uppercase().ifBlank { "DOC" }
        )
        DocumentCategory.EXCEL -> CategoryStyle(
            icon = Icons.Default.GridOn,
            primaryColor = Color(0xFF10B981),
            containerColor = Color(0x2810B981),
            label = extension.uppercase().ifBlank { "XLS" }
        )
        DocumentCategory.POWERPOINT -> CategoryStyle(
            icon = Icons.Default.Slideshow,
            primaryColor = Color(0xFFF59E0B),
            containerColor = Color(0x28F59E0B),
            label = extension.uppercase().ifBlank { "PPT" }
        )
        else -> CategoryStyle(
            icon = Icons.AutoMirrored.Filled.InsertDriveFile,
            primaryColor = Color(0xFF8B5CF6),
            containerColor = Color(0x288B5CF6),
            label = extension.uppercase().ifBlank { "TXT" }
        )
    }
}

@Composable
fun CherryDocumentCard(
    document: DocumentItem,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onDeleteClick: () -> Unit,
    onShareClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = CherryTheme.colors
    val category = DocumentUtils.getCategoryForDocument(document.mimeType, document.extension)
    val style = getCategoryStyle(category, document.extension)
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = colors.card,

        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Icon Badge Tile
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(style.containerColor),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = style.icon,
                        contentDescription = style.label,
                        tint = style.primaryColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = style.label.take(4),
                        style = CherryTheme.typography.labelMonospace.copy(
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = style.primaryColor
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Document Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = document.name,
                    style = CherryTheme.typography.documentTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = DocumentUtils.formatFileSize(document.size),
                        style = CherryTheme.typography.documentMeta
                    )
                    Text(
                        text = " • ",
                        style = CherryTheme.typography.documentMeta
                    )
                    Text(
                        text = DocumentUtils.formatLastOpened(document.lastOpened),
                        style = CherryTheme.typography.documentMeta
                    )
                }
            }

            // Actions: Favorite + Overflow Menu
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (document.isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                        contentDescription = if (document.isFavorite) "Remove favorite" else "Add favorite",
                        tint = if (document.isFavorite) Color(0xFFFFC107) else colors.textSoftGreyPurple,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More Options",
                            tint = colors.textSoftGreyPurple,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Share") },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onShareClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = Color(0xFFFF453A)) },
                            onClick = {
                                showMenu = false
                                onDeleteClick()
                            }
                        )
                    }
                }
            }
        }
    }
}
