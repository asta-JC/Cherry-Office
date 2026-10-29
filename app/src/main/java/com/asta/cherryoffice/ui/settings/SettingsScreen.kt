package com.asta.cherryoffice.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.asta.cherryoffice.data.repository.AppearanceMode

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBackClick: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.snackbarMessage) { state.snackbarMessage?.let { snackbar.showSnackbar(it); viewModel.clearSnackbarMessage() } }
    SettingsContent(state, onBackClick, viewModel::setAppearance, viewModel::setDefaultZoom,
        viewModel::setKeepScreenOn, viewModel::clearRecentHistory, snackbar, modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(state: SettingsUiState, onBack: () -> Unit, onAppearance: (AppearanceMode) -> Unit,
    onZoom: (Float) -> Unit, onKeepOn: (Boolean) -> Unit, onClear: () -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() }, modifier: Modifier = Modifier) {
    var confirmClear by remember { mutableStateOf(false) }
    Scaffold(modifier = modifier, snackbarHost = { SnackbarHost(snackbar) }, topBar = {
        TopAppBar(title = { Text("Settings") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
        })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)) {
            item {
                SettingsSection("Appearance", "A little cherry. A lot more clarity.") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppearanceMode.entries.forEach { mode ->
                            val selected = AppearanceMode.fromStored(state.themeMode) == mode
                            val preview = when (mode) { AppearanceMode.LIGHT -> Color(0xFFFAF8F9); AppearanceMode.AMOLED -> Color.Black; else -> Color(0xFF211E24) }
                            Surface(shape = RoundedCornerShape(14.dp), color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
                                Row(Modifier.fillMaxWidth().semantics { role = Role.RadioButton; this.selected = selected }
                                    .clickable { onAppearance(mode) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Box(Modifier.size(44.dp, 36.dp).background(preview, RoundedCornerShape(8.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))) {
                                        Box(Modifier.padding(7.dp).size(18.dp, 4.dp).background(Color(0xFFC44B69), RoundedCornerShape(2.dp)))
                                    }
                                    Text(mode.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                    if (selected) Icon(Icons.Rounded.Check, "Selected", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
            item { SettingsSection("Reading", "Document colors and pictures always stay original.") {
                Text("Default PDF zoom", style = MaterialTheme.typography.titleSmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1f, 1.25f, 1.5f, 2f).forEach { zoom ->
                        FilterChip(selected = state.defaultZoom == zoom, onClick = { onZoom(zoom) }, label = { Text("${(zoom * 100).toInt()}%") })
                    }
                }
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Keep screen on", style = MaterialTheme.typography.bodyLarge)
                        Text("Only while a document is open", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(state.keepScreenOn, onKeepOn)
                }
            } }
            item { SettingsSection("Storage", "Your files stay on your device.") {
                Text("Clear recent files", style = MaterialTheme.typography.titleSmall)
                Text("Removes recent-file records and favorites from Cherry Office. Your documents are not deleted.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { confirmClear = true }) { Text("Clear history") }
            } }
            item { SettingsSection("About", "Cherry Office") {
                Text("An offline space for your documents. PDF, Word, spreadsheets, slide text, images and plain text.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("PDF text: PDFBox Android 2.0.27.0 • XLS: Apache POI 5.5.1\nApache License 2.0. Third-party notices are included with the app.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } }
        }
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("Clear recent files?") },
        text = { Text("This removes history records and favorites. Files on your device will stay where they are.") },
        confirmButton = { TextButton(onClick = { confirmClear = false; onClear() }) { Text("Clear history") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } })
}

@Composable private fun SettingsSection(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}
