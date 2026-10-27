package com.neilturner.aerialviews.data

import android.content.Context
import android.net.Uri
import com.neilturner.aerialviews.models.MediaPlaylist
import com.neilturner.aerialviews.models.TimeOfDayFilter
import com.neilturner.aerialviews.models.enums.AerialMediaSource
import com.neilturner.aerialviews.models.enums.AerialMediaType
import com.neilturner.aerialviews.models.enums.SceneType
import com.neilturner.aerialviews.models.enums.TimeOfDay
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.models.videos.AerialMediaMetadata
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class PlaylistCacheRepositoryTest {
    private val context = mockk<Context>(relaxed = true)
    private val dao = FakePlaylistCacheDao()
    private var prefs = PlaylistCachePrefs(enabled = true, refresh = "-1", resumeSame = false)
    private lateinit var repository: PlaylistCacheRepository

    private val dayFilter = TimeOfDayFilter.forPeriod(TimeOfDay.DAY, dayIncludes = emptySet(), nightIncludes = emptySet())

    @BeforeEach
    fun setUp() {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } answers {
            val raw = firstArg<String>()
            mockk<Uri>(relaxed = true) {
                every { lastPathSegment } returns raw.substringAfterLast('/')
            }
        }
        repository = PlaylistCacheRepository(context, dao, { prefs })
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Uri::class)
    }

    @Test
    fun `cache is valid before the playlist has finished`() =
        runTest {
            useCache(media = List(3) { cachedVideo(it, TimeOfDay.UNKNOWN) }, position = 0)

            assertTrue(repository.isCacheValid(HASH, TimeOfDayFilter.ALL))
        }

    @Test
    fun `cache is invalid when the settings hash changed`() =
        runTest {
            useCache(media = List(3) { cachedVideo(it, TimeOfDay.UNKNOWN) }, position = 0, hash = "old-hash")

            assertFalse(repository.isCacheValid(HASH, TimeOfDayFilter.ALL))
        }

    @Test
    fun `cache is cleared and invalid when caching is disabled`() =
        runTest {
            useCache(media = List(3) { cachedVideo(it, TimeOfDay.UNKNOWN) }, position = 0)
            prefs = prefs.copy(enabled = false)

            assertFalse(repository.isCacheValid(HASH, TimeOfDayFilter.ALL))
            assertNull(dao.state)
            assertTrue(dao.mediaItems.isEmpty())
        }

    @Test
    fun `cache is invalid once the last item has played`() =
        runTest {
            useCache(media = List(3) { cachedVideo(it, TimeOfDay.UNKNOWN) }, position = 2)

            assertFalse(repository.isCacheValid(HASH, TimeOfDayFilter.ALL))
        }

    @Test
    fun `cache stays valid when the period shrank the filtered list below the saved position`() =
        runTest {
            // Four items were cached, but only two match the current period. The saved position of
            // 3 belongs to the old, longer list; that should restart rather than refetch.
            useCache(
                media =
                    listOf(
                        cachedVideo(0, TimeOfDay.DAY),
                        cachedVideo(1, TimeOfDay.NIGHT),
                        cachedVideo(2, TimeOfDay.DAY),
                        cachedVideo(3, TimeOfDay.NIGHT),
                    ),
                position = 3,
            )

            assertTrue(repository.isCacheValid(HASH, dayFilter))
        }

    @Test
    fun `time based cache expires when older than the configured interval`() =
        runTest {
            useCache(
                media = listOf(cachedVideo(0, TimeOfDay.UNKNOWN)),
                position = 0,
                cachedAt = System.currentTimeMillis() - 8L * 24 * 60 * 60 * 1000,
            )
            prefs = prefs.copy(refresh = "1")

            assertFalse(repository.isCacheValid(HASH, TimeOfDayFilter.ALL))
        }

    @Test
    fun `time based cache is valid inside the configured interval`() =
        runTest {
            useCache(media = listOf(cachedVideo(0, TimeOfDay.UNKNOWN)), position = 0)
            prefs = prefs.copy(refresh = "1")

            assertTrue(repository.isCacheValid(HASH, TimeOfDayFilter.ALL))
        }

    @Test
    fun `getCachedPlaylist filters at read time and reports the filtered size`() =
        runTest {
            useCache(
                media =
                    listOf(
                        cachedVideo(0, TimeOfDay.DAY),
                        cachedVideo(1, TimeOfDay.NIGHT),
                        cachedVideo(2, TimeOfDay.DAY),
                        cachedPhoto(3),
                        cachedVideo(4, TimeOfDay.SUNSET),
                    ),
                position = -1,
            )

            val result = repository.getCachedPlaylist(dayFilter)
            assertNotNull(result)
            val playlist = result!!.mediaPlaylist

            assertEquals(3, playlist.size, "Expected two day videos and the photo")
            assertEquals(listOf("video-0", "video-2", "photo-3"), playlist.titles())
        }

    @Test
    fun `getCachedPlaylist falls back to the full list when no video matches`() =
        runTest {
            useCache(
                media = List(3) { cachedVideo(it, TimeOfDay.NIGHT) },
                position = -1,
            )

            val result = repository.getCachedPlaylist(dayFilter)
            assertNotNull(result)
            val playlist = result!!.mediaPlaylist

            assertEquals(3, playlist.size)
            assertEquals(listOf("video-0", "video-1", "video-2"), playlist.titles())
        }

    @Test
    fun `getCachedPlaylist restarts when the saved position is outside the filtered list`() =
        runTest {
            useCache(
                media =
                    listOf(
                        cachedVideo(0, TimeOfDay.DAY),
                        cachedVideo(1, TimeOfDay.DAY),
                        cachedVideo(2, TimeOfDay.DAY),
                        cachedVideo(3, TimeOfDay.NIGHT),
                    ),
                position = 3,
            )

            val result = repository.getCachedPlaylist(dayFilter)
            assertNotNull(result)
            val playlist = result!!.mediaPlaylist

            assertEquals(-1, playlist.currentPosition)
            assertEquals("video-0", playlist.nextItem()?.metadata?.title)
        }

    @Test
    fun `next resume behaviour starts after the saved position`() =
        runTest {
            useCache(media = List(4) { cachedVideo(it, TimeOfDay.UNKNOWN) }, position = 2)

            val result = repository.getCachedPlaylist(TimeOfDayFilter.ALL)
            assertNotNull(result)
            val playlist = result!!.mediaPlaylist

            assertEquals(2, playlist.currentPosition)
            assertEquals("video-3", playlist.nextItem()?.metadata?.title)
        }

    @Test
    fun `same resume behaviour replays the saved position`() =
        runTest {
            useCache(media = List(4) { cachedVideo(it, TimeOfDay.UNKNOWN) }, position = 2)
            prefs = prefs.copy(resumeSame = true)

            val result = repository.getCachedPlaylist(TimeOfDayFilter.ALL)
            assertNotNull(result)
            val playlist = result!!.mediaPlaylist

            assertEquals(1, playlist.currentPosition)
            assertEquals("video-2", playlist.nextItem()?.metadata?.title)
        }

    @Test
    fun `getCachedPlaylist returns null when there is no state`() =
        runTest {
            assertNull(repository.getCachedPlaylist(TimeOfDayFilter.ALL))
        }

    @Test
    fun `getCachedPlaylist returns null when the cache holds no media`() =
        runTest {
            useCache(media = emptyList(), position = 0, totalMediaItems = 0)

            assertNull(repository.getCachedPlaylist(TimeOfDayFilter.ALL))
        }

    @Test
    fun `cachePlaylist stores the full list and read back applies the filter`() =
        runTest {
            repository.cachePlaylist(
                media =
                    listOf(
                        testMedia("video-0", TimeOfDay.DAY),
                        testMedia("video-1", TimeOfDay.NIGHT),
                        testMedia("video-2", TimeOfDay.DAY),
                    ),
                musicPlaylist = null,
                settingsHash = HASH,
                shuffleEnabled = false,
            )

            assertEquals(3, dao.mediaItems.size)
            assertEquals(3, dao.state?.totalMediaItems)

            val result = repository.getCachedPlaylist(dayFilter)
            assertNotNull(result)
            val playlist = result!!.mediaPlaylist
            assertEquals(2, playlist.size)
            assertEquals(listOf("video-0", "video-2"), playlist.titles())
        }

    @Test
    fun `lazy chunks are fetched in the filtered index space`() =
        runTest {
            // 120 videos, 60 day and 60 night. The filtered playlist must be 60 items long and
            // must cycle exactly those 60, not replay or skip rows from the full table.
            useCache(
                media = List(120) { index -> cachedVideo(index, if (index % 2 == 0) TimeOfDay.DAY else TimeOfDay.NIGHT) },
                position = -1,
            )

            val result = repository.getCachedPlaylist(dayFilter)
            assertNotNull(result)
            val playlist = result!!.mediaPlaylist
            assertEquals(60, playlist.size)

            val titles = playlist.titles(count = 60)
            assertEquals((0 until 120 step 2).map { "video-$it" }, titles)

            // The 61st item wraps to the start of the filtered list.
            assertEquals("video-0", playlist.nextItem()?.metadata?.title)
        }

    @Test
    fun `countMatching reflects the filter and its fallback`() =
        runTest {
            useCache(
                media =
                    listOf(
                        cachedVideo(0, TimeOfDay.DAY),
                        cachedVideo(1, TimeOfDay.NIGHT),
                        cachedPhoto(2),
                    ),
                position = -1,
            )

            assertEquals(2, repository.countMatching(dayFilter))

            // All videos are night videos, so the day filter falls back to the whole list.
            dao.mediaItems.clear()
            dao.mediaItems += List(2) { cachedVideo(it, TimeOfDay.NIGHT) }
            assertEquals(2, repository.countMatching(dayFilter))
        }

    private fun useCache(
        media: List<CachedMediaEntity>,
        position: Int,
        hash: String = HASH,
        cachedAt: Long = System.currentTimeMillis(),
        totalMediaItems: Int = media.size,
    ) {
        dao.mediaItems.clear()
        dao.mediaItems += media
        dao.state =
            PlaylistStateEntity(
                mediaPosition = position,
                musicTrackIndex = 0,
                cachedAt = cachedAt,
                settingsHash = hash,
                totalMediaItems = totalMediaItems,
                totalMusicTracks = 0,
                shuffleEnabled = false,
                musicShuffleEnabled = false,
                musicRepeatEnabled = false,
            )
    }

    private fun cachedVideo(
        order: Int,
        timeOfDay: TimeOfDay,
    ) = cachedMedia(order, AerialMediaType.VIDEO, timeOfDay)

    private fun cachedPhoto(order: Int) = cachedMedia(order, AerialMediaType.IMAGE, TimeOfDay.UNKNOWN)

    private fun cachedMedia(
        order: Int,
        type: AerialMediaType,
        timeOfDay: TimeOfDay,
    ) = CachedMediaEntity(
        playlistOrder = order,
        uri = "file:///test/${type.name.lowercase()}-$order.mp4",
        type = type.name,
        source = AerialMediaSource.LOCAL.name,
        shortDescription = "",
        pointsOfInterest = emptyMap(),
        timeOfDay = timeOfDay.name,
        scene = SceneType.UNKNOWN.name,
        albumName = "",
        title = "${if (type == AerialMediaType.VIDEO) "video" else "photo"}-$order",
        exifDate = null,
        exifOffset = null,
        exifLatitude = null,
        exifLongitude = null,
        exifCity = null,
        exifState = null,
        exifCountry = null,
        exifDescription = null,
    )

    private fun testMedia(
        title: String,
        timeOfDay: TimeOfDay,
    ) = AerialMedia(
        uri = "file:///test/$title.mp4".let(Uri::parse),
        type = AerialMediaType.VIDEO,
        source = AerialMediaSource.LOCAL,
        metadata = AerialMediaMetadata(timeOfDay = timeOfDay, title = title),
    )

    /** Collects [count] items by repeatedly calling nextItem(). */
    private fun MediaPlaylist.titles(count: Int = size): List<String?> = List(count) { nextItem()?.metadata?.title }

    private companion object {
        const val HASH = "test-hash"
    }
}
