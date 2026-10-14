package com.asta.cherryoffice.data.local

import com.asta.cherryoffice.data.model.DocumentItem

fun DocumentEntity.toModel(): DocumentItem {
    return DocumentItem(
        id = id,
        uri = uri,
        name = name,
        mimeType = mimeType,
        extension = extension,
        size = size,
        lastOpened = lastOpened,
        isFavorite = isFavorite,
        pageCount = pageCount,
        lastPagePosition = lastPagePosition
    )
}

fun DocumentItem.toEntity(): DocumentEntity {
    return DocumentEntity(
        id = id,
        uri = uri,
        name = name,
        mimeType = mimeType,
        extension = extension,
        size = size,
        lastOpened = lastOpened,
        isFavorite = isFavorite,
        pageCount = pageCount,
        lastPagePosition = lastPagePosition
    )
}
