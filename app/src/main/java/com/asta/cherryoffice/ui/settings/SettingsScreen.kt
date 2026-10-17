package com.asta.cherryoffice.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.asta.cherryoffice.ui.components.*
import com.asta.cherryoffice.ui.theme.CherryTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val colors = CherryTheme.colors

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbarMessage()
        }
    }

    GlowBackground(modifier = modifier) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(10.dp))

                // Top Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CherryIconButton(
                            icon = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            onClick = onBackClick
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "SETTINGS",
                                style = CherryTheme.typography.sectionHeader,
                                fontSize = 20.sp
                            )
                            Text(
                                text = "Preferences & Customization",
                                style = CherryTheme.typography.labelMonospace
                            )
                        }
                    }

                    CherryIconButton(
                        icon = Icons.Rounded.Info,
                        contentDescription = "About",
                        onClick = { viewModel.setShowAboutDialog(true) }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    // Theme Mode Section
                    item {
                        CherryCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.Palette,
                                        contentDescription = null,
                                        tint = colors.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Appearance Theme",
                                        style = CherryTheme.typography.sectionHeader.copy(fontSize = 16.sp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Choose your preferred visual neon style",
                                    style = CherryTheme.typography.documentMeta,
                                    color = colors.textSoftGreyPurple
                                )
                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CherryChip(
                                        text = "Dark Glossy",
                                        isSelected = state.themeMode == "DARK_GLOSSY",
                                        onClick = { viewModel.setThemeMode("DARK_GLOSSY") },
                                        modifier = Modifier.weight(1f)
                                    )
                                    CherryChip(
                                        text = "AMOLED",
                                        isSelected = state.themeMode == "AMOLED_BLACK",
                                        onClick = { viewModel.setThemeMode("AMOLED_BLACK") },
                                        modifier = Modifier.weight(1f)
                                    )
                                    CherryChip(
                                        text = "Light Cherry",
                                        isSelected = state.themeMode == "LIGHT_CHERRY",
                                        onClick = { viewModel.setThemeMode("LIGHT_CHERRY") },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    // Default Zoom Section
                    item {
                        CherryCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.ZoomIn,
                                        contentDescription = null,
                                        tint = colors.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Default Document Zoom",
                                        style = CherryTheme.typography.sectionHeader.copy(fontSize = 16.sp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Initial magnification level for PDFs and documents: ${(state.defaultZoom * 100).toInt()}%",
                                    style = CherryTheme.typography.documentMeta,
                                    color = colors.textSoftGreyPurple
                                )
                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(1.0f, 1.25f, 1.5f, 2.0f).forEach { zoom ->
                                        CherryChip(
                                            text = "${(zoom * 100).toInt()}%",
                                            isSelected = state.defaultZoom == zoom,
                                            onClick = { viewModel.setDefaultZoom(zoom) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Keep Screen On Section
                    item {
                        CherryCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.WbSunny,
                                        contentDescription = null,
                                        tint = colors.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Keep Screen On",
                                            style = CherryTheme.typography.sectionHeader.copy(fontSize = 16.sp)
                                        )
                                        Text(
                                            text = "Prevent screen timeout while reading",
                                            style = CherryTheme.typography.documentMeta,
                                            color = colors.textSoftGreyPurple
                                        )
                                    }
                                }
                                Switch(
                                    checked = state.keepScreenOn,
                                    onCheckedChange = { viewModel.setKeepScreenOn(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = colors.textWhite,
                                        checkedTrackColor = colors.primary,
                                        uncheckedThumbColor = colors.textSoftGreyPurple,
                                        uncheckedTrackColor = colors.card
                                    )
                                )
                            }
                        }
                    }

                    // Data Management / Clear History
                    item {
                        CherryCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.History,
                                        contentDescription = null,
                                        tint = colors.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Recent Files History",
                                        style = CherryTheme.typography.sectionHeader.copy(fontSize = 16.sp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Clear all recently opened documents and history records from local storage.",
                                    style = CherryTheme.typography.documentMeta,
                                    color = colors.textSoftGreyPurple
                                )
                                Spacer(modifier = Modifier.height(16.dp))

                                CherryButton(
                                    text = "CLEAR HISTORY",
                                    icon = Icons.Rounded.DeleteOutline,
                                    variant = CherryButtonVariant.ACTION,
                                    onClick = { viewModel.clearRecentHistory() },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            )
        }
    }

    if (state.showAboutDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.setShowAboutDialog(false) },
            containerColor = colors.surface,
            shape = RoundedCornerShape(24.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(colors.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Description,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Cherry Office",
                        style = CherryTheme.typography.appTitle.copy(fontSize = 18.sp)
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = "Version 1.0 (Asta Edition)",
                        style = CherryTheme.typography.sectionHeader.copy(fontSize = 14.sp),
                        color = colors.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "A high-performance, dark glossy cherry red neon office suite for viewing PDFs, rich text, spreadsheets, and presentations with lightning speed and gorgeous Material 3 design.",
                        style = CherryTheme.typography.documentMeta,
                        color = colors.textSoftGreyPurple
                    )
                }
            },
            confirmButton = {
                CherryButton(
                    text = "CLOSE",
                    variant = CherryButtonVariant.MAIN,
                    onClick = { viewModel.setShowAboutDialog(false) },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        )
    }
}
