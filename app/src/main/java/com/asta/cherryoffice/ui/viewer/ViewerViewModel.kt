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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale
import javax.inject.Inject

enum class ViewerDocumentType {
    PDF,
    TEXT,
    RICH_WORD,
    RICH_EXCEL,
    RICH_PPT,
    UNSUPPORTED
}

data class ViewerUiState(
    val isLoading: Boolean = true,
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
    val sheetNames: List<String> = listOf("Sheet 1", "Sheet 2", "Data Summary"),
    val slideTitles: List<String> = listOf("Title Slide", "Executive Summary", "Key Metrics", "Conclusion"),
    val errorMessage: String? = null
)

@HiltViewModel
class ViewerViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ViewerUiState())
    val uiState: StateFlow<ViewerUiState> = _uiState.asStateFlow()

    fun loadDocument(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                DocumentUtils.tryTakePersistableUriPermission(context, uri)
                val metadata = DocumentUtils.getDocumentMetadata(context, uri)
                val ext = metadata.extension.lowercase(Locale.ROOT)
                val mime = metadata.mimeType.lowercase(Locale.ROOT)

                val docType = when {
                    ext == "pdf" || mime.contains("pdf") -> ViewerDocumentType.PDF
                    ext in listOf("txt", "md", "log", "csv", "json", "xml") || mime.contains("text/") -> ViewerDocumentType.TEXT
                    ext in listOf("doc", "docx") || mime.contains("word") || mime.contains("wordprocessingml") -> ViewerDocumentType.RICH_WORD
                    ext in listOf("xls", "xlsx") || mime.contains("excel") || mime.contains("spreadsheetml") -> ViewerDocumentType.RICH_EXCEL
                    ext in listOf("ppt", "pptx") || mime.contains("powerpoint") || mime.contains("presentationml") -> ViewerDocumentType.RICH_PPT
                    else -> ViewerDocumentType.UNSUPPORTED
                }

                var content = ""
                var paragraphs = emptyList<String>()
                if (docType == ViewerDocumentType.TEXT) {
                    content = withContext(Dispatchers.IO) {
                        try {
                            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                                    val sb = StringBuilder()
                                    var line: String?
                                    var count = 0
                                    while (reader.readLine().also { line = it } != null && count < 2000) {
                                        sb.append(line).append("\n")
                                        count++
                                    }
                                    sb.toString()
                                }
                            } ?: "Unable to read document stream."
                        } catch (e: Exception) {
                            "Error reading text content: ${e.localizedMessage}"
                        }
                    }
                } else if (docType == ViewerDocumentType.RICH_WORD) {
                    // Word renders itself: WordViewer parses the .docx on a
                    // background thread, so nothing to pre-load here.
                    paragraphs = emptyList()
                    content = ""
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        documentItem = metadata,
                        documentType = docType,
                        textContent = content,
                        wordParagraphs = paragraphs,
                        pageCount = if (docType == ViewerDocumentType.PDF) 5 else 4,
                        zoomScale = 1f
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        documentType = ViewerDocumentType.UNSUPPORTED,
                        errorMessage = e.localizedMessage ?: "Failed to load document"
                    )
                }
            }
        }
    }

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
            _uiState.update { it.copy(errorMessage = "Failed to share: ${e.localizedMessage}") }
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
            context.startActivity(intent)
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = "No app found to open this file: ${e.localizedMessage}") }
        }
    }
}
