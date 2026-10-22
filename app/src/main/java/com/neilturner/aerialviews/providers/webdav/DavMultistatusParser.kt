package com.neilturner.aerialviews.providers.webdav

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import timber.log.Timber
import java.io.InputStream
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.xml.parsers.SAXParserFactory

/**
 * Parses a WebDAV `207 Multi-Status` response into the resources it describes.
 *
 * The parser is a plain function over the response body so that it can be unit tested without a
 * server, and it only uses the classes that are available both on Android and on the JVM.
 */
internal object DavMultistatusParser {
    private const val DAV_NAMESPACE = "DAV:"

    fun parse(input: InputStream): List<DavResource> {
        val handler = MultistatusHandler()
        val factory = SAXParserFactory.newInstance()
        factory.isNamespaceAware = true
        // The response comes from a server, so external entities must never be resolved.
        // Not every implementation knows these features, and Android does not support
        // setXIncludeAware at all, so they are all best effort.
        setFeature(factory, "http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature(factory, "http://xml.org/sax/features/external-general-entities", false)
        setFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false)
        setFeature(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        factory.newSAXParser().parse(input, handler)
        return handler.resources
    }

    private fun setFeature(
        factory: SAXParserFactory,
        name: String,
        value: Boolean,
    ) {
        try {
            factory.setFeature(name, value)
        } catch (ex: Exception) {
            Timber.w("DavMultistatusParser: feature $name is not supported: ${ex.message}")
        }
    }

    /**
     * The name of a resource is the last segment of its path, with the percent escapes
     * decoded. A plus sign is left alone, it is only a space in query strings.
     */
    fun nameFromHref(href: String): String {
        val path = href.substringBefore('?').substringBefore('#')
        val decoded = percentDecode(path)
        val withoutTrailingSlash = if (decoded.endsWith("/")) decoded.dropLast(1) else decoded
        return withoutTrailingSlash.substringAfterLast('/')
    }

    /**
     * Decodes the percent escapes of a path. Characters that are not escaped are kept as
     * they are, so a path with a raw space still works.
     */
    fun percentDecode(value: String): String {
        if (!value.contains('%')) return value

        val bytes = java.io.ByteArrayOutputStream(value.length)
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char == '%' && index + 3 <= value.length) {
                val hex = value.substring(index + 1, index + 3)
                val byte = hex.toIntOrNull(16)
                if (byte != null) {
                    bytes.write(byte)
                    index += 3
                    continue
                }
            }
            bytes.write(value.substring(index, index + 1).toByteArray(Charsets.UTF_8))
            index++
        }
        return bytes.toByteArray().toString(Charsets.UTF_8)
    }

    private val dateFormats =
        listOf(
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "EEE MMM dd HH:mm:ss zzz yyyy",
            "EEEEEE, dd-MMM-yy HH:mm:ss zzz",
            "EEE MMMM d HH:mm:ss yyyy",
        )

    /** Parses a `getlastmodified` value, returning 0 when it is missing or unreadable. */
    fun parseDate(value: String?): Long {
        if (value.isNullOrBlank()) return 0L

        dateFormats.forEach { format ->
            try {
                val parser = SimpleDateFormat(format, Locale.US)
                parser.timeZone = TimeZone.getTimeZone("UTC")
                val date = parser.parse(value.trim())
                if (date != null) return date.time
            } catch (ex: ParseException) {
                Timber.d("DavMultistatusParser: cannot parse date '$value' with '$format'")
            }
        }
        return 0L
    }

    private class MultistatusHandler : DefaultHandler() {
        val resources = mutableListOf<DavResource>()

        private var href: String? = null
        private var displayName: String? = null
        private var isDirectory = false
        private var modifiedTimeMs = 0L
        private var contentLength = -1L

        private var inPropstat = false
        private var propstatStatus: String? = null
        private var pending = mutableMapOf<String, String>()
        private var pendingIsDirectory = false

        private var element: String? = null
        private val text = StringBuilder()

        override fun startElement(
            uri: String?,
            localName: String?,
            qName: String?,
            attributes: Attributes?,
        ) {
            val name = localName?.lowercase().orEmpty()
            text.setLength(0)

            when {
                name == "response" && isDav(uri, name) -> {
                    startResponse()
                }

                name == "propstat" && isDav(uri, name) -> {
                    inPropstat = true
                    propstatStatus = null
                    pending = mutableMapOf()
                    pendingIsDirectory = false
                }

                name == "status" && inPropstat -> {
                    element = "status"
                }

                name == "collection" && inPropstat && element == "resourcetype" -> {
                    pendingIsDirectory = true
                }

                name == "resourcetype" && inPropstat -> {
                    element = "resourcetype"
                }

                name == "getlastmodified" && inPropstat -> {
                    element = "getlastmodified"
                }

                name == "getcontentlength" && inPropstat -> {
                    element = "getcontentlength"
                }

                name == "displayname" && inPropstat -> {
                    element = "displayname"
                }

                name == "href" && isDav(uri, name) -> {
                    element = "href"
                }
            }
        }

        override fun characters(
            ch: CharArray,
            start: Int,
            length: Int,
        ) {
            text.appendRange(ch, start, start + length)
        }

        override fun endElement(
            uri: String?,
            localName: String?,
            qName: String?,
        ) {
            val name = localName?.lowercase().orEmpty()
            val value = text.toString().trim()

            when {
                name == "response" && isDav(uri, name) -> {
                    endResponse()
                }

                name == "propstat" && inPropstat -> {
                    inPropstat = false
                    // Only the properties of a successful propstat are used, a 404 block holds
                    // the properties the server does not know about.
                    if (isSuccessful(propstatStatus)) {
                        pending["displayname"]?.let { displayName = it.ifBlank { null } }
                        pending["getlastmodified"]?.let { modifiedTimeMs = parseDate(it) }
                        pending["getcontentlength"]?.let { contentLength = it.toLongOrNull() ?: -1L }
                        if (pendingIsDirectory) isDirectory = true
                    }
                    pending = mutableMapOf()
                    pendingIsDirectory = false
                    propstatStatus = null
                }

                name == "status" && inPropstat -> {
                    propstatStatus = value
                }

                name == "href" && isDav(uri, name) -> {
                    href = value
                }

                name == "displayname" && inPropstat -> {
                    pending["displayname"] = value
                }

                name == "getlastmodified" && inPropstat -> {
                    pending["getlastmodified"] = value
                }

                name == "getcontentlength" && inPropstat -> {
                    pending["getcontentlength"] = value
                }
            }

            element = null
            text.setLength(0)
        }

        private fun startResponse() {
            href = null
            displayName = null
            isDirectory = false
            modifiedTimeMs = 0L
            contentLength = -1L
        }

        private fun endResponse() {
            val path = href.orEmpty()
            resources.add(
                DavResource(
                    name = nameFromHref(path),
                    displayName = displayName,
                    isDirectory = isDirectory,
                    modifiedTimeMs = modifiedTimeMs,
                    contentLength = contentLength,
                    href = path,
                ),
            )
        }

        private fun isDav(
            uri: String?,
            name: String,
        ): Boolean = uri == null || uri.isEmpty() || uri == DAV_NAMESPACE

        private fun isSuccessful(status: String?): Boolean {
            if (status.isNullOrBlank()) return true
            val code = status.substringAfter(' ', "").substringBefore(' ').toIntOrNull()
            return code == null || code in 200..299
        }
    }
}
