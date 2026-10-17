package com.asta.cherryoffice.ui.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asta.cherryoffice.excel.CellData
import com.asta.cherryoffice.excel.SheetData
import com.asta.cherryoffice.excel.SpreadsheetDocument
import com.asta.cherryoffice.excel.XlsxFileReader
import com.asta.cherryoffice.ui.theme.CherryTheme
import kotlinx.coroutines.delay

@Composable
fun ExcelViewer(
    document: SpreadsheetDocument?,
    nightMode: Boolean,
    searchQuery: String = "",
    modifier: Modifier = Modifier
) {
    if (document == null || document.sheets.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(if (nightMode) Color(0xFF0F0D13) else MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Rounded.TableChart,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No spreadsheet sheets found.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
        return
    }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val sheets = document.sheets
    val currentSheet = sheets.getOrElse(selectedTabIndex) { sheets[0] }

    val colors = CherryTheme.colors
    val bgColor = if (nightMode) Color(0xFF110E17) else Color(0xFFF8FAFC)
    val cardBg = if (nightMode) Color(0xFF1B1724) else Color(0xFFFFFFFF)
    val textColor = if (nightMode) Color(0xFFEDEDED) else Color(0xFF1E293B)
    val borderColor = if (nightMode) Color(0xFF2E273B) else Color(0xFFE2E8F0)

    var zoomScale by remember { mutableFloatStateOf(1.0f) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        // Multi-sheet tab row if there is more than 1 sheet
        if (sheets.size > 1) {
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = if (nightMode) Color(0xFF16121E) else Color(0xFFEFF2F6),
                contentColor = colors.primary,
                edgePadding = 12.dp,
                indicator = { tabPositions ->
                    if (selectedTabIndex < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                            color = colors.primary,
                            height = 3.dp
                        )
                    }
                }
            ) {
                sheets.forEachIndexed { index, sheet ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.TableChart,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (selectedTabIndex == index) colors.primary else if (nightMode) Color(0xFF8E84A0) else Color(0xFF64748B)
                            )
                        },
                        text = {
                            Text(
                                text = sheet.name,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp,
                                maxLines = 1,
                                color = if (selectedTabIndex == index) colors.primary else if (nightMode) Color(0xFFD4CCE0) else Color(0xFF334155)
                            )
                        }
                    )
                }
            }
        }

        // Sheet Status & Info Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (nightMode) Color(0xFF191422) else Color(0xFFF1F5F9))
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = currentSheet.name,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${currentSheet.rows.size} rows × ${currentSheet.maxColumns} cols",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (nightMode) Color(0xFF9E95B0) else Color(0xFF64748B)
                )
            }

            // Zoom controls pill
            Surface(
                shape = CircleShape,
                color = if (nightMode) Color(0xFF251E31) else Color(0xFFE2E8F0),
                modifier = Modifier.height(26.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    IconButton(
                        onClick = { zoomScale = (zoomScale - 0.15f).coerceAtLeast(0.65f) },
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Remove,
                            contentDescription = "Zoom Out",
                            modifier = Modifier.size(14.dp),
                            tint = if (nightMode) Color(0xFFE2E8F0) else Color(0xFF334155)
                        )
                    }
                    Text(
                        text = "${(zoomScale * 100).toInt()}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (nightMode) Color(0xFFE2E8F0) else Color(0xFF334155),
                        modifier = Modifier
                            .clickable { zoomScale = 1.0f }
                            .padding(horizontal = 4.dp)
                    )
                    IconButton(
                        onClick = { zoomScale = (zoomScale + 0.15f).coerceAtMost(1.8f) },
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Zoom In",
                            modifier = Modifier.size(14.dp),
                            tint = if (nightMode) Color(0xFFE2E8F0) else Color(0xFF334155)
                        )
                    }
                }
            }
        }

        // Spreadsheet Data Grid Table
        SpreadsheetGrid(
            sheet = currentSheet,
            nightMode = nightMode,
            textColor = textColor,
            borderColor = borderColor,
            cardBg = cardBg,
            zoomScale = zoomScale,
            searchQuery = searchQuery,
            onZoomChange = { zoomScale = it },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )
    }
}

@Composable
fun SpreadsheetGrid(
    sheet: SheetData,
    nightMode: Boolean,
    textColor: Color,
    borderColor: Color,
    cardBg: Color,
    zoomScale: Float,
    searchQuery: String,
    onZoomChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val hScrollState = rememberScrollState()
    val colors = CherryTheme.colors
    val maxCols = sheet.maxColumns.coerceAtLeast(1)
    val clipboardManager = LocalClipboardManager.current

    // Auto-fit column widths based on sampled cell contents
    val baseColumnWidths = remember(sheet) {
        val widths = FloatArray(maxCols) { 85f }
        val sampleRows = sheet.rows.take(60)
        for (colIdx in 0 until maxCols) {
            val colLetter = XlsxFileReader.indexToColumnName(colIdx)
            var maxLen = colLetter.length
            for (r in sampleRows) {
                val cellVal = r.cells.getOrNull(colIdx)?.value.orEmpty()
                if (cellVal.length > maxLen) {
                    maxLen = cellVal.length
                }
            }
            // Natural dynamic width: numbers/short strings stay compact (~65-90dp),
            // sentences and titles have ample room (~160-260dp).
            val optimal = (maxLen * 8.5f + 26f).coerceIn(65f, 260f)
            widths[colIdx] = optimal
        }
        widths
    }

    val rowHeaderWidth = (44.dp * zoomScale).coerceIn(36.dp, 60.dp)
    val rowHeight = (38.dp * zoomScale).coerceIn(30.dp, 56.dp)
    val headerHeight = (34.dp * zoomScale).coerceIn(28.dp, 50.dp)
    val fontSize = (12f * zoomScale).coerceIn(9f, 16f).sp
    val headerFontSize = (11f * zoomScale).coerceIn(9f, 14f).sp

    var selectedCell by remember { mutableStateOf<CellData?>(null) }
    var copiedFeedback by remember { mutableStateOf(false) }

    LaunchedEffect(copiedFeedback) {
        if (copiedFeedback) {
            delay(1500)
            copiedFeedback = false
        }
    }

    val selectedCol = selectedCell?.columnIndex
    val selectedRow = selectedCell?.rowIndex

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    if (zoom != 1f) {
                        onZoomChange((zoomScale * zoom).coerceIn(0.65f, 1.8f))
                    }
                }
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Sticky Column Headers (A, B, C...)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (nightMode) Color(0xFF181422) else Color(0xFFF1F5F9))
                    .border(0.5.dp, borderColor)
            ) {
                // Top-left corner (# / All)
                Box(
                    modifier = Modifier
                        .width(rowHeaderWidth)
                        .height(headerHeight)
                        .background(if (nightMode) Color(0xFF221B2E) else Color(0xFFE2E8F0))
                        .border(0.5.dp, borderColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = colors.primary
                    )
                }

                // Horizontally scrolled column letters
                Row(modifier = Modifier.horizontalScroll(hScrollState)) {
                    for (colIdx in 0 until maxCols) {
                        val colW = (baseColumnWidths[colIdx] * zoomScale).dp
                        val isColActive = (colIdx == selectedCol)

                        Box(
                            modifier = Modifier
                                .width(colW)
                                .height(headerHeight)
                                .background(
                                    if (isColActive) colors.primary.copy(alpha = 0.22f)
                                    else if (nightMode) Color(0xFF181422)
                                    else Color(0xFFF1F5F9)
                                )
                                .border(0.5.dp, borderColor)
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = XlsxFileReader.indexToColumnName(colIdx),
                                fontSize = headerFontSize,
                                fontWeight = if (isColActive) FontWeight.ExtraBold else FontWeight.SemiBold,
                                color = if (isColActive) colors.primary else if (nightMode) Color(0xFFD4CCE0) else Color(0xFF475569),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Data Rows (Sticky row index on left, horizontally synced cells)
            SelectionContainer(modifier = Modifier.weight(1f)) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(sheet.rows, key = { index, _ -> "row_$index" }) { rowIndex, row ->
                        val isFirstRow = (rowIndex == 0)
                        val isRowActive = (row.rowIndex == selectedRow)

                        val defaultRowBg = when {
                            isFirstRow -> if (nightMode) colors.primary.copy(alpha = 0.12f) else Color(0xFFEFF6FF)
                            rowIndex % 2 == 1 -> if (nightMode) Color(0xFF15111D) else Color(0xFFFAFBFC)
                            else -> if (nightMode) Color(0xFF120E19) else Color(0xFFFFFFFF)
                        }

                        val rowBg = if (isRowActive) colors.primary.copy(alpha = 0.08f) else defaultRowBg

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(rowHeight)
                                .background(rowBg)
                        ) {
                            // Sticky Row Index (1, 2, 3...)
                            Box(
                                modifier = Modifier
                                    .width(rowHeaderWidth)
                                    .height(rowHeight)
                                    .background(
                                        if (isRowActive) colors.primary.copy(alpha = 0.22f)
                                        else if (nightMode) Color(0xFF1A1525)
                                        else Color(0xFFF8FAFC)
                                    )
                                    .border(0.5.dp, borderColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${row.rowIndex}",
                                    fontSize = headerFontSize,
                                    fontWeight = if (isRowActive) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isRowActive) colors.primary else if (nightMode) Color(0xFF9E95B0) else Color(0xFF64748B)
                                )
                            }

                            // Horizontally scrolled cells in this row
                            Row(modifier = Modifier.horizontalScroll(hScrollState)) {
                                for (colIdx in 0 until maxCols) {
                                    val cell = row.cells.getOrNull(colIdx)
                                    val cellText = cell?.value.orEmpty()
                                    val colW = (baseColumnWidths[colIdx] * zoomScale).dp
                                    val isSelected = (selectedCell == cell && cell != null)

                                    val isSearchMatch = remember(searchQuery, cellText) {
                                        searchQuery.isNotBlank() && cellText.contains(searchQuery, ignoreCase = true)
                                    }

                                    val cellBg = when {
                                        isSelected -> colors.primary.copy(alpha = 0.16f)
                                        isSearchMatch -> if (nightMode) Color(0xFF854D0E).copy(alpha = 0.5f) else Color(0xFFFEF08A)
                                        else -> Color.Transparent
                                    }

                                    val cellAlignment = remember(cellText, isFirstRow) {
                                        if (isFirstRow) TextAlign.Start else getNaturalCellAlignment(cellText)
                                    }

                                    Box(
                                        modifier = Modifier
                                            .width(colW)
                                            .height(rowHeight)
                                            .background(cellBg)
                                            .border(
                                                width = if (isSelected) 2.dp else if (isSearchMatch) 1.5.dp else 0.5.dp,
                                                color = if (isSelected) colors.primary else if (isSearchMatch) Color(0xFFF59E0B) else borderColor
                                            )
                                            .clickable {
                                                if (cell != null) {
                                                    selectedCell = if (selectedCell == cell) null else cell
                                                }
                                            }
                                            .padding(horizontal = 7.dp, vertical = 2.dp),
                                        contentAlignment = when (cellAlignment) {
                                            TextAlign.End -> Alignment.CenterEnd
                                            TextAlign.Center -> Alignment.Center
                                            else -> Alignment.CenterStart
                                        }
                                    ) {
                                        Text(
                                            text = cellText,
                                            fontSize = fontSize,
                                            fontWeight = if (isFirstRow) FontWeight.SemiBold else FontWeight.Normal,
                                            fontFamily = if (cellAlignment == TextAlign.End) FontFamily.Monospace else FontFamily.Default,
                                            color = when {
                                                isSelected -> colors.primary
                                                isSearchMatch -> if (nightMode) Color(0xFFFDE047) else Color(0xFF854D0E)
                                                isFirstRow -> colors.primary
                                                else -> textColor
                                            },
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = cellAlignment
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Active Cell Inspector / Formula Bar Card
        AnimatedVisibility(
            visible = selectedCell != null && selectedCell!!.value.isNotBlank(),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (selectedCell != null) {
                val cellRef = "${XlsxFileReader.indexToColumnName(selectedCell!!.columnIndex)}${selectedCell!!.rowIndex}"

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (nightMode) Color(0xFF221B2E) else Color(0xFFFFFFFF)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    border = CardDefaults.outlinedCardBorder().copy(width = 1.dp, brush = androidx.compose.ui.graphics.SolidColor(colors.primary.copy(alpha = 0.4f))),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Coordinate badge e.g. "B4"
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = colors.primary.copy(alpha = 0.15f),
                                contentColor = colors.primary
                            ) {
                                Text(
                                    text = cellRef,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Copy cell content button
                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(selectedCell!!.value))
                                        copiedFeedback = true
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = if (copiedFeedback) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                                        contentDescription = "Copy cell value",
                                        tint = if (copiedFeedback) Color(0xFF10B981) else colors.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                // Dismiss inspector
                                IconButton(
                                    onClick = { selectedCell = null },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Close inspector",
                                        tint = if (nightMode) Color(0xFF9E95B0) else Color(0xFF64748B),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Full content (scrollable if very long)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 120.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = selectedCell!!.value,
                                style = MaterialTheme.typography.bodyMedium,
                                color = textColor,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Detects whether a cell string represents a number, currency, or short code
 * so spreadsheets can align numbers naturally to the right and text to the left.
 */
private fun getNaturalCellAlignment(value: String): TextAlign {
    if (value.isBlank()) return TextAlign.Start
    val trimmed = value.trim()

    // Currency or numeric formats (e.g. 1,234.56, $50.00, 15%, -42)
    val numStr = trimmed
        .removePrefix("$")
        .removePrefix("€")
        .removePrefix("£")
        .removePrefix("¥")
        .removeSuffix("%")
        .replace(",", "")
        .trim()

    if (numStr.isNotEmpty() && numStr.toDoubleOrNull() != null) {
        return TextAlign.End
    }

    // Short status codes (e.g. OK, YES, NO, TRUE, FALSE, -)
    if (trimmed.length <= 4 && (
        trimmed.equals("true", ignoreCase = true) ||
        trimmed.equals("false", ignoreCase = true) ||
        trimmed.equals("yes", ignoreCase = true) ||
        trimmed.equals("no", ignoreCase = true) ||
        !trimmed.contains(" ")
    )) {
        return TextAlign.Center
    }

    return TextAlign.Start
}
