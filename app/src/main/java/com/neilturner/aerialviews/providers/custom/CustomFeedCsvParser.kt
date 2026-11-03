package com.neilturner.aerialviews.providers.custom

import com.neilturner.aerialviews.models.enums.AerialMediaType

internal object CustomFeedCsvParser {
    data class CsvMediaItem(
        val url: String,
        val description: String,
        val type: AerialMediaType,
    )

    fun parse(content: String): List<CsvMediaItem> =
        content
            .removePrefix("\uFEFF")
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { parseLine(it) }
            .toList()

    private fun parseLine(line: String): CsvMediaItem? {
        val (rawUrl, rawDescription) = splitRow(line)
        var url = rawUrl.parseField()
        var description = rawDescription.parseField()

        if (description.isEmpty() && url.contains(',')) {
            // Legacy support for a whole row wrapped in quotes: "url,description"
            val commaIndex = url.indexOf(',')
            description = url.substring(commaIndex + 1).trim()
            url = url.substring(0, commaIndex)
        }

        url = url.trim()
        if (url.equals("url", ignoreCase = true)) return null

        val mediaType = mediaTypeFor(url) ?: return null
        return CsvMediaItem(
            url = url,
            description = description,
            type = mediaType,
        )
    }

    /**
     * Splits a row at the first comma that is not inside a quoted field, so commas within a
     * quoted description stay part of that description.
     */
    private fun splitRow(line: String): Pair<String, String> {
        var inQuotes = false
        line.forEachIndexed { index, char ->
            when {
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> return line.substring(0, index) to line.substring(index + 1)
            }
        }
        return line to ""
    }

    /**
     * Removes one pair of wrapping quotes, if present, and unescapes doubled quotes.
     */
    private fun String.parseField(): String {
        val trimmed = trim()
        if (trimmed.length < 2 || trimmed.first() != '"' || trimmed.last() != '"') return trimmed
        return trimmed.substring(1, trimmed.lastIndex).replace("\"\"", "\"")
    }

    private fun mediaTypeFor(url: String): AerialMediaType? {
        val path = url.substringBefore('?').substringBefore('#')
        return when {
            supportedVideoExtensions.any { path.endsWith(it, ignoreCase = true) } -> AerialMediaType.VIDEO
            supportedImageExtensions.any { path.endsWith(it, ignoreCase = true) } -> AerialMediaType.IMAGE
            else -> null
        }
    }

    private val supportedVideoExtensions =
        listOf(".mov", ".mp4", ".m4v", ".webm", ".mkv", ".ts", ".m3u8")
    private val supportedImageExtensions = listOf(".jpg", ".jpeg", ".gif", ".webp", ".heic", ".png", ".avif")
}
