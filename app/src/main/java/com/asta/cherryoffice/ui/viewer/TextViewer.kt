package com.asta.cherryoffice.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TextViewer(
    textContent: String,
    fontSize: Float,
    nightMode: Boolean,
    searchQuery: String,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val backgroundColor = if (nightMode) Color(0xFF0A0A0F) else MaterialTheme.colorScheme.surface
    val textColor = if (nightMode) Color(0xFFE0E0E0) else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        SelectionContainer {
            Text(
                text = textContent.ifBlank { "Document is empty." },
                color = textColor,
                fontFamily = FontFamily.Monospace,
                fontSize = fontSize.sp,
                lineHeight = (fontSize * 1.4f).sp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
