package com.asta.cherryoffice.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asta.cherryoffice.data.model.DocumentItem
import com.asta.cherryoffice.ui.components.CherryButton
import com.asta.cherryoffice.ui.components.CherryButtonVariant
import com.asta.cherryoffice.ui.components.CherryCard
import com.asta.cherryoffice.ui.components.CherryChip
import com.asta.cherryoffice.ui.components.CherryIconButton
import com.asta.cherryoffice.ui.components.GlowBackground
import com.asta.cherryoffice.ui.theme.CherryTheme
import com.asta.cherryoffice.utils.DocumentCategory
import com.asta.cherryoffice.utils.DocumentUtils

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onDocumentOpenRequested: (DocumentItem) -> Unit = {},
    onSettingsClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.onDocumentSelected(it) }
    }

    LaunchedEffect(uiState.openedDocument) {
        uiState.openedDocument?.let { doc ->
            onDocumentOpenRequested(doc)
            viewModel.clearOpenedDocumentEvent()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    HomeScreenContent(
        uiState = uiState,
        onOpenFileClick = {
            openDocumentLauncher.launch(
                arrayOf(
                    "*/*",
                    "application/pdf",
                    "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/vnd.ms-excel",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/vnd.ms-powerpoint",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    "text/plain"
                )
            )
        },
        onCategorySelect = { viewModel.onCategorySelected(it) },
        onSearchQueryChange = { viewModel.onSearchQueryChanged(it) },
        onToggleSearch = { viewModel.toggleSearchVisibility() },
        onDocumentClick = { doc ->
            val uri = Uri.parse(doc.uri)
            viewModel.onDocumentSelected(uri)
        },
        onFavoriteToggle = { id, isFav -> viewModel.onFavoriteToggled(id, isFav) },
        onDeleteDocument = { id -> viewModel.onDeleteDocument(id) },
        onClearHistory = { viewModel.onClearHistory() },
        onSettingsClick = onSettingsClick,
        snackbarHostState = snackbarHostState
    )
}

@Composable
fun HomeScreenContent(
    uiState: HomeUiState,
    onOpenFileClick: () -> Unit,
    onCategorySelect: (DocumentCategory) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onDocumentClick: (DocumentItem) -> Unit,
    onFavoriteToggle: (Long, Boolean) -> Unit,
    onDeleteDocument: (Long) -> Unit,
    onClearHistory: () -> Unit,
    onSettingsClick: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    val colors = CherryTheme.colors

    GlowBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(48.dp))

                // Top Bar
                HomeTopBar(
                    totalCount = uiState.totalDocumentCount,
                    isSearchVisible = uiState.isSearchVisible,
                    onToggleSearch = onToggleSearch,
                    onSettingsClick = onSettingsClick
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Search Bar input (animated visibility)
                AnimatedVisibility(
                    visible = uiState.isSearchVisible,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    SearchBarInput(
                        query = uiState.searchQuery,
                        onQueryChange = onSearchQueryChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    )
                }

                // Big "Open file" main gradient button
                CherryButton(
                    text = "OPEN FILE",
                    icon = Icons.Default.FolderOpen,
                    variant = CherryButtonVariant.MAIN,
                    onClick = onOpenFileClick,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Filter Chips
                FilterChipsRow(
                    selectedCategory = uiState.selectedCategory,
                    onCategorySelect = onCategorySelect
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Recent Files Section Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RECENT FILES",
                        style = CherryTheme.typography.sectionHeader
                    )
                    if (uiState.recentDocuments.isNotEmpty()) {
                        IconButton(
                            onClick = onClearHistory,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear History",
                                tint = colors.textSoftGreyPurple,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Document List or Empty State
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (uiState.recentDocuments.isEmpty()) {
                        EmptyDocumentState(searchQuery = uiState.searchQuery)
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(
                                items = uiState.recentDocuments,
                                key = { it.id }
                            ) { doc ->
                                DocumentCardItem(
                                    document = doc,
                                    onClick = { onDocumentClick(doc) },
                                    onFavoriteToggle = { onFavoriteToggle(doc.id, !doc.isFavorite) },
                                    onDeleteClick = { onDeleteDocument(doc.id) }
                                )
                            }
                        }
                    }
                }

                // Footer Text
                Text(
                    text = "Cherry Office // Asta v1.0",
                    style = CherryTheme.typography.footerMonospace,
                    color = colors.textSoftGreyPurple.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                )
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            )
        }
    }
}

@Composable
fun HomeTopBar(
    totalCount: Int,
    isSearchVisible: Boolean,
    onToggleSearch: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val colors = CherryTheme.colors

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Rounded app icon tile in cherry gradient
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(colors.gradientStart, colors.gradientEnd)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = "Cherry Office Icon",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = "CHERRY OFFICE",
                    style = CherryTheme.typography.appTitle
                )
                Text(
                    text = "Asta's project",
                    style = CherryTheme.typography.labelMonospace
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CherryIconButton(
                icon = Icons.Default.Search,
                contentDescription = "Search",
                onClick = onToggleSearch,
                containerColor = if (isSearchVisible) colors.deepCherry.copy(alpha = 0.5f) else colors.card,
                borderColor = if (isSearchVisible) colors.primary else colors.borderTranslucent
            )

            CherryIconButton(
                icon = Icons.Default.Settings,
                contentDescription = "Settings",
                onClick = onSettingsClick
            )

            BadgedBox(
                badge = {
                    if (totalCount > 0) {
                        Badge(
                            containerColor = colors.primary,
                            contentColor = Color.White
                        ) {
                            Text(
                                text = if (totalCount > 99) "99+" else totalCount.toString(),
                                style = CherryTheme.typography.labelMonospace.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            ) {
                CherryIconButton(
                    icon = Icons.Default.History,
                    contentDescription = "Recent Files Count",
                    onClick = {}
                )
            }
        }
    }
}

@Composable
fun SearchBarInput(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = CherryTheme.colors

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(colors.card)
            .border(1.dp, colors.borderTranslucent, CircleShape)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = "Search documents...",
                        style = CherryTheme.typography.documentMeta,
                        color = colors.textSoftGreyPurple
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = CherryTheme.typography.documentBody.copy(color = colors.textWhite),
                    cursorBrush = SolidColor(colors.primary),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear Search",
                        tint = colors.textSoftGreyPurple,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun FilterChipsRow(
    selectedCategory: DocumentCategory,
    onCategorySelect: (DocumentCategory) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(DocumentCategory.entries.toTypedArray()) { category ->
            CherryChip(
                text = category.displayName,
                isSelected = (selectedCategory == category),
                onClick = { onCategorySelect(category) }
            )
        }
    }
}

@Composable
fun DocumentCardItem(
    document: DocumentItem,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val colors = CherryTheme.colors
    val category = DocumentUtils.getCategoryForDocument(document.mimeType, document.extension)
    val (icon, iconBgColor, iconTint) = getCategoryVisuals(category)

    CherryCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        contentPadding = 12.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Category Icon Tile
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = category.displayName,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Document Metadata
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

            // Favorite Star & Delete Actions
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (document.isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                        contentDescription = "Favorite",
                        tint = if (document.isFavorite) Color(0xFFFFC107) else colors.textSoftGreyPurple,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete",
                        tint = colors.textSoftGreyPurple,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

private data class CategoryVisuals(
    val icon: ImageVector,
    val containerColor: Color,
    val iconTint: Color
)

@Composable
private fun getCategoryVisuals(category: DocumentCategory): CategoryVisuals {
    val primary = CherryTheme.colors.primary

    return when (category) {
        DocumentCategory.PDF -> CategoryVisuals(
            icon = Icons.Default.PictureAsPdf,
            containerColor = Color(0x33FF2D55),
            iconTint = primary
        )
        DocumentCategory.WORD -> CategoryVisuals(
            icon = Icons.AutoMirrored.Filled.Article,
            containerColor = Color(0x332196F3),
            iconTint = Color(0xFF42A5F5)
        )
        DocumentCategory.EXCEL -> CategoryVisuals(
            icon = Icons.Default.GridOn,
            containerColor = Color(0x334CAF50),
            iconTint = Color(0xFF66BB6A)
        )
        DocumentCategory.POWERPOINT -> CategoryVisuals(
            icon = Icons.Default.Slideshow,
            containerColor = Color(0x33FF9800),
            iconTint = Color(0xFFFFA726)
        )
        else -> CategoryVisuals(
            icon = Icons.AutoMirrored.Filled.InsertDriveFile,
            containerColor = Color(0x339C27B0),
            iconTint = Color(0xFFAB47BC)
        )
    }
}

@Composable
fun EmptyDocumentState(searchQuery: String) {
    val colors = CherryTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(colors.deepCherry.copy(alpha = 0.3f))
                .border(1.dp, colors.borderTranslucent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (searchQuery.isNotBlank()) Icons.Default.FolderOff else Icons.Default.FolderOpen,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "NO RECENT DOCUMENTS",
            style = CherryTheme.typography.sectionHeader,
            color = colors.textWhite
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (searchQuery.isNotBlank()) {
                "No documents matching \"$searchQuery\"."
            } else {
                "Tap 'OPEN FILE' to pick a document from your storage."
            },
            style = CherryTheme.typography.documentMeta,
            textAlign = TextAlign.Center,
            color = colors.textSoftGreyPurple
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun HomeScreenPreview() {
    val sampleDocs = listOf(
        DocumentItem(
            id = 1,
            uri = "content://sample/1",
            name = "Project_Proposal_2025.pdf",
            mimeType = "application/pdf",
            extension = "pdf",
            size = 2450000L,
            lastOpened = System.currentTimeMillis() - 3600000L,
            isFavorite = true
        ),
        DocumentItem(
            id = 2,
            uri = "content://sample/2",
            name = "Financial_Report_Q4.xlsx",
            mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            extension = "xlsx",
            size = 1800000L,
            lastOpened = System.currentTimeMillis() - 86400000L,
            isFavorite = false
        )
    )

    CherryTheme {
        HomeScreenContent(
            uiState = HomeUiState(
                recentDocuments = sampleDocs,
                totalDocumentCount = 2,
                selectedCategory = DocumentCategory.ALL
            ),
            onOpenFileClick = {},
            onCategorySelect = {},
            onSearchQueryChange = {},
            onToggleSearch = {},
            onDocumentClick = {},
            onFavoriteToggle = { _, _ -> },
            onDeleteDocument = {},
            onClearHistory = {},
            onSettingsClick = {}
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun HomeScreenEmptyPreview() {
    CherryTheme {
        HomeScreenContent(
            uiState = HomeUiState(
                recentDocuments = emptyList(),
                totalDocumentCount = 0,
                selectedCategory = DocumentCategory.ALL
            ),
            onOpenFileClick = {},
            onCategorySelect = {},
            onSearchQueryChange = {},
            onToggleSearch = {},
            onDocumentClick = {},
            onFavoriteToggle = { _, _ -> },
            onDeleteDocument = {},
            onClearHistory = {},
            onSettingsClick = {}
        )
    }
}
