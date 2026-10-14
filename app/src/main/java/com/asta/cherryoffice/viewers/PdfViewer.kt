package com.asta.cherryoffice.viewers

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun PdfViewer(
    uriString: String,
    zoomScale: Float,
    nightMode: Boolean,
    onPageCountChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onZoomChanged: (Float) -> Unit = {}
) {
    com.asta.cherryoffice.ui.viewer.PdfViewer(
        uriString = uriString,
        zoomScale = zoomScale,
        nightMode = nightMode,
        onPageCountChanged = onPageCountChanged,
        modifier = modifier,
        onZoomChanged = onZoomChanged
    )
}
