package com.annodata.api.service.dataset

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SingleColumnCsvReaderTest {
    @Test
    fun `preserves double quotes inside an unquoted comment`() {
        val comment = "要建一层\"非常之绝美\"楼［无限暖暖_暖暖］［惊喜］这次新年真是夯爆了；"
        val importedRows = mutableListOf<CsvDataRow>()

        val importedCount = readSingleColumnCsv(comment.reader().buffered(), 1_000) { rows ->
            importedRows += rows
        }

        assertEquals(1, importedCount)
        assertEquals(comment, importedRows.single().content)
    }

    @Test
    fun `preserves commas quotes and surrounding spaces as raw content`() {
        val comment = "  他说\"明天送达\",结果今天就到了  "
        val importedRows = mutableListOf<CsvDataRow>()

        readSingleColumnCsv(comment.reader().buffered(), 1_000) { rows ->
            importedRows += rows
        }

        assertEquals(comment, importedRows.single().content)
    }
}
