package com.asta.cherryoffice.pdf

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.OverScroller
import android.widget.Toast
import com.asta.cherryoffice.ui.components.ZoomAnchor
import java.util.concurrent.Executors
import kotlin.math.*

/** Native gesture and selection surface. Page content is never recolored. */
class PdfReadingView(context: Context) : View(context) {
    var onPages: (Int) -> Unit = {}
    var onPage: (Int) -> Unit = {}
    var onZoom: (Float) -> Unit = {}
    var onError: (String) -> Unit = {}
    private val worker = Executors.newSingleThreadExecutor()
    private var disposed = false
    private var session: PdfSession? = null
    private val pending = mutableSetOf<String>()
    private val failed = mutableSetOf<String>()
    private val pageRects = mutableListOf<RectF>()
    private var scale = 1f
    private var x = 0f
    private var y = 0f
    private var totalHeight = 0f
    private var lastPage = -1
    private val density = resources.displayMetrics.density
    private val gap get() = 12f * density
    private var selection: PdfSelection? = null
    private var dragging = 0 // 1 anchor, 2 focus
    private var dragX = 0f
    private var dragY = 0f
    private var pinching = false
    private var ignoreUntilDown = false
    private val endpoints = mutableMapOf<Int, PdfTextPage>()
    private var previousFocusX = 0f
    private var previousFocusY = 0f
    private var action: ActionMode? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var accent = Color.rgb(168, 45, 75)
    private val scroller = OverScroller(context)
    private var uri: String? = null

    init {
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = "PDF document. Long press text to select and copy."
        setBackgroundColor(Color.rgb(233, 235, 239))
    }

    fun configureBackground(color: Int, selectionColor: Int) { setBackgroundColor(color); accent = selectionColor; invalidate() }
    fun open(uriString: String) {
        if (uri == uriString) return
        uri = uriString
        worker.execute {
            try {
                val opened = PdfSession(context, Uri.parse(uriString))
                post {
                    if (disposed) worker.execute { opened.close() }
                    else {
                        session = opened; layoutPages(); onPages(opened.sizes.size); invalidate()
                    }
                }
            } catch (e: Exception) { post { if (!disposed) onError(e.localizedMessage ?: "This PDF could not be opened.") } }
        }
    }
    fun dispose() {
        disposed = true; action?.finish(); removeCallbacks(edgeScroll)
        // Queue cleanup after all native rendering/extraction calls.
        val opened = session
        worker.execute { opened?.close() }
        // An in-flight open must still be able to hand cleanup to its completion.
        worker.execute { post { worker.shutdown() } }
    }
    fun setZoom(value: Float) {
        if (abs(value - scale) > .002f) zoomTo(value, width / 2f, height / 2f)
    }
    fun goToPage(index: Int) {
        if (pageRects.isEmpty()) return
        scroller.forceFinished(true)
        y = pageRects[index.coerceIn(pageRects.indices)].top * scale - gap
        clamp(); reportPage(); invalidate()
    }
    private fun layoutPages() {
        pageRects.clear()
        val doc = session ?: return
        val available = (width - gap * 2).coerceAtLeast(1f)
        var top = gap
        doc.sizes.forEach { (w, h) ->
            val bottom = top + available * h / w
            pageRects.add(RectF(gap, top, gap + available, bottom))
            top = bottom + gap
        }
        totalHeight = top
        clamp()
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val page = currentPage()
        val old = pageRects.getOrNull(page)
        val relative = if (old == null) 0f else ((y / scale - old.top) / old.height()).coerceIn(0f, 1f)
        val horizontal = if (oldw > 0) (x + oldw / 2f) / (oldw * scale) else .5f
        layoutPages()
        pageRects.getOrNull(page)?.let { y = (it.top + relative * it.height()) * scale }
        x = horizontal * w * scale - w / 2f
        clamp()
    }
    private fun clamp() {
        x = x.coerceIn(0f, (width * scale - width).coerceAtLeast(0f))
        y = y.coerceIn(0f, (totalHeight * scale - height).coerceAtLeast(0f))
    }
    private fun zoomTo(value: Float, focusX: Float, focusY: Float, previousX: Float = focusX, previousY: Float = focusY) {
        val next = value.coerceIn(1f, 5f)
        val anchor = ZoomAnchor(x, y).scaled(scale, next, previousX, previousY, focusX, focusY)
        x = anchor.scrollX; y = anchor.scrollY; scale = next
        clamp(); onZoom(scale); reportPage(); action?.invalidateContentRect(); invalidate()
    }
    private fun currentPage(): Int {
        if (pageRects.isEmpty()) return 0
        val top = y / scale
        return pageRects.indexOfFirst { it.bottom > top + gap }.let { if (it < 0) pageRects.lastIndex else it }
    }
    private fun reportPage() {
        if (pageRects.isEmpty()) return
        val page = currentPage()
        if (page != lastPage) { lastPage = page; onPage(page) }
    }
    private fun screenRect(page: Int): RectF = RectF(pageRects[page]).apply {
        left = left * scale - x; right = right * scale - x
        top = top * scale - y; bottom = bottom * scale - y
    }
    private fun glyphRect(page: Int, glyph: PdfGlyph): RectF {
        val rect = screenRect(page)
        val size = session!!.sizes[page]
        val sx = rect.width() / size.first
        val sy = rect.height() / size.second
        return RectF(rect.left + glyph.left * sx, rect.top + glyph.top * sy, rect.left + glyph.right * sx, rect.top + glyph.bottom * sy)
    }
    private fun requestPage(page: Int, targetWidth: Int) {
        val doc = session ?: return
        val renderKey = "$page:${doc.bitmapWidth(page, targetWidth)}"
        if (doc.bitmaps.get(renderKey) == null && renderKey !in failed && pending.add(renderKey)) worker.execute {
            var error = false
            try { doc.render(page, targetWidth) } catch (_: Exception) { error = true }
            post { pending.remove(renderKey); if (error) failed.add(renderKey); if (!disposed) invalidate() }
        }
        requestText(page)
    }
    private fun requestText(page: Int, after: ((PdfTextPage) -> Unit)? = null) {
        val doc = session ?: return
        doc.text.get(page)?.let { after?.invoke(it); return }
        val key = "text:$page"
        if (!pending.add(key)) {
            if (after != null) postDelayed({ if (!disposed) requestText(page, after) }, 100)
            return
        }
        worker.execute {
            val data = try { doc.extract(page) } catch (_: Exception) { PdfTextPage("", emptyList(), false, "Text selection is unavailable for this page.") }
            post {
                pending.remove(key)
                if (!disposed) { doc.text.put(page, data); after?.invoke(data); invalidate() }
            }
        }
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val doc = session
        if (doc == null) { drawMessage(canvas, "Opening PDF…", width / 2f, height / 2f); return }
        val first = currentPage()
        for (page in first until pageRects.size) {
            val rect = screenRect(page)
            if (rect.top > height) break
            if (rect.bottom < 0) continue
            paint.color = Color.WHITE; canvas.drawRect(rect, paint)
            val target = rect.width().toInt()
            val key = "$page:${doc.bitmapWidth(page, target)}"
            requestPage(page, target)
            val bitmap = doc.bitmaps.get(key) ?: doc.bitmaps.snapshot().entries.lastOrNull { it.key.startsWith("$page:") }?.value
            if (bitmap != null) canvas.drawBitmap(bitmap, null, rect, paint)
            else drawMessage(canvas, if (key in failed) "Tap to retry page ${page + 1}" else "Page ${page + 1}…", rect.centerX(), rect.centerY())
            val selected = selection
            val data = doc.text.get(page)
            if (selected != null && data != null) {
                paint.color = (accent and 0x00FFFFFF) or 0x55000000
                data.glyphs.filter { selected.contains(page, it) }.forEach { canvas.drawRect(glyphRect(page, it), paint) }
            }
        }
        // Prefetch only adjacent pages, with bounded bitmap and text caches.
        if (first > 0) requestText(first - 1)
        if (first + 1 < pageRects.size) requestText(first + 1)
        handlePoint(true)?.let { drawHandle(canvas, it) }
        handlePoint(false)?.let { drawHandle(canvas, it) }
    }
    private fun drawMessage(canvas: Canvas, message: String, atX: Float, atY: Float) {
        paint.color = Color.rgb(100, 90, 98); paint.textSize = 14 * density; paint.textAlign = Paint.Align.CENTER
        canvas.drawText(message, atX, atY, paint)
    }
    private fun drawHandle(canvas: Canvas, point: PointF) {
        paint.color = accent
        canvas.drawCircle(point.x, point.y + 8 * density, 9 * density, paint)
        paint.strokeWidth = 2 * density; canvas.drawLine(point.x, point.y - 10 * density, point.x, point.y + 8 * density, paint)
    }
    private fun handlePoint(start: Boolean): PointF? {
        val selected = selection ?: return null
        val point = if (start) selected.start else selected.end
        if (point.page !in pageRects.indices) return null
        val data = endpoints[point.page] ?: session?.text?.get(point.page) ?: return null
        val glyph = if (start) data.glyphs.firstOrNull { it.end > point.offset } else data.glyphs.lastOrNull { it.start < point.offset }
        return glyph?.let { val rect = glyphRect(point.page, it); PointF(if (start) rect.left else rect.right, rect.bottom) }
    }
    private fun pageAt(screenY: Float): Int {
        val global = (screenY + y) / scale
        return pageRects.indexOfFirst { global <= it.bottom }.let { if (it < 0) pageRects.lastIndex else it }
    }
    private fun nearest(page: Int, screenX: Float, screenY: Float): PdfGlyph? {
        val data = session?.text?.get(page) ?: return null
        return data.glyphs.minByOrNull {
            val r = glyphRect(page, it)
            val dx = maxOf(r.left - screenX, 0f, screenX - r.right)
            val dy = maxOf(r.top - screenY, 0f, screenY - r.bottom)
            dx * dx + dy * dy * 4
        }
    }
    private fun selectWord(atX: Float, atY: Float) {
        if (pageRects.isEmpty()) return
        val page = pageAt(atY)
        requestText(page) { data ->
            if (!data.copyAllowed || data.glyphs.isEmpty()) {
                Toast.makeText(context, data.message ?: "No selectable text on this page.", Toast.LENGTH_SHORT).show()
            } else {
                val glyph = nearest(page, atX, atY) ?: return@requestText
                val rect = glyphRect(page, glyph)
                if (atX < rect.left - 32 * density || atX > rect.right + 32 * density || abs(atY - rect.centerY()) > 32 * density) return@requestText
                val word = data.wordAt(glyph.start)
                endpoints.clear(); endpoints[page] = data
                selection = PdfSelection(PdfTextPoint(page, word.first), PdfTextPoint(page, word.last + 1))
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                showCopyAction(); invalidate()
            }
        }
    }
    private fun extendSelection() {
        val selected = selection ?: return
        val page = pageAt(dragY.coerceIn(0f, height.toFloat()))
        if (page < 0) return
        if (session?.text?.get(page) == null) { requestText(page) { if (dragging != 0) extendSelection() }; return }
        val glyph = nearest(page, dragX, dragY) ?: return
        val rect = glyphRect(page, glyph)
        val offset = if (dragX > rect.centerX()) glyph.end else glyph.start
        val point = PdfTextPoint(page, offset)
        selection = if (dragging == 1) PdfSelection(point, selected.focus) else PdfSelection(selected.anchor, point)
        session?.text?.get(page)?.let { endpoints[page] = it }
        val updated = selection!!
        endpoints.keys.retainAll(setOf(updated.anchor.page, updated.focus.page))
        action?.invalidateContentRect(); invalidate()
    }
    private val edgeScroll = object : Runnable {
        override fun run() {
            if (dragging == 0 || disposed) return
            val edge = 56 * density
            val delta = when { dragY < edge -> -18 * density; dragY > height - edge -> 18 * density; else -> 0f }
            if (delta != 0f) { y += delta; clamp(); reportPage(); extendSelection(); invalidate() }
            postDelayed(this, 16)
        }
    }
    private fun showCopyAction() {
        if (action != null) { action?.invalidate(); return }
        action = startActionMode(object : ActionMode.Callback2() {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                menu.add(0, android.R.id.copy, 0, "Copy").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                return true
            }
            override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false
            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                if (item.itemId != android.R.id.copy) return false
                copySelection(); return true
            }
            override fun onDestroyActionMode(mode: ActionMode) { action = null; selection = null; invalidate() }
            override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) {
                val start = handlePoint(true) ?: PointF(width / 2f, height / 2f)
                outRect.set((start.x - 20 * density).toInt().coerceAtLeast(0), (start.y - 32 * density).toInt().coerceAtLeast(0),
                    (start.x + 20 * density).toInt().coerceAtMost(width), start.y.toInt().coerceIn(0, height))
            }
        }, ActionMode.TYPE_FLOATING)
    }
    private fun copySelection() {
        val chosen = selection ?: return
        val doc = session ?: return
        worker.execute {
            val result = runCatching { doc.copy(chosen) }
            post {
                if (!disposed) result.onSuccess {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("PDF text", it))
                    if (android.os.Build.VERSION.SDK_INT < 33) Toast.makeText(context, "Text copied", Toast.LENGTH_SHORT).show()
                    action?.finish()
                }.onFailure { Toast.makeText(context, it.localizedMessage ?: "The selection could not be copied.", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            scroller.forceFinished(true); pinching = true; ignoreUntilDown = true
            val cancel = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
            gestures.onTouchEvent(cancel); cancel.recycle()
            previousFocusX = detector.focusX; previousFocusY = detector.focusY
            return true
        }
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            zoomTo(scale * detector.scaleFactor, detector.focusX, detector.focusY, previousFocusX, previousFocusY)
            previousFocusX = detector.focusX; previousFocusY = detector.focusY
            return true
        }
        override fun onScaleEnd(detector: ScaleGestureDetector) { pinching = false }
    })
    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(event: MotionEvent): Boolean { scroller.forceFinished(true); return true }
        override fun onScroll(first: MotionEvent?, event: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (!pinching && dragging == 0) { x += distanceX; y += distanceY; clamp(); reportPage(); action?.invalidateContentRect(); invalidate() }
            return true
        }
        override fun onFling(first: MotionEvent?, last: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (pinching || dragging != 0) return false
            scroller.fling(x.toInt(), y.toInt(), -velocityX.toInt(), -velocityY.toInt(), 0, (width * scale - width).toInt().coerceAtLeast(0), 0, (totalHeight * scale - height).toInt().coerceAtLeast(0))
            postInvalidateOnAnimation(); return true
        }
        override fun onDoubleTap(event: MotionEvent): Boolean { zoomTo(if (scale > 1.1f) 1f else 2.5f, event.x, event.y); return true }
        override fun onLongPress(event: MotionEvent) { if (!pinching && dragging == 0) selectWord(event.x, event.y) }
        override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
            performClick()
            if (selection != null) action?.finish()
            else if (failed.isNotEmpty()) { failed.clear(); invalidate() }
            return true
        }
    })
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (disposed) return false
        parent?.requestDisallowInterceptTouchEvent(true)
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            ignoreUntilDown = false
            val selected = selection
            fun distance(point: PointF?) = if (point == null) Float.POSITIVE_INFINITY else hypot(event.x - point.x, event.y - point.y - 8 * density)
            val startDistance = distance(handlePoint(true))
            val endDistance = distance(handlePoint(false))
            if (selected != null && minOf(startDistance, endDistance) < 28 * density) {
                val choseStart = startDistance <= endDistance
                dragging = if ((choseStart && selected.anchor == selected.start) || (!choseStart && selected.anchor == selected.end)) 1 else 2
                dragX = event.x; dragY = event.y; post(edgeScroll); return true
            }
        }
        if (dragging != 0) {
            if (event.actionMasked == MotionEvent.ACTION_MOVE) { dragX = event.x; dragY = event.y - 8 * density; extendSelection() }
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL || event.pointerCount > 1) {
                dragging = 0; removeCallbacks(edgeScroll); action?.invalidateContentRect()
            }
            return true
        }
        scaleDetector.onTouchEvent(event)
        if (!scaleDetector.isInProgress && !ignoreUntilDown) gestures.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP) parent?.requestDisallowInterceptTouchEvent(false)
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun computeScroll() {
        if (scroller.computeScrollOffset()) { x = scroller.currX.toFloat(); y = scroller.currY.toFloat(); reportPage(); action?.invalidateContentRect(); postInvalidateOnAnimation() }
    }
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.isScrollable = true
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
        if (selection != null) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_COPY)
        info.text = session?.text?.get(currentPage())?.text
    }
    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean = when (action) {
        AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> { y += height * .8f; clamp(); reportPage(); invalidate(); true }
        AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> { y -= height * .8f; clamp(); reportPage(); invalidate(); true }
        AccessibilityNodeInfo.ACTION_COPY -> { copySelection(); true }
        else -> super.performAccessibilityAction(action, arguments)
    }
}
