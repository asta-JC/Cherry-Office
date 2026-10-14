package com.asta.cherryoffice.ui.home

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asta.cherryoffice.data.model.DocumentItem
import com.asta.cherryoffice.data.repository.DocumentRepository
import com.asta.cherryoffice.utils.DocumentCategory
import com.asta.cherryoffice.utils.DocumentUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val recentDocuments: List<DocumentItem> = emptyList(),
    val totalDocumentCount: Int = 0,
    val selectedCategory: DocumentCategory = DocumentCategory.ALL,
    val searchQuery: String = "",
    val isSearchVisible: Boolean = false,
    val isLoading: Boolean = false,
    val openedDocument: DocumentItem? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: DocumentRepository,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow(DocumentCategory.ALL)
    private val _searchQuery = MutableStateFlow("")
    private val _isSearchVisible = MutableStateFlow(false)
    private val _openedDocument = MutableStateFlow<DocumentItem?>(null)
    private val _errorMessage = MutableStateFlow<String?>(null)

    private val allDocumentsFlow = repository.getAllRecentDocuments()

    private val filteredDocumentsFlow = combine(
        allDocumentsFlow,
        _selectedCategory,
        _searchQuery
    ) { docs, category, query ->
        val filtered = docs.filter { doc ->
            val matchesCategory = when (category) {
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
        Pair(filtered, docs.size)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        filteredDocumentsFlow,
        _selectedCategory,
        _searchQuery,
        _isSearchVisible,
        _openedDocument
    ) { (filteredDocs, totalCount), category, query, isSearchVisible, openedDoc ->
        HomeUiState(
            recentDocuments = filteredDocs,
            totalDocumentCount = totalCount,
            selectedCategory = category,
            searchQuery = query,
            isSearchVisible = isSearchVisible,
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
        _selectedCategory.value = category
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

    fun clearOpenedDocumentEvent() {
        _openedDocument.value = null
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
