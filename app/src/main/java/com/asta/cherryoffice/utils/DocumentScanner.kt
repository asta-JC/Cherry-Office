package com.asta.cherryoffice.utils

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.asta.cherryoffice.data.model.DocumentItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

object DocumentScanner {

    val SUPPORTED_EXTENSIONS = listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv")

    suspend fun scanDeviceDocuments(context: Context): List<DocumentItem> = withContext(Dispatchers.IO) {
        val documents = mutableListOf<DocumentItem>()
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.MIME_TYPE
        )

        val selectionParts = SUPPORTED_EXTENSIONS.map {
            "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.$it'"
        }
        val selection = selectionParts.joinToString(" OR ")
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC LIMIT 500"

        try {
            val queryUri = MediaStore.Files.getContentUri("external")
            context.contentResolver.query(
                queryUri,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Document"
                    val size = cursor.getLong(sizeCol)
                    val dateModified = cursor.getLong(dateCol) * 1000L
                    val mimeType = cursor.getString(mimeCol) ?: "application/octet-stream"

                    val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                    if (ext in SUPPORTED_EXTENSIONS) {
                        val contentUri = ContentUris.withAppendedId(queryUri, id)
                        documents.add(
                            DocumentItem(
                                uri = contentUri.toString(),
                                name = name,
                                mimeType = mimeType,
                                extension = ext,
                                size = size,
                                lastOpened = if (dateModified > 0) dateModified else System.currentTimeMillis()
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
            // MediaStore query fallback
        }

        documents
    }

    suspend fun scanDocumentTree(context: Context, treeUri: Uri): List<DocumentItem> = withContext(Dispatchers.IO) {
        val documents = mutableListOf<DocumentItem>()
        try {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            walkTreeUri(context, treeUri, docId, documents, depth = 0, maxDepth = 3)
        } catch (_: Exception) {}
        documents
    }

    private fun walkTreeUri(
        context: Context,
        treeUri: Uri,
        parentId: String,
        outList: MutableList<DocumentItem>,
        depth: Int,
        maxDepth: Int
    ) {
        if (depth > maxDepth || outList.size >= 500) return

        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        try {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (cursor.moveToNext()) {
                    val childId = cursor.getString(idCol)
                    val name = cursor.getString(nameCol) ?: continue
                    val mimeType = cursor.getString(mimeCol) ?: "application/octet-stream"
                    val size = cursor.getLong(sizeCol)
                    val dateModified = cursor.getLong(dateCol)

                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        walkTreeUri(context, treeUri, childId, outList, depth + 1, maxDepth)
                    } else {
                        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                        if (ext in SUPPORTED_EXTENSIONS) {
                            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                            outList.add(
                                DocumentItem(
                                    uri = docUri.toString(),
                                    name = name,
                                    mimeType = mimeType,
                                    extension = ext,
                                    size = size,
                                    lastOpened = if (dateModified > 0) dateModified else System.currentTimeMillis()
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }
}
