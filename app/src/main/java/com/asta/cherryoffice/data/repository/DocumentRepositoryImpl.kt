package com.asta.cherryoffice.data.repository

import com.asta.cherryoffice.data.local.DocumentDao
import com.asta.cherryoffice.data.local.toEntity
import com.asta.cherryoffice.data.local.toModel
import com.asta.cherryoffice.data.model.DocumentItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DocumentRepositoryImpl @Inject constructor(
    private val documentDao: DocumentDao
) : DocumentRepository {

    override fun getAllRecentDocuments(): Flow<List<DocumentItem>> {
        return documentDao.getAllRecentDocuments().map { entities ->
            entities.map { it.toModel() }
        }
    }

    override fun getFavoriteDocuments(): Flow<List<DocumentItem>> {
        return documentDao.getFavoriteDocuments().map { entities ->
            entities.map { it.toModel() }
        }
    }

    override fun searchDocuments(query: String): Flow<List<DocumentItem>> {
        return documentDao.searchDocuments(query).map { entities ->
            entities.map { it.toModel() }
        }
    }

    override suspend fun getDocumentById(id: Long): DocumentItem? {
        return documentDao.getDocumentById(id)?.toModel()
    }

    override suspend fun getDocumentByUri(uri: String): DocumentItem? {
        return documentDao.getDocumentByUri(uri)?.toModel()
    }

    override suspend fun saveDocument(document: DocumentItem): Long {
        val existing = documentDao.getDocumentByUri(document.uri)
        val entityToSave = if (existing != null) {
            document.copy(
                id = existing.id,
                isFavorite = existing.isFavorite || document.isFavorite,
                lastOpened = System.currentTimeMillis()
            ).toEntity()
        } else {
            document.copy(lastOpened = System.currentTimeMillis()).toEntity()
        }
        return documentDao.insertOrUpdateDocument(entityToSave)
    }

    override suspend fun saveDocuments(documents: List<DocumentItem>) {
        val entities = documents.map { doc ->
            val existing = documentDao.getDocumentByUri(doc.uri)
            if (existing != null) {
                doc.copy(
                    id = existing.id,
                    isFavorite = existing.isFavorite || doc.isFavorite,
                    lastOpened = existing.lastOpened
                ).toEntity()
            } else {
                doc.toEntity()
            }
        }
        documentDao.insertDocuments(entities)
    }

    override suspend fun setFavorite(id: Long, isFavorite: Boolean) {
        documentDao.setFavorite(id, isFavorite)
    }

    override suspend fun updateLastOpened(id: Long, timestamp: Long, lastPagePosition: Int) {
        documentDao.updateLastOpened(id, timestamp, lastPagePosition)
    }

    override suspend fun deleteDocument(id: Long) {
        documentDao.deleteDocumentById(id)
    }

    override suspend fun clearAll() {
        documentDao.clearAll()
    }
}
