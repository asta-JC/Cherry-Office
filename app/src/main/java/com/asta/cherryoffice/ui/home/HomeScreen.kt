package com.asta.cherryoffice.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asta.cherryoffice.data.model.DocumentItem
import com.asta.cherryoffice.ui.components.CherryButton
import com.asta.cherryoffice.ui.components.CherryButtonVariant
import com.asta.cherryoffice.ui.components.CherryChip
import com.asta.cherryoffice.ui.components.CherryDocumentCard
import com.asta.cherryoffice.ui.components.CherryFab
import com.asta.cherryoffice.ui.components.CherryIconButton
import com.asta.cherryoffice.ui.components.GlowBackground
import com.asta.cherryoffice.ui.components.QuickStatsRow
import com.asta.cherryoffice.ui.theme.CherryTheme
import com.asta.cherryoffice.utils.DocumentCategory

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onDocumentOpenRequested: (DocumentItem) -> Unit = {},
    onSettingsClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.onDocumentSelected(it) }
    }

    val openFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { viewModel.scanDirectory(it) }
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
                    "text/plain",
                    "text/csv"
                )
            )
        },
        onScanFolderClick = {
            openFolderLauncher.launch(null)
        },
        onScanStorageClick = { viewModel.scanDeviceStorage() },
        onCategorySelect = { viewModel.onCategorySelected(it) },
        onToggleFavorites = { viewModel.onToggleFavoritesOnly() },
        onSortOrderChange = { viewModel.onSortOrderChanged(it) },
        onSearchQueryChange = { viewModel.onSearchQueryChanged(it) },
        onToggleSearch = { viewModel.toggleSearchVisibility() },
        onDocumentClick = { doc ->
            val uri = Uri.parse(doc.uri)
            viewModel.onDocumentSelected(uri)
        },
        onFavoriteToggle = { id, isFav -> viewModel.onFavoriteToggled(id, isFav) },
        onDeleteDocument = { id -> viewModel.onDeleteDocument(id) },
        onShareDocument = { doc -> viewModel.shareDocument(doc) },
        onSettingsClick = onSettingsClick,
        snackbarHostState = snackbarHostState
    )
}

@Composable
fun HomeScreenContent(
    uiState: HomeUiState,
    onOpenFileClick: () -> Unit,
    onScanFolderClick: () -> Unit,
    onScanStorageClick: () -> Unit,
    onCategorySelect: (DocumentCategory) -> Unit,
    onToggleFavorites: () -> Unit,
    onSortOrderChange: (DocumentSortOrder) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onDocumentClick: (DocumentItem) -> Unit,
    onFavoriteToggle: (Long, Boolean) -> Unit,
    onDeleteDocument: (Long) -> Unit,
    onShareDocument: (DocumentItem) -> Unit,
    onSettingsClick: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    val colors = CherryTheme.colors
    var showSortDialog by remember { mutableStateOf(false) }

    GlowBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 18.dp)
            ) {
                Spacer(modifier = Modifier.height(10.dp))

                // Top Bar with Cherry Branding & Actions
                HomeTopBar(
                    isSearchVisible = uiState.isSearchVisible,
                    isScanning = uiState.isScanning,
                    onToggleSearch = onToggleSearch,
                    onSortClick = { showSortDialog = true },
                    onRefreshClick = onScanStorageClick,
                    onSettingsClick = onSettingsClick
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Expandable Search Bar
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
                            .padding(bottom = 12.dp)
                    )
                }

                // Quick Format Stats Dashboard Cards
                QuickStatsRow(
                    pdfCount = uiState.pdfCount,
                    wordCount = uiState.wordCount,
                    excelCount = uiState.excelCount,
                    pptCount = uiState.pptCount,
                    selectedCategory = if (uiState.showOnlyFavorites) DocumentCategory.OTHER else uiState.selectedCategory,
                    onCategoryClick = onCategorySelect,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Category Filter Pills
                CategoryChipsRow(
                    selectedCategory = uiState.selectedCategory,
                    showOnlyFavorites = uiState.showOnlyFavorites,
                    favoriteCount = uiState.favoriteCount,
                    totalCount = uiState.totalDocumentCount,
                    onCategorySelect = onCategorySelect,
                    onToggleFavorites = onToggleFavorites
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Section Header with Document Count & Active Sort
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (uiState.showOnlyFavorites) "FAVORITES" else if (uiState.selectedCategory == DocumentCategory.ALL) "ALL DOCUMENTS" else uiState.selectedCategory.displayName.uppercase(),
                            style = CherryTheme.typography.sectionHeader,
                            color = colors.textWhite
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "(${uiState.recentDocuments.size})",
                            style = CherryTheme.typography.labelMonospace.copy(fontWeight = FontWeight.Bold),
                            color = colors.primary
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showSortDialog = true }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "Sort",
                            tint = colors.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = uiState.sortOrder.displayName,
                            style = CherryTheme.typography.labelMonospace,
                            color = colors.textSoftGreyPurple
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Document List or Empty State
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (uiState.recentDocuments.isEmpty()) {
                        EmptyDocumentState(
                            searchQuery = uiState.searchQuery,
                            isScanning = uiState.isScanning,
                            onScanStorage = onScanStorageClick,
                            onOpenFile = onOpenFileClick
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 88.dp)
                        ) {
                            items(
                                items = uiState.recentDocuments,
                                key = { it.uri + it.id }
                            ) { doc ->
                                CherryDocumentCard(
                                    document = doc,
                                    onClick = { onDocumentClick(doc) },
                                    onFavoriteToggle = { onFavoriteToggle(doc.id, !doc.isFavorite) },
                                    onDeleteClick = { onDeleteDocument(doc.id) },
                                    onShareClick = { onShareDocument(doc) }
                                )
                            }
                        }
                    }
                }
            }

            // Floating Cherry Action Button (FAB)
            CherryFab(
                onOpenFile = onOpenFileClick,
                onScanFolder = onScanFolderClick,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 28.dp)
            )

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            )
        }
    }

    // Sort Order Selection Dialog
    if (showSortDialog) {
        SortOrderDialog(
            currentOrder = uiState.sortOrder,
            onOrderSelected = {
                onSortOrderChange(it)
                showSortDialog = false
            },
            onDismiss = { showSortDialog = false }
        )
    }
}

@Composable
fun HomeTopBar(
    isSearchVisible: Boolean,
    isScanning: Boolean,
    onToggleSearch: () -> Unit,
    onSortClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val colors = CherryTheme.colors

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // App Identity with Cherry Badge
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(colors.gradientStart, colors.gradientEnd)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🍒",
                    fontSize = 22.sp
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Text(
                    text = "CHERRY OFFICE",
                    style = CherryTheme.typography.appTitle
                )
                Text(
                    text = "All-Document Opener",
                    style = CherryTheme.typography.labelMonospace
                )
            }
        }

        // Top Action Icons
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CherryIconButton(
                icon = Icons.Default.Search,
                contentDescription = "Search",
                onClick = onToggleSearch,
                containerColor = if (isSearchVisible) colors.deepCherry.copy(alpha = 0.5f) else colors.card,
                borderColor = if (isSearchVisible) colors.primary else colors.borderTranslucent,
                size = 38.dp,
                iconSize = 19.dp
            )

            val rotation = if (isScanning) {
                val infiniteTransition = rememberInfiniteTransition(label = "scan_rotation")
                val angle by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "scan_spin"
                )
                angle
            } else 0f

            CherryIconButton(
                icon = Icons.Default.Sync,
                contentDescription = "Scan Storage",
                onClick = onRefreshClick,
                size = 38.dp,
                iconSize = 19.dp,
                modifier = Modifier.rotate(rotation)
            )

            CherryIconButton(
                icon = Icons.Default.Settings,
                contentDescription = "Settings",
                onClick = onSettingsClick,
                size = 38.dp,
                iconSize = 19.dp
            )
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
            .clip(RoundedCornerShape(20.dp))
            .background(colors.card)
            .border(1.dp, colors.primary.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(19.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = "Search all documents (PDF, DOCX, XLSX...)",
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
                    modifier = Modifier.size(22.dp)
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
fun CategoryChipsRow(
    selectedCategory: DocumentCategory,
    showOnlyFavorites: Boolean,
    favoriteCount: Int,
    totalCount: Int,
    onCategorySelect: (DocumentCategory) -> Unit,
    onToggleFavorites: () -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // "All" chip
        item {
            CherryChip(
                text = "All",
                isSelected = (!showOnlyFavorites && selectedCategory == DocumentCategory.ALL),
                count = totalCount,
                onClick = { onCategorySelect(DocumentCategory.ALL) }
            )
        }

        // Format chips
        items(
            listOf(
                DocumentCategory.PDF,
                DocumentCategory.WORD,
                DocumentCategory.EXCEL,
                DocumentCategory.POWERPOINT
            )
        ) { category ->
            CherryChip(
                text = category.displayName,
                isSelected = (!showOnlyFavorites && selectedCategory == category),
                onClick = { onCategorySelect(category) }
            )
        }

        // Favorites chip
        item {
            CherryChip(
                text = "Starred",
                icon = Icons.Default.Star,
                isSelected = showOnlyFavorites,
                count = favoriteCount,
                onClick = onToggleFavorites
            )
        }
    }
}

@Composable
fun EmptyDocumentState(
    searchQuery: String,
    isScanning: Boolean,
    onScanStorage: () -> Unit,
    onOpenFile: () -> Unit
) {
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
                .size(76.dp)
                .clip(CircleShape)
                .background(colors.deepCherry.copy(alpha = 0.35f))
                .border(1.5.dp, colors.primary.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (isScanning) {
                CircularProgressIndicator(
                    color = colors.primary,
                    modifier = Modifier.size(36.dp),
                    strokeWidth = 3.dp
                )
            } else {
                Text(
                    text = "🍒",
                    fontSize = 36.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (isScanning) "SCANNING STORAGE…" else if (searchQuery.isNotBlank()) "NO MATCHING DOCUMENTS" else "NO DOCUMENTS FOUND",
            style = CherryTheme.typography.sectionHeader,
            color = colors.textWhite
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (isScanning) {
                "Indexing PDF, Word, Excel, and presentations from your device storage..."
            } else if (searchQuery.isNotBlank()) {
                "No documents matching \"$searchQuery\"."
            } else {
                "Scan your phone storage to list all documents automatically, or pick a file to view."
            },
            style = CherryTheme.typography.documentMeta,
            textAlign = TextAlign.Center,
            color = colors.textSoftGreyPurple
        )

        if (!isScanning && searchQuery.isBlank()) {
            Spacer(modifier = Modifier.height(24.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CherryButton(
                    text = "SCAN STORAGE",
                    icon = Icons.Default.Sync,
                    variant = CherryButtonVariant.MAIN,
                    onClick = onScanStorage,
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                )

                CherryButton(
                    text = "BROWSE FILE",
                    icon = Icons.Default.FolderOpen,
                    variant = CherryButtonVariant.ACTION,
                    onClick = onOpenFile,
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
fun SortOrderDialog(
    currentOrder: DocumentSortOrder,
    onOrderSelected: (DocumentSortOrder) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = CherryTheme.colors

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = "Sort Documents",
                style = CherryTheme.typography.sectionHeader
            )
        },
        text = {
            Column {
                DocumentSortOrder.entries.forEach { order ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onOrderSelected(order) }
                            .padding(vertical = 8.dp, horizontal = 4.dp)
                    ) {
                        RadioButton(
                            selected = (currentOrder == order),
                            onClick = { onOrderSelected(order) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = colors.primary,
                                unselectedColor = colors.textSoftGreyPurple
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = order.displayName,
                            style = CherryTheme.typography.documentBody,
                            color = if (currentOrder == order) colors.primary else colors.textWhite
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Cancel",
                    color = colors.textSoftGreyPurple
                )
            }
        }
    )
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
                pdfCount = 1,
                excelCount = 1,
                selectedCategory = DocumentCategory.ALL
            ),
            onOpenFileClick = {},
            onScanFolderClick = {},
            onScanStorageClick = {},
            onCategorySelect = {},
            onToggleFavorites = {},
            onSortOrderChange = {},
            onSearchQueryChange = {},
            onToggleSearch = {},
            onDocumentClick = {},
            onFavoriteToggle = { _, _ -> },
            onDeleteDocument = {},
            onShareDocument = {},
            onSettingsClick = {}
        )
    }
}
