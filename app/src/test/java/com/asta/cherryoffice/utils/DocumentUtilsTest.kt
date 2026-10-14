package com.asta.cherryoffice.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentUtilsTest {

    @Test
    fun formatFileSize_returnsFormattedStrings() {
        assertEquals("0 B", DocumentUtils.formatFileSize(0))
        assertEquals("512.0 B", DocumentUtils.formatFileSize(512))
        assertEquals("1.0 KB", DocumentUtils.formatFileSize(1024))
        assertEquals("2.5 MB", DocumentUtils.formatFileSize(2621440))
        assertEquals("1.5 GB", DocumentUtils.formatFileSize(1610612736))
    }

    @Test
    fun getCategoryForDocument_mapsMimeAndExtensionCorrectly() {
        assertEquals(
            DocumentCategory.PDF,
            DocumentUtils.getCategoryForDocument("application/pdf", "pdf")
        )
        assertEquals(
            DocumentCategory.WORD,
            DocumentUtils.getCategoryForDocument("application/msword", "doc")
        )
        assertEquals(
            DocumentCategory.WORD,
            DocumentUtils.getCategoryForDocument("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx")
        )
        assertEquals(
            DocumentCategory.EXCEL,
            DocumentUtils.getCategoryForDocument("application/vnd.ms-excel", "xls")
        )
        assertEquals(
            DocumentCategory.EXCEL,
            DocumentUtils.getCategoryForDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx")
        )
        assertEquals(
            DocumentCategory.POWERPOINT,
            DocumentUtils.getCategoryForDocument("application/vnd.ms-powerpoint", "ppt")
        )
        assertEquals(
            DocumentCategory.POWERPOINT,
            DocumentUtils.getCategoryForDocument("application/vnd.openxmlformats-officedocument.presentationml.presentation", "pptx")
        )
        assertEquals(
            DocumentCategory.OTHER,
            DocumentUtils.getCategoryForDocument("text/plain", "txt")
        )
    }

    @Test
    fun formatLastOpened_returnsRelativeOrFormattedDate() {
        val now = System.currentTimeMillis()
        assertEquals("Just now", DocumentUtils.formatLastOpened(now - 10000))
        assertEquals("15m ago", DocumentUtils.formatLastOpened(now - 15 * 60 * 1000))
        assertEquals("2h ago", DocumentUtils.formatLastOpened(now - 2 * 60 * 60 * 1000))
    }
}
