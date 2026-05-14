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
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { parseLine(it) }
            .toList()

    private fun parseLine(line: String): CsvMediaItem? {
        val unquotedLine = line.trimWrappingQuotes()
        val commaIndex = unquotedLine.indexOf(',')
        val rawUrl = if (commaIndex >= 0) unquotedLine.substring(0, commaIndex) else unquotedLine
        val rawDescription = if (commaIndex >= 0) unquotedLine.substring(commaIndex + 1) else ""

        val url = rawUrl.trim().trimWrappingQuotes()
        if (url.equals("url", ignoreCase = true)) return null

        val mediaType = mediaTypeFor(url) ?: return null
        return CsvMediaItem(
            url = url,
            description = rawDescription.trim().trimWrappingQuotes(),
            type = mediaType,
        )
    }

    private fun mediaTypeFor(url: String): AerialMediaType? {
        val path = url.substringBefore('?').substringBefore('#')
        return when {
            supportedVideoExtensions.any { path.endsWith(it, ignoreCase = true) } -> AerialMediaType.VIDEO
            supportedImageExtensions.any { path.endsWith(it, ignoreCase = true) } -> AerialMediaType.IMAGE
            else -> null
        }
    }

    private fun String.trimWrappingQuotes(): String {
        val trimmed = trim()
        return if (trimmed.length >= 2 && trimmed.first() == '"' && trimmed.last() == '"') {
            trimmed.substring(1, trimmed.lastIndex)
        } else {
            trimmed
        }
    }

    private val supportedVideoExtensions =
        listOf(".mov", ".mp4", ".m4v", ".webm", ".mkv", ".ts", ".m3u8")
    private val supportedImageExtensions = listOf(".jpg", ".jpeg", ".gif", ".webp", ".heic", ".png", ".avif")
}
