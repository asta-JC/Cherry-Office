package com.asta.cherryoffice.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import java.io.File

class PdfSession(context: Context, uri: Uri) : AutoCloseable {
    // Older platform PDFium versions share process-wide native state.
    private companion object { val nativeLock = Any() }
    private val file = File.createTempFile("reading_", ".pdf", context.cacheDir)
    private var descriptor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private var textReader: PdfTextReader? = null
    private var closed = false
    val sizes: List<Pair<Int, Int>>
    val bitmaps = object : LruCache<String, Bitmap>(40 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = (value.byteCount / 1024).coerceAtLeast(1)
    }
    val text = LruCache<Int, PdfTextPage>(8)
    init {
        try {
            PDFBoxResourceLoader.init(context.applicationContext)
            context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var total = 0L
                while (true) {
                    val n = input.read(buffer); if (n < 0) break
                    total += n
                    require(total <= 256L * 1024 * 1024) { "This PDF is too large to preview (maximum 256 MB)." }
                    output.write(buffer, 0, n)
                }
            } } ?: error("The PDF could not be read. It may have moved.")
            descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            sizes = synchronized(nativeLock) {
                val native = PdfRenderer(requireNotNull(descriptor)).also { renderer = it }
                require(native.pageCount > 0) { "This PDF contains no pages." }
                List(native.pageCount) { i -> native.openPage(i).use { it.width to it.height } }
            }
        } catch (e: Exception) { close(); throw e }
    }
    fun bitmapWidth(index: Int, requested: Int): Int = minOf(requested, 2400,
        (4096f * sizes[index].first / sizes[index].second).toInt()).coerceAtLeast(1)
    @Synchronized fun render(index: Int, requested: Int): Bitmap {
        check(!closed)
        val width = bitmapWidth(index, requested)
        val key = "$index:$width"
        bitmaps.get(key)?.let { return it }
        val height = (width.toFloat() * sizes[index].second / sizes[index].first).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(android.graphics.Color.WHITE)
            synchronized(nativeLock) {
                requireNotNull(renderer).openPage(index).use { it.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
            }
            bitmaps.put(key, bitmap)
            return bitmap
        } catch (e: Exception) { bitmap.recycle(); throw e }
    }
    @Synchronized fun extract(index: Int): PdfTextPage {
        check(!closed)
        text.get(index)?.let { return it }
        val reader = textReader ?: PdfBoxTextReader(file).also { textReader = it }
        return reader.page(index).also { text.put(index, it) }
    }
    @Synchronized fun copy(selection: PdfSelection): String = buildString {
        for (page in selection.start.page..selection.end.page) {
            val data = extract(page)
            check(data.copyAllowed) { "Copying is restricted by this PDF." }
            val start = if (page == selection.start.page) selection.start.offset.coerceIn(0, data.text.length) else 0
            val end = if (page == selection.end.page) selection.end.offset.coerceIn(start, data.text.length) else data.text.length
            if (isNotEmpty()) append('\n')
            append(data.text.substring(start, end))
        }
    }
    @Synchronized override fun close() {
        if (closed) return
        closed = true
        try { textReader?.close() } catch (_: Exception) {}
        synchronized(nativeLock) { try { renderer?.close() } catch (_: Exception) {} }
        renderer = null
        try { descriptor?.close() } catch (_: Exception) {}
        text.evictAll(); bitmaps.evictAll()
        file.delete()
    }
}
