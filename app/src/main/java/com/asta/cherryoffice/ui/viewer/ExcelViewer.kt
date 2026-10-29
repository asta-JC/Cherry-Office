package com.asta.cherryoffice.ui.viewer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.asta.cherryoffice.excel.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExcelViewer(document: SpreadsheetDocument?, nightMode: Boolean, searchQuery: String = "", modifier: Modifier = Modifier) {
    if (document == null || document.sheets.isEmpty()) { Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No readable sheets in this workbook.") }; return }
    var tab by remember(document) { mutableIntStateOf(0) }
    var zoom by remember(document) { mutableFloatStateOf(1f) }
    var selected by remember(document, tab) { mutableStateOf<CellData?>(null) }
    var notes by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Column(modifier.fillMaxSize()) {
        if (document.sheets.size > 1) SecondaryScrollableTabRow(selectedTabIndex = tab) {
            document.sheets.forEachIndexed { i, sheet -> Tab(tab == i, { tab = i }, text = { Text(sheet.name) }) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(document.sheets[tab].name, Modifier.weight(1f).padding(8.dp), style = MaterialTheme.typography.labelLarge, maxLines = 1)
            if (document.warnings.isNotEmpty()) IconButton(onClick = { notes = true }) { Icon(Icons.Rounded.Info, "Preview notes") }
            IconButton(onClick = { zoom = (zoom - .15f).coerceAtLeast(.65f) }) { Icon(Icons.Rounded.Remove, "Zoom out") }
            TextButton(onClick = { zoom = 1f }) { Text("${(zoom * 100).toInt()}%") }
            IconButton(onClick = { zoom = (zoom + .15f).coerceAtMost(3f) }) { Icon(Icons.Rounded.Add, "Zoom in") }
        }
        key(document, tab) {
            AndroidView(factory = { ctx -> SpreadsheetReadingView(ctx).apply { onZoom = { zoom = it }; onCell = { selected = it }; setSheet(document.sheets[tab]) } },
                update = { it.setSheet(document.sheets[tab]); it.setZoom(zoom); it.setQuery(searchQuery) }, modifier = Modifier.fillMaxWidth().weight(1f))
        }
        selected?.let { cell ->
            Surface(tonalElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().heightIn(max = 160.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${XlsxFileReader.indexToColumnName(cell.columnIndex)}${cell.rowIndex}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        SelectionContainer { Text(cell.value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.heightIn(max = 76.dp)) }
                        cell.formula?.let { Text("=$it", style = MaterialTheme.typography.bodySmall, maxLines = 2) }
                    }
                    IconButton(onClick = { (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Cell", cell.value)) }) { Icon(Icons.Rounded.ContentCopy, "Copy cell") }
                    IconButton(onClick = { selected = null }) { Icon(Icons.Rounded.Close, "Close cell details") }
                }
            }
        }
    }
    if (notes) AlertDialog(onDismissRequest = { notes = false }, title = { Text("About this preview") }, text = { Text(document.warnings.joinToString("\n\n")) }, confirmButton = { TextButton(onClick = { notes = false }) { Text("Done") } })
}
