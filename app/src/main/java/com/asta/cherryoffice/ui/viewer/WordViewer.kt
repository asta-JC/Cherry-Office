package com.asta.cherryoffice.ui.viewer

// Word (.docx) viewer: shows the document as a vertical stack of white
// A4-like sheets on a dark backdrop, like Word's "print layout".
// The heavy lifting (OOXML -> styled HTML + JS pagination) happens in
// DocxToHtml.kt; this file only loads that HTML into a WebView with
// pinch-to-zoom, link handling and a "Page X / N" indicator.

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.asta.cherryoffice.data.model.DocumentItem
import com.asta.cherryoffice.docx.DocxFileReader
import com.asta.cherryoffice.docx.DocxLoadResult
import kotlinx.coroutines.delay

// Bridge so the page's JavaScript can tell us how many sheets it built.
// (R8 rule in app/src/main/keepRules/rules.keep keeps this method.)
class WordJsBridge(private val onPagesReady: (Int) -> Unit) {
    @JavascriptInterface
    fun onPagesReady(count: Int) {
        onPagesReady(count)
    }
}

@Composable
fun WordViewer(
    documentUri: Uri,
    documentItem: DocumentItem?,
    onOpenWithAnotherApp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var loadState by remember(documentUri) { mutableStateOf<DocxLoadResult?>(null) }

    // Parse on a background thread (DocxFileReader switches to IO itself).
    LaunchedEffect(documentUri) {
        loadState = null
        loadState = DocxFileReader.load(context, documentUri)
    }

    when (val state = loadState) {
        null -> Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Opening document…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        is DocxLoadResult.Failed -> ErrorScreen(
            documentItem = documentItem,
            errorMessage = state.message,
            onOpenWithAnotherApp = onOpenWithAnotherApp,
            modifier = modifier
        )
        is DocxLoadResult.Ready -> WordPages(
            html = state.html,
            baseUrl = "file://" + state.baseDir.absolutePath + "/",
            modifier = modifier
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WordPages(
    html: String,
    baseUrl: String,
    modifier: Modifier = Modifier
) {
    var pageCount by remember(html) { mutableIntStateOf(1) }
    var currentPage by remember(html) { mutableIntStateOf(1) }
    var userScrolling by remember(html) { mutableStateOf(false) }
    var scrollTick by remember(html) { mutableIntStateOf(0) }
    var lastQueryMs by remember(html) { mutableLongStateOf(0L) }
    // Plain holder (not State): updated from WebView callbacks, read on dispose.
    val webHolder = remember(html) { object { var view: WebView? = null } }

    // Hide the page pill ~1.5 s after scrolling stops.
    LaunchedEffect(scrollTick) {
        if (scrollTick > 0) {
            delay(1500)
            userScrolling = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    // Native pinch-to-zoom for the whole document.
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    // Lets <img src="media/..."> resolve against baseUrl below.
                    settings.allowFileAccess = true
                    WebView.setWebContentsDebuggingEnabled(false)
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            // Insurance: re-run pagination once the engine has
                            // laid the page out (fonts/images can shift heights).
                            try {
                                view?.evaluateJavascript(
                                    "window.__repaginate&&window.__repaginate()",
                                    null
                                )
                            } catch (_: Exception) {
                                // Page not ready; inline script already paginated.
                            }
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: android.webkit.WebResourceRequest?
                        ): Boolean {
                            val url = request?.url?.toString().orEmpty()
                            // Document hyperlinks open in the browser; anything
                            // else (anchors, files) stays inside the viewer.
                            if (url.startsWith("http://") || url.startsWith("https://")) {
                                try {
                                    ctx.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    )
                                } catch (_: Exception) {
                                    // No browser: stay on the page.
                                }
                                return true
                            }
                            return false
                        }
                    }
                    addJavascriptInterface(
                        WordJsBridge { count ->
                            pageCount = count.coerceAtLeast(1)
                            currentPage = currentPage.coerceIn(1, pageCount)
                        },
                        "Android"
                    )
                    setOnScrollChangeListener { v, _, scrollY, _, _ ->
                        userScrolling = true
                        scrollTick++
                        // Ask the page which sheet is at this scroll offset,
                        // throttled so we don't spam the JS bridge.
                        val now = SystemClock.uptimeMillis()
                        if (now - lastQueryMs > 150) {
                            lastQueryMs = now
                            try {
                                @Suppress("DEPRECATION")
                                val scale = (v as WebView).scale
                                val cssY = if (scale > 0) (scrollY / scale).toInt() else scrollY
                                v.evaluateJavascript("window.__currentPage($cssY)") { raw ->
                                    raw?.trim()?.trim('"')?.toIntOrNull()?.let { page ->
                                        currentPage = page.coerceIn(1, pageCount.coerceAtLeast(1))
                                    }
                                }
                            } catch (_: Exception) {
                                // Page not ready yet; indicator updates on next scroll.
                            }
                        }
                    }
                    loadDataWithBaseURL(baseUrl, html, "text/html", "utf-8", null)
                }.also { webHolder.view = it }
            },
            modifier = Modifier.fillMaxSize()
        )

        AnimatedVisibility(
            visible = userScrolling && pageCount > 1,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shadowElevation = 6.dp
            ) {
                Text(
                    text = "Page $currentPage / $pageCount",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }

    DisposableEffect(html) {
        onDispose {
            try {
                webHolder.view?.stopLoading()
                webHolder.view?.destroy()
            } catch (_: Exception) {
                // Already gone; nothing to clean up.
            }
            webHolder.view = null
        }
    }
}
