package com.example.api.config

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * 加载根目录 `.env`，并让系统环境变量拥有更高优先级。
 */
object EnvLoader {
    fun load(): Map<String, String> {
        val dotEnv = readDotEnv(findRootDotEnv())

        return dotEnv + System.getenv()
    }

    private fun findRootDotEnv(): Path? {
        var current: Path? = Path.of("").toAbsolutePath().normalize()

        while (current != null) {
            val candidate = current.resolve(".env")
            if (candidate.exists()) {
                return candidate
            }

            current = current.parent
        }

        return null
    }

    private fun readDotEnv(path: Path?): Map<String, String> {
        if (path == null || !path.exists()) {
            return emptyMap()
        }

        return Files.readAllLines(path)
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull(::parseLine)
            .toMap()
    }

    private fun parseLine(line: String): Pair<String, String>? {
        val separatorIndex = line.indexOf('=')
        if (separatorIndex <= 0) {
            return null
        }

        val key = line.substring(0, separatorIndex).trim()
        val value = line.substring(separatorIndex + 1).trim().trimMatchingQuotes()
        return key to value
    }

    private fun String.trimMatchingQuotes(): String {
        if (length < 2) {
            return this
        }

        val first = first()
        val last = last()
        return if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            substring(1, length - 1)
        } else {
            this
        }
    }
}
