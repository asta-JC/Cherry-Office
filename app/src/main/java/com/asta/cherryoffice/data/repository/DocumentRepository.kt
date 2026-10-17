package com.asta.cherryoffice.data.repository

import com.asta.cherryoffice.data.model.DocumentItem
import kotlinx.coroutines.flow.Flow

interface DocumentRepository {
    fun getAllRecentDocuments(): Flow<List<DocumentItem>>
    fun getFavoriteDocuments(): Flow<List<DocumentItem>>
    fun searchDocuments(query: String): Flow<List<DocumentItem>>
    suspend fun getDocumentById(id: Long): DocumentItem?
    suspend fun getDocumentByUri(uri: String): DocumentItem?
    suspend fun saveDocument(document: DocumentItem): Long
    suspend fun saveDocuments(documents: List<DocumentItem>)
    suspend fun setFavorite(id: Long, isFavorite: Boolean)
    suspend fun updateLastOpened(id: Long, timestamp: Long = System.currentTimeMillis(), lastPagePosition: Int = 0)
    suspend fun deleteDocument(id: Long)
    suspend fun clearAll()
}
