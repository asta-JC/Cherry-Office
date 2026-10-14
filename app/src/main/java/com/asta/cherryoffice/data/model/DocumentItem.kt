package com.asta.cherryoffice.data.model

data class DocumentItem(
    val id: Long = 0,
    val uri: String,
    val name: String,
    val mimeType: String,
    val extension: String,
    val size: Long,
    val lastOpened: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val pageCount: Int = 0,
    val lastPagePosition: Int = 0
)
