package com.asta.cherryoffice.utils

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.asta.cherryoffice.data.model.DocumentItem
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipInputStream
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

enum class DocumentCategory(val displayName: String) {
    ALL("All"),
    PDF("PDF"),
    WORD("Word"),
    EXCEL("Excel"),
    POWERPOINT("PowerPoint"),
    OTHER("Other")
}

object DocumentUtils {

    fun getDocumentMetadata(context: Context, uri: Uri): DocumentItem {
        val contentResolver = context.contentResolver
        var displayName = "Unnamed_Document"
        var size = 0L

        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            try {
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIndex != -1 && !cursor.isNull(nameIndex)) {
                            displayName = cursor.getString(nameIndex)
                        }
                        if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                            size = cursor.getLong(sizeIndex)
                        }
                    }
                }
            } catch (_: Exception) {
                // Ignore query failure, fallback to path segment
            }
        }

        if (displayName == "Unnamed_Document") {
            uri.lastPathSegment?.let { pathSegment ->
                displayName = pathSegment.substringAfterLast('/')
            }
        }

        val rawExtension = if (displayName.contains('.')) {
            displayName.substringAfterLast('.').lowercase(Locale.ROOT)
        } else {
            ""
        }

        var mimeType = contentResolver.getType(uri) ?: ""
        if (mimeType.isBlank() || mimeType == "application/octet-stream") {
            if (rawExtension.isNotBlank()) {
                mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(rawExtension) ?: ""
            }
        }

        if (mimeType.isBlank() || mimeType == "application/octet-stream") {
            mimeType = detectMimeTypeFromMagicNumbers(context, uri, rawExtension)
        }

        val finalExtension = if (rawExtension.isBlank()) {
            MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "doc"
        } else {
            rawExtension
        }

        return DocumentItem(
            uri = uri.toString(),
            name = displayName,
            mimeType = if (mimeType.isBlank()) "application/octet-stream" else mimeType,
            extension = finalExtension,
            size = size,
            lastOpened = System.currentTimeMillis()
        )
    }

    private fun detectMimeTypeFromMagicNumbers(context: Context, uri: Uri, extension: String): String {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val buffer = ByteArray(16)
                val bytesRead = inputStream.read(buffer)
                if (bytesRead >= 4) {
                    // PDF: %PDF -> 0x25, 0x50, 0x44, 0x46
                    if (buffer[0] == 0x25.toByte() && buffer[1] == 0x50.toByte() &&
                        buffer[2] == 0x44.toByte() && buffer[3] == 0x46.toByte()
                    ) {
                        return "application/pdf"
                    }
                    // Zip container (docx, xlsx, pptx): PK\x03\x04 -> 0x50, 0x4B, 0x03, 0x04
                    if (buffer[0] == 0x50.toByte() && buffer[1] == 0x4B.toByte() &&
                        buffer[2] == 0x03.toByte() && buffer[3] == 0x04.toByte()
                    ) {
                        return when (extension) {
                            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                            else -> "application/zip"
                        }
                    }
                    // OLE2 Compound Document (doc, xls, ppt): 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1
                    if (bytesRead >= 8 &&
                        buffer[0] == 0xD0.toByte() && buffer[1] == 0xCF.toByte() &&
                        buffer[2] == 0x11.toByte() && buffer[3] == 0xE0.toByte()
                    ) {
                        return when (extension) {
                            "xls" -> "application/vnd.ms-excel"
                            "ppt" -> "application/vnd.ms-powerpoint"
                            else -> "application/msword"
                        }
                    }
                    // PNG: 0x89, 0x50, 0x4E, 0x47
                    if (buffer[0] == 0x89.toByte() && buffer[1] == 0x50.toByte() &&
                        buffer[2] == 0x4E.toByte() && buffer[3] == 0x47.toByte()
                    ) {
                        return "image/png"
                    }
                    // JPEG: 0xFF, 0xD8, 0xFF
                    if (buffer[0] == 0xFF.toByte() && buffer[1] == 0xD8.toByte() && buffer[2] == 0xFF.toByte()) {
                        return "image/jpeg"
                    }
                }
            }
        } catch (_: Exception) {
            // Stream read exception
        }
        return "application/octet-stream"
    }

    fun extractDocxParagraphs(context: Context, uri: Uri): List<String> {
        val paragraphs = mutableListOf<String>()
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zipStream ->
                    var entry = zipStream.nextEntry
                    while (entry != null) {
                        if (entry.name == "word/document.xml") {
                            val reader = BufferedReader(InputStreamReader(zipStream, Charsets.UTF_8))
                            val xmlContent = reader.readText()

                            val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
                            val parser = factory.newPullParser()
                            parser.setInput(StringReader(xmlContent))

                            var eventType = parser.eventType
                            var inParagraph = false
                            val paragraphText = StringBuilder()

                            while (eventType != XmlPullParser.END_DOCUMENT) {
                                val name = parser.name
                                when (eventType) {
                                    XmlPullParser.START_TAG -> {
                                        if (name == "p" || name?.endsWith(":p") == true) {
                                            inParagraph = true
                                            paragraphText.clear()
                                        }
                                    }
                                    XmlPullParser.TEXT -> {
                                        if (inParagraph) {
                                            val text = parser.text
                                            if (!text.isNullOrEmpty()) {
                                                paragraphText.append(text)
                                            }
                                        }
                                    }
                                    XmlPullParser.END_TAG -> {
                                        if (name == "p" || name?.endsWith(":p") == true) {
                                            inParagraph = false
                                            val pStr = paragraphText.toString().trim()
                                            if (pStr.isNotBlank()) {
                                                paragraphs.add(pStr)
                                            }
                                            paragraphText.clear()
                                        }
                                    }
                                }
                                eventType = parser.next()
                            }
                            break
                        }
                        zipStream.closeEntry()
                        entry = zipStream.nextEntry
                    }
                }
            }
        } catch (_: Exception) {
            // Ignore extraction errors
        }
        return if (paragraphs.isNotEmpty()) paragraphs else listOf("Word document is empty or text could not be extracted.")
    }

    fun extractDocxText(context: Context, uri: Uri): String {
        val paragraphs = extractDocxParagraphs(context, uri)
        return paragraphs.joinToString("\n\n")
    }

    fun tryTakePersistableUriPermission(context: Context, uri: Uri) {
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (_: SecurityException) {
                try {
                    val readFlag = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(uri, readFlag)
                } catch (_: SecurityException) {
                    // Ignore if permission is non-persistable
                }
            } catch (_: Exception) {
                // Ignore general exceptions
            }
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val formattedValue = String.format(Locale.getDefault(), "%.1f", bytes / Math.pow(1024.0, digitGroups.toDouble()))
        return "$formattedValue ${units[digitGroups]}"
    }

    fun formatLastOpened(timestamp: Long): String {
        if (timestamp <= 0) return "Never"
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        val oneMinute = 60 * 1000L
        val oneHour = 60 * oneMinute
        val oneDay = 24 * oneHour

        return when {
            diff < oneMinute -> "Just now"
            diff < oneHour -> "${diff / oneMinute}m ago"
            diff < oneDay -> "${diff / oneHour}h ago"
            diff < 2 * oneDay -> "Yesterday"
            else -> {
                val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                sdf.format(Date(timestamp))
            }
        }
    }

    fun getCategoryForDocument(mimeType: String, extension: String): DocumentCategory {
        val mime = mimeType.lowercase(Locale.ROOT)
        val ext = extension.lowercase(Locale.ROOT)

        return when {
            mime.contains("pdf") || ext == "pdf" -> DocumentCategory.PDF
            mime.contains("word") || mime.contains("msword") || mime.contains("officedocument.wordprocessingml") || ext in listOf("doc", "docx") -> DocumentCategory.WORD
            mime.contains("excel") || mime.contains("ms-excel") || mime.contains("officedocument.spreadsheetml") || ext in listOf("xls", "xlsx", "csv") -> DocumentCategory.EXCEL
            mime.contains("powerpoint") || mime.contains("ms-powerpoint") || mime.contains("officedocument.presentationml") || ext in listOf("ppt", "pptx") -> DocumentCategory.POWERPOINT
            else -> DocumentCategory.OTHER
        }
    }
}
