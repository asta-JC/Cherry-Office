package com.asta.cherryoffice.ui.viewer

import org.junit.Assert.assertEquals
import org.junit.Test

class ViewerFormatTest {
    @Test fun recognizedExtensionsTakePriorityOverIncorrectProviderMime() {
        assertEquals(ViewerDocumentType.RICH_WORD, resolveViewerType("DOCX", "application/pdf"))
        assertEquals(ViewerDocumentType.RICH_EXCEL, resolveViewerType("tsv", "text/plain"))
        assertEquals(ViewerDocumentType.RICH_EXCEL, resolveViewerType("xls", "application/octet-stream"))
        assertEquals(ViewerDocumentType.RICH_EXCEL, resolveViewerType("xlsm", "application/octet-stream"))
        assertEquals(ViewerDocumentType.RICH_PPT, resolveViewerType("pptx", "application/zip"))
    }

    @Test fun missingExtensionUsesSpecificProviderMime() {
        assertEquals(ViewerDocumentType.RICH_EXCEL, resolveViewerType("", "text/csv; charset=utf-8"))
        assertEquals(ViewerDocumentType.RICH_WORD, resolveViewerType("", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
        assertEquals(ViewerDocumentType.IMAGE, resolveViewerType("", "image/jpeg"))
    }

    @Test fun unsupportedOfficeFormatsNeverDisplayAsRawText() {
        listOf("doc", "ppt", "odt", "ods", "odp", "rtf").forEach {
            assertEquals(ViewerDocumentType.UNSUPPORTED, resolveViewerType(it, "text/plain"))
        }
    }
    @Test fun wordPageBridgeInvokesTheCallbackInsteadOfRecursing() {
        var pages = 0
        WordJsBridge { pages = it }.onPagesReady(7)
        assertEquals(7, pages)
    }

}
