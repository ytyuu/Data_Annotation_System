package com.annodata.api.service.dataset

import java.io.BufferedReader

internal data class CsvDataRow(
    val lineNumber: Int,
    val content: String,
)

internal class CsvImportException(message: String) : IllegalArgumentException(message)

/**
 * 按物理行读取无表头导入文件。空行会被忽略，其余内容原样保留。
 */
internal fun readSingleColumnCsv(
    reader: BufferedReader,
    batchSize: Int,
    consumeBatch: (List<CsvDataRow>) -> Unit,
): Int {
    val batch = ArrayList<CsvDataRow>(batchSize)
    var importedCount = 0
    var lineNumber = 0

    while (true) {
        val rawLine = reader.readLine() ?: break
        lineNumber += 1
        val line = if (lineNumber == 1) rawLine.removePrefix("\uFEFF") else rawLine
        if (line.isBlank()) continue

        batch += CsvDataRow(lineNumber = lineNumber, content = line)
        if (batch.size == batchSize) {
            consumeBatch(batch)
            importedCount += batch.size
            batch.clear()
        }
    }

    if (batch.isNotEmpty()) {
        consumeBatch(batch)
        importedCount += batch.size
    }

    if (importedCount == 0) {
        throw CsvImportException("CSV 文件中没有可导入的数据")
    }

    return importedCount
}
