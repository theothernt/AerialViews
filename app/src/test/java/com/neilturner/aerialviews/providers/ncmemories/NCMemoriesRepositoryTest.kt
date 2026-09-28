package com.neilturner.aerialviews.providers.ncmemories

import com.neilturner.aerialviews.models.enums.ProviderMediaType
import com.neilturner.aerialviews.models.prefs.NCMemoriesRepositoryPrefs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import retrofit2.Response
import java.net.SocketTimeoutException

internal class NCMemoriesRepositoryTest {
    private lateinit var api: NCMemoriesApi
    private lateinit var prefs: NCMemoriesRepositoryPrefs
    private lateinit var repository: NCMemoriesRepository

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        api = mockk(relaxed = true)
        prefs = mockk(relaxed = true)
        repository = NCMemoriesRepository(prefs, apiOverride = api)
        repository.server = "http://test-server.com"
    }

    private fun <T> errorResponse(code: Int): Response<T> = Response.error(code, mockk<ResponseBody>(relaxed = true))

    // -----------------------------------------------------------------------
    // fetchAlbumList
    // -----------------------------------------------------------------------

    @Nested
    inner class FetchAlbumList {
        @Test
        fun `returns albums on success`() =
            runTest {
                val albums =
                    listOf(
                        Album(albumId = 1, clusterId = "c1", name = "Vacation", count = 10),
                        Album(albumId = 2, clusterId = "c2", name = "Family", count = 5),
                    )
                coEvery { api.getAlbumList(any()) } returns Response.success(albums)

                val result = repository.fetchAlbumList()

                assertTrue(result.isSuccess)
                assertEquals(2, result.getOrNull()?.size)
            }

        @Test
        fun `returns failure on API error`() =
            runTest {
                coEvery { api.getAlbumList(any()) } returns errorResponse(500)

                val result = repository.fetchAlbumList()

                assertTrue(result.isFailure)
            }

        @Test
        fun `returns failure on exception`() =
            runTest {
                coEvery { api.getAlbumList(any()) } throws SocketTimeoutException("timeout")

                val result = runCatching { repository.fetchAlbumList() }
                assertTrue(result.isFailure)
                assertTrue(result.exceptionOrNull() is SocketTimeoutException)
            }
    }

    // -----------------------------------------------------------------------
    // fetchClusterNames
    // -----------------------------------------------------------------------

    @Nested
    inner class FetchClusterNames {
        @Test
        fun `returns matching albums from server list`() =
            runTest {
                val serverAlbums =
                    listOf(
                        Album(albumId = 1, clusterId = "c1", name = "Vacation", count = 10),
                        Album(albumId = 2, clusterId = "c2", name = "Family", count = 5),
                        Album(albumId = 3, clusterId = "c3", name = "Work", count = 20),
                    )
                coEvery { api.getAlbumList(any()) } returns Response.success(serverAlbums)

                val result = repository.fetchClusterNames(setOf("1", "3"))

                assertEquals(2, result.size)
                assertEquals("Vacation", result[0].name)
                assertEquals("Work", result[1].name)
            }

        @Test
        fun `returns empty list when server returns empty`() =
            runTest {
                coEvery { api.getAlbumList(any()) } returns Response.success(emptyList())

                val result = repository.fetchClusterNames(setOf("1"))

                assertTrue(result.isEmpty())
            }

        @Test
        fun `throws on API error`() =
            runTest {
                coEvery { api.getAlbumList(any()) } returns errorResponse(404)

                val result = runCatching { repository.fetchClusterNames(setOf("1")) }
                assertTrue(result.isFailure)
            }

        @Test
        fun `throws on exception`() =
            runTest {
                coEvery { api.getAlbumList(any()) } throws SocketTimeoutException("timeout")

                val result = runCatching { repository.fetchClusterNames(setOf("1")) }
                assertTrue(result.isFailure)
            }
    }

    // -----------------------------------------------------------------------
    // fetchImagesByDayIds
    // -----------------------------------------------------------------------

    @Nested
    inner class FetchImagesByDayIds {
        @Test
        fun `returns empty list for empty input`() =
            runTest {
                val result = repository.fetchImagesByDayIds(emptyList())

                assertTrue(result.isEmpty())
                coVerify(exactly = 0) { api.getImages(any(), any(), any(), any(), any()) }
            }

        @Test
        fun `returns images for single batch`() =
            runTest {
                val images =
                    listOf(
                        Image(fileId = 1, etag = "e1", baseName = "photo1.jpg"),
                        Image(fileId = 2, etag = "e2", baseName = "photo2.jpg"),
                    )
                coEvery { api.getImages(any(), any(), any(), any(), any()) } returns Response.success(images)

                val result = repository.fetchImagesByDayIds(listOf(20230826, 20230827))

                assertEquals(2, result.size)
            }

        @Test
        fun `splits into multiple batches of max 100`() =
            runTest {
                val dayIds = (1..250).toList()
                val batch1 = listOf(Image(fileId = 1, etag = "e1", baseName = "photo1.jpg"))
                val batch2 = listOf(Image(fileId = 2, etag = "e2", baseName = "photo2.jpg"))
                val batch3 = listOf(Image(fileId = 3, etag = "e3", baseName = "photo3.jpg"))

                coEvery { api.getImages(any(), dayIds.take(100).joinToString(","), any(), any(), any()) } returns
                    Response.success(batch1)
                coEvery { api.getImages(any(), dayIds.drop(100).take(100).joinToString(","), any(), any(), any()) } returns
                    Response.success(batch2)
                coEvery { api.getImages(any(), dayIds.drop(200).joinToString(","), any(), any(), any()) } returns
                    Response.success(batch3)

                val result = repository.fetchImagesByDayIds(dayIds)

                assertEquals(3, result.size)
                assertEquals(1, result[0].fileId)
                assertEquals(2, result[1].fileId)
                assertEquals(3, result[2].fileId)
            }

        @Test
        fun `continues on partial batch failure`() =
            runTest {
                val dayIds = (1..150).toList()
                val successImages = listOf(Image(fileId = 1, etag = "e1", baseName = "photo1.jpg"))

                coEvery { api.getImages(any(), dayIds.take(100).joinToString(","), any(), any(), any()) } returns
                    Response.success(successImages)
                coEvery { api.getImages(any(), dayIds.drop(100).joinToString(","), any(), any(), any()) } returns
                    errorResponse(500)

                val result = repository.fetchImagesByDayIds(dayIds)

                assertEquals(1, result.size)
            }

        @Test
        fun `returns empty when all batches fail`() =
            runTest {
                val dayIds = listOf(20230826, 20230827)

                coEvery { api.getImages(any(), any(), any(), any(), any()) } returns errorResponse(500)

                val result = repository.fetchImagesByDayIds(dayIds)

                assertTrue(result.isEmpty())
            }
    }

    // -----------------------------------------------------------------------
    // fetchExifInfo
    // -----------------------------------------------------------------------

    @Nested
    inner class FetchExifInfo {
        @Test
        fun `enriches images with EXIF data`() =
            runTest {
                val images =
                    listOf(
                        Image(fileId = 1, etag = "e1", baseName = "photo1.jpg", albumName = "Album1"),
                    )
                val exifImage =
                    Image(
                        fileId = 1,
                        etag = "e1",
                        baseName = "photo1.jpg",
                        albumName = "Album1",
                        exif = ExifInfo(dateTimeEpoch = 1693500000),
                    )
                coEvery { api.getFullImageInfo(any(), 1) } returns Response.success(exifImage)

                val result = repository.fetchExifInfo(images)

                assertEquals(1, result.size)
                assertEquals(1693500000L, result[0].exif?.dateTimeEpoch)
            }

        @Test
        fun `preserves original image when EXIF API returns error`() =
            runTest {
                val images =
                    listOf(
                        Image(fileId = 1, etag = "e1", baseName = "photo1.jpg", albumName = "Album1"),
                    )
                coEvery { api.getFullImageInfo(any(), 1) } returns errorResponse(404)

                val result = repository.fetchExifInfo(images)

                assertEquals(1, result.size)
                assertEquals("photo1.jpg", result[0].baseName)
                assertTrue(result[0].exif == null)
            }

        @Test
        fun `preserves original image when EXIF API throws`() =
            runTest {
                val images =
                    listOf(
                        Image(fileId = 1, etag = "e1", baseName = "photo1.jpg", albumName = "Album1"),
                        Image(fileId = 2, etag = "e2", baseName = "photo2.jpg", albumName = "Album1"),
                    )
                coEvery { api.getFullImageInfo(any(), 1) } throws SocketTimeoutException("timeout")
                coEvery { api.getFullImageInfo(any(), 2) } returns
                    Response.success(
                        Image(fileId = 2, etag = "e2", baseName = "photo2.jpg", albumName = "Album1"),
                    )

                val result = repository.fetchExifInfo(images)

                assertEquals(2, result.size)
                assertEquals("photo1.jpg", result[0].baseName)
                assertEquals("photo2.jpg", result[1].baseName)
            }

        @Test
        fun `preserves original image when EXIF body is null`() =
            runTest {
                val images =
                    listOf(
                        Image(fileId = 1, etag = "e1", baseName = "photo1.jpg", albumName = "Album1"),
                    )
                coEvery { api.getFullImageInfo(any(), 1) } returns Response.success(204, null as Image?)

                val result = repository.fetchExifInfo(images)

                assertEquals(1, result.size)
                assertEquals("photo1.jpg", result[0].baseName)
            }
    }

    // -----------------------------------------------------------------------
    // getSelectedAlbums
    // -----------------------------------------------------------------------

    @Nested
    inner class GetSelectedAlbums {
        @Test
        fun `returns empty for empty selection`() =
            runTest {
                every { prefs.selectedAlbumIds } returns setOf<String>()

                val result = repository.getSelectedAlbums()

                assertTrue(result.isEmpty())
            }

        @Test
        fun `returns images for single album`() =
            runTest {
                every { prefs.selectedAlbumIds } returns setOf("1")
                val albums = listOf(Album(albumId = 1, clusterId = "c1", name = "Vacation", count = 10))
                val days = listOf(Day(dayId = 20230826), Day(dayId = 20230827))
                val images =
                    listOf(
                        Image(fileId = 1, etag = "e1", baseName = "photo1.jpg"),
                        Image(fileId = 2, etag = "e2", baseName = "photo2.jpg"),
                    )

                coEvery { api.getAlbumList(any()) } returns Response.success(albums)
                coEvery { api.getDays(any(), clusterId = "c1", vid = any()) } returns Response.success(days)
                coEvery { api.getImages(any(), dayIds = "20230826,20230827", clusterId = "c1", vid = any()) } returns
                    Response.success(images)

                val result = repository.getSelectedAlbums()

                assertEquals(2, result.size)
                assertEquals("Vacation", result[0].albumName)
            }

        @Test
        fun `deduplicates images across albums`() =
            runTest {
                every { prefs.selectedAlbumIds } returns setOf("1", "2")
                val albums =
                    listOf(
                        Album(albumId = 1, clusterId = "c1", name = "Vacation", count = 10),
                        Album(albumId = 2, clusterId = "c2", name = "Family", count = 5),
                    )
                val days1 = listOf(Day(dayId = 20230826))
                val days2 = listOf(Day(dayId = 20230826), Day(dayId = 20230827))
                val sharedImage = Image(fileId = 1, etag = "e1", baseName = "photo1.jpg")
                val uniqueImage = Image(fileId = 2, etag = "e2", baseName = "photo2.jpg")

                coEvery { api.getAlbumList(any()) } returns Response.success(albums)
                coEvery { api.getDays(any(), clusterId = "c1", vid = any()) } returns Response.success(days1)
                coEvery { api.getDays(any(), clusterId = "c2", vid = any()) } returns Response.success(days2)
                coEvery { api.getImages(any(), dayIds = "20230826", clusterId = "c1", vid = any()) } returns
                    Response.success(listOf(sharedImage))
                coEvery { api.getImages(any(), dayIds = "20230826,20230827", clusterId = "c2", vid = any()) } returns
                    Response.success(listOf(sharedImage, uniqueImage))

                val result = repository.getSelectedAlbums()

                assertEquals(2, result.size)
            }

        @Test
        fun `handles album with no images`() =
            runTest {
                every { prefs.selectedAlbumIds } returns setOf("1")
                val albums = listOf(Album(albumId = 1, clusterId = "c1", name = "Empty", count = 0))
                val days = listOf(Day(dayId = 20230826))

                coEvery { api.getAlbumList(any()) } returns Response.success(albums)
                coEvery { api.getDays(any(), clusterId = "c1", vid = any()) } returns Response.success(days)
                coEvery { api.getImages(any(), dayIds = "20230826", clusterId = "c1", vid = any()) } returns
                    Response.success(emptyList())

                val result = runCatching { repository.getSelectedAlbums() }
                assertTrue(result.isFailure)
            }

        @Test
        fun `continues when one album fails`() =
            runTest {
                every { prefs.selectedAlbumIds } returns setOf("1", "2")
                val albums =
                    listOf(
                        Album(albumId = 1, clusterId = "c1", name = "Good", count = 10),
                        Album(albumId = 2, clusterId = "c2", name = "Bad", count = 5),
                    )
                val days1 = listOf(Day(dayId = 20230826))
                val images = listOf(Image(fileId = 1, etag = "e1", baseName = "photo1.jpg"))

                coEvery { api.getAlbumList(any()) } returns Response.success(albums)
                coEvery { api.getDays(any(), clusterId = "c1", vid = any()) } returns Response.success(days1)
                coEvery { api.getDays(any(), clusterId = "c2", vid = any()) } returns errorResponse(500)
                coEvery { api.getImages(any(), dayIds = "20230826", clusterId = "c1", vid = any()) } returns
                    Response.success(images)

                val result = repository.getSelectedAlbums()

                assertEquals(1, result.size)
                assertEquals("Good", result[0].albumName)
            }
    }

    // -----------------------------------------------------------------------
    // getOptionalImages
    // -----------------------------------------------------------------------

    @Nested
    inner class GetOptionalImages {
        @Test
        fun `returns favorites when enabled`() =
            runTest {
                every { prefs.favoritesName } returns "Favorites"
                every { prefs.recentName } returns "Recent"
                every { prefs.mediaType } returns ProviderMediaType.VIDEOS_PHOTOS
                every { prefs.isTestConnection } returns false

                val days = listOf(Day(dayId = 20230826))
                val images = listOf(Image(fileId = 1, etag = "e1", baseName = "photo1.jpg"))

                coEvery { api.getDays(any(), fav = 1, vid = null) } returns Response.success(days)
                coEvery { api.getImages(any(), dayIds = "20230826", fav = 1, vid = null) } returns Response.success(images)

                val result = repository.getOptionalImages("Favorites", 10)

                assertEquals(1, result.size)
            }

        @Test
        fun `returns empty for null count`() =
            runTest {
                val result = repository.getOptionalImages("Favorites", null)

                assertTrue(result.isEmpty())
                coVerify(exactly = 0) { api.getDays(any(), any(), any(), any()) }
            }

        @Test
        fun `throws on no days found`() =
            runTest {
                every { prefs.favoritesName } returns "Favorites"
                every { prefs.recentName } returns "Recent"
                every { prefs.mediaType } returns ProviderMediaType.VIDEOS_PHOTOS

                coEvery { api.getDays(any(), fav = 1, vid = null) } returns Response.success(emptyList())

                val result = runCatching { repository.getOptionalImages("Favorites", 10) }
                assertTrue(result.isFailure)
            }

        @Test
        fun `throws on no images found`() =
            runTest {
                every { prefs.favoritesName } returns "Favorites"
                every { prefs.recentName } returns "Recent"
                every { prefs.mediaType } returns ProviderMediaType.VIDEOS_PHOTOS

                val days = listOf(Day(dayId = 20230826))
                coEvery { api.getDays(any(), fav = 1, vid = null) } returns Response.success(days)
                coEvery { api.getImages(any(), dayIds = "20230826", fav = 1, vid = null) } returns
                    Response.success(emptyList())

                val result = runCatching { repository.getOptionalImages("Favorites", 10) }
                assertTrue(result.isFailure)
            }
    }

    // -----------------------------------------------------------------------
    // isTestConnection behavior
    // -----------------------------------------------------------------------

    @Nested
    inner class IsTestConnection {
        @Test
        fun `skips EXIF when test connection is true`() =
            runTest {
                every { prefs.selectedAlbumIds } returns setOf("1")
                every { prefs.isTestConnection } returns true
                val albums = listOf(Album(albumId = 1, clusterId = "c1", name = "Vacation", count = 10))
                val days = listOf(Day(dayId = 20230826))
                val images = listOf(Image(fileId = 1, etag = "e1", baseName = "photo1.jpg"))

                coEvery { api.getAlbumList(any()) } returns Response.success(albums)
                coEvery { api.getDays(any(), clusterId = "c1", vid = any()) } returns Response.success(days)
                coEvery { api.getImages(any(), dayIds = "20230826", clusterId = "c1", vid = any()) } returns
                    Response.success(images)

                val result = repository.getSelectedAlbums()

                assertEquals(1, result.size)
                assertTrue(result[0].exif == null)
                coVerify(exactly = 0) { api.getFullImageInfo(any(), any()) }
            }

        @Test
        fun `fetches EXIF when test connection is false`() =
            runTest {
                every { prefs.selectedAlbumIds } returns setOf("1")
                every { prefs.isTestConnection } returns false
                val albums = listOf(Album(albumId = 1, clusterId = "c1", name = "Vacation", count = 10))
                val days = listOf(Day(dayId = 20230826))
                val images = listOf(Image(fileId = 1, etag = "e1", baseName = "photo1.jpg"))
                val exifImage =
                    Image(
                        fileId = 1,
                        etag = "e1",
                        baseName = "photo1.jpg",
                        exif = ExifInfo(dateTimeEpoch = 1693500000),
                    )

                coEvery { api.getAlbumList(any()) } returns Response.success(albums)
                coEvery { api.getDays(any(), clusterId = "c1", vid = any()) } returns Response.success(days)
                coEvery { api.getImages(any(), dayIds = "20230826", clusterId = "c1", vid = any()) } returns
                    Response.success(images)
                coEvery { api.getFullImageInfo(any(), 1) } returns Response.success(exifImage)

                val result = repository.getSelectedAlbums()

                assertEquals(1, result.size)
                assertEquals(1693500000L, result[0].exif?.dateTimeEpoch)
                coVerify(exactly = 1) { api.getFullImageInfo(any(), 1) }
            }
    }
}
