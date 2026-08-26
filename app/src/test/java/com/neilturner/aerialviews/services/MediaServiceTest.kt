package com.neilturner.aerialviews.services

import android.content.Context
import android.os.Bundle
import com.neilturner.aerialviews.models.enums.AerialMediaSource
import com.neilturner.aerialviews.models.enums.ProviderSourceType
import com.neilturner.aerialviews.models.music.MusicTrack
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.providers.MediaProvider
import com.neilturner.aerialviews.providers.ProviderFetchResult
import com.neilturner.aerialviews.utils.FirebaseHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.unmockkConstructor
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.random.Random

internal class MediaServiceTest {
    private val context = mockk<Context>(relaxed = true)

    @BeforeEach
    fun setUp() {
        mockkConstructor(Bundle::class)
        every { anyConstructed<Bundle>().putString(any(), any()) } returns Unit
        every { anyConstructed<Bundle>().keySet() } returns emptySet()
        mockkObject(FirebaseHelper)
        every { FirebaseHelper.analyticsEvent(any(), any()) } returns Unit
        mockkObject(com.neilturner.aerialviews.data.network.NetworkHelper)
        every {
            com.neilturner.aerialviews.data.network.NetworkHelper
                .isOnWifiOrEthernet(any())
        } returns false
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(com.neilturner.aerialviews.data.network.NetworkHelper)
        unmockkObject(FirebaseHelper)
        unmockkConstructor(Bundle::class)
    }

    @Test
    fun `returns only visual playlist when no provider exposes music`() =
        runTest {
            val service =
                MediaService(
                    context = context,
                    providers =
                        mutableListOf(
                            FakeMediaProvider(
                                context = context,
                                media = listOf(AerialMedia(mockk(relaxed = true))),
                            ),
                        ),
                    config = defaultConfig(),
                    hashFn = { "test-hash" },
                )

            val result = service.fetchMedia()

            assertEquals(1, result.mediaPlaylist.size)
            assertNull(result.musicPlaylist)
        }

    @Test
    fun `returns both playlists when provider exposes music tracks`() =
        runTest {
            val service =
                MediaService(
                    context = context,
                    providers =
                        mutableListOf(
                            FakeMediaProvider(
                                context = context,
                                media = listOf(AerialMedia(mockk(relaxed = true))),
                                tracks = listOf(MusicTrack(uri = mockk(relaxed = true))),
                            ),
                        ),
                    config = defaultConfig(),
                    hashFn = { "test-hash" },
                )

            val result = service.fetchMedia()

            assertEquals(1, result.mediaPlaylist.size)
            assertNotNull(result.musicPlaylist)
            assertEquals(1, result.musicPlaylist?.size)
        }

    @Test
    fun `returns null music playlist when providers expose no tracks`() =
        runTest {
            val service =
                MediaService(
                    context = context,
                    providers =
                        mutableListOf(
                            FakeMediaProvider(
                                context = context,
                                tracks = emptyList(),
                            ),
                        ),
                    config = defaultConfig(),
                    hashFn = { "test-hash" },
                )

            val result = service.fetchMedia()

            assertEquals(0, result.mediaPlaylist.size)
            assertNull(result.musicPlaylist)
        }

    @Test
    fun `filters out remote media sources when wifiOnly is true and not on wifi`() =
        runTest {
            val appleMedia = testMedia("apple-1", AerialMediaSource.APPLE)
            val localMedia = testMedia("local-1", AerialMediaSource.LOCAL)

            val service =
                MediaService(
                    context = context,
                    providers =
                        mutableListOf(
                            FakeMediaProvider(
                                context = context,
                                media = listOf(appleMedia, localMedia),
                            ),
                        ),
                    config = defaultConfig().copy(wifiOnly = true),
                    hashFn = { "test-hash" },
                )

            val result = service.fetchMedia()

            assertEquals(1, result.mediaPlaylist.size)
            assertEquals(AerialMediaSource.LOCAL, result.mediaPlaylist.nextItem().source)
        }

    @Test
    fun `weighted interleaved shuffle preserves all items and mixes sources`() {
        val media =
            listOf(
                testMedia("apple-1", AerialMediaSource.APPLE),
                testMedia("apple-2", AerialMediaSource.APPLE),
                testMedia("apple-3", AerialMediaSource.APPLE),
                testMedia("apple-4", AerialMediaSource.APPLE),
                testMedia("amazon-1", AerialMediaSource.AMAZON),
                testMedia("amazon-2", AerialMediaSource.AMAZON),
                testMedia("local-1", AerialMediaSource.LOCAL),
            )

        val shuffled = MediaServiceHelper.weightedInterleavedShuffle(media, Random(0))

        assertEquals(media.size, shuffled.size)
        assertEquals(media.toSet(), shuffled.toSet())
        assertEquals(4, shuffled.count { it.source == AerialMediaSource.APPLE })
        assertEquals(2, shuffled.count { it.source == AerialMediaSource.AMAZON })
        assertEquals(1, shuffled.count { it.source == AerialMediaSource.LOCAL })
        assertTrue(
            shuffled
                .take(4)
                .map { it.source }
                .toSet()
                .size > 1,
        )
    }

    private class FakeMediaProvider(
        context: Context,
        private val media: List<AerialMedia> = emptyList(),
        private val tracks: List<MusicTrack> = emptyList(),
    ) : MediaProvider(context) {
        override val type: ProviderSourceType = ProviderSourceType.LOCAL
        override val enabled: Boolean = true

        override suspend fun fetch(): ProviderFetchResult = ProviderFetchResult.Success(media = media, summary = "")

        override suspend fun fetchMusic(): List<MusicTrack> = tracks

        override suspend fun fetchMetadata(media: List<AerialMedia>): List<AerialMedia> = media

        override fun settingsHash(): String = "fake"
    }

    private fun defaultConfig() =
        MediaService.Config(
            removeDuplicates = false,
            ignoreNonManifestVideos = false,
            autoTimeOfDay = false,
            playlistTimeOfDayDayIncludes = emptySet(),
            playlistTimeOfDayNightIncludes = emptySet(),
            playlistCache = false,
            shuffleVideos = false,
            shuffleMusic = false,
            repeatMusic = false,
            wifiOnly = false,
        )

    private fun testMedia(
        id: String,
        source: AerialMediaSource,
    ) = AerialMedia(uri = mockk(relaxed = true, name = id), source = source)
}
