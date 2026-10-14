package com.asta.cherryoffice.docx

// Android glue around the pure-JVM DocxToHtmlConverter.
// - Reads the .docx bytes from any content/file Uri on Dispatchers.IO.
// - Saves embedded images into the app cache (downsampled so big photos
//   don't blow up the WebView) and rewrites their URLs.
// - Maps failures to friendly, non-technical messages for the UI.

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Result shown by WordViewer: either HTML ready to load, or a message. */
sealed interface DocxLoadResult {
    /** HTML plus the folder its relative "media/..." URLs resolve against. */
    data class Ready(val html: String, val baseDir: File) : DocxLoadResult

    /** Friendly message, never a stack trace. */
    data class Failed(val message: String) : DocxLoadResult
}

object DocxFileReader {

    // Never load more than this into RAM (protects against huge files).
    private const val MAX_DOCX_BYTES = 100 * 1024 * 1024

    // Images wider than this are downsampled before hitting the WebView.
    private const val MAX_IMAGE_DIMENSION = 1600

    suspend fun load(context: Context, uri: Uri): DocxLoadResult = withContext(Dispatchers.IO) {
        // 1. Read the raw bytes (capped).
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                var total = 0
                while (true) {
                    val n = input.read(chunk)
                    if (n < 0) break
                    total += n
                    if (total > MAX_DOCX_BYTES) {
                        return@withContext DocxLoadResult.Failed(
                            "This file is too large to open on this device " +
                                "(over 100 MB). Try a smaller document."
                        )
                    }
                    out.write(chunk, 0, n)
                }
                out.toByteArray()
            } ?: return@withContext DocxLoadResult.Failed(
                "The file could not be read. It may have been moved or deleted."
            )
        } catch (_: Exception) {
            return@withContext DocxLoadResult.Failed(
                "The file could not be read. Check storage permission and try again."
            )
        }

        // 2. Fresh cache folder for this document's images.
        val docDir = File(context.cacheDir, "docx/" + (uri.lastPathSegment?.hashCode() ?: 0).toString() + "_" + System.currentTimeMillis())
        val mediaDir = File(docDir, "media")
        mediaDir.mkdirs()

        // 3. Convert (pure CPU work, already on Dispatchers.IO).
        try {
            val converted = DocxToHtmlConverter.convert(bytes) { fileName, imageBytes ->
                saveImage(mediaDir, fileName, imageBytes)
                "media/$fileName"
            }
            DocxLoadResult.Ready(converted.html, docDir)
        } catch (e: DocxParseException) {
            DocxLoadResult.Failed(friendlyMessage(e))
        } catch (_: Exception) {
            DocxLoadResult.Failed(
                "This document couldn't be opened. It may be corrupted " +
                    "or use features this viewer doesn't support yet."
            )
        }
    }

    private fun friendlyMessage(e: DocxParseException): String = when (e.kind) {
        DocxFailureKind.ENCRYPTED ->
            "This file is password-protected. Remove the password in Word " +
                "and try again."
        DocxFailureKind.CORRUPT ->
            "This file couldn't be opened. It may be corrupted or not a " +
                "real Word document. Try opening it in Word or Google Docs."
        DocxFailureKind.EMPTY ->
            "This document has no readable content to show."
    }

    // Save one embedded image, downsampling large photos. Any failure falls
    // back to the original bytes (a broken image icon beats a crash), and on
    // non-Android runtimes (unit tests) the BitmapFactory stubs throw, which
    // the same fallback handles.
    private fun saveImage(mediaDir: File, fileName: String, bytes: ByteArray) {
        val safeName = fileName.substringAfterLast('/').takeIf { it.isNotBlank() } ?: "image"
        val out = File(mediaDir, safeName)
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val widest = maxOf(bounds.outWidth, bounds.outHeight)
            if (widest <= 0 || widest <= MAX_IMAGE_DIMENSION) {
                out.writeBytes(bytes) // small enough: keep original quality
                return
            }
            // Power-of-two sample size covering the excess.
            var sample = 1
            while (widest / (sample * 2) >= MAX_IMAGE_DIMENSION) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                ?: run { out.writeBytes(bytes); return }
            try {
                // Photos -> JPEG; art with transparency -> PNG.
                if (safeName.lowercase().endsWith(".png")) {
                    out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                } else {
                    out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 82, it) }
                }
            } finally {
                if (!bmp.isRecycled) bmp.recycle()
            }
        } catch (_: Exception) {
            try {
                out.writeBytes(bytes)
            } catch (_: Exception) {
                // Leave the placeholder to the HTML layer.
            }
        }
    }
}
