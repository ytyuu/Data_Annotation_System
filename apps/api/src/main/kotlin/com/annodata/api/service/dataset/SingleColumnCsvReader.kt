package com.annodata.api.service.dataset

import java.io.BufferedReader

internal data class CsvDataRow(
    val lineNumber: Int,
    val content: String,
)

internal class CsvImportException(message: String) : IllegalArgumentException(message)

/**
 * 按物理行读取无表头、单列 CSV。空行会被忽略，不支持跨行字段。
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

        val content = parseSingleColumnCsvLine(line, lineNumber).trim()
        if (content.isEmpty()) {
            throw CsvImportException("CSV 第 ${lineNumber} 行内容不能为空")
        }

        batch += CsvDataRow(lineNumber = lineNumber, content = content)
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

private fun parseSingleColumnCsvLine(line: String, lineNumber: Int): String {
    val value = line.trim()
    if (!value.startsWith('"')) {
        if (value.contains(',')) {
            throw CsvImportException("CSV 第 ${lineNumber} 行包含多列；单列内容含英文逗号时请使用双引号包裹")
        }
        if (value.contains('"')) {
            throw CsvImportException("CSV 第 ${lineNumber} 行双引号格式不正确")
        }
        return value
    }

    val parsed = StringBuilder()
    var index = 1
    var closed = false
    while (index < value.length) {
        val character = value[index]
        if (character != '"') {
            parsed.append(character)
            index += 1
            continue
        }

        if (index + 1 < value.length && value[index + 1] == '"') {
            parsed.append('"')
            index += 2
            continue
        }

        if (index == value.lastIndex) {
            closed = true
            break
        }

        throw CsvImportException("CSV 第 ${lineNumber} 行双引号格式不正确")
    }

    if (!closed) {
        throw CsvImportException("CSV 第 ${lineNumber} 行双引号未闭合；当前不支持跨行内容")
    }
    return parsed.toString()
}
