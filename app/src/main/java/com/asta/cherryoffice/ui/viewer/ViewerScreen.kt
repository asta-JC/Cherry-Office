package com.asta.cherryoffice.ui.viewer

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    viewModel: ViewerViewModel,
    documentUri: Uri,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var showControls by remember { mutableStateOf(true) }
    var showMoreMenu by remember { mutableStateOf(false) }

    LaunchedEffect(documentUri) {
        viewModel.loadDocument(documentUri)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 72.dp)
        ) {
            when (state.documentType) {
                ViewerDocumentType.PDF -> {
                    PdfViewer(
                        uriString = documentUri.toString(),
                        zoomScale = state.zoomScale,
                        nightMode = state.nightMode,
                        onPageCountChanged = { count -> viewModel.updatePageCount(count) },
                        onZoomChanged = { scale -> viewModel.setZoom(scale) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                ViewerDocumentType.TEXT -> {
                    TextViewer(
                        textContent = state.textContent,
                        fontSize = state.fontSize,
                        nightMode = state.nightMode,
                        searchQuery = state.searchQuery,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                ViewerDocumentType.RICH_WORD -> {
                    WordViewer(
                        documentUri = documentUri,
                        documentItem = state.documentItem,
                        onOpenWithAnotherApp = { viewModel.openWithAnotherApp(context, documentUri.toString()) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                ViewerDocumentType.RICH_EXCEL,
                ViewerDocumentType.RICH_PPT -> {
                    RichDocumentViewer(
                        documentItem = state.documentItem,
                        documentType = state.documentType,
                        selectedTab = state.selectedTab,
                        sheetNames = state.sheetNames,
                        slideTitles = state.slideTitles,
                        textContent = state.textContent,
                        wordParagraphs = state.wordParagraphs,
                        nightMode = state.nightMode,
                        onTabSelected = { tab -> viewModel.setSelectedTab(tab) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                ViewerDocumentType.UNSUPPORTED -> {
                    ErrorScreen(
                        documentItem = state.documentItem,
                        errorMessage = state.errorMessage,
                        onOpenWithAnotherApp = { viewModel.openWithAnotherApp(context, documentUri.toString()) }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.documentItem?.name ?: "Document Viewer",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1
                        )
                        Text(
                            text = "${state.documentItem?.extension?.uppercase() ?: ""} • ${state.documentItem?.size?.let { com.asta.cherryoffice.utils.DocumentUtils.formatFileSize(it) } ?: ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.shareDocument(context, documentUri.toString()) }) {
                        Icon(imageVector = Icons.Rounded.Share, contentDescription = "Share Document")
                    }
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(imageVector = Icons.Rounded.MoreVert, contentDescription = "More Options")
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Open with another app") },
                                onClick = {
                                    showMoreMenu = false
                                    viewModel.openWithAnotherApp(context, documentUri.toString())
                                },
                                leadingIcon = { Icon(Icons.Rounded.OpenInNew, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text(if (state.nightMode) "Disable Night Mode" else "Enable Night Mode") },
                                onClick = {
                                    showMoreMenu = false
                                    viewModel.toggleNightMode()
                                },
                                leadingIcon = { Icon(if (state.nightMode) Icons.Rounded.LightMode else Icons.Rounded.DarkMode, contentDescription = null) }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                )
            )
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (state.documentType == ViewerDocumentType.PDF) {
                        IconButton(onClick = { viewModel.setZoom(state.zoomScale - 0.25f) }) {
                            Icon(Icons.Rounded.ZoomOut, contentDescription = "Zoom Out")
                        }
                        Text(
                            text = "${(state.zoomScale * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium
                        )
                        IconButton(onClick = { viewModel.setZoom(state.zoomScale + 0.25f) }) {
                            Icon(Icons.Rounded.ZoomIn, contentDescription = "Zoom In")
                        }
                    } else if (state.documentType == ViewerDocumentType.TEXT) {
                        IconButton(onClick = { viewModel.setFontSize(state.fontSize - 2f) }) {
                            Icon(Icons.Rounded.Remove, contentDescription = "Decrease Font Size")
                        }
                        Text(
                            text = "${state.fontSize.toInt()} sp",
                            style = MaterialTheme.typography.labelLarge
                        )
                        IconButton(onClick = { viewModel.setFontSize(state.fontSize + 2f) }) {
                            Icon(Icons.Rounded.Add, contentDescription = "Increase Font Size")
                        }

                        VerticalDivider(modifier = Modifier.height(24.dp))

                        IconButton(onClick = { viewModel.toggleSearch() }) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "Search",
                                tint = if (state.isSearchActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    } else {
                        Text(
                            text = "Formatted ${state.documentItem?.extension?.uppercase() ?: "Rich"} View",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    VerticalDivider(modifier = Modifier.height(24.dp))

                    IconButton(onClick = { viewModel.toggleNightMode() }) {
                        Icon(
                            imageVector = if (state.nightMode) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                            contentDescription = "Night Mode Toggle",
                            tint = if (state.nightMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(onClick = { showControls = !showControls }) {
                        Icon(
                            imageVector = Icons.Rounded.VisibilityOff,
                            contentDescription = "Hide Controls"
                        )
                    }
                }
            }
        }
    }
}
