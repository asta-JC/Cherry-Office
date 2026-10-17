package com.asta.cherryoffice.ui.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asta.cherryoffice.data.model.DocumentItem
import com.asta.cherryoffice.data.repository.DocumentRepository
import com.asta.cherryoffice.utils.DocumentCategory
import com.asta.cherryoffice.utils.DocumentScanner
import com.asta.cherryoffice.utils.DocumentUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class DocumentSortOrder(val displayName: String) {
    DATE_DESC("Newest First"),
    DATE_ASC("Oldest First"),
    NAME_ASC("Name (A–Z)"),
    NAME_DESC("Name (Z–A)"),
    SIZE_DESC("Largest First")
}

data class HomeUiState(
    val recentDocuments: List<DocumentItem> = emptyList(),
    val totalDocumentCount: Int = 0,
    val pdfCount: Int = 0,
    val wordCount: Int = 0,
    val excelCount: Int = 0,
    val pptCount: Int = 0,
    val favoriteCount: Int = 0,
    val selectedCategory: DocumentCategory = DocumentCategory.ALL,
    val showOnlyFavorites: Boolean = false,
    val sortOrder: DocumentSortOrder = DocumentSortOrder.DATE_DESC,
    val searchQuery: String = "",
    val isSearchVisible: Boolean = false,
    val isScanning: Boolean = false,
    val openedDocument: DocumentItem? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: DocumentRepository,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow(DocumentCategory.ALL)
    private val _showOnlyFavorites = MutableStateFlow(false)
    private val _sortOrder = MutableStateFlow(DocumentSortOrder.DATE_DESC)
    private val _searchQuery = MutableStateFlow("")
    private val _isSearchVisible = MutableStateFlow(false)
    private val _isScanning = MutableStateFlow(false)
    private val _openedDocument = MutableStateFlow<DocumentItem?>(null)
    private val _errorMessage = MutableStateFlow<String?>(null)

    private val allDocumentsFlow = repository.getAllRecentDocuments()

    init {
        // Automatically check if database is empty on start, and trigger initial device scan
        viewModelScope.launch {
            try {
                val currentDocs = allDocumentsFlow.first()
                if (currentDocs.isEmpty()) {
                    scanDeviceStorage()
                }
            } catch (_: Exception) {}
        }
    }

    private val filteredAndSortedFlow = combine(
        allDocumentsFlow,
        _selectedCategory,
        _showOnlyFavorites,
        _searchQuery,
        _sortOrder
    ) { docs, category, onlyFavorites, query, sortOrder ->
        // 1. Calculate overall counts for dashboard pills
        var pdfs = 0
        var words = 0
        var excels = 0
        var ppts = 0
        var favs = 0

        for (doc in docs) {
            if (doc.isFavorite) favs++
            when (DocumentUtils.getCategoryForDocument(doc.mimeType, doc.extension)) {
                DocumentCategory.PDF -> pdfs++
                DocumentCategory.WORD -> words++
                DocumentCategory.EXCEL -> excels++
                DocumentCategory.POWERPOINT -> ppts++
                else -> {}
            }
        }

        // 2. Filter documents
        val filtered = docs.filter { doc ->
            val matchesCategory = if (onlyFavorites) {
                doc.isFavorite
            } else when (category) {
                DocumentCategory.ALL -> true
                else -> DocumentUtils.getCategoryForDocument(doc.mimeType, doc.extension) == category
            }

            val matchesQuery = if (query.isBlank()) {
                true
            } else {
                doc.name.contains(query, ignoreCase = true) ||
                        doc.extension.contains(query, ignoreCase = true)
            }

            matchesCategory && matchesQuery
        }

        // 3. Sort documents
        val sorted = when (sortOrder) {
            DocumentSortOrder.DATE_DESC -> filtered.sortedByDescending { it.lastOpened }
            DocumentSortOrder.DATE_ASC -> filtered.sortedBy { it.lastOpened }
            DocumentSortOrder.NAME_ASC -> filtered.sortedBy { it.name.lowercase() }
            DocumentSortOrder.NAME_DESC -> filtered.sortedByDescending { it.name.lowercase() }
            DocumentSortOrder.SIZE_DESC -> filtered.sortedByDescending { it.size }
        }

        CountsAndDocs(
            docs = sorted,
            totalCount = docs.size,
            pdfCount = pdfs,
            wordCount = words,
            excelCount = excels,
            pptCount = ppts,
            favoriteCount = favs,
            selectedCategory = category,
            showOnlyFavorites = onlyFavorites,
            sortOrder = sortOrder,
            searchQuery = query
        )
    }

    val uiState: StateFlow<HomeUiState> = combine(
        filteredAndSortedFlow,
        _isSearchVisible,
        _isScanning,
        _openedDocument
    ) { data, isSearchVisible, isScanning, openedDoc ->
        HomeUiState(
            recentDocuments = data.docs,
            totalDocumentCount = data.totalCount,
            pdfCount = data.pdfCount,
            wordCount = data.wordCount,
            excelCount = data.excelCount,
            pptCount = data.pptCount,
            favoriteCount = data.favoriteCount,
            selectedCategory = data.selectedCategory,
            showOnlyFavorites = data.showOnlyFavorites,
            sortOrder = data.sortOrder,
            searchQuery = data.searchQuery,
            isSearchVisible = isSearchVisible,
            isScanning = isScanning,
            openedDocument = openedDoc,
            errorMessage = _errorMessage.value
        )
    }.combine(_errorMessage) { state, error ->
        state.copy(errorMessage = error)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    fun scanDeviceStorage() {
        viewModelScope.launch {
            _isScanning.value = true
            try {
                val scanned = DocumentScanner.scanDeviceDocuments(context)
                if (scanned.isNotEmpty()) {
                    repository.saveDocuments(scanned)
                }
            } catch (e: Exception) {
                _errorMessage.value = "Storage scan error: ${e.localizedMessage}"
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun scanDirectory(treeUri: Uri) {
        viewModelScope.launch {
            _isScanning.value = true
            try {
                DocumentUtils.tryTakePersistableUriPermission(context, treeUri)
                val scanned = DocumentScanner.scanDocumentTree(context, treeUri)
                if (scanned.isNotEmpty()) {
                    repository.saveDocuments(scanned)
                }
            } catch (e: Exception) {
                _errorMessage.value = "Folder scan error: ${e.localizedMessage}"
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun onDocumentSelected(uri: Uri) {
        viewModelScope.launch {
            try {
                DocumentUtils.tryTakePersistableUriPermission(context, uri)
                val metadata = DocumentUtils.getDocumentMetadata(context, uri)
                val id = repository.saveDocument(metadata)
                val savedDoc = repository.getDocumentById(id) ?: metadata
                _openedDocument.value = savedDoc
            } catch (e: Exception) {
                _errorMessage.value = "Failed to open document: ${e.localizedMessage}"
            }
        }
    }

    fun processIntentUri(uri: Uri) {
        onDocumentSelected(uri)
    }

    fun onCategorySelected(category: DocumentCategory) {
        _showOnlyFavorites.value = false
        _selectedCategory.value = category
    }

    fun onToggleFavoritesOnly() {
        _showOnlyFavorites.value = !_showOnlyFavorites.value
    }

    fun onSortOrderChanged(order: DocumentSortOrder) {
        _sortOrder.value = order
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun toggleSearchVisibility() {
        _isSearchVisible.value = !_isSearchVisible.value
        if (!_isSearchVisible.value) {
            _searchQuery.value = ""
        }
    }

    fun onFavoriteToggled(id: Long, isFavorite: Boolean) {
        viewModelScope.launch {
            repository.setFavorite(id, isFavorite)
        }
    }

    fun onDeleteDocument(id: Long) {
        viewModelScope.launch {
            repository.deleteDocument(id)
        }
    }

    fun onClearHistory() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }

    fun shareDocument(doc: DocumentItem) {
        try {
            val uri = Uri.parse(doc.uri)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = doc.mimeType.ifBlank { "*/*" }
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(shareIntent, "Share ${doc.name} via")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            _errorMessage.value = "Could not share document: ${e.localizedMessage}"
        }
    }

    fun clearOpenedDocumentEvent() {
        _openedDocument.value = null
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private data class CountsAndDocs(
        val docs: List<DocumentItem>,
        val totalCount: Int,
        val pdfCount: Int,
        val wordCount: Int,
        val excelCount: Int,
        val pptCount: Int,
        val favoriteCount: Int,
        val selectedCategory: DocumentCategory,
        val showOnlyFavorites: Boolean,
        val sortOrder: DocumentSortOrder,
        val searchQuery: String
    )
}
