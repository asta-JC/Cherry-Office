package com.asta.cherryoffice.ui.components

/** Positive scroll offsets; keeps a document point under a moving focal point. */
data class ZoomAnchor(val scrollX: Float, val scrollY: Float) {
    fun scaled(oldScale: Float, newScale: Float, previousX: Float, previousY: Float,
        focusX: Float = previousX, focusY: Float = previousY): ZoomAnchor {
        val ratio = newScale / oldScale
        return ZoomAnchor((scrollX + previousX) * ratio - focusX, (scrollY + previousY) * ratio - focusY)
    }
}
