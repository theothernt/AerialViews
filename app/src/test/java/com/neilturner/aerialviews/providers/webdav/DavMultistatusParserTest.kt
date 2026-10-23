package com.neilturner.aerialviews.providers.webdav

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Tests for the multistatus parser. The expectations are the ones the sardine implementation
 * produced for the same fixtures, so the parser keeps the behaviour the app relied on.
 */
internal class DavMultistatusParserTest {
    @Test
    fun `parses a mod dav listing`() {
        val resources = parse("apache-mod_dav-207.xml")

        assertEquals(
            listOf(
                Triple("IMG", true, -1L),
                Triple("Thumbs.db", false, 145408L),
                Triple("robinfrcd_hennigsvaer_1080p_H264.m4v", false, 76029430L),
                Triple("Roadways of Nevada.jpg", false, 4278120L),
                Triple("robinfrcd_nusfjord_1080p_H264.m4v", false, 151213479L),
                Triple("⁨Fuji-Hakone-Izu National Park⁩.HEIC", false, 1617922L),
                Triple(".DS_Store", false, 6148L),
                Triple("6602526-hd_1920_1080_30fps.mp4", false, 21542181L),
                Triple("More Media", true, -1L),
                Triple("Side street.jpg", false, 2492299L),
                Triple("Tokyo tower.jpg", false, 2888167L),
                Triple("Train station at Hakone.HEIC", false, 2201867L),
            ),
            resources.map { Triple(it.name, it.isDirectory, it.contentLength) },
        )
    }

    @Test
    fun `parses a nextcloud listing`() {
        val resources = parse("nextcloud-207.xml")

        assertEquals(
            listOf(
                Triple("Aerial", true, 0L),
                Triple("Dolomites sunrise.jpg", false, 4194304L),
                Triple("Alps timelapse.mp4", false, 104857600L),
                Triple("Subfolder", true, 0L),
            ),
            resources.map { Triple(it.name, it.isDirectory, it.contentLength) },
        )
        assertEquals("Aerial", resources.first().displayName)
    }

    @Test
    fun `parses an owncloud listing`() {
        val resources = parse("owncloud-207.xml")

        assertEquals(
            listOf(
                Triple("webdav", true, 0L),
                Triple("beach.jpg", false, 2097152L),
                Triple("city at night.mp4", false, 52428800L),
            ),
            resources.map { Triple(it.name, it.isDirectory, it.contentLength) },
        )
        assertNull(resources.first().displayName)
    }

    @Test
    fun `parses a synology listing`() {
        val resources = parse("synology-207.xml")

        assertEquals(
            listOf(
                Triple("Aerial", true, 0L),
                Triple("IMG_0001.JPG", false, 3145728L),
                Triple("VID_0002.MP4", false, 157286400L),
            ),
            resources.map { Triple(it.name, it.isDirectory, it.contentLength) },
        )
        assertNull(resources.first().displayName)
    }

    @Test
    fun `parses an iis listing`() {
        val resources = parse("iis-207.xml")

        assertEquals(
            listOf(
                Triple("Uploads", true, 0L),
                Triple("Autumn Leaves.jpg", false, 12582912L),
                Triple("Winter Snow.mp4", false, 83886080L),
            ),
            resources.map { Triple(it.name, it.isDirectory, it.contentLength) },
        )
        assertEquals("Uploads", resources.first().displayName)
    }

    @Test
    fun `parses a listing without any properties`() {
        val resources = parse("minimal-207.xml")

        assertEquals(
            listOf(
                Triple("minimal", true, -1L),
                Triple("only-a-name.jpg", false, -1L),
            ),
            resources.map { Triple(it.name, it.isDirectory, it.contentLength) },
        )
        resources.forEach {
            assertNull(it.displayName)
            assertEquals(0L, it.modifiedTimeMs)
        }
    }

    @Test
    fun `parses a listing with a missing modification date`() {
        val resources = parse("edge-no-lastmodified-207.xml")

        assertEquals(0L, resources[1].modifiedTimeMs)
        assertEquals(1024L, resources[1].contentLength)
        assertEquals(1741351606000L, resources[2].modifiedTimeMs)
    }

    @Test
    fun `parses a listing with more than one propstat block`() {
        val resources = parse("edge-multiple-propstat-207.xml")

        assertEquals(4096L, resources[1].contentLength)
        assertEquals(0L, resources.first().modifiedTimeMs)
    }

    @Test
    fun `parses a listing with relative hrefs`() {
        val resources = parse("edge-relative-href-207.xml")

        assertEquals(listOf("Aerial", "relative-href.jpg"), resources.map { it.name })
    }

    @Test
    fun `parses a listing with encoded names`() {
        val resources = parse("edge-encoded-names-207.xml")

        assertEquals(
            listOf(
                "encoded",
                "space in name.jpg",
                "plus+sign.jpg",
                "percent%sign.jpg",
                "emoji🌍 sunset.jpg",
                "question?mark.jpg",
            ),
            resources.map { it.name },
        )
    }

    @Test
    fun `parses an empty multistatus as no resources`() {
        // Sardine rejected an empty multistatus as an invalid DAV response, a folder without
        // any files is not an error.
        val resources = parse("edge-empty-207.xml")

        assertTrue(resources.isEmpty())
    }

    @Test
    fun `refuses a response with a doctype declaration`() {
        val body =
            """
            <?xml version="1.0" encoding="utf-8"?>
            <!DOCTYPE foo [ <!ENTITY xxe SYSTEM "file:///etc/passwd"> ]>
            <D:multistatus xmlns:D="DAV:">
              <D:response><D:href>/media/&xxe;</D:href></D:response>
            </D:multistatus>
            """.trimIndent()

        assertThrows(Exception::class.java) {
            DavMultistatusParser.parse(body.byteInputStream())
        }
    }

    @Test
    fun `takes the name from a href`() {
        assertEquals("IMG", DavMultistatusParser.nameFromHref("/Video/Aerial/Test/IMG/"))
        assertEquals("IMG", DavMultistatusParser.nameFromHref("/Video/Aerial/Test/IMG"))
        assertEquals("Aerial", DavMultistatusParser.nameFromHref("Aerial/"))
        assertEquals("photo.jpg", DavMultistatusParser.nameFromHref("/media/photo.jpg?query=1"))
        assertEquals("photo.jpg", DavMultistatusParser.nameFromHref("/media/photo.jpg#anchor"))
        assertEquals("", DavMultistatusParser.nameFromHref("/"))
    }

    @Test
    fun `decodes percent escapes without turning a plus into a space`() {
        assertEquals("a b", DavMultistatusParser.percentDecode("a%20b"))
        assertEquals("a+b", DavMultistatusParser.percentDecode("a+b"))
        assertEquals("100%", DavMultistatusParser.percentDecode("100%25"))
        assertEquals("🌍", DavMultistatusParser.percentDecode("%F0%9F%8C%8D"))
        assertEquals("a b", DavMultistatusParser.percentDecode("a b"))
    }

    @Test
    fun `parses the date formats servers use`() {
        assertEquals(1741351606000L, DavMultistatusParser.parseDate("Fri, 07 Mar 2025 12:46:46 GMT"))
        assertEquals(1741351606000L, DavMultistatusParser.parseDate("2025-03-07T12:46:46Z"))
        assertEquals(1741351606000L, DavMultistatusParser.parseDate("2025-03-07T12:46:46.000Z"))
        assertEquals(0L, DavMultistatusParser.parseDate(null))
        assertEquals(0L, DavMultistatusParser.parseDate(""))
        assertEquals(0L, DavMultistatusParser.parseDate("not a date"))
    }

    private fun parse(fixture: String): List<DavResource> {
        val stream: InputStream =
            requireNotNull(javaClass.getResourceAsStream("/webdav/$fixture")) {
                "Missing fixture /webdav/$fixture"
            }
        return DavMultistatusParser.parse(stream)
    }

    private fun String.byteInputStream(): InputStream = ByteArrayInputStream(toByteArray())
}
