package com.asta.cherryoffice.excel

data class CellData(
    val value: String,
    val isHeader: Boolean = false,
    val columnIndex: Int = 0,
    val rowIndex: Int = 0,
    val formula: String? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val fontColor: String? = null,
    val fillColor: String? = null,
    val alignment: String = "",
    val wrapText: Boolean = false
)

data class RowData(
    val rowIndex: Int,
    val cells: List<CellData>
)

data class SheetData(
    val name: String,
    val rows: List<RowData>,
    val maxColumns: Int = 0,
    val columnWidths: Map<Int, Float> = emptyMap()
)

data class SpreadsheetDocument(
    val title: String,
    val sheets: List<SheetData>,
    val warnings: List<String> = emptyList()
)
