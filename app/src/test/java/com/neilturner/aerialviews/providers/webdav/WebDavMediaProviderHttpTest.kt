package com.neilturner.aerialviews.providers.webdav

import android.content.Context
import android.content.res.Resources
import android.net.Uri
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.ProviderMediaType
import com.neilturner.aerialviews.models.enums.SchemeType
import com.neilturner.aerialviews.models.prefs.WebDavProviderPreferences
import com.neilturner.aerialviews.providers.ProviderFetchResult
import com.neilturner.aerialviews.utils.filename
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The WebDAV provider driven over real HTTP, with the server responses replayed from the
 * captured fixtures. These cover the behaviour the app relies on, so a replacement client has to
 * keep it working.
 */
internal class WebDavMediaProviderHttpTest {
    private val resources =
        mockk<Resources>().also { res ->
            every { res.getString(R.string.webdav_media_test_summary1) } returns "Files found: %1\$s"
            every { res.getString(R.string.webdav_media_test_summary2) } returns "Unsupported files: %1\$s"
            every { res.getString(R.string.webdav_media_test_summary3) } returns "Videos found: %1\$s"
            every { res.getString(R.string.webdav_media_test_summary4) } returns "Photos found: %1\$s"
            every { res.getString(R.string.webdav_media_test_summary5) } returns "Selected for playback: %1\$s"
        }

    private val context =
        mockk<Context>().also { ctx ->
            every { ctx.resources } returns resources
        }

    private lateinit var server: MockWebServer
    private val responses = mutableMapOf<String, String>()

    @BeforeEach
    fun startServer() {
        server = MockWebServer()
        server.start()
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val requested = request.path.orEmpty().substringBefore('?')
                    val decoded = java.net.URLDecoder.decode(requested, "UTF-8")
                    val body = responses[decoded] ?: return MockResponse().setResponseCode(404).setBody("Not found")
                    return MockResponse().setResponseCode(207).setBody(body)
                }
            }
    }

    @AfterEach
    fun stopServer() {
        server.shutdown()
    }

    @BeforeEach
    fun stubUriParsing() {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } answers {
            val raw = firstArg<String>()
            mockk<Uri>(relaxed = true) {
                every { lastPathSegment } returns raw.substringAfterLast('/')
            }
        }
    }

    @AfterEach
    fun unstubUriParsing() {
        unmockkStatic(Uri::class)
    }

    @Test
    fun `finds the media in a captured server listing`() =
        runTest {
            serve("/media", fixture("apache-mod_dav-207.xml"))

            val result = provider(searchSubfolders = false).fetch()

            val success = assertInstanceOf(ProviderFetchResult.Success::class.java, result)
            assertEquals(
                setOf(
                    "robinfrcd_hennigsvaer_1080p_H264.m4v",
                    "robinfrcd_nusfjord_1080p_H264.m4v",
                    "6602526-hd_1920_1080_30fps.mp4",
                    "Roadways of Nevada.jpg",
                    "Side street.jpg",
                    "Tokyo tower.jpg",
                    "⁨Fuji-Hakone-Izu National Park⁩.HEIC",
                    "Train station at Hakone.HEIC",
                ),
                success.media.map { it.uri.filename }.toSet(),
            )
            assertEquals(
                // The dot file is skipped before it is counted, so only Thumbs.db is unsupported.
                "Files found: 9\nUnsupported files: 1\nVideos found: 3\nPhotos found: 5\nSelected for playback: 8",
                success.summary,
            )
        }

    @Test
    fun `recurses into subfolders when asked to`() =
        runTest {
            serve(
                "/media",
                """
                <?xml version="1.0" encoding="utf-8"?>
                <D:multistatus xmlns:D="DAV:">
                  <D:response>
                    <D:href>/media/</D:href>
                    <D:propstat><D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop>
                    <D:status>HTTP/1.1 200 OK</D:status></D:propstat>
                  </D:response>
                  <D:response>
                    <D:href>/media/Sub/</D:href>
                    <D:propstat><D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop>
                    <D:status>HTTP/1.1 200 OK</D:status></D:propstat>
                  </D:response>
                  <D:response>
                    <D:href>/media/top.mp4</D:href>
                    <D:propstat><D:prop><D:getcontentlength>10</D:getcontentlength>
                    <D:getlastmodified>Fri, 07 Mar 2025 12:46:46 GMT</D:getlastmodified>
                    <D:resourcetype/></D:prop>
                    <D:status>HTTP/1.1 200 OK</D:status></D:propstat>
                  </D:response>
                </D:multistatus>
                """.trimIndent(),
            )
            serve(
                "/media/Sub",
                """
                <?xml version="1.0" encoding="utf-8"?>
                <D:multistatus xmlns:D="DAV:">
                  <D:response>
                    <D:href>/media/Sub/</D:href>
                    <D:propstat><D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop>
                    <D:status>HTTP/1.1 200 OK</D:status></D:propstat>
                  </D:response>
                  <D:response>
                    <D:href>/media/Sub/nested.jpg</D:href>
                    <D:propstat><D:prop><D:getcontentlength>20</D:getcontentlength>
                    <D:getlastmodified>Sat, 08 Mar 2025 12:46:46 GMT</D:getlastmodified>
                    <D:resourcetype/></D:prop>
                    <D:status>HTTP/1.1 200 OK</D:status></D:propstat>
                  </D:response>
                </D:multistatus>
                """.trimIndent(),
            )

            val withSubfolders = provider(searchSubfolders = true).fetch()
            val success = assertInstanceOf(ProviderFetchResult.Success::class.java, withSubfolders)
            assertEquals(setOf("top.mp4", "nested.jpg"), success.media.map { it.uri.filename }.toSet())

            // Without subfolder search the nested file is not reached.
            val withoutSubfolders = provider(searchSubfolders = false).fetch()
            val limited = assertInstanceOf(ProviderFetchResult.Success::class.java, withoutSubfolders)
            assertEquals(setOf("top.mp4"), limited.media.map { it.uri.filename }.toSet())
        }

    @Test
    fun `orders the media by modification date with the newest first`() =
        runTest {
            serve(
                "/media",
                """
                <?xml version="1.0" encoding="utf-8"?>
                <D:multistatus xmlns:D="DAV:">
                  <D:response>
                    <D:href>/media/</D:href>
                    <D:propstat><D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop>
                    <D:status>HTTP/1.1 200 OK</D:status></D:propstat>
                  </D:response>
                  <D:response>
                    <D:href>/media/older.jpg</D:href>
                    <D:propstat><D:prop><D:getlastmodified>Mon, 03 Mar 2025 12:00:00 GMT</D:getlastmodified>
                    <D:resourcetype/></D:prop>
                    <D:status>HTTP/1.1 200 OK</D:status></D:propstat>
                  </D:response>
                  <D:response>
                    <D:href>/media/newer.jpg</D:href>
                    <D:propstat><D:prop><D:getlastmodified>Wed, 05 Mar 2025 12:00:00 GMT</D:getlastmodified>
                    <D:resourcetype/></D:prop>
                    <D:status>HTTP/1.1 200 OK</D:status></D:propstat>
                  </D:response>
                </D:multistatus>
                """.trimIndent(),
            )

            val result = provider().fetch()

            val success = assertInstanceOf(ProviderFetchResult.Success::class.java, result)
            assertEquals(listOf("newer.jpg", "older.jpg"), success.media.map { it.uri.filename })
        }

    @Test
    fun `skips dot files and hidden files`() =
        runTest {
            serve(
                "/media",
                """
                <?xml version="1.0" encoding="utf-8"?>
                <D:multistatus xmlns:D="DAV:">
                  <D:response>
                    <D:href>/media/</D:href>
                    <D:propstat><D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop>
                    <D:status>HTTP/1.1 200 OK</D:status></D:propstat>
                  </D:response>
                  <D:response>
                    <D:href>/media/.hidden.jpg</D:href>
                    <D:propstat><D:prop><D:resourcetype/></D:prop>
                    <D:status>HTTP/1.1 200 OK</D:status></D:propstat>
                  </D:response>
                  <D:response>
                    <D:href>/media/visible.jpg</D:href>
                    <D:propstat><D:prop><D:resourcetype/></D:prop>
                    <D:status>HTTP/1.1 200 OK</D:status></D:propstat>
                  </D:response>
                </D:multistatus>
                """.trimIndent(),
            )

            val result = provider().fetch()

            val success = assertInstanceOf(ProviderFetchResult.Success::class.java, result)
            assertEquals(listOf("visible.jpg"), success.media.map { it.uri.filename })
        }

    @Test
    fun `keeps audio out of the media playlist and in the music playlist`() =
        runTest {
            serve(
                "/media",
                """
                <?xml version="1.0" encoding="utf-8"?>
                <D:multistatus xmlns:D="DAV:">
                  <D:response>
                    <D:href>/media/</D:href>
                    <D:propstat><D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop>
                    <D:status>HTTP/1.1 200 OK</D:status></D:propstat>
                  </D:response>
                  <D:response>
                    <D:href>/media/clip.mp4</D:href>
                    <D:propstat><D:prop><D:resourcetype/></D:prop>
                    <D:status>HTTP/1.1 200 OK</D:status></D:propstat>
                  </D:response>
                  <D:response>
                    <D:href>/media/song.mp3</D:href>
                    <D:propstat><D:prop><D:resourcetype/></D:prop>
                    <D:status>HTTP/1.1 200 OK</D:status></D:propstat>
                  </D:response>
                </D:multistatus>
                """.trimIndent(),
            )

            val provider = provider(musicEnabled = true)

            val result = provider.fetch()
            val success = assertInstanceOf(ProviderFetchResult.Success::class.java, result)
            assertEquals(listOf("clip.mp4"), success.media.map { it.uri.filename })

            val music = provider.fetchMusic()
            assertEquals(listOf("song.mp3"), music.map { it.uri.filename })
        }

    @Test
    fun `reports an authentication failure`() =
        runTest {
            server.dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse =
                        MockResponse().setResponseCode(401).setBody("Unauthorized")
                }

            val result = provider().fetch()

            val error = assertInstanceOf(ProviderFetchResult.Error::class.java, result)
            assertEquals(
                "Authentication failed. Please check the username, password, and server permissions.",
                error.message,
            )
        }

    @Test
    fun `reports a missing path`() =
        runTest {
            server.dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse = MockResponse().setResponseCode(404).setBody("Not found")
                }

            val result = provider().fetch()

            val error = assertInstanceOf(ProviderFetchResult.Error::class.java, result)
            assertEquals(
                "Unable to access path /media. Please check the path name and server permissions.",
                error.message,
            )
        }

    @Test
    fun `reports a connection failure and hints at a non default port`() =
        runTest {
            // Nothing is listening on this port, so the connection is refused.
            val unusedPort = server.port
            server.shutdown()

            val result = provider(hostName = "localhost").fetch()

            val error = assertInstanceOf(ProviderFetchResult.Error::class.java, result)
            assertTrue(
                error.message.orEmpty().startsWith("Could not connect to localhost:80."),
                "unexpected message: ${error.message}",
            )
            assertTrue(
                error.message.orEmpty().contains("non-default port"),
                "unexpected message: ${error.message}",
            )
            assertTrue(unusedPort > 0)
        }

    private fun provider(
        hostName: String = "localhost:${server.port}",
        searchSubfolders: Boolean = false,
        musicEnabled: Boolean = false,
    ): WebDavMediaProvider =
        WebDavMediaProvider(
            context = context,
            prefs =
                object : WebDavProviderPreferences {
                    override var enabled: Boolean = true
                    override val mediaSelection: Set<String> = setOf("VIDEOS", "PHOTOS")
                    override val mediaType: ProviderMediaType? = null
                    override val musicEnabled: Boolean = musicEnabled
                    override val includeVideos: Boolean = true
                    override val includePhotos: Boolean = true
                    override var scheme: SchemeType? = SchemeType.HTTP
                    override var hostName: String = hostName
                    override var pathName: String = "/media"
                    override var userName: String = ""
                    override var password: String = ""
                    override var searchSubfolders: Boolean = searchSubfolders
                    override var validateSsl: Boolean = true

                    override fun settingsHash(): String = "test"
                },
            clientFactory = {
                OkHttpWebDavClient(OkHttpClient()).apply {
                    setCredentials("", "", true)
                }
            },
        )

    /** Serves a response for a path, and a 404 for anything else. */
    private fun serve(
        path: String,
        body: String,
    ) {
        responses[path] = body
    }

    private fun fixture(name: String): String {
        val stream =
            requireNotNull(javaClass.getResourceAsStream("/webdav/$name")) {
                "Missing fixture /webdav/$name"
            }
        return stream.bufferedReader().use { it.readText() }
    }
}
