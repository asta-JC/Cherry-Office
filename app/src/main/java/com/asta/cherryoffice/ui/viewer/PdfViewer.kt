package com.asta.cherryoffice.ui.viewer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.asta.cherryoffice.pdf.PdfReadingView

@Composable
fun PdfViewer(uriString: String, zoomScale: Float, nightMode: Boolean, onPageCountChanged: (Int) -> Unit,
    modifier: Modifier = Modifier, onZoomChanged: (Float) -> Unit = {}, onCurrentPageChanged: (Int) -> Unit = {},
    requestedPage: Int? = null, pageRequestId: Int = 0, initialPage: Int = 0, onOpenWithAnotherApp: () -> Unit = {}) {
    var error by remember(uriString) { mutableStateOf<String?>(null) }
    val background = MaterialTheme.colorScheme.surfaceContainer.toArgb()
    val accent = MaterialTheme.colorScheme.primary.toArgb()
    val latestCount by rememberUpdatedState(onPageCountChanged)
    val latestInitialPage by rememberUpdatedState(initialPage)
    val latestPage by rememberUpdatedState(onCurrentPageChanged)
    val latestZoom by rememberUpdatedState(onZoomChanged)
    val holder = remember(uriString) { object { var view: PdfReadingView? = null; var request = -1 } }
    key(uriString) {
        if (error != null) ErrorScreen(null, error, onOpenWithAnotherApp, modifier)
        else AndroidView(factory = { ctx -> PdfReadingView(ctx).also { view ->
            holder.view = view
            view.onPages = { count -> latestCount(count); view.goToPage(requestedPage ?: latestInitialPage) }
            view.onPage = { latestPage(it) }; view.onZoom = { latestZoom(it) }; view.onError = { error = it }
            view.setZoom(zoomScale); view.open(uriString)
        } }, update = { view ->
            view.configureBackground(background, accent); view.setZoom(zoomScale)
            if (pageRequestId != holder.request) { holder.request = pageRequestId; requestedPage?.let(view::goToPage) }
        }, modifier = modifier.fillMaxSize())
    }
    DisposableEffect(uriString) { onDispose { holder.view?.dispose(); holder.view = null } }
}
