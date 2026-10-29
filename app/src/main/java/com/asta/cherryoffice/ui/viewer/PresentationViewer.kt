package com.asta.cherryoffice.ui.viewer

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.asta.cherryoffice.formats.PresentationSlide
import java.io.ByteArrayInputStream
import java.util.Locale

/** A single scrolling surface avoids allocating a WebView or full-size bitmap for every slide. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PresentationViewer(slides: List<PresentationSlide>, nightMode: Boolean, modifier: Modifier = Modifier) {
    val background = MaterialTheme.colorScheme.surfaceContainer
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val html = remember(slides, background, ink, nightMode) {
        fun cssColor(argb: Int) = String.format(Locale.ROOT, "#%06X", argb and 0xFFFFFF)
        buildString {
            append("<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1,maximum-scale=5\"/>")
            append("<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; img-src data:; style-src 'unsafe-inline'\"/>")
            append("<style>body{margin:0;padding:16px 12px 32px;background:${cssColor(background.toArgb())};color:${cssColor(ink.toArgb())};font-family:sans-serif;-webkit-text-size-adjust:100%;text-size-adjust:100%}figure{margin:0 0 24px}figcaption{font-size:12px;padding:0 4px 8px}figure>svg{display:block;width:100%;height:auto;background:white;box-shadow:0 2px 8px #0001;border-radius:4px}details{font-size:12px;margin:8px 4px;line-height:1.5}p{white-space:pre-wrap}summary{padding:8px 0}</style></head><body>")
            slides.forEachIndexed { i, slide ->
                fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                append("<figure aria-label=\"Slide ${i+1}\"><figcaption>Slide ${i+1} / ${slides.size}</figcaption>")
                append(slide.svg)
                if (slide.warnings.isNotEmpty()) append("<details><summary>Preview details</summary>${slide.warnings.joinToString("<br/>") { escape(it) }}</details>")
                if (slide.paragraphs.isNotEmpty()) append("<details><summary>Slide text</summary>${slide.paragraphs.joinToString("") { "<p>${escape(it)}</p>" }}</details>")
                append("</figure>")
            }
            append("</body></html>")
        }
    }
    AndroidView(modifier = modifier.fillMaxSize(), factory = { context ->
        WebView(context).apply {
            setBackgroundColor(background.toArgb())
            settings.apply {
                javaScriptEnabled = false
                allowFileAccess = false
                allowContentAccess = false
                blockNetworkLoads = true
                builtInZoomControls = true
                displayZoomControls = false
                setSupportZoom(true)
                defaultTextEncodingName = "UTF-8"
                textZoom = 100
            }
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    // Document scripts stay disabled during load. Only this fixed local layout
                    // routine runs; disable scripting again as soon as measurement finishes.
                    view.settings.javaScriptEnabled = true
                    view.evaluateJavascript(presentationTextFitScript) {
                        view.settings.javaScriptEnabled = false
                    }
                }
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = true
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    return if (request?.url?.scheme == "data") null else WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                }
            }
        }
    }, update = { view ->
        if (view.tag != html) {
            view.tag = html
            view.loadDataWithBaseURL("https://slides.invalid/", html, "text/html", "UTF-8", null)
        }
    }, onRelease = { it.stopLoading(); it.destroy() })
}
