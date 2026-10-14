package com.asta.cherryoffice.ui.home

import android.content.ContextWrapper
import com.asta.cherryoffice.data.model.DocumentItem
import com.asta.cherryoffice.data.repository.DocumentRepository
import com.asta.cherryoffice.utils.DocumentCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeDocumentRepository
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeDocumentRepository()
        val dummyContext = object : ContextWrapper(null) {}
        viewModel = HomeViewModel(fakeRepository, dummyContext)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun uiState_initialState_loadsDocuments() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        val doc1 = DocumentItem(
            id = 1,
            uri = "content://sample/1",
            name = "Doc1.pdf",
            mimeType = "application/pdf",
            extension = "pdf",
            size = 100L
        )
        fakeRepository.addDocument(doc1)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.recentDocuments.size)
        assertEquals("Doc1.pdf", state.recentDocuments[0].name)
    }

    @Test
    fun onCategorySelected_filtersDocumentsCorrectly() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        val pdfDoc = DocumentItem(
            id = 1,
            uri = "content://sample/pdf",
            name = "Document.pdf",
            mimeType = "application/pdf",
            extension = "pdf",
            size = 100L
        )
        val wordDoc = DocumentItem(
            id = 2,
            uri = "content://sample/word",
            name = "Letter.docx",
            mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            extension = "docx",
            size = 200L
        )
        fakeRepository.addDocument(pdfDoc)
        fakeRepository.addDocument(wordDoc)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onCategorySelected(DocumentCategory.PDF)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.recentDocuments.size)
        assertEquals("Document.pdf", state.recentDocuments[0].name)
    }

    @Test
    fun onSearchQueryChanged_filtersDocumentsByName() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        val doc1 = DocumentItem(
            id = 1,
            uri = "content://sample/1",
            name = "Quarterly_Report.pdf",
            mimeType = "application/pdf",
            extension = "pdf",
            size = 100L
        )
        val doc2 = DocumentItem(
            id = 2,
            uri = "content://sample/2",
            name = "Invoice.pdf",
            mimeType = "application/pdf",
            extension = "pdf",
            size = 200L
        )
        fakeRepository.addDocument(doc1)
        fakeRepository.addDocument(doc2)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onSearchQueryChanged("Invoice")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.recentDocuments.size)
        assertEquals("Invoice.pdf", state.recentDocuments[0].name)
    }

    @Test
    fun onDeleteDocument_removesDocumentFromRepository() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        val doc1 = DocumentItem(
            id = 1,
            uri = "content://sample/1",
            name = "Doc1.pdf",
            mimeType = "application/pdf",
            extension = "pdf",
            size = 100L
        )
        fakeRepository.addDocument(doc1)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onDeleteDocument(1)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.recentDocuments.isEmpty())
    }
}

private class FakeDocumentRepository : DocumentRepository {
    private val docsState = MutableStateFlow<List<DocumentItem>>(emptyList())

    fun addDocument(doc: DocumentItem) {
        docsState.value = docsState.value + doc
    }

    override fun getAllRecentDocuments(): Flow<List<DocumentItem>> = docsState

    override fun getFavoriteDocuments(): Flow<List<DocumentItem>> = MutableStateFlow(docsState.value.filter { it.isFavorite })

    override fun searchDocuments(query: String): Flow<List<DocumentItem>> =
        MutableStateFlow(docsState.value.filter { it.name.contains(query, ignoreCase = true) })

    override suspend fun getDocumentById(id: Long): DocumentItem? = docsState.value.find { it.id == id }

    override suspend fun getDocumentByUri(uri: String): DocumentItem? = docsState.value.find { it.uri == uri }

    override suspend fun saveDocument(document: DocumentItem): Long {
        val id = if (document.id == 0L) (docsState.value.size + 1).toLong() else document.id
        val newDoc = document.copy(id = id)
        docsState.value = docsState.value.filterNot { it.id == id } + newDoc
        return id
    }

    override suspend fun setFavorite(id: Long, isFavorite: Boolean) {
        docsState.value = docsState.value.map {
            if (it.id == id) it.copy(isFavorite = isFavorite) else it
        }
    }

    override suspend fun updateLastOpened(id: Long, timestamp: Long, lastPagePosition: Int) {
        docsState.value = docsState.value.map {
            if (it.id == id) it.copy(lastOpened = timestamp, lastPagePosition = lastPagePosition) else it
        }
    }

    override suspend fun deleteDocument(id: Long) {
        docsState.value = docsState.value.filterNot { it.id == id }
    }

    override suspend fun clearAll() {
        docsState.value = emptyList()
    }
}
