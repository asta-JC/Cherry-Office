package com.asta.cherryoffice.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uri: String,
    val name: String,
    val mimeType: String,
    val extension: String,
    val size: Long,
    val lastOpened: Long,
    val isFavorite: Boolean,
    val pageCount: Int,
    val lastPagePosition: Int
)
