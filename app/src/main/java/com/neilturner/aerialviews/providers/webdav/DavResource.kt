package com.neilturner.aerialviews.providers.webdav

/**
 * A single resource from a WebDAV `PROPFIND` multistatus response.
 */
internal data class DavResource(
    val name: String,
    val displayName: String? = null,
    val isDirectory: Boolean = false,
    val modifiedTimeMs: Long = 0L,
    val contentLength: Long = -1L,
    val href: String = "",
) {
    fun toResourceInfo(): WebDavResourceInfo =
        WebDavResourceInfo(
            name = name,
            displayName = displayName,
            isDirectory = isDirectory,
            modifiedTimeMs = modifiedTimeMs,
        )
}
