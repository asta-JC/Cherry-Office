package com.asta.cherryoffice.viewers

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.asta.cherryoffice.data.model.DocumentItem
import com.asta.cherryoffice.ui.viewer.ViewerDocumentType

@Composable
fun RichDocumentViewer(
    documentItem: DocumentItem?,
    documentType: ViewerDocumentType,
    selectedTab: Int,
    sheetNames: List<String>,
    slideTitles: List<String>,
    textContent: String,
    wordParagraphs: List<String>,
    nightMode: Boolean,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    com.asta.cherryoffice.ui.viewer.RichDocumentViewer(
        documentItem = documentItem,
        documentType = documentType,
        selectedTab = selectedTab,
        sheetNames = sheetNames,
        slideTitles = slideTitles,
        textContent = textContent,
        wordParagraphs = wordParagraphs,
        nightMode = nightMode,
        onTabSelected = onTabSelected,
        modifier = modifier
    )
}
