package com.neilturner.aerialviews.providers.webdav

import android.content.Context
import android.content.res.Resources
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.ProviderMediaType
import com.neilturner.aerialviews.models.enums.SchemeType
import com.neilturner.aerialviews.models.prefs.WebDavProviderPreferences
import com.neilturner.aerialviews.providers.ProviderFetchResult
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
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

    private fun fakePrefs(
        hostName: String,
        pathName: String,
        searchSubfolders: Boolean = false,
    ): WebDavProviderPreferences =
        object : WebDavProviderPreferences {
            override var enabled: Boolean = true
            override val mediaSelection: Set<String> = setOf("VIDEOS", "PHOTOS")
            override val mediaType: ProviderMediaType? = null
            override val musicEnabled: Boolean = false
            override val includeVideos: Boolean = true
            override val includePhotos: Boolean = true
            override var scheme: SchemeType? = SchemeType.HTTP
            override var hostName: String = hostName
            override var pathName: String = pathName
            override var userName: String = ""
            override var password: String = ""
            override var searchSubfolders: Boolean = searchSubfolders
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
