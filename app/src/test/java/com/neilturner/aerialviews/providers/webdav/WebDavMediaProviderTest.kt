package com.neilturner.aerialviews.providers.webdav

import android.content.Context
import android.content.res.Resources
import android.net.Uri
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.data.storage.FileHelper
import com.neilturner.aerialviews.models.enums.AerialMediaType
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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class WebDavMediaProviderTest {
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

    /**
     * There is no Robolectric in this module, so android.net.Uri is an unmocked stub. The existing
     * tests never reach a listing that yields media, so they never call Uri.parse. These tests do, so
     * parse() is stubbed to return a Uri whose lastPathSegment is the real filename - that keeps
     * filename-based assertions meaningful rather than trivially true against a relaxed mock.
     */
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
    fun `returns error when root listing fails`() =
        runTest {
            val provider =
                WebDavMediaProvider(
                    context = context,
                    prefs = fakePrefs(hostName = "example.com", pathName = "/media"),
                    clientFactory = {
                        FakeWebDavListingClient(
                            responses = emptyMap(),
                            failure = ConnectFailure("Connection refused"),
                        )
                    },
                )

            val result = provider.fetch()

            val error = assertInstanceOf(ProviderFetchResult.Error::class.java, result)
            assertEquals(
                "Could not connect to example.com:80. This server may use a non-default port. Specify the port in Hostname & port.",
                error.message,
            )
        }

    @Test
    fun `returns success when root listing succeeds with no files`() =
        runTest {
            val provider =
                WebDavMediaProvider(
                    context = context,
                    prefs = fakePrefs(hostName = "example.com", pathName = "/media"),
                    clientFactory = {
                        FakeWebDavListingClient(
                            responses =
                                mapOf(
                                    "http://example.com/media" to listOf(WebDavResourceInfo("media", isDirectory = true)),
                                ),
                        )
                    },
                )

            val result = provider.fetch()

            val success = assertInstanceOf(ProviderFetchResult.Success::class.java, result)
            assertEquals(emptyList<Any>(), success.media)
            assertEquals(
                "Files found: 0\nUnsupported files: 0\nVideos found: 0\nPhotos found: 0\nSelected for playback: 0",
                success.summary,
            )
        }

    @Test
    fun `continues when subfolder listing fails after root success`() =
        runTest {
            val provider =
                WebDavMediaProvider(
                    context = context,
                    prefs = fakePrefs(hostName = "example.com", pathName = "/media", searchSubfolders = true),
                    clientFactory = {
                        FakeWebDavListingClient(
                            responses =
                                mapOf(
                                    "http://example.com/media" to
                                        listOf(
                                            WebDavResourceInfo("media", isDirectory = true),
                                            WebDavResourceInfo("notes.txt", isDirectory = false, modifiedTimeMs = 10),
                                            WebDavResourceInfo("child", isDirectory = true),
                                        ),
                                ),
                            perUrlFailures =
                                mapOf(
                                    "http://example.com/media/child" to IllegalStateException("HTTP 500"),
                                ),
                        )
                    },
                )

            val result = provider.fetch()

            val success = assertInstanceOf(ProviderFetchResult.Success::class.java, result)
            assertEquals(0, success.media.size)
            assertEquals(
                "Files found: 1\nUnsupported files: 1\nVideos found: 0\nPhotos found: 0\nSelected for playback: 0",
                success.summary,
            )
        }

    /**
     * Regression guard for the SMB black-screen bug (SambaMediaProvider.findSambaFiles used to add
     * music files to the slideshow list). Audio must never reach the media playlist: AerialMedia.type
     * defaults to VIDEO, so an audio file that slips through is played as a video and shows black.
     */
    @Test
    fun `audio files never reach the media playlist`() =
        runTest {
            val provider = mixedProvider()

            val success = assertInstanceOf(ProviderFetchResult.Success::class.java, provider.fetch())

            val leaked = success.media.filter { FileHelper.isSupportedAudioType(it.uri.filename) }
            assertTrue(
                leaked.isEmpty(),
                "Audio leaked into the media playlist: ${leaked.map { it.uri.filename }}",
            )
            assertEquals(
                listOf(AerialMediaType.VIDEO, AerialMediaType.IMAGE),
                success.media.map { it.type },
            )
        }

    @Test
    fun `media and music playlists are disjoint`() =
        runTest {
            val provider = mixedProvider()

            val success = assertInstanceOf(ProviderFetchResult.Success::class.java, provider.fetch())
            val music = provider.fetchMusic()

            val mediaNames = success.media.map { it.uri.filename }.toSet()
            val musicNames = music.map { it.uri.filename }.toSet()

            assertEquals(setOf("clip.mp4", "photo.jpg"), mediaNames)
            assertEquals(setOf("song.mp3", "track.flac"), musicNames)
            assertTrue(
                mediaNames.intersect(musicNames).isEmpty(),
                "A file appeared in both playlists: ${mediaNames.intersect(musicNames)}",
            )
        }

    @Test
    fun `unrecognised files are dropped from the media playlist`() =
        runTest {
            val provider =
                WebDavMediaProvider(
                    context = context,
                    prefs = fakePrefs(hostName = "example.com", pathName = "/media"),
                    clientFactory = {
                        FakeWebDavListingClient(
                            responses =
                                mapOf(
                                    "http://example.com/media" to
                                        listOf(
                                            WebDavResourceInfo("media", isDirectory = true),
                                            WebDavResourceInfo("notes.txt", isDirectory = false, modifiedTimeMs = 30),
                                            WebDavResourceInfo("noextension", isDirectory = false, modifiedTimeMs = 20),
                                        ),
                                ),
                        )
                    },
                )

            val success = assertInstanceOf(ProviderFetchResult.Success::class.java, provider.fetch())

            assertEquals(0, success.media.size)
            assertEquals(
                "Files found: 2\nUnsupported files: 2\nVideos found: 0\nPhotos found: 0\nSelected for playback: 0",
                success.summary,
            )
        }

    /** A share containing one video, one photo, two tracks and one unsupported file. */
    private fun mixedProvider() =
        WebDavMediaProvider(
            context = context,
            prefs = fakePrefs(hostName = "example.com", pathName = "/media", musicEnabled = true),
            clientFactory = {
                FakeWebDavListingClient(
                    responses =
                        mapOf(
                            "http://example.com/media" to
                                listOf(
                                    WebDavResourceInfo("media", isDirectory = true),
                                    WebDavResourceInfo("clip.mp4", isDirectory = false, modifiedTimeMs = 50),
                                    WebDavResourceInfo("photo.jpg", isDirectory = false, modifiedTimeMs = 40),
                                    WebDavResourceInfo("song.mp3", isDirectory = false, modifiedTimeMs = 30),
                                    WebDavResourceInfo("track.flac", isDirectory = false, modifiedTimeMs = 20),
                                    WebDavResourceInfo("readme.txt", isDirectory = false, modifiedTimeMs = 10),
                                ),
                        ),
                )
            },
        )

    private fun fakePrefs(
        hostName: String,
        pathName: String,
        searchSubfolders: Boolean = false,
        validateSsl: Boolean = true,
        musicEnabled: Boolean = false,
    ): WebDavProviderPreferences =
        object : WebDavProviderPreferences {
            override var enabled: Boolean = true
            override val mediaSelection: Set<String> = setOf("VIDEOS", "PHOTOS")
            override val mediaType: ProviderMediaType? = null
            override val musicEnabled: Boolean = musicEnabled
            override val includeVideos: Boolean = true
            override val includePhotos: Boolean = true
            override var scheme: SchemeType? = SchemeType.HTTP
            override var hostName: String = hostName
            override var pathName: String = pathName
            override var userName: String = ""
            override var password: String = ""
            override var searchSubfolders: Boolean = searchSubfolders
            override var validateSsl: Boolean = validateSsl

            override fun settingsHash(): String = "test"
        }

    private class FakeWebDavListingClient(
        private val responses: Map<String, List<WebDavResourceInfo>>,
        private val failure: Exception? = null,
        private val perUrlFailures: Map<String, Exception> = emptyMap(),
    ) : WebDavListingClient {
        override fun setCredentials(
            userName: String,
            password: String,
            preemptive: Boolean,
        ) {
        }

        override fun list(url: String): List<WebDavResourceInfo> {
            failure?.let { throw it }
            perUrlFailures[url]?.let { throw it }
            return responses[url] ?: error("Unexpected URL $url")
        }
    }

    private class ConnectFailure(
        message: String,
    ) : RuntimeException(message, java.net.ConnectException(message))
}
