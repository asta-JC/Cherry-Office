package com.asta.cherryoffice.ui.viewer

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asta.cherryoffice.data.model.DocumentItem
import com.asta.cherryoffice.utils.DocumentUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject

enum class ViewerDocumentType {
    PDF,
    IMAGE,
    TEXT,
    RICH_WORD,
    RICH_EXCEL,
    RICH_PPT,
    UNSUPPORTED
}

/** Prefer recognized file extensions over generic or incorrect provider MIME types. */
internal fun resolveViewerType(extension: String, mimeType: String): ViewerDocumentType {
    val ext = extension.lowercase(Locale.ROOT)
    val mime = mimeType.lowercase(Locale.ROOT).substringBefore(';').trim()
    return when (ext) {
        "pdf" -> ViewerDocumentType.PDF
        "png", "jpg", "jpeg", "webp", "gif", "bmp", "heic", "heif" -> ViewerDocumentType.IMAGE
        "xls", "csv", "tsv", "xlsx", "xlsm", "xltx" -> ViewerDocumentType.RICH_EXCEL
        "docx", "docm", "dotx" -> ViewerDocumentType.RICH_WORD
        "pptx", "pptm", "potx" -> ViewerDocumentType.RICH_PPT
        "txt", "md", "log", "json", "xml", "yaml", "yml" -> ViewerDocumentType.TEXT
        "doc", "ppt", "odt", "ods", "odp", "rtf", "html", "htm" -> ViewerDocumentType.UNSUPPORTED
        else -> when {
            mime == "application/pdf" -> ViewerDocumentType.PDF
            mime.startsWith("image/") -> ViewerDocumentType.IMAGE
            mime.contains("spreadsheetml") || mime == "application/vnd.ms-excel" || mime in listOf("text/csv", "text/tab-separated-values") -> ViewerDocumentType.RICH_EXCEL
            mime.contains("wordprocessingml") -> ViewerDocumentType.RICH_WORD
            mime.contains("presentationml") -> ViewerDocumentType.RICH_PPT
            mime.startsWith("text/") && mime !in listOf("text/html", "text/rtf") -> ViewerDocumentType.TEXT
            else -> ViewerDocumentType.UNSUPPORTED
        }
    }
}

data class ViewerUiState(
    val isLoading: Boolean = true,
    val sourceUri: String? = null,
    val documentItem: DocumentItem? = null,
    val documentType: ViewerDocumentType = ViewerDocumentType.UNSUPPORTED,
    val textContent: String = "",
    val wordParagraphs: List<String> = emptyList(),
    val pageCount: Int = 1,
    val currentPage: Int = 0,
    val zoomScale: Float = 1.0f,
    val fontSize: Float = 16f,
    val nightMode: Boolean = false,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val selectedTab: Int = 0,
    val sheetNames: List<String> = emptyList(),
    val slideTitles: List<String> = emptyList(),
    val slides: List<com.asta.cherryoffice.formats.PresentationSlide> = emptyList(),
    val notice: String? = null,
    val spreadsheetDocument: com.asta.cherryoffice.excel.SpreadsheetDocument? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class ViewerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: com.asta.cherryoffice.data.repository.SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ViewerUiState())
    val uiState: StateFlow<ViewerUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    fun loadDocument(uri: Uri) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = ViewerUiState(sourceUri = uri.toString())
            val initialZoom = settings.defaultZoomFlow.first()
            try {
                val metadata = withContext(Dispatchers.IO) {
                    DocumentUtils.tryTakePersistableUriPermission(context, uri)
                    DocumentUtils.getDocumentMetadata(context, uri)
                }
                _uiState.update { it.copy(documentItem = metadata) }
                val ext = metadata.extension.lowercase(Locale.ROOT)
                val mime = metadata.mimeType.lowercase(Locale.ROOT).substringBefore(';').trim()
                val docType = resolveViewerType(ext, mime)
                var content = ""
                var spreadsheet: com.asta.cherryoffice.excel.SpreadsheetDocument? = null
                var slides = emptyList<com.asta.cherryoffice.formats.PresentationSlide>()
                withContext(Dispatchers.IO) {
                    when (docType) {
                        ViewerDocumentType.TEXT -> {
                            content = context.contentResolver.openInputStream(uri)?.use { stream ->
                                val bytes = stream.buffered().use { input ->
                                    val out = java.io.ByteArrayOutputStream()
                                    val buffer = ByteArray(8192)
                                    while (out.size() <= 8 * 1024 * 1024) {
                                        val n = input.read(buffer)
                                        if (n < 0) break
                                        out.write(buffer, 0, n)
                                    }
                                    require(out.size() <= 8 * 1024 * 1024) { "This text file is too large to preview (maximum 8 MB)." }
                                    out.toByteArray()
                                }
                                val charset = when {
                                    bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> Charsets.UTF_16LE
                                    bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> Charsets.UTF_16BE
                                    else -> Charsets.UTF_8
                                }
                                String(bytes, charset).removePrefix("\uFEFF")
                            } ?: error("The file could not be read. It may have been moved or deleted.")
                        }
                        ViewerDocumentType.RICH_EXCEL -> {
                            spreadsheet = context.contentResolver.openInputStream(uri)?.use {
                                if (ext in listOf("csv", "tsv") || mime in listOf("text/csv", "text/tab-separated-values")) {
                                    com.asta.cherryoffice.excel.XlsxFileReader.readCsv(it, if (ext == "tsv" || mime == "text/tab-separated-values") "${metadata.name}.tsv" else "${metadata.name}.csv")
                                } else com.asta.cherryoffice.excel.OfficeSpreadsheetReader.read(it, metadata.name)
                            } ?: error("The spreadsheet could not be read.")
                        }
                        ViewerDocumentType.RICH_PPT -> {
                            slides = context.contentResolver.openInputStream(uri)?.use {
                                com.asta.cherryoffice.formats.PresentationReader.read(it)
                            } ?: error("The presentation could not be read.")
                        }
                        else -> Unit
                    }
                }
                val error = if (docType == ViewerDocumentType.UNSUPPORTED) {
                    when (ext) {
                        "doc", "xls", "ppt" -> "This older Office format needs an external office app. Save it as .docx, .xlsx or .pptx to preview here."
                        "odt", "ods", "odp", "rtf" -> "This format needs an external office app. Export it as PDF or a modern Office file to preview here."
                        else -> "This file type is not supported yet. Try opening it with another app."
                    }
                } else null
                _uiState.update { it.copy(isLoading = false, documentItem = metadata, documentType = docType,
                    textContent = content, spreadsheetDocument = spreadsheet, slides = slides,
                    sheetNames = spreadsheet?.sheets?.map { sheet -> sheet.name }.orEmpty(),
                    pageCount = if (slides.isNotEmpty()) slides.size else 1, zoomScale = initialZoom,
                    errorMessage = error) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, documentType = ViewerDocumentType.UNSUPPORTED,
                    errorMessage = e.localizedMessage ?: "This document could not be opened.") }
            }
        }
    }

    val keepScreenOn = settings.keepScreenOnFlow

    fun clearActionMessage() { _uiState.update { it.copy(notice = null) } }

    fun setPage(page: Int) {
        val maxPages = _uiState.value.pageCount.coerceAtLeast(1)
        val clamped = page.coerceIn(0, maxPages - 1)
        _uiState.update { it.copy(currentPage = clamped) }
    }

    fun nextPage() {
        val state = _uiState.value
        if (state.currentPage < state.pageCount - 1) {
            _uiState.update { it.copy(currentPage = it.currentPage + 1) }
        }
    }

    fun prevPage() {
        val state = _uiState.value
        if (state.currentPage > 0) {
            _uiState.update { it.copy(currentPage = it.currentPage - 1) }
        }
    }

    fun setZoom(scale: Float) {
        _uiState.update { it.copy(zoomScale = scale.coerceIn(1.0f, 5.0f)) }
    }

    fun setFontSize(size: Float) {
        _uiState.update { it.copy(fontSize = size.coerceIn(10f, 32f)) }
    }

    fun toggleNightMode() {
        _uiState.update { it.copy(nightMode = !it.nightMode) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun toggleSearch() {
        _uiState.update {
            val nextActive = !it.isSearchActive
            it.copy(isSearchActive = nextActive, searchQuery = if (!nextActive) "" else it.searchQuery)
        }
    }

    fun setSelectedTab(tabIndex: Int) {
        _uiState.update { it.copy(selectedTab = tabIndex) }
    }

    fun updatePageCount(count: Int) {
        if (count > 0) {
            _uiState.update { it.copy(pageCount = count, currentPage = it.currentPage.coerceIn(0, count - 1)) }
        }
    }

    fun shareDocument(context: Context, uriString: String) {
        try {
            val uri = Uri.parse(uriString)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = context.contentResolver.getType(uri) ?: "*/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Share Document via")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            _uiState.update { it.copy(notice = "Failed to share: ${e.localizedMessage}") }
        }
    }

    fun openWithAnotherApp(context: Context, uriString: String) {
        try {
            val uri = Uri.parse(uriString)
            val mimeType = context.contentResolver.getType(uri) ?: "*/*"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Open document with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            _uiState.update { it.copy(notice = "No app found to open this file: ${e.localizedMessage}") }
        }
    }
}
