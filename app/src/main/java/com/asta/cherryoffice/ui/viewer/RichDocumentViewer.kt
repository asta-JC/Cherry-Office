package com.asta.cherryoffice.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.asta.cherryoffice.data.model.DocumentItem
import com.asta.cherryoffice.utils.DocumentUtils

@Composable
fun RichDocumentViewer(
    documentItem: DocumentItem?,
    documentType: ViewerDocumentType,
    selectedTab: Int,
    sheetNames: List<String>,
    slideTitles: List<String>,
    textContent: String,
    wordParagraphs: List<String>,
    nightMode: Boolean,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (nightMode) Color(0xFF0A0A0F) else MaterialTheme.colorScheme.background
    val textColor = if (nightMode) Color(0xFFE0E0E0) else MaterialTheme.colorScheme.onSurface

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        when (documentType) {
            ViewerDocumentType.RICH_EXCEL -> {
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = if (nightMode) Color(0xFF140E12) else MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    edgePadding = 16.dp
                ) {
                    sheetNames.forEachIndexed { index, name ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { onTabSelected(index) },
                            icon = { Icon(Icons.Rounded.Assessment, contentDescription = null) },
                            text = { Text(name) }
                        )
                    }
                }
            }
            ViewerDocumentType.RICH_PPT -> {
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = if (nightMode) Color(0xFF140E12) else MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    edgePadding = 16.dp
                ) {
                    slideTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { onTabSelected(index) },
                            icon = { Icon(Icons.Rounded.Slideshow, contentDescription = null) },
                            text = { Text("Slide ${index + 1}: $title") }
                        )
                    }
                }
            }
            else -> {
                Surface(
                    color = if (nightMode) Color(0xFF1C1520) else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Word Document Viewer",
                                style = MaterialTheme.typography.titleMedium,
                                color = textColor
                            )
                            Text(
                                text = "Extracted structured OpenXML paragraphs & list view",
                                style = MaterialTheme.typography.bodySmall,
                                color = textColor.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (documentType) {
                ViewerDocumentType.RICH_EXCEL -> {
                    ExcelSheetTablePreview(selectedTab, nightMode)
                }
                ViewerDocumentType.RICH_PPT -> {
                    PptSlidePreview(selectedTab, slideTitles, nightMode)
                }
                else -> {
                    WordDocumentContentPreview(documentItem, wordParagraphs, nightMode)
                }
            }
        }
    }
}

@Composable
fun ExcelSheetTablePreview(sheetIndex: Int, nightMode: Boolean) {
    val cardBg = if (nightMode) Color(0xFF1C1520) else MaterialTheme.colorScheme.surface
    val textColor = if (nightMode) Color(0xFFE0E0E0) else MaterialTheme.colorScheme.onSurface
    val rows = listOf(
        listOf("ID", "Product Name", "Category", "Units Sold", "Revenue"),
        listOf("101", "Cherry Office Suite Pro", "Software", "1,240", "$49,600"),
        listOf("102", "Cloud Sync Extension", "Services", "850", "$17,000"),
        listOf("103", "Enterprise Security Pack", "Security", "320", "$32,000"),
        listOf("104", "Analytics Dashboard Module", "Analytics", "610", "$18,300"),
        listOf("105", "Mobile Companion App", "Mobile", "2,100", "$21,000")
    )

    SelectionContainer {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "Spreadsheet Sheet #${sheetIndex + 1} Data View",
                style = MaterialTheme.typography.titleMedium,
                color = textColor,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = cardBg),
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    itemsIndexed(rows) { rowIndex, row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (rowIndex == 0) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent)
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            row.forEach { cell ->
                                Text(
                                    text = cell,
                                    style = if (rowIndex == 0) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
                                    color = textColor,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    }
                }
            }
        }
    }
}

@Composable
fun PptSlidePreview(slideIndex: Int, slideTitles: List<String>, nightMode: Boolean) {
    val cardBg = if (nightMode) Color(0xFF1C1520) else MaterialTheme.colorScheme.surface
    val textColor = if (nightMode) Color(0xFFE0E0E0) else MaterialTheme.colorScheme.onSurface
    val title = slideTitles.getOrElse(slideIndex) { "Slide Title" }
    val scrollState = rememberScrollState()

    SelectionContainer {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .heightIn(min = 320.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Slide ${slideIndex + 1} Content Overview:\n• Key architectural insights and structured highlights\n• Professional presentation rendering\n• Smooth slide navigation controls",
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor
                    )
                }
            }
        }
    }
}

@Composable
fun WordDocumentContentPreview(documentItem: DocumentItem?, wordParagraphs: List<String>, nightMode: Boolean) {
    val cardBg = if (nightMode) Color(0xFF1C1520) else MaterialTheme.colorScheme.surface
    val textColor = if (nightMode) Color(0xFFE0E0E0) else MaterialTheme.colorScheme.onSurface

    SelectionContainer {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = cardBg)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = documentItem?.name ?: "Word Document",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Size: ${DocumentUtils.formatFileSize(documentItem?.size ?: 0L)} | Type: ${documentItem?.extension?.uppercase()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor.copy(alpha = 0.8f)
                        )
                    }
                }
            }
            item {
                Text(
                    text = "Document Paragraphs (${wordParagraphs.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            itemsIndexed(wordParagraphs) { index, paragraph ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(28.dp)
                        )
                        Text(
                            text = paragraph,
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
