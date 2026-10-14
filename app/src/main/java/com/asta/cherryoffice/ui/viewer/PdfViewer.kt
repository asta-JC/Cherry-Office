package com.asta.cherryoffice.ui.viewer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.min

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 5f
private const val DOUBLE_TAP_SCALE = 2.5f
private const val MAX_BITMAP_WIDTH_PX = 2048
private const val MAX_BITMAP_HEIGHT_PX = 6000
/** LruCache size is in KB (see sizeOf below): 48 MB of page bitmaps. */
private const val BITMAP_CACHE_KB = 48 * 1024
private val RENDER_BUCKETS = floatArrayOf(1f, 1.5f, 2f, 2.5f, 3f)

/** Smallest render bucket covering [scale]; capped so zoomed pages stay memory-safe. */
private fun bucketFor(scale: Float): Float =
    RENDER_BUCKETS.firstOrNull { it >= scale - 0.001f } ?: RENDER_BUCKETS.last()

/**
 * Single shared zoom state for the whole document, hoisted above the page list.
 * Survives rotation via rememberSaveable; recreated per document.
 */
@Stable
class PdfZoomState(
    scale: Float = MIN_SCALE,
    offsetX: Float = 0f,
    offsetY: Float = 0f
) {
    var scale by mutableFloatStateOf(scale)
    var offsetX by mutableFloatStateOf(offsetX)
    var offsetY by mutableFloatStateOf(offsetY)

    fun reset() {
        scale = MIN_SCALE
        offsetX = 0f
        offsetY = 0f
    }

    companion object {
        val Saver = listSaver<PdfZoomState, Float>(
            save = { listOf(it.scale, it.offsetX, it.offsetY) },
            restore = { PdfZoomState(scale = it[0], offsetX = it[1], offsetY = it[2]) }
        )
    }
}

@Composable
private fun rememberPdfZoomState(docKey: String): PdfZoomState =
    rememberSaveable(docKey, saver = PdfZoomState.Saver) { PdfZoomState() }

@Composable
fun PdfViewer(
    uriString: String,
    zoomScale: Float,
    nightMode: Boolean,
    onPageCountChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onZoomChanged: (Float) -> Unit = {}
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var renderer by remember(uriString) { mutableStateOf<PdfRenderer?>(null) }
    var fileDescriptor by remember(uriString) { mutableStateOf<ParcelFileDescriptor?>(null) }
    var pageCount by remember(uriString) { mutableIntStateOf(0) }
    var pageAspects by remember(uriString) { mutableStateOf<List<Float>>(emptyList()) }
    var isLoading by remember(uriString) { mutableStateOf(true) }
    var errorMessage by remember(uriString) { mutableStateOf<String?>(null) }

    // All PdfRenderer access confined to one background thread (it is not thread-safe).
    val renderDispatcher = remember(uriString) { Dispatchers.IO.limitedParallelism(1) }
    val bitmapCache = remember(uriString) {
        object : LruCache<String, Bitmap>(BITMAP_CACHE_KB) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
        }
    }

    // Single shared zoom state for the whole document.
    val zoom = rememberPdfZoomState(uriString)
    val listState: LazyListState =
        rememberSaveable(uriString, saver = LazyListState.Saver) { LazyListState() }
    var pinchActive by remember(uriString) { mutableStateOf(false) }
    var renderBucket by remember(uriString) { mutableFloatStateOf(1f) }
    var animJob by remember(uriString) { mutableStateOf<Job?>(null) }

    val latestOnZoomChanged by rememberUpdatedState(onZoomChanged)

    // Debounced sharp re-render after pinch / buttons / double-tap settle.
    // Layout never changes; only the bitmap resolution is swapped, so no flicker.
    LaunchedEffect(zoom.scale, pinchActive, uriString) {
        if (pinchActive) return@LaunchedEffect
        delay(250)
        renderBucket = bucketFor(zoom.scale)
    }

    // Open the document. Zoom is reset (fresh saveable state per uri + explicit reset).
    LaunchedEffect(uriString) {
        isLoading = true
        errorMessage = null
        zoom.reset()
        renderBucket = 1f
        try {
            val uri = Uri.parse(uriString)
            val pfd = withContext(Dispatchers.IO) {
                if (uri.scheme == "content" || uri.scheme == "file") {
                    try {
                        context.contentResolver.openFileDescriptor(uri, "r")
                    } catch (e: Exception) {
                        val tempFile = File(context.cacheDir, "temp_pdf_${System.currentTimeMillis()}.pdf")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(tempFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    }
                } else {
                    null
                }
            }

            if (pfd != null) {
                val r = PdfRenderer(pfd)
                renderer = r
                fileDescriptor = pfd
                val n = r.pageCount
                // Read per-page aspect ratios up front so every item has the
                // correct size before its bitmap arrives (no layout jumps).
                val aspects = withContext(renderDispatcher) {
                    List(n) { i ->
                        r.openPage(i).use { page ->
                            (page.width.toFloat() / page.height)
                                .takeIf { it.isFinite() && it > 0f } ?: 0.707f
                        }
                    }
                }
                pageCount = n
                pageAspects = aspects
                onPageCountChanged(n)
                isLoading = false
            } else {
                errorMessage = "Could not open PDF file descriptor."
                isLoading = false
            }
        } catch (e: Exception) {
            errorMessage = "Failed to load PDF: ${e.localizedMessage}"
            isLoading = false
        }
    }

    DisposableEffect(uriString) {
        onDispose {
            try { renderer?.close() } catch (_: Exception) {}
            try { fileDescriptor?.close() } catch (_: Exception) {}
            bitmapCache.evictAll()
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(if (nightMode) Color(0xFF0A0A0F) else MaterialTheme.colorScheme.background)
    ) {
        val viewportW = with(density) { maxWidth.toPx() }
        val viewportH = with(density) { maxHeight.toPx() }
        // Page content width = list width minus LazyColumn horizontal padding (12dp each side).
        val pageContentW = viewportW - with(density) { 24.dp.toPx() }
        // Estimated total document height (card padding + gaps + list padding) for offset clamping.
        val contentH = remember(pageAspects, pageContentW) {
            if (pageAspects.isEmpty() || pageContentW <= 0f) {
                0f
            } else {
                val cardPad = with(density) { 16.dp.toPx() }
                val gap = with(density) { 12.dp.toPx() }
                val vPad = with(density) { 24.dp.toPx() }
                pageAspects.sumOf { (pageContentW / it).toDouble() }.toFloat() +
                    cardPad * pageAspects.size +
                    gap * (pageAspects.size - 1).coerceAtLeast(0) + vPad
            }
        }

        // Transform origin is top-left (0,0); all math below is consistent with it:
        // screenPoint = scale * contentPoint + offset.
        fun clampX(s: Float, x: Float): Float = x.coerceIn(viewportW * (1f - s), 0f)
        fun clampY(s: Float, y: Float): Float =
            y.coerceIn(min(viewportH - contentH * s, 0f), 0f)

        // Toolbar +/- buttons drive zoomScale; apply it anchored at viewport center.
        LaunchedEffect(zoomScale) {
            if (abs(zoomScale - zoom.scale) > 0.02f && !pinchActive) {
                val s = zoomScale.coerceIn(MIN_SCALE, MAX_SCALE)
                val cx = viewportW / 2f
                val cy = viewportH / 2f
                val k = s / zoom.scale
                zoom.offsetX = clampX(s, cx - (cx - zoom.offsetX) * k)
                zoom.offsetY = clampY(s, cy - (cy - zoom.offsetY) * k)
                zoom.scale = s
            }
        }
        // Re-clamp after rotation / window size change.
        LaunchedEffect(viewportW, viewportH, contentH) {
            zoom.offsetX = clampX(zoom.scale, zoom.offsetX)
            zoom.offsetY = clampY(zoom.scale, zoom.offsetY)
        }

        fun animateZoomTo(target: Float, anchor: Offset) {
            animJob?.cancel()
            animJob = scope.launch {
                val s0 = zoom.scale
                val ox0 = zoom.offsetX
                val oy0 = zoom.offsetY
                if (abs(target - s0) < 0.01f) return@launch
                Animatable(s0).animateTo(
                    target,
                    animationSpec = tween(280, easing = FastOutSlowInEasing)
                ) {
                    val s = value
                    if (target > s0) {
                        // Zoom in anchored at the tap point.
                        val k = s / s0
                        zoom.offsetX = clampX(s, anchor.x - (anchor.x - ox0) * k)
                        zoom.offsetY = clampY(s, anchor.y - (anchor.y - oy0) * k)
                    } else {
                        // Zoom out back to the resting position.
                        val t = if (target == s0) 1f else (s - s0) / (target - s0)
                        zoom.offsetX = ox0 * (1f - t)
                        zoom.offsetY = oy0 * (1f - t)
                    }
                    zoom.scale = s
                }
                latestOnZoomChanged(zoom.scale)
            }
        }

        // ONE shared gesture detector for the whole document (never per page).
        val tapModifier = Modifier.pointerInput(uriString, viewportW, viewportH, contentH) {
            detectTapGestures(
                onDoubleTap = { tap ->
                    if (zoom.scale < 1.4f) animateZoomTo(DOUBLE_TAP_SCALE, tap)
                    else animateZoomTo(MIN_SCALE, tap)
                }
            )
        }
        val gestureModifier = Modifier.pointerInput(uriString, viewportW, viewportH, contentH) {
            var lastSent = zoom.scale
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                animJob?.cancel()
                var pinching = false
                do {
                    val event = awaitPointerEvent()
                    val pressed = event.changes.filter { it.pressed }
                    when {
                        pressed.size >= 2 -> {
                            // Pinch: zoom around the gesture centroid so the point
                            // between the fingers stays under the fingers.
                            if (!pinching) {
                                pinching = true
                                pinchActive = true
                            }
                            val old = zoom.scale
                            val newScale =
                                (old * event.calculateZoom()).coerceIn(MIN_SCALE, MAX_SCALE)
                            val pan = event.calculatePan()
                            val centroid = event.calculateCentroid()
                            val k = newScale / old
                            if (k != 1f || pan != Offset.Zero) {
                                zoom.offsetX = clampX(
                                    newScale,
                                    centroid.x - (centroid.x - zoom.offsetX) * k + pan.x
                                )
                                zoom.offsetY = clampY(
                                    newScale,
                                    centroid.y - (centroid.y - zoom.offsetY) * k + pan.y
                                )
                                zoom.scale = newScale
                                if (abs(newScale - lastSent) > 0.01f) {
                                    lastSent = newScale
                                    latestOnZoomChanged(newScale)
                                }
                            }
                            event.changes.forEach { it.consume() }
                        }
                        pressed.size == 1 && zoom.scale > 1.01f -> {
                            // Zoomed in: one-finger drag pans. Leftovers at the
                            // vertical edge scroll the list, so every page stays
                            // reachable while zoomed.
                            val c = pressed[0]
                            val drag = c.position - c.previousPosition
                            if (drag != Offset.Zero) {
                                zoom.offsetX = clampX(zoom.scale, zoom.offsetX + drag.x)
                                val desiredY = zoom.offsetY + drag.y
                                val clampedY = clampY(zoom.scale, desiredY)
                                zoom.offsetY = clampedY
                                c.consume()
                                val remainder = desiredY - clampedY
                                if (remainder != 0f) scope.launch { listState.scrollBy(-remainder) }
                            }
                        }
                        // At 1x a single finger is never consumed:
                        // the LazyColumn scrolls vertically as usual.
                    }
                    if (event.changes.all { !it.pressed }) break
                } while (true)
                if (pinching) {
                    pinchActive = false
                    lastSent = zoom.scale
                    latestOnZoomChanged(zoom.scale)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(tapModifier)
                .then(gestureModifier),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                errorMessage != null -> Text(
                    text = errorMessage ?: "Error rendering PDF",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
                renderer != null && pageCount > 0 -> {
                    // Pages fit screen width at 1x; layout size NEVER changes with zoom.
                    // Zoom is a single graphicsLayer on the whole list container.
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = zoom.scale
                                scaleY = zoom.scale
                                translationX = zoom.offsetX
                                translationY = zoom.offsetY
                                transformOrigin = TransformOrigin(0f, 0f)
                                clip = false
                            },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        items(count = pageCount, key = { "$uriString-$it" }) { pageIndex ->
                            PdfPage(
                                renderer = renderer!!,
                                renderDispatcher = renderDispatcher,
                                cache = bitmapCache,
                                pageIndex = pageIndex,
                                aspect = pageAspects.getOrNull(pageIndex) ?: 0.707f,
                                bucket = renderBucket,
                                nightMode = nightMode,
                                targetWidthPx = (pageContentW * renderBucket)
                                    .toInt()
                                    .coerceIn(1, MAX_BITMAP_WIDTH_PX)
                            )
                        }
                    }
                    PdfPageIndicator(listState = listState, pageCount = pageCount)
                }
                else -> Text(
                    "No pages found in PDF",
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}

private fun invertBitmap(src: Bitmap): Bitmap {
    val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val paint = Paint().apply {
        colorFilter = ColorMatrixColorFilter(
            ColorMatrix(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
    }
    canvas.drawBitmap(src, 0f, 0f, paint)
    src.recycle()
    return out
}

@Composable
private fun PdfPage(
    renderer: PdfRenderer,
    renderDispatcher: kotlinx.coroutines.CoroutineDispatcher,
    cache: LruCache<String, Bitmap>,
    pageIndex: Int,
    aspect: Float,
    bucket: Float,
    nightMode: Boolean,
    targetWidthPx: Int
) {
    val cacheKey = "$pageIndex|$bucket|$nightMode"
    // The old bitmap stays on screen until the sharper one is ready: no flicker.
    var bitmap by remember(renderer, pageIndex) { mutableStateOf<Bitmap?>(null) }
    var pendingKey by remember(renderer, pageIndex) { mutableStateOf("") }

    LaunchedEffect(renderer, pageIndex, bucket, nightMode, targetWidthPx) {
        val key = cacheKey
        pendingKey = key
        cache.get(key)?.let {
            bitmap = it
            return@LaunchedEffect
        }
        val fresh = withContext(renderDispatcher) {
            try {
                val rendered = renderer.openPage(pageIndex).use { page ->
                    val w = targetWidthPx
                    val h = (w / aspect).toInt().coerceIn(1, MAX_BITMAP_HEIGHT_PX)
                    Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
                if (nightMode) invertBitmap(rendered) else rendered
            } catch (_: Exception) {
                null
            }
        } ?: return@LaunchedEffect
        cache.put(key, fresh)
        if (pendingKey == key) bitmap = fresh
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (nightMode) Color(0xFF1C1520) else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            val bmp = bitmap
            if (bmp != null && !bmp.isRecycled) {
                androidx.compose.foundation.Image(
                    bitmap = remember(bmp) { bmp.asImageBitmap() },
                    contentDescription = "PDF Page ${pageIndex + 1}",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(aspect)
                )
            } else {
                // Same size as the finished page: layout never shifts when it loads.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(aspect),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun BoxScope.PdfPageIndicator(listState: LazyListState, pageCount: Int) {
    var visible by remember { mutableStateOf(false) }
    val scrolling = listState.isScrollInProgress
    LaunchedEffect(scrolling) {
        if (scrolling) {
            visible = true
        } else {
            delay(1500)
            visible = false
        }
    }
    AnimatedVisibility(
        visible = visible && pageCount > 1,
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
                text = "${listState.firstVisibleItemIndex + 1} / $pageCount",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }
    }
}
