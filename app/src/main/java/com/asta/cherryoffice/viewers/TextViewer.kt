package com.asta.cherryoffice.viewers

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun TextViewer(
    textContent: String,
    fontSize: Float,
    nightMode: Boolean,
    searchQuery: String,
    modifier: Modifier = Modifier
) {
    com.asta.cherryoffice.ui.viewer.TextViewer(
        textContent = textContent,
        fontSize = fontSize,
        nightMode = nightMode,
        searchQuery = searchQuery,
        modifier = modifier
    )
}
