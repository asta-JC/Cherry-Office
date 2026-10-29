package com.asta.cherryoffice.ui.viewer

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import com.asta.cherryoffice.ui.components.ZoomableBox
import com.asta.cherryoffice.utils.DocumentUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(viewModel: ViewerViewModel, documentUri: Uri, onBackClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val darkSurroundings = MaterialTheme.colorScheme.background.luminance() < .5f
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showControls by rememberSaveable(documentUri.toString()) { mutableStateOf(true) }
    var more by remember { mutableStateOf(false) }
    var showPageDialog by remember { mutableStateOf(false) }
    var pageInput by remember { mutableStateOf("") }
    var requestedPage by remember(documentUri) { mutableStateOf<Int?>(null) }
    var pageRequestId by remember(documentUri) { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val ownsDocument = state.sourceUri == documentUri.toString()
    fun stillOwnsDocument() = viewModel.uiState.value.sourceUri == documentUri.toString()
    val canSearch = ownsDocument && state.documentType in listOf(ViewerDocumentType.TEXT, ViewerDocumentType.RICH_EXCEL, ViewerDocumentType.RICH_WORD)
    val paged = ownsDocument && state.documentType in listOf(ViewerDocumentType.PDF, ViewerDocumentType.RICH_WORD)
    fun goTo(page: Int) { requestedPage = page.coerceIn(0, state.pageCount - 1); pageRequestId++ }

    LaunchedEffect(documentUri) { viewModel.loadDocument(documentUri) }
    LaunchedEffect(state.notice) {
        state.notice?.let { snackbar.showSnackbar(it); viewModel.clearActionMessage() }
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (showControls) Column {
                TopAppBar(
                    title = { Column {
                        Text(if (ownsDocument) state.documentItem?.name ?: "Opening document…" else "Opening document…", maxLines = 1,
                            overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        state.documentItem?.takeIf { ownsDocument }?.let { item -> Text(
                            "${item.extension.uppercase()}  •  ${DocumentUtils.formatFileSize(item.size)}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    } },
                    navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                    actions = {
                        if (canSearch && !state.isLoading) IconButton(onClick = viewModel::toggleSearch) { Icon(Icons.Rounded.Search, "Find in document") }
                        IconButton(onClick = { more = true }) { Icon(Icons.Rounded.MoreVert, "Document options") }
                        DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                            DropdownMenuItem(text = { Text("Share") }, leadingIcon = { Icon(Icons.Rounded.Share, null) },
                                onClick = { more = false; viewModel.shareDocument(context, documentUri.toString()) })
                            DropdownMenuItem(text = { Text("Open with another app") }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null) },
                                onClick = { more = false; viewModel.openWithAnotherApp(context, documentUri.toString()) })
                        }
                    })
                if (state.isSearchActive && canSearch) OutlinedTextField(
                    value = state.searchQuery, onValueChange = viewModel::setSearchQuery,
                    placeholder = { Text("Find in document") }, singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = { IconButton(onClick = viewModel::toggleSearch) { Icon(Icons.Rounded.Close, "Close search") } },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp))
                HorizontalDivider()
            }
        },
        bottomBar = {
            if (showControls && ownsDocument && !state.isLoading && state.documentType != ViewerDocumentType.UNSUPPORTED) Surface(tonalElevation = 3.dp) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    if (paged) {
                        IconButton(enabled = state.currentPage > 0, onClick = { goTo(state.currentPage - 1) }) { Icon(Icons.Rounded.ChevronLeft, "Previous page") }
                        TextButton(onClick = { pageInput = (state.currentPage + 1).toString(); showPageDialog = true }) { Text("${state.currentPage + 1} / ${state.pageCount}") }
                        IconButton(enabled = state.currentPage < state.pageCount - 1, onClick = { goTo(state.currentPage + 1) }) { Icon(Icons.Rounded.ChevronRight, "Next page") }
                    }
                    if (state.documentType == ViewerDocumentType.PDF) {
                        IconButton(enabled = state.zoomScale > 1f, onClick = { viewModel.setZoom(state.zoomScale - .25f) }) { Icon(Icons.Rounded.ZoomOut, "Zoom out") }
                        TextButton(onClick = { viewModel.setZoom(1f) }) { Text("${(state.zoomScale * 100).toInt()}%") }
                        IconButton(enabled = state.zoomScale < 5f, onClick = { viewModel.setZoom(state.zoomScale + .25f) }) { Icon(Icons.Rounded.ZoomIn, "Zoom in") }
                    }
                    if (state.documentType == ViewerDocumentType.TEXT) {
                        IconButton(onClick = { viewModel.setFontSize(state.fontSize - 2) }) { Icon(Icons.Rounded.Remove, "Smaller text") }
                        Text("${state.fontSize.toInt()} sp", style = MaterialTheme.typography.labelLarge)
                        IconButton(onClick = { viewModel.setFontSize(state.fontSize + 2) }) { Icon(Icons.Rounded.Add, "Larger text") }
                    }
                    IconButton(onClick = { showControls = false }) { Icon(Icons.Rounded.VisibilityOff, "Read full screen") }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            // A new URI must never reuse a previous viewer's local selection or rendering state.
            key(documentUri) {
                if (state.isLoading || !ownsDocument) Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("Opening document…")
                } else when (state.documentType) {
                    ViewerDocumentType.PDF -> PdfViewer(
                        uriString = documentUri.toString(), zoomScale = state.zoomScale, nightMode = darkSurroundings,
                        onPageCountChanged = { if (stillOwnsDocument()) viewModel.updatePageCount(it) }, onZoomChanged = { if (stillOwnsDocument()) viewModel.setZoom(it) },
                        onCurrentPageChanged = { if (stillOwnsDocument()) viewModel.setPage(it) }, requestedPage = requestedPage, pageRequestId = pageRequestId, initialPage = state.currentPage,
                        onOpenWithAnotherApp = { viewModel.openWithAnotherApp(context, documentUri.toString()) })
                    ViewerDocumentType.RICH_WORD -> WordViewer(
                        documentUri = documentUri, documentItem = state.documentItem, nightMode = darkSurroundings,
                        searchQuery = state.searchQuery, requestedPage = requestedPage, pageRequestId = pageRequestId,
                        onPageCountChanged = { if (stillOwnsDocument()) viewModel.updatePageCount(it) }, onCurrentPageChanged = { if (stillOwnsDocument()) viewModel.setPage(it - 1) },
                        onOpenWithAnotherApp = { viewModel.openWithAnotherApp(context, documentUri.toString()) })
                    ViewerDocumentType.RICH_EXCEL -> ExcelViewer(state.spreadsheetDocument, darkSurroundings, state.searchQuery)
                    ViewerDocumentType.RICH_PPT -> PresentationViewer(state.slides, false)
                    ViewerDocumentType.TEXT -> TextViewer(state.textContent, state.fontSize, darkSurroundings, state.searchQuery)
                    ViewerDocumentType.IMAGE -> ZoomableBox(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)) {
                        SubcomposeAsyncImage(model = documentUri, contentDescription = state.documentItem?.name,
                            modifier = Modifier.fillMaxSize(), loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } },
                            error = { ErrorScreen(state.documentItem, "This image could not be decoded on this device.", { viewModel.openWithAnotherApp(context, documentUri.toString()) }) })
                    }
                    ViewerDocumentType.UNSUPPORTED -> ErrorScreen(state.documentItem, state.errorMessage,
                        { viewModel.openWithAnotherApp(context, documentUri.toString()) })
                }
            }
            if (!showControls) FilledTonalIconButton(onClick = { showControls = true }, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp)) {
                Icon(Icons.Rounded.Visibility, "Show reading controls")
            }
        }
    }
    if (showPageDialog) AlertDialog(
        onDismissRequest = { showPageDialog = false }, title = { Text("Go to page") },
        text = { OutlinedTextField(pageInput, { pageInput = it.filter(Char::isDigit).take(7) },
            label = { Text("Page 1–${state.pageCount}") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) },
        confirmButton = { TextButton(enabled = pageInput.toIntOrNull() in 1..state.pageCount,
            onClick = { goTo(pageInput.toInt() - 1); showPageDialog = false }) { Text("Go") } },
        dismissButton = { TextButton(onClick = { showPageDialog = false }) { Text("Cancel") } })
}
