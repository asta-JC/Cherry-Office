package com.asta.cherryoffice.excel

import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.*
import android.widget.OverScroller
import com.asta.cherryoffice.ui.components.ZoomAnchor
import kotlin.math.abs

/** A viewport over cell coordinates; pinch zoom uses the same anchor math as PDF. */
class SpreadsheetReadingView(context: Context) : View(context) {
    var onZoom: (Float) -> Unit = {}
    var onCell: (CellData?) -> Unit = {}
    private var sheet: SheetData? = null
    private var zoom = 1f
    private var x = 0f
    private var y = 0f
    private var columns = floatArrayOf(0f)
    private var rows = floatArrayOf(0f)
    private val dp = resources.displayMetrics.density
    private val textSize = 12f * resources.displayMetrics.scaledDensity
    private val gutter = 44f * dp
    private val header = 34f * dp
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private var selected: CellData? = null
    private var query = ""
    private var previousFocusX = 0f
    private var previousFocusY = 0f
    private var ignoreScroll = false
    private val fling = OverScroller(context)

    init { setBackgroundColor(Color.WHITE); isFocusable = true; contentDescription = "Spreadsheet. Tap a cell to read or copy its value." }
    fun setSheet(value: SheetData) {
        if (sheet === value) return
        sheet = value; selected = null; onCell(null); x = 0f; y = 0f
        val count = value.maxColumns.coerceAtLeast(1)
        columns = FloatArray(count + 1)
        for (c in 0 until count) {
            val sampled = value.rows.take(60).maxOfOrNull { it.cells.getOrNull(c)?.value?.length ?: 0 } ?: 0
            val width = value.columnWidths[c] ?: (sampled * 7.5f + 24).coerceIn(76f, 260f)
            columns[c + 1] = columns[c] + width * dp
        }
        rows = FloatArray(value.rows.size + 1)
        value.rows.forEachIndexed { i, row -> rows[i + 1] = rows[i] + if (row.cells.any { it.wrapText || '\n' in it.value }) maxOf(76 * dp, textSize * 3.8f) else maxOf(38 * dp, textSize * 1.8f) }
        clamp(); invalidate()
    }
    fun setZoom(value: Float) { if (abs(value - zoom) > .002f) zoomTo(value, width / 2f, height / 2f) }
    fun setQuery(value: String) {
        if (value == query) return
        query = value
        val data = sheet ?: return
        if (value.isNotBlank()) {
            val row = data.rows.indexOfFirst { it.cells.any { cell -> cell.value.contains(value, true) } }
            if (row >= 0) {
                val col = data.rows[row].cells.indexOfFirst { it.value.contains(value, true) }
                y = rows[row] * zoom; x = columns[col] * zoom; clamp()
            }
        }
        invalidate()
    }
    private fun clamp() {
        x = x.coerceIn(0f, (columns.last() * zoom - (width - gutter)).coerceAtLeast(0f))
        y = y.coerceIn(0f, (rows.last() * zoom - (height - header)).coerceAtLeast(0f))
    }
    private fun zoomTo(value: Float, fx: Float, fy: Float, previousX: Float = fx, previousY: Float = fy) {
        val next = value.coerceIn(.65f, 3f)
        val anchor = ZoomAnchor(x, y).scaled(zoom, next, previousX - gutter, previousY - header, fx - gutter, fy - header)
        x = anchor.scrollX; y = anchor.scrollY; zoom = next; clamp(); onZoom(next); invalidate()
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { clamp() }
    private fun index(offsets: FloatArray, value: Float): Int {
        val found = java.util.Arrays.binarySearch(offsets, value)
        return (if (found >= 0) found else -found - 2).coerceIn(0, (offsets.size - 2).coerceAtLeast(0))
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val data = sheet ?: return
        val firstCol = index(columns, x / zoom)
        val firstRow = index(rows, y / zoom)
        val lastCol = index(columns, (x + width - gutter) / zoom)
        val lastRow = index(rows, (y + height - header) / zoom)
        canvas.save(); canvas.clipRect(gutter, header, width.toFloat(), height.toFloat())
        if (data.rows.isNotEmpty()) for (r in firstRow..minOf(lastRow, data.rows.lastIndex)) {
            val top = header + rows[r] * zoom - y
            val bottom = header + rows[r + 1] * zoom - y
            for (c in firstCol..lastCol) {
                val left = gutter + columns[c] * zoom - x
                val right = gutter + columns[c + 1] * zoom - x
                val cell = data.rows[r].cells.getOrNull(c)
                val value = cell?.value.orEmpty()
                paint.style = Paint.Style.FILL
                paint.color = color(cell?.fillColor) ?: Color.WHITE
                canvas.drawRect(left, top, right, bottom, paint)
                if (query.isNotBlank() && value.contains(query, true)) { paint.color = Color.rgb(255, 235, 157); canvas.drawRect(left, top, right, bottom, paint) }
                paint.color = Color.rgb(225, 226, 230); paint.style = Paint.Style.STROKE; paint.strokeWidth = dp * .5f
                canvas.drawRect(left, top, right, bottom, paint); paint.style = Paint.Style.FILL
                if (cell != null) {
                    textPaint.textSize = textSize * zoom
                    textPaint.color = color(cell.fontColor) ?: Color.rgb(30, 32, 38)
                    val fontStyle = when {
                        (cell.bold || cell.isHeader) && cell.italic -> Typeface.BOLD_ITALIC
                        cell.bold || cell.isHeader -> Typeface.BOLD
                        cell.italic -> Typeface.ITALIC
                        else -> Typeface.NORMAL
                    }
                    textPaint.typeface = Typeface.create(Typeface.DEFAULT, fontStyle)
                    val available = (right - left - 14 * dp * zoom).toInt().coerceAtLeast(1)
                    val alignment = when {
                        cell.alignment == "center" -> Layout.Alignment.ALIGN_CENTER
                        cell.alignment == "right" || (cell.alignment.isBlank() || cell.alignment == "general") && value.replace(",", "").toDoubleOrNull() != null -> Layout.Alignment.ALIGN_OPPOSITE
                        else -> Layout.Alignment.ALIGN_NORMAL
                    }
                    val layout = StaticLayout.Builder.obtain(value, 0, value.length, textPaint, available)
                        .setAlignment(alignment).setIncludePad(false).setMaxLines(if (cell.wrapText || '\n' in value) 3 else 1)
                        .setEllipsize(android.text.TextUtils.TruncateAt.END).build()
                    canvas.save(); canvas.clipRect(left + 2, top + 2, right - 2, bottom - 2)
                    canvas.translate(left + 7 * dp * zoom, top + ((bottom - top - layout.height) / 2).coerceAtLeast(3 * dp))
                    layout.draw(canvas); canvas.restore()
                    if (selected == cell) {
                        paint.style = Paint.Style.STROKE; paint.color = Color.rgb(168, 45, 75); paint.strokeWidth = 2 * dp
                        canvas.drawRect(left + dp, top + dp, right - dp, bottom - dp, paint); paint.style = Paint.Style.FILL
                    }
                }
            }
        }
        canvas.restore()
        paint.color = Color.rgb(245, 244, 246)
        canvas.drawRect(0f, 0f, width.toFloat(), header, paint); canvas.drawRect(0f, 0f, gutter, height.toFloat(), paint)
        textPaint.textSize = 11 * dp; textPaint.color = Color.rgb(91, 82, 91); textPaint.typeface = Typeface.DEFAULT
        textPaint.textAlign = Paint.Align.CENTER
        canvas.save(); canvas.clipRect(gutter, 0f, width.toFloat(), header)
        for (c in firstCol..lastCol) canvas.drawText(XlsxFileReader.indexToColumnName(c), gutter + (columns[c] + columns[c + 1]) * zoom / 2 - x, header / 2 + textPaint.textSize / 3, textPaint)
        canvas.restore()
        if (data.rows.isNotEmpty()) {
            canvas.save(); canvas.clipRect(0f, header, gutter, height.toFloat())
            for (r in firstRow..minOf(lastRow, data.rows.lastIndex)) canvas.drawText(data.rows[r].rowIndex.toString(), gutter / 2, header + (rows[r] + rows[r + 1]) * zoom / 2 - y + textPaint.textSize / 3, textPaint)
            canvas.restore()
        } else canvas.drawText("This sheet is empty", width / 2f, height / 2f, textPaint)
        textPaint.textAlign = Paint.Align.LEFT
    }
    private fun color(rgb: String?): Int? = rgb?.toLongOrNull(16)?.let { (0xFF000000L or it).toInt() }
    private fun select(event: MotionEvent) {
        val data = sheet ?: return
        if (event.x < gutter || event.y < header || data.rows.isEmpty()) return
        val row = index(rows, (event.y - header + y) / zoom)
        val col = index(columns, (event.x - gutter + x) / zoom)
        selected = data.rows.getOrNull(row)?.cells?.getOrNull(col)
        onCell(selected); invalidate()
    }
    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean { fling.forceFinished(true); return true }
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean { x += dx; y += dy; clamp(); invalidate(); return true }
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean { performClick(); select(e); return true }
        override fun onLongPress(e: MotionEvent) { select(e); performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }
        override fun onDoubleTap(e: MotionEvent): Boolean { zoomTo(if (zoom > 1.1f) 1f else 2f, e.x, e.y); return true }
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            fling.fling(x.toInt(), y.toInt(), -vx.toInt(), -vy.toInt(), 0, (columns.last() * zoom - width + gutter).toInt().coerceAtLeast(0), 0, (rows.last() * zoom - height + header).toInt().coerceAtLeast(0))
            postInvalidateOnAnimation(); return true
        }
    })
    private val scaling = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(d: ScaleGestureDetector): Boolean {
            ignoreScroll = true; fling.forceFinished(true); previousFocusX = d.focusX; previousFocusY = d.focusY
            val cancel = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0f, 0f, 0); gestures.onTouchEvent(cancel); cancel.recycle()
            return true
        }
        override fun onScale(d: ScaleGestureDetector): Boolean {
            zoomTo(zoom * d.scaleFactor, d.focusX, d.focusY, previousFocusX, previousFocusY)
            previousFocusX = d.focusX; previousFocusY = d.focusY; return true
        }
    })
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) ignoreScroll = false
        parent?.requestDisallowInterceptTouchEvent(true)
        scaling.onTouchEvent(event)
        if (!scaling.isInProgress && !ignoreScroll) gestures.onTouchEvent(event)
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun computeScroll() { if (fling.computeScrollOffset()) { x = fling.currX.toFloat(); y = fling.currY.toFloat(); postInvalidateOnAnimation() } }
}
