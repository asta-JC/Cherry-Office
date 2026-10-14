package com.asta.cherryoffice.data.repository

import com.asta.cherryoffice.data.local.DocumentDao
import com.asta.cherryoffice.data.local.DocumentEntity
import com.asta.cherryoffice.data.model.DocumentItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class DocumentRepositoryTest {

    private lateinit var fakeDao: FakeDocumentDao
    private lateinit var repository: DocumentRepositoryImpl

    @Before
    fun setUp() {
        fakeDao = FakeDocumentDao()
        repository = DocumentRepositoryImpl(fakeDao)
    }

    @Test
    fun saveDocument_insertsOrUpdatesInDao() = runTest {
        val item = DocumentItem(
            uri = "content://media/external/file/1",
            name = "Test_Report.pdf",
            mimeType = "application/pdf",
            extension = "pdf",
            size = 1024L
        )

        val id = repository.saveDocument(item)
        assertEquals(1L, id)

        val retrieved = repository.getDocumentById(1L)
        assertNotNull(retrieved)
        assertEquals("Test_Report.pdf", retrieved?.name)
    }

    @Test
    fun setFavorite_updatesDaoState() = runTest {
        val item = DocumentItem(
            uri = "content://media/external/file/2",
            name = "Doc2.pdf",
            mimeType = "application/pdf",
            extension = "pdf",
            size = 2048L
        )
        repository.saveDocument(item)

        repository.setFavorite(1L, true)
        val retrieved = repository.getDocumentById(1L)
        assertEquals(true, retrieved?.isFavorite)
    }

    @Test
    fun deleteDocument_removesFromDao() = runTest {
        val item = DocumentItem(
            uri = "content://media/external/file/3",
            name = "Doc3.pdf",
            mimeType = "application/pdf",
            extension = "pdf",
            size = 512L
        )
        repository.saveDocument(item)

        repository.deleteDocument(1L)
        val retrieved = repository.getDocumentById(1L)
        assertNull(retrieved)
    }
}

private class FakeDocumentDao : DocumentDao {
    private val documents = mutableMapOf<Long, DocumentEntity>()
    private var idCounter = 1L

    override fun getAllRecentDocuments() = flowOf(documents.values.sortedByDescending { it.lastOpened })

    override fun getFavoriteDocuments() = flowOf(documents.values.filter { it.isFavorite }.sortedByDescending { it.lastOpened })

    override fun searchDocuments(query: String) = flowOf(
        documents.values.filter { it.name.contains(query, ignoreCase = true) }.sortedByDescending { it.lastOpened }
    )

    override suspend fun getDocumentById(id: Long) = documents[id]

    override suspend fun getDocumentByUri(uri: String) = documents.values.find { it.uri == uri }

    override suspend fun insertOrUpdateDocument(document: DocumentEntity): Long {
        val id = if (document.id == 0L) idCounter++ else document.id
        val entityWithId = document.copy(id = id)
        documents[id] = entityWithId
        return id
    }

    override suspend fun updateDocument(document: DocumentEntity) {
        documents[document.id] = document
    }

    override suspend fun setFavorite(id: Long, isFavorite: Boolean) {
        documents[id]?.let {
            documents[id] = it.copy(isFavorite = isFavorite)
        }
    }

    override suspend fun updateLastOpened(id: Long, timestamp: Long, lastPagePosition: Int) {
        documents[id]?.let {
            documents[id] = it.copy(lastOpened = timestamp, lastPagePosition = lastPagePosition)
        }
    }

    override suspend fun deleteDocument(document: DocumentEntity) {
        documents.remove(document.id)
    }

    override suspend fun deleteDocumentById(id: Long) {
        documents.remove(id)
    }

    override suspend fun clearAll() {
        documents.clear()
    }
}
