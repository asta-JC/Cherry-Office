package com.asta.cherryoffice.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Encapsulates the zoom scale and 2D pan offset.
 * Survives configuration changes and provides animated transitions.
 */
@Stable
class ZoomState(
    initialScale: Float = 1.0f,
    initialOffset: Offset = Offset.Zero,
    val minScale: Float = 1.0f,
    val maxScale: Float = 5.0f,
    val doubleTapScale: Float = 2.5f
) {
    var scale by mutableFloatStateOf(initialScale)
    var offset by mutableStateOf(initialOffset)

    // True while the user has >= 2 fingers on screen
    var isPinching by mutableStateOf(false)

    // Helper property to let parent containers (like Pagers) know whether to disable scrolling
    val isZoomed: Boolean
        get() = scale > 1.02f

    fun reset() {
        scale = minScale
        offset = Offset.Zero
    }

    companion object {
        val Saver = listSaver<ZoomState, Float>(
            save = { listOf(it.scale, it.offset.x, it.offset.y) },
            restore = {
                ZoomState(
                    initialScale = it[0],
                    initialOffset = Offset(it[1], it[2])
                )
            }
        )
    }
}

@Composable
fun rememberZoomState(
    key: String? = null,
    minScale: Float = 1.0f,
    maxScale: Float = 5.0f,
    doubleTapScale: Float = 2.5f
): ZoomState {
    return rememberSaveable(key, saver = ZoomState.Saver) {
        ZoomState(
            initialScale = 1.0f,
            initialOffset = Offset.Zero,
            minScale = minScale,
            maxScale = maxScale,
            doubleTapScale = doubleTapScale
        )
    }
}

/**
 * A drop-in zoomable container for PDF pages and document viewers.
 *
 * @param zoomState State managing scale, offset, and bounds.
 * @param modifier Outer layout modifier.
 * @param onZoomChanged Optional callback triggered when scale settles.
 * @param content The composable view to scale and pan (e.g., Image, Canvas, or Page View).
 */
@Composable
fun ZoomableBox(
    zoomState: ZoomState = rememberZoomState(),
    modifier: Modifier = Modifier,
    onZoomChanged: (Float) -> Unit = {},
    content: @Composable BoxScope.() -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var animJob by remember { mutableStateOf<Job?>(null) }
    val latestOnZoomChanged by rememberUpdatedState(onZoomChanged)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val viewportWidth = constraints.maxWidth.toFloat()
        val viewportHeight = constraints.maxHeight.toFloat()

        /**
         * Clamps the translation offset so the zoomed document cannot be
         * panned completely off the screen.
         *
         * Top-Left Anchor Coordinate Rules:
         * - If scaled dimension > viewport: content bounds must cover the viewport.
         *   minOffset = viewportSize - scaledContentSize
         *   maxOffset = 0f
         * - If scaled dimension <= viewport: keep content centered.
         *   offset = (viewportSize - scaledContentSize) / 2
         */
        fun clampOffset(targetScale: Float, candidateOffset: Offset): Offset {
            val contentWidth = viewportWidth * targetScale
            val contentHeight = viewportHeight * targetScale

            val clampedX = if (contentWidth > viewportWidth) {
                candidateOffset.x.coerceIn(viewportWidth - contentWidth, 0f)
            } else {
                (viewportWidth - contentWidth) / 2f
            }

            val clampedY = if (contentHeight > viewportHeight) {
                candidateOffset.y.coerceIn(viewportHeight - contentHeight, 0f)
            } else {
                (viewportHeight - contentHeight) / 2f
            }

            return Offset(clampedX, clampedY)
        }

        /**
         * Smoothly animates scale and offset to target values (e.g. on double-tap).
         */
        fun animateZoomTo(targetScale: Float, targetOffset: Offset) {
            animJob?.cancel()
            animJob = coroutineScope.launch {
                val clampedTargetOffset = clampOffset(targetScale, targetOffset)

                val scaleAnim = Animatable(zoomState.scale)
                val offsetAnim = Animatable(zoomState.offset, Offset.VectorConverter)

                val spec = tween<Float>(durationMillis = 280, easing = FastOutSlowInEasing)
                val offsetSpec = tween<Offset>(durationMillis = 280, easing = FastOutSlowInEasing)

                launch {
                    scaleAnim.animateTo(targetScale, animationSpec = spec) {
                        zoomState.scale = value
                    }
                }
                launch {
                    offsetAnim.animateTo(clampedTargetOffset, animationSpec = offsetSpec) {
                        zoomState.offset = value
                    }
                }

                latestOnZoomChanged(targetScale)
            }
        }

        // 1. Double-Tap Detection Modifier
        val doubleTapModifier = Modifier.pointerInput(viewportWidth, viewportHeight) {
            detectTapGestures(
                onDoubleTap = { tapPosition ->
                    if (zoomState.scale > 1.05f) {
                        // Reset to 1x and centered position
                        val restingOffset = clampOffset(zoomState.minScale, Offset.Zero)
                        animateZoomTo(zoomState.minScale, restingOffset)
                    } else {
                        // Zoom in anchored at tap focal point
                        val targetScale = zoomState.doubleTapScale
                        val scaleRatio = targetScale / zoomState.scale

                        // offset = (offset - centroid) * (newScale / oldScale) + centroid
                        val calculatedOffset = (zoomState.offset - tapPosition) * scaleRatio + tapPosition
                        animateZoomTo(targetScale, calculatedOffset)
                    }
                }
            )
        }

        // 2. Pinch-to-Zoom & Pan Gesture Handler
        val gestureModifier = Modifier.pointerInput(viewportWidth, viewportHeight) {
            awaitEachGesture {
                // Wait for touch down without consuming so taps are not swallowed
                awaitFirstDown(requireUnconsumed = false)
                animJob?.cancel()

                var isGestureActive = false

                do {
                    val event = awaitPointerEvent()
                    val activePointers = event.changes.filter { it.pressed }

                    when {
                        // Multi-touch: User is pinching with 2 or more fingers
                        activePointers.size >= 2 -> {
                            if (!isGestureActive) {
                                isGestureActive = true
                                zoomState.isPinching = true
                            }

                            val oldScale = zoomState.scale
                            val zoomFactor = event.calculateZoom()
                            val pan = event.calculatePan()
                            val centroid = event.calculateCentroid()

                            val newScale = (oldScale * zoomFactor).coerceIn(
                                zoomState.minScale,
                                zoomState.maxScale
                            )

                            val scaleRatio = newScale / oldScale

                            // Math: Anchor zoom translation to touch centroid:
                            // offset = (offset - centroid) * (newScale / oldScale) + centroid + pan
                            val rawOffset = (zoomState.offset - centroid) * scaleRatio + centroid + pan
                            val clampedOffset = clampOffset(newScale, rawOffset)

                            zoomState.scale = newScale
                            zoomState.offset = clampedOffset

                            // CRITICAL: Consume all changes to block parent Pagers and Lists
                            // from intercepting horizontal/vertical touch deltas
                            event.changes.forEach { it.consume() }
                        }

                        // Single-touch: User is panning while zoomed in
                        activePointers.size == 1 && zoomState.scale > 1.01f -> {
                            val pointerChange = activePointers[0]
                            val dragDelta = pointerChange.position - pointerChange.previousPosition

                            if (dragDelta != Offset.Zero) {
                                val rawOffset = zoomState.offset + dragDelta
                                val clampedOffset = clampOffset(zoomState.scale, rawOffset)

                                zoomState.offset = clampedOffset

                                // Consume event to prevent parent scroll conflict
                                pointerChange.consume()
                            }
                        }

                        // Single-touch at 1x: Leave unconsumed so parent LazyColumn or Pager scrolls normally
                    }

                    val hasActiveTouches = event.changes.any { it.pressed }
                } while (hasActiveTouches)

                if (isGestureActive) {
                    zoomState.isPinching = false
                    latestOnZoomChanged(zoomState.scale)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(doubleTapModifier)
                .then(gestureModifier),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Use (0,0) transform origin so math exactly maps screen coordinates
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = zoomState.scale
                        scaleY = zoomState.scale
                        translationX = zoomState.offset.x
                        translationY = zoomState.offset.y
                    },
                content = content
            )
        }
    }
}
