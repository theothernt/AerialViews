package com.neilturner.aerialviews.providers

import android.content.Context
import android.content.res.Resources
import android.net.Uri
import com.neilturner.aerialviews.models.enums.AerialMediaSource
import com.neilturner.aerialviews.models.enums.AerialMediaType
import com.neilturner.aerialviews.models.enums.ProviderSourceType
import com.neilturner.aerialviews.models.enums.SceneType
import com.neilturner.aerialviews.models.enums.TimeOfDay
import com.neilturner.aerialviews.models.enums.VideoQuality
import com.neilturner.aerialviews.models.prefs.ProviderPreferences
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.utils.filenameWithoutExtension
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Shared contract for the four bundled-JSON providers (Apple, Amazon, Comm1, Comm2).
 *
 * They all parse a raw resource, filter assets by preferences, and build a metadata lookup;
 * the only real differences are the resource IDs, the source enum and which quality maps to
 * which filename. Running the same suite against each provider also guards against copy/paste
 * drift (wrong raw file, wrong source enum) between these near-identical classes.
 */
abstract class BundledVideosProviderContract(
    private val videosResId: Int,
    private val stringsResId: Int,
    private val expectedSource: AerialMediaSource,
    private val dayFilenameFor1080Sdr: String,
    private val createProvider: (Context, ProviderPreferences) -> MediaProvider,
) {
    private val context = mockk<Context>()
    private val resources = mockk<Resources>()

    @BeforeEach
    fun setUp() {
        // No Robolectric here: Uri is a stub unless parse() is stubbed, and JsonHelper reads
        // the bundled JSON through context.resources.openRawResource().
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } answers {
            val raw = firstArg<String>()
            mockk<Uri>(relaxed = true) {
                every { lastPathSegment } returns raw.substringAfterLast('/')
            }
        }
        every { context.resources } returns resources
        every { resources.openRawResource(videosResId) } answers { videosJson.byteInputStream() }
        every { resources.openRawResource(stringsResId) } answers { stringsJson.byteInputStream() }
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Uri::class)
    }

    private fun provider(prefs: ProviderPreferences = fakePrefs()) = createProvider(context, prefs)

    private fun mediaAt(url: String) = AerialMedia(Uri.parse(url))

    private fun fakePrefs(
        isEnabled: Boolean = true,
        quality: VideoQuality? = VideoQuality.VIDEO_1080_SDR,
        timeOfDay: Set<String> = setOf("DAY", "NIGHT"),
        scene: Set<String> = setOf("NATURE", "CITY"),
        hash: String = "test-hash",
    ): ProviderPreferences =
        mockk {
            every { enabled } returns isEnabled
            every { this@mockk.quality } returns quality
            every { this@mockk.timeOfDay } returns timeOfDay
            every { this@mockk.scene } returns scene
            every { settingsHash() } returns hash
        }

    @Test
    fun `type is remote`() {
        assertEquals(ProviderSourceType.REMOTE, provider().type)
    }

    @Test
    fun `enabled follows preferences`() {
        assertTrue(provider(fakePrefs(isEnabled = true)).enabled)
        assertFalse(provider(fakePrefs(isEnabled = false)).enabled)
    }

    @Test
    fun `settings hash delegates to preferences`() {
        assertEquals("test-hash", provider(fakePrefs(hash = "test-hash")).settingsHash())
    }

    @Test
    fun `fetch includes only assets matching time of day, scene and enabled state`() =
        runTest {
            val success = assertInstanceOf(ProviderFetchResult.Success::class.java, provider().fetch())

            // "sunset/beach" and the unrecognised "bogus" asset are filtered out by prefs.
            assertEquals(2, success.media.size)
            success.media.forEach { media ->
                assertEquals(AerialMediaType.VIDEO, media.type)
                assertEquals(expectedSource, media.source)
            }

            val day = success.media[0]
            assertEquals(TimeOfDay.DAY, day.metadata.timeOfDay)
            assertEquals(SceneType.NATURE, day.metadata.scene)
            assertEquals(dayFilenameFor1080Sdr, day.uri.filenameWithoutExtension)

            val night = success.media[1]
            assertEquals(TimeOfDay.NIGHT, night.metadata.timeOfDay)
            assertEquals(SceneType.CITY, night.metadata.scene)
        }

    @Test
    fun `fetch returns nothing when provider is disabled`() =
        runTest {
            val success = assertInstanceOf(ProviderFetchResult.Success::class.java, provider(fakePrefs(isEnabled = false)).fetch())
            assertTrue(success.media.isEmpty())
        }

    @Test
    fun `repeated fetch does not duplicate videos`() =
        runTest {
            val p = provider()
            val first = assertInstanceOf(ProviderFetchResult.Success::class.java, p.fetch())
            val second = assertInstanceOf(ProviderFetchResult.Success::class.java, p.fetch())
            assertEquals(2, first.media.size)
            assertEquals(2, second.media.size)
        }

    @Test
    fun `fetch metadata resolves description and points of interest from the strings bundle`() =
        runTest {
            val result = provider().fetchMetadata(listOf(mediaAt("https://example.com/day_4k_sdr.mp4"))).single()
            assertEquals("A daytime description", result.metadata.shortDescription)
            assertEquals("Resolved daytime POI", result.metadata.pointsOfInterest[0])
        }

    @Test
    fun `fetch metadata falls back to the description for unknown POI keys`() =
        runTest {
            val result = provider().fetchMetadata(listOf(mediaAt("https://example.com/night_4k_sdr.mp4"))).single()
            assertEquals("A nighttime description", result.metadata.shortDescription)
            assertEquals("A nighttime description", result.metadata.pointsOfInterest[0])
        }

    @Test
    fun `fetch metadata leaves media with no matching asset unchanged`() =
        runTest {
            val result = provider().fetchMetadata(listOf(mediaAt("https://example.com/not_in_feed.mp4"))).single()
            assertEquals("", result.metadata.shortDescription)
            assertTrue(result.metadata.pointsOfInterest.isEmpty())
        }

    @Test
    fun `fetch metadata works without calling fetch first`() =
        runTest {
            val p = provider()
            val result = p.fetchMetadata(listOf(mediaAt("https://example.com/day_4k_sdr.mp4"))).single()
            assertEquals("A daytime description", result.metadata.shortDescription)
        }

    private fun asset(
        base: String,
        description: String,
        timeOfDay: String,
        scene: String,
        poi: String,
    ): String =
        """
        {
          "url-1080-H264": "https://example.com/${base}_h264.mp4",
          "url-1080-SDR": "https://example.com/${base}_sdr.mp4",
          "url-1080-HDR": "https://example.com/${base}_hdr.mp4",
          "url-4K-SDR": "https://example.com/${base}_4k_sdr.mp4",
          "url-4K-HDR": "https://example.com/${base}_4k_hdr.mp4",
          "accessibilityLabel": "$description",
          "pointsOfInterest": { "0": "$poi" },
          "timeOfDay": "$timeOfDay",
          "scene": "$scene"
        }
        """.trimIndent()

    private val videosJson: String
        get() =
            """
            {
              "assets": [
                ${asset("day", "A daytime description", "day", "nature", "poi_key_day")},
                ${asset("night", "A nighttime description", "night", "city", "missing_poi_key")},
                ${asset("sunset", "A filtered sunset", "sunset", "beach", "")},
                ${asset("bogus", "Unknown values", "bogus", "bogus", "")}
              ]
            }
            """.trimIndent()

    private val stringsJson =
        """
        { "poi_key_day": "Resolved daytime POI" }
        """.trimIndent()
}
