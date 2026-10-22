package com.neilturner.aerialviews.providers.webdav

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The behaviour every WebDAV listing client has to provide.
 *
 * The expectations come from the captured server responses in `app/src/test/resources/webdav`,
 * replayed through the implementation the app used before, so they describe the behaviour the
 * app relies on rather than an idealised version of it. A replacement client has to pass this
 * same suite.
 */
internal abstract class WebDavListingClientContract {
    protected abstract fun createClient(): WebDavListingClient

    private lateinit var server: MockWebServer

    @BeforeEach
    fun startServer() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun stopServer() {
        server.shutdown()
    }

    @Test
    fun `returns the collection itself as the first entry`() {
        val resources = list(fixture = "apache-mod_dav-207.xml")

        assertEquals("IMG", resources.first().name)
        assertTrue(resources.first().isDirectory)
    }

    @Test
    fun `lists every resource in a mod dav response`() {
        val resources = list(fixture = "apache-mod_dav-207.xml")

        assertEquals(
            listOf(
                "IMG",
                "Thumbs.db",
                "robinfrcd_hennigsvaer_1080p_H264.m4v",
                "Roadways of Nevada.jpg",
                "robinfrcd_nusfjord_1080p_H264.m4v",
                "⁨Fuji-Hakone-Izu National Park⁩.HEIC",
                ".DS_Store",
                "6602526-hd_1920_1080_30fps.mp4",
                "More Media",
                "Side street.jpg",
                "Tokyo tower.jpg",
                "Train station at Hakone.HEIC",
            ),
            resources.map { it.name },
        )
        assertEquals(
            listOf(true, false, false, false, false, false, false, false, true, false, false, false),
            resources.map { it.isDirectory },
        )
    }

    @Test
    fun `decodes percent encoded names`() {
        val resources = list(fixture = "edge-encoded-names-207.xml")

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
    fun `reads the display name when the server sends one`() {
        val resources = list(fixture = "nextcloud-207.xml")

        assertEquals(
            listOf("Aerial", "Dolomites sunrise.jpg", "Alps timelapse.mp4", "Subfolder"),
            resources.map { it.displayName },
        )
    }

    @Test
    fun `has no display name when the server does not send one`() {
        val resources = list(fixture = "synology-207.xml")

        resources.forEach { assertNull(it.displayName, "unexpected display name for ${it.name}") }
    }

    @Test
    fun `has no display name when the server sends an empty one`() {
        val resources = list(fixture = "owncloud-207.xml")

        assertNull(resources.first().displayName)
        assertEquals("beach.jpg", resources[1].displayName)
    }

    @Test
    fun `reports a missing modification date as zero`() {
        val resources = list(fixture = "edge-no-lastmodified-207.xml")

        assertEquals(0L, resources[1].modifiedTimeMs)
        assertEquals(1741351606000L, resources[2].modifiedTimeMs)
    }

    @Test
    fun `ignores the properties of an unsuccessful propstat block`() {
        val resources = list(fixture = "edge-multiple-propstat-207.xml")

        // The collection only has a modification date in the 404 block, so it is not used.
        assertEquals(0L, resources.first().modifiedTimeMs)
        assertTrue(resources[1].isDirectory.not())
    }

    @Test
    fun `reads the name from a relative href`() {
        val resources = list(fixture = "edge-relative-href-207.xml")

        assertEquals(listOf("Aerial", "relative-href.jpg"), resources.map { it.name })
    }

    @Test
    fun `sends a propfind request with depth 1`() {
        list(fixture = "apache-mod_dav-207.xml")

        val request = server.takeRequest()
        assertEquals("PROPFIND", request.method)
        assertEquals("1", request.getHeader("Depth"))
    }

    @Test
    fun `sends preemptive basic authentication`() {
        list(fixture = "apache-mod_dav-207.xml")

        val request = server.takeRequest()
        val expected =
            "Basic " +
                java.util.Base64
                    .getEncoder()
                    .encodeToString("user:password".toByteArray())
        assertEquals(expected, request.getHeader("Authorization"))
    }

    @Test
    fun `throws an error that mentions the status code when the server rejects the request`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))

        val error =
            assertThrows(Exception::class.java) {
                createClient().list(server.url("/media").toString())
            }

        assertTrue(
            error.message.orEmpty().contains("401"),
            "expected the status code in the message but got: ${error.message}",
        )
    }

    private fun list(
        fixture: String,
        path: String = "/Video/Aerial/Test/IMG",
    ): List<WebDavResourceInfo> {
        server.enqueue(MockResponse().setResponseCode(207).setBody(fixture(fixture)))
        return createClient().list(server.url(path).toString())
    }

    private fun fixture(name: String): String {
        val stream =
            requireNotNull(javaClass.getResourceAsStream("/webdav/$name")) {
                "Missing fixture /webdav/$name"
            }
        return stream.bufferedReader().use { it.readText() }
    }
}
