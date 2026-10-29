package com.asta.cherryoffice.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.asta.cherryoffice.data.model.DocumentItem
import com.asta.cherryoffice.ui.components.CherryDocumentCard
import com.asta.cherryoffice.ui.components.CherryMark
import com.asta.cherryoffice.utils.DocumentCategory

@Composable
fun HomeScreen(viewModel: HomeViewModel, onDocumentOpenRequested: (DocumentItem) -> Unit = {}, onSettingsClick: () -> Unit = {}) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val file = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::onDocumentSelected) }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(viewModel::scanDirectory) }
    LaunchedEffect(state.openedDocument) { state.openedDocument?.let { onDocumentOpenRequested(it); viewModel.clearOpenedDocumentEvent() } }
    LaunchedEffect(state.errorMessage) { state.errorMessage?.let { snackbar.showSnackbar(it); viewModel.clearError() } }
    HomeScreenContent(state, { file.launch(arrayOf("*/*")) }, { folder.launch(null) }, viewModel::scanDeviceStorage,
        viewModel::onCategorySelected, viewModel::onToggleFavoritesOnly, viewModel::onSortOrderChanged,
        viewModel::onSearchQueryChanged, viewModel::toggleSearchVisibility,
        { viewModel.onDocumentSelected(Uri.parse(it.uri)) }, viewModel::onFavoriteToggled,
        viewModel::onDeleteDocument, viewModel::shareDocument, onSettingsClick, snackbar)
}

@OptIn(ExperimentalMaterial3Api::class)
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
    var sort by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var remove by remember { mutableStateOf<Long?>(null) }
    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }, topBar = {
        TopAppBar(title = { Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CherryMark(Modifier.size(24.dp))
                Text("Cherry Office", style = MaterialTheme.typography.titleLarge)
            }
            Text("Your documents, together", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }, actions = {
            IconButton(onClick = onSettingsClick) { Icon(Icons.Rounded.Settings, "Settings") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Library options") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Add a folder") }, onClick = { menu = false; onScanFolderClick() }, leadingIcon = { Icon(Icons.Rounded.FolderOpen, null) })
                    DropdownMenuItem(text = { Text("Refresh files") }, onClick = { menu = false; onScanStorageClick() }, leadingIcon = { Icon(Icons.Rounded.Refresh, null) })
                }
            }
        })
    }, floatingActionButton = {
        ExtendedFloatingActionButton(onClick = onOpenFileClick, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Open file") },
            containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(value = uiState.searchQuery, onValueChange = onSearchQueryChange,
                placeholder = { Text("Search your files") }, singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (uiState.searchQuery.isNotEmpty()) IconButton(onClick = { onSearchQueryChange("") }) { Icon(Icons.Rounded.Close, "Clear search") } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), shape = MaterialTheme.shapes.large)
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf(DocumentCategory.ALL, DocumentCategory.PDF, DocumentCategory.WORD, DocumentCategory.EXCEL, DocumentCategory.POWERPOINT, DocumentCategory.OTHER)) { category ->
                    FilterChip(selected = !uiState.showOnlyFavorites && uiState.selectedCategory == category,
                        onClick = { onCategorySelect(category) }, label = { Text(if (category == DocumentCategory.OTHER) "Other" else category.displayName) })
                }
                item { FilterChip(selected = uiState.showOnlyFavorites, onClick = onToggleFavorites, label = { Text("Favorites") }, leadingIcon = { Icon(Icons.Rounded.StarOutline, null, Modifier.size(18.dp)) }) }
            }
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (uiState.showOnlyFavorites) "Favorites" else "Recent files", style = MaterialTheme.typography.titleMedium)
                    Text("${uiState.recentDocuments.size} files", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { sort = true }) { Icon(Icons.AutoMirrored.Rounded.Sort, "Sort files") }
            }
            if (uiState.isScanning) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp))
            if (uiState.recentDocuments.isEmpty()) Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CherryMark(Modifier.size(48.dp))
                    Text(if (uiState.searchQuery.isNotBlank()) "No matching files" else "A little space for your files", style = MaterialTheme.typography.titleMedium)
                    Text(if (uiState.searchQuery.isNotBlank()) "Try another name or file type." else "Open a document to start reading. It will appear here next time.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = onScanFolderClick) { Text("Browse a folder") }
                }
            } else LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 104.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(uiState.recentDocuments, key = { it.uri + it.id }) { doc ->
                    CherryDocumentCard(doc, { onDocumentClick(doc) }, { onFavoriteToggle(doc.id, !doc.isFavorite) },
                        { remove = doc.id }, { onShareDocument(doc) })
                }
            }
        }
    }
    if (sort) AlertDialog(onDismissRequest = { sort = false }, title = { Text("Sort files") }, text = {
        Column { DocumentSortOrder.entries.forEach { order ->
            TextButton(onClick = { onSortOrderChange(order); sort = false }, modifier = Modifier.fillMaxWidth()) {
                RadioButton(uiState.sortOrder == order, null)
                Spacer(Modifier.width(12.dp)); Text(order.displayName, Modifier.weight(1f))
            }
        } }
    }, confirmButton = { TextButton(onClick = { sort = false }) { Text("Done") } })
    if (remove != null) AlertDialog(onDismissRequest = { remove = null }, title = { Text("Remove from recent files?") },
        text = { Text("The document on your device will not be deleted.") },
        confirmButton = { TextButton(onClick = { remove?.let(onDeleteDocument); remove = null }) { Text("Remove") } },
        dismissButton = { TextButton(onClick = { remove = null }) { Text("Cancel") } })
}
