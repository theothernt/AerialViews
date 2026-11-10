package com.neilturner.aerialviews.data

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.neilturner.aerialviews.models.TimeOfDayFilter
import com.neilturner.aerialviews.models.enums.AerialMediaSource
import com.neilturner.aerialviews.models.enums.AerialMediaType
import com.neilturner.aerialviews.models.enums.SceneType
import com.neilturner.aerialviews.models.enums.TimeOfDay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Runs the real cache queries against an in-memory database using the bundled SQLite driver, so
 * the time-of-day predicate and the ordering of LIMIT/OFFSET are verified by SQLite itself rather
 * than by a fake.
 */
internal class PlaylistCacheDaoTest {
    private lateinit var db: AerialDatabase
    private lateinit var dao: PlaylistCacheDao

    private val dayAllowed = TimeOfDayFilter.forPeriod(TimeOfDay.DAY, emptySet(), emptySet()).allowedNames()
    private val dayWithSunrise = TimeOfDayFilter.forPeriod(TimeOfDay.DAY, setOf("SUNRISE"), emptySet()).allowedNames()
    private val nightAllowed = TimeOfDayFilter.forPeriod(TimeOfDay.NIGHT, emptySet(), emptySet()).allowedNames()

    @BeforeEach
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder<AerialDatabase>()
                .setDriver(BundledSQLiteDriver())
                .build()
        dao = db.playlistCacheDao()
    }

    @AfterEach
    fun tearDown() {
        db.close()
    }

    @Test
    fun `counts match the time-of-day predicate and always include photos`() =
        runTest {
            dao.insertMediaItems(
                listOf(
                    video(0, TimeOfDay.DAY),
                    video(1, TimeOfDay.NIGHT),
                    video(2, TimeOfDay.SUNRISE),
                    photo(3),
                ),
            )

            assertEquals(2, dao.countMediaItems(dayAllowed))
            assertEquals(1, dao.countVideoItems(dayAllowed))
            assertEquals(3, dao.countMediaItems(dayWithSunrise))
            assertEquals(2, dao.countVideoItems(dayWithSunrise))
            assertEquals(2, dao.countMediaItems(nightAllowed))
            assertEquals(1, dao.countVideoItems(nightAllowed))
        }

    @Test
    fun `chunk offsets are applied after filtering`() =
        runTest {
            dao.insertMediaItems(
                listOf(
                    video(0, TimeOfDay.DAY),
                    video(1, TimeOfDay.NIGHT),
                    video(2, TimeOfDay.DAY),
                    video(3, TimeOfDay.NIGHT),
                    video(4, TimeOfDay.DAY),
                ),
            )

            val window = dao.getMediaItemsChunkFiltered(dayAllowed, limit = 2, offset = 1)

            assertEquals(listOf(2, 4), window.map { it.playlistOrder })
        }

    @Test
    fun `filtered chunks stay inside the filtered count`() =
        runTest {
            dao.insertMediaItems(List(10) { index -> video(index, TimeOfDay.DAY) })

            val first = dao.getMediaItemsChunkFiltered(dayAllowed, limit = 4, offset = 0)
            val last = dao.getMediaItemsChunkFiltered(dayAllowed, limit = 4, offset = 8)

            assertEquals(listOf(0, 1, 2, 3), first.map { it.playlistOrder })
            assertEquals(listOf(8, 9), last.map { it.playlistOrder })
        }

    @Test
    fun `updateCache replaces the previous contents transactionally`() =
        runTest {
            dao.updateCache(
                media = listOf(video(0, TimeOfDay.DAY)),
                music = emptyList(),
                state = state(totalMediaItems = 1),
            )

            dao.updateCache(
                media = listOf(video(0, TimeOfDay.NIGHT), video(1, TimeOfDay.NIGHT)),
                music = emptyList(),
                state = state(totalMediaItems = 2),
            )

            assertEquals(2, dao.getAllMediaItemsOrdered().size)
            assertEquals(2, dao.getPlaylistState()?.totalMediaItems)
        }

    @Test
    fun `saving a position updates the cached state`() =
        runTest {
            dao.insertOrUpdateState(state(totalMediaItems = 3))

            dao.updateMediaPosition(2)
            dao.updateMusicTrackIndex(1)

            assertEquals(2, dao.getPlaylistState()?.mediaPosition)
            assertEquals(1, dao.getPlaylistState()?.musicTrackIndex)
        }

    @Test
    fun `clearing the cache removes media music and state`() =
        runTest {
            dao.updateCache(
                media = listOf(video(0, TimeOfDay.DAY)),
                music = listOf(CachedMusicTrackEntity(playlistOrder = 0, uri = "file:///music.mp3", source = AerialMediaSource.LOCAL.name)),
                state = state(totalMediaItems = 1),
            )

            dao.clearAll()

            assertEquals(0, dao.getAllMediaItemsOrdered().size)
            assertEquals(0, dao.getAllMusicTracksOrdered().size)
            assertEquals(null, dao.getPlaylistState())
        }

    private fun state(totalMediaItems: Int) =
        PlaylistStateEntity(
            mediaPosition = -1,
            musicTrackIndex = 0,
            cachedAt = System.currentTimeMillis(),
            settingsHash = "test-hash",
            totalMediaItems = totalMediaItems,
            totalMusicTracks = 0,
            shuffleEnabled = false,
            musicShuffleEnabled = false,
            musicRepeatEnabled = false,
        )

    private fun video(
        order: Int,
        timeOfDay: TimeOfDay,
    ) = cachedMedia(order, AerialMediaType.VIDEO, timeOfDay)

    private fun photo(order: Int) = cachedMedia(order, AerialMediaType.IMAGE, TimeOfDay.UNKNOWN)

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
        title = "${type.name.lowercase()}-$order",
        exifDate = null,
        exifOffset = null,
        exifLatitude = null,
        exifLongitude = null,
        exifCity = null,
        exifState = null,
        exifCountry = null,
        exifDescription = null,
    )
}
