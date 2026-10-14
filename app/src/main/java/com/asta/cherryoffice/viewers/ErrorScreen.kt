package com.asta.cherryoffice.viewers

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.asta.cherryoffice.data.model.DocumentItem

@Composable
fun ErrorScreen(
    documentItem: DocumentItem?,
    errorMessage: String?,
    onOpenWithAnotherApp: () -> Unit,
    modifier: Modifier = Modifier
) {
    com.asta.cherryoffice.ui.viewer.ErrorScreen(
        documentItem = documentItem,
        errorMessage = errorMessage,
        onOpenWithAnotherApp = onOpenWithAnotherApp,
        modifier = modifier
    )
}
