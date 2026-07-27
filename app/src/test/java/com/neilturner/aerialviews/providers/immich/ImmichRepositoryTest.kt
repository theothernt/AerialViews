package com.neilturner.aerialviews.providers.immich

import com.neilturner.aerialviews.models.enums.ProviderMediaType
import com.neilturner.aerialviews.models.prefs.ImmichRepositoryPrefs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import java.net.SocketTimeoutException
import okhttp3.ResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import retrofit2.Response

internal class ImmichRepositoryTest {
    private lateinit var api: ImmichApi
    private lateinit var prefs: ImmichRepositoryPrefs
    private lateinit var urlBuilder: ImmichUrlBuilder
    private lateinit var repository: ImmichRepository

    @BeforeEach
    fun setUp() {
        api = mockk(relaxed = true)
        prefs = mockk(relaxed = true)
        urlBuilder = mockk(relaxed = true)
        repository = ImmichRepository(prefs, urlBuilder, apiOverride = api)
        repository.server = "http://test-server.com"
    }

    private fun <T> errorResponse(code: Int): Response<T> =
        Response.error(code, mockk<ResponseBody>(relaxed = true))

    private fun serverVersionResponse(major: Int): ServerVersionResponse =
        ServerVersionResponse(major = major, minor = 0, patch = 0)

    // -----------------------------------------------------------------------
    // Server version detection
    // -----------------------------------------------------------------------

    @Nested
    inner class GetServerVersion {
        @Test
        fun `returns major version from API`() = runTest {
            coEvery { api.getServerVersion() } returns Response.success(serverVersionResponse(3))

            val result = repository.getServerVersion()

            assertEquals(3, result)
        }

        @Test
        fun `caches result after first call`() = runTest {
            coEvery { api.getServerVersion() } returns Response.success(serverVersionResponse(3))

            repository.getServerVersion()
            repository.getServerVersion()

            coVerify(exactly = 1) { api.getServerVersion() }
        }

        @Test
        fun `falls back to v2 on API error`() = runTest {
            coEvery { api.getServerVersion() } returns errorResponse(404)

            val result = repository.getServerVersion()

            assertEquals(2, result)
        }

        @Test
        fun `falls back to v2 on network exception`() = runTest {
            coEvery { api.getServerVersion() } throws RuntimeException("network error")

            val result = repository.getServerVersion()

            assertEquals(2, result)
        }
    }

    // -----------------------------------------------------------------------
    // Shared album fetching
    // -----------------------------------------------------------------------

    @Nested
    inner class GetSharedAlbumFromAPI {
        @Test
        fun `returns INDIVIDUAL type shared link assets`() = runTest {

            every { prefs.pathName } returns "share/12345"
            every { prefs.password } returns ""
            coEvery { api.getServerVersion() } returns Response.success(serverVersionResponse(2))

            val sharedResponse = SharedLinkResponse(
                id = "shared-1",
                key = "resolved-key",
                type = "INDIVIDUAL",
                description = "My Shared Photos",
                assets = listOf(
                    Asset(id = "a1", type = "IMAGE", originalPath = "/photo1.jpg"),
                    Asset(id = "a2", type = "VIDEO", originalPath = "/video1.mp4"),
                ),
            )
            coEvery { api.getSharedAlbum(key = "12345", slug = null, password = null) } returns Response.success(sharedResponse)

            val result = repository.getSharedAlbumFromAPI()

            assertEquals("shared-shared-1", result.id)
            assertEquals("My Shared Photos", result.name)
            assertEquals(2, result.assets.size)
            verify { urlBuilder.setResolvedSharedKey("resolved-key") }
        }

        @Test
        fun `returns ALBUM type with fetched album details`() = runTest {

            every { prefs.pathName } returns "share/12345"
            every { prefs.password } returns ""
            coEvery { api.getServerVersion() } returns Response.success(serverVersionResponse(2))

            val sharedResponse = SharedLinkResponse(
                id = "shared-1",
                key = "resolved-key",
                type = "ALBUM",
                description = "Shared Album",
                album = Album(id = "album-1", name = "Vacation"),
                assets = emptyList(),
            )
            val albumResponse = Album(
                id = "album-1",
                name = "Vacation",
                assets = listOf(
                    Asset(id = "a1", type = "IMAGE", originalPath = "/photo1.jpg"),
                ),
            )

            coEvery { api.getSharedAlbum(key = "12345", slug = null, password = null) } returns Response.success(sharedResponse)
            coEvery { api.getSharedAlbumById(albumId = "album-1", key = "resolved-key", password = null) } returns Response.success(albumResponse)

            val result = repository.getSharedAlbumFromAPI()

            assertEquals("album-1", result.id)
            assertEquals("Vacation", result.name)
            assertEquals(1, result.assets.size)
            assertEquals("Vacation", result.assets[0].albumName)
        }

        @Test
        fun `handles ALBUM type with null album`() = runTest {

            every { prefs.pathName } returns "share/12345"
            every { prefs.password } returns ""
            coEvery { api.getServerVersion() } returns Response.success(serverVersionResponse(2))

            val sharedResponse = SharedLinkResponse(
                id = "shared-1",
                key = "resolved-key",
                type = "ALBUM",
                description = "Shared Album",
                album = null,
                assets = emptyList(),
            )

            coEvery { api.getSharedAlbum(key = "12345", slug = null, password = null) } returns Response.success(sharedResponse)

            val result = repository.getSharedAlbumFromAPI()

            assertEquals("shared-shared-1", result.id)
            assertEquals("Shared Album", result.name)
            assertEquals("Album information not available", result.description)
            assertTrue(result.assets.isEmpty())
        }

        @Test
        fun `handles unknown type with fallback`() = runTest {

            every { prefs.pathName } returns "share/12345"
            every { prefs.password } returns ""
            coEvery { api.getServerVersion() } returns Response.success(serverVersionResponse(2))

            val sharedResponse = SharedLinkResponse(
                id = "shared-1",
                key = "resolved-key",
                type = "UNKNOWN",
                description = "Fallback Album",
                assets = listOf(
                    Asset(id = "a1", type = "IMAGE", originalPath = "/photo1.jpg"),
                ),
            )

            coEvery { api.getSharedAlbum(key = "12345", slug = null, password = null) } returns Response.success(sharedResponse)

            val result = repository.getSharedAlbumFromAPI()

            assertEquals("shared-shared-1", result.id)
            assertEquals("Fallback Album", result.name)
            assertEquals(1, result.assets.size)
        }

        @Test
        fun `throws exception on API error`() = runTest {

            every { prefs.pathName } returns "share/12345"
            every { prefs.password } returns ""
            coEvery { api.getServerVersion() } returns Response.success(serverVersionResponse(2))

            coEvery { api.getSharedAlbum(key = "12345", slug = null, password = null) } returns errorResponse(404)

            assertThrows<Exception> {
                repository.getSharedAlbumFromAPI()
            }
        }

        @Test
        fun `handles slug format`() = runTest {

            every { prefs.pathName } returns "/s/my-slug/"
            every { prefs.password } returns ""
            coEvery { api.getServerVersion() } returns Response.success(serverVersionResponse(2))

            val sharedResponse = SharedLinkResponse(
                id = "shared-1",
                key = "resolved-key",
                type = "INDIVIDUAL",
                assets = listOf(Asset(id = "a1")),
            )

            coEvery { api.getSharedAlbum(key = null, slug = "my-slug", password = null) } returns Response.success(sharedResponse)

            val result = repository.getSharedAlbumFromAPI()

            assertEquals(1, result.assets.size)
        }

        // v3 shared link tests

        @Test
        fun `v3 - uses getSharedAlbumV3 (no password param)`() = runTest {
            every { prefs.pathName } returns "share/12345"
            every { prefs.password } returns "secret"
            coEvery { api.getServerVersion() } returns Response.success(serverVersionResponse(3))

            val sharedResponse = SharedLinkResponse(
                id = "shared-1",
                key = "resolved-key",
                type = "INDIVIDUAL",
                assets = listOf(Asset(id = "a1", type = "IMAGE", originalPath = "/photo1.jpg")),
            )
            coEvery { api.getSharedAlbumV3(key = "12345", slug = null) } returns Response.success(sharedResponse)

            val result = repository.getSharedAlbumFromAPI()

            assertEquals(1, result.assets.size)
            // Ensure the v2 password-bearing endpoint was NOT called
            coVerify(exactly = 0) { api.getSharedAlbum(any(), any(), any()) }
        }

        @Test
        fun `v3 - fetches ALBUM assets via search metadata`() = runTest {
            every { prefs.pathName } returns "share/12345"
            every { prefs.password } returns ""
            every { prefs.apiKey } returns ""
            every { prefs.mediaType } returns ProviderMediaType.VIDEOS_PHOTOS
            coEvery { api.getServerVersion() } returns Response.success(serverVersionResponse(3))

            val sharedResponse = SharedLinkResponse(
                id = "shared-1",
                key = "resolved-key",
                type = "ALBUM",
                description = "Shared Album v3",
                album = Album(id = "album-1", name = "Vacation"),
                assets = emptyList(),
            )
            val albumMeta = Album(id = "album-1", name = "Vacation", assetCount = 2)
            val searchAssets = listOf(
                Asset(id = "a1", type = "IMAGE", originalPath = "/photo1.jpg"),
                Asset(id = "a2", type = "IMAGE", originalPath = "/photo2.jpg"),
            )
            val searchResponse = SearchAssetsResponse(assets = AssetsResult(items = searchAssets))

            coEvery { api.getSharedAlbumV3(key = "12345", slug = null) } returns Response.success(sharedResponse)
            coEvery { api.getSharedAlbumByIdV3(albumId = "album-1", key = "resolved-key") } returns Response.success(albumMeta)
            coEvery {
                api.getSharedAlbumAssets(
                    key = "resolved-key",
                    searchRequest = match { it.albumIds == listOf("album-1") },
                )
            } returns Response.success(searchResponse)

            val result = repository.getSharedAlbumFromAPI()

            assertEquals("album-1", result.id)
            assertEquals("Vacation", result.name)
            assertEquals(2, result.assets.size)
        }
    }

    // -----------------------------------------------------------------------
    // Selected album fetching
    // -----------------------------------------------------------------------

    @Nested
    inner class GetSelectedAlbumFromAPI {
        @Test
        fun `fetches and combines multiple albums (v2)`() = runTest {

            every { prefs.selectedAlbumIds } returns mutableSetOf("album-1", "album-2")
            every { prefs.apiKey } returns "test-api-key"
            coEvery { api.getServerVersion() } returns Response.success(serverVersionResponse(2))

            val album1 = Album(
                id = "album-1",
                name = "Album One",
                assets = listOf(Asset(id = "a1", type = "IMAGE", originalPath = "/photo1.jpg")),
            )
            val album2 = Album(
                id = "album-2",
                name = "Album Two",
                assets = listOf(Asset(id = "a2", type = "VIDEO", originalPath = "/video1.mp4")),
            )

            coEvery { api.getAlbum(apiKey = "test-api-key", albumId = "album-1") } returns Response.success(album1)
            coEvery { api.getAlbum(apiKey = "test-api-key", albumId = "album-2") } returns Response.success(album2)

            val result = repository.getSelectedAlbumFromAPI()

            assertEquals("combined", result.id)
            assertEquals(2, result.assets.size)
            assertTrue(result.name.contains("Album One"))
            assertTrue(result.name.contains("Album Two"))
        }

        @Test
        fun `deduplicates assets across albums (v2)`() = runTest {

            every { prefs.selectedAlbumIds } returns mutableSetOf("album-1", "album-2")
            every { prefs.apiKey } returns "test-api-key"
            coEvery { api.getServerVersion() } returns Response.success(serverVersionResponse(2))

            val sharedAsset = Asset(id = "a1", type = "IMAGE", originalPath = "/photo1.jpg")
            val album1 = Album(id = "album-1", name = "Album One", assets = listOf(sharedAsset))
            val album2 = Album(id = "album-2", name = "Album Two", assets = listOf(sharedAsset))

            coEvery { api.getAlbum(apiKey = "test-api-key", albumId = "album-1") } returns Response.success(album1)
            coEvery { api.getAlbum(apiKey = "test-api-key", albumId = "album-2") } returns Response.success(album2)

            val result = repository.getSelectedAlbumFromAPI()

            assertEquals(1, result.assets.size)
        }

        @Test
        fun `returns empty album when no albums selected`() = runTest {

            every { prefs.selectedAlbumIds } returns mutableSetOf<String>()
            coEvery { api.getServerVersion() } returns Response.success(serverVersionResponse(2))

            val result = repository.getSelectedAlbumFromAPI()

            assertEquals("combined", result.id)
            assertEquals("No albums selected", result.description)
        }

        @Test
        fun `throws exception when no assets found (v2)`() = runTest {

            every { prefs.selectedAlbumIds } returns mutableSetOf("album-1")
            every { prefs.apiKey } returns "test-api-key"
            coEvery { api.getServerVersion() } returns Response.success(serverVersionResponse(2))

            val emptyAlbum = Album(id = "album-1", name = "Empty", assets = emptyList())
            coEvery { api.getAlbum(apiKey = "test-api-key", albumId = "album-1") } returns Response.success(emptyAlbum)

            assertThrows<Exception> {
                repository.getSelectedAlbumFromAPI()
            }
        }

        @Test
        fun `continues with other albums when one fails (v2)`() = runTest {

            every { prefs.selectedAlbumIds } returns mutableSetOf("album-1", "album-2")
            every { prefs.apiKey } returns "test-api-key"
            coEvery { api.getServerVersion() } returns Response.success(serverVersionResponse(2))

            val album1 = Album(id = "album-1", name = "Album One", assets = listOf(Asset(id = "a1")))
            coEvery { api.getAlbum(apiKey = "test-api-key", albumId = "album-1") } returns Response.success(album1)
            coEvery { api.getAlbum(apiKey = "test-api-key", albumId = "album-2") } returns errorResponse(500)

            val result = repository.getSelectedAlbumFromAPI()

            assertEquals(1, result.assets.size)
        }

        @Test
        fun `v3 - fetches album assets via search metadata`() = runTest {
            every { prefs.selectedAlbumIds } returns mutableSetOf("album-1")
            every { prefs.apiKey } returns "test-api-key"
            every { prefs.mediaType } returns ProviderMediaType.VIDEOS_PHOTOS
            coEvery { api.getServerVersion() } returns Response.success(serverVersionResponse(3))

            val albumMeta = Album(id = "album-1", name = "My Album", assetCount = 2)
            val searchAssets = listOf(
                Asset(id = "a1", type = "IMAGE", originalPath = "/photo1.jpg"),
                Asset(id = "a2", type = "IMAGE", originalPath = "/photo2.jpg"),
            )
            val searchResponse = SearchAssetsResponse(assets = AssetsResult(items = searchAssets))

            coEvery { api.getAlbum(apiKey = "test-api-key", albumId = "album-1") } returns Response.success(albumMeta)
            coEvery {
                api.getAlbumAssets(
                    apiKey = "test-api-key",
                    searchRequest = match { it.albumIds == listOf("album-1") },
                )
            } returns Response.success(searchResponse)

            val result = repository.getSelectedAlbumFromAPI()

            assertEquals("combined", result.id)
            assertEquals(2, result.assets.size)
            assertEquals("My Album", result.assets[0].albumName)
        }

        @Test
        fun `v3 - paginates album asset fetching`() = runTest {
            every { prefs.selectedAlbumIds } returns mutableSetOf("album-1")
            every { prefs.apiKey } returns "test-api-key"
            every { prefs.mediaType } returns ProviderMediaType.VIDEOS_PHOTOS
            coEvery { api.getServerVersion() } returns Response.success(serverVersionResponse(3))

            val albumMeta = Album(id = "album-1", name = "Big Album", assetCount = 600)

            // Page 1: full page (500 items)
            val page1Assets = (1..500).map { Asset(id = "a$it", type = "IMAGE", originalPath = "/photo$it.jpg") }
            val page1Response = SearchAssetsResponse(assets = AssetsResult(items = page1Assets))

            // Page 2: partial page (100 items) — signals end
            val page2Assets = (501..600).map { Asset(id = "a$it", type = "IMAGE", originalPath = "/photo$it.jpg") }
            val page2Response = SearchAssetsResponse(assets = AssetsResult(items = page2Assets))

            coEvery { api.getAlbum(apiKey = "test-api-key", albumId = "album-1") } returns Response.success(albumMeta)
            coEvery {
                api.getAlbumAssets(
                    apiKey = "test-api-key",
                    searchRequest = match { it.page == 1 && it.albumIds == listOf("album-1") },
                )
            } returns Response.success(page1Response)
            coEvery {
                api.getAlbumAssets(
                    apiKey = "test-api-key",
                    searchRequest = match { it.page == 2 && it.albumIds == listOf("album-1") },
                )
            } returns Response.success(page2Response)

            val result = repository.getSelectedAlbumFromAPI()

            assertEquals(600, result.assets.size)
        }
    }

    // -----------------------------------------------------------------------
    // Favorite assets
    // -----------------------------------------------------------------------

    @Nested
    inner class GetFavoriteAssetsFromAPI {
        @Test
        fun `fetches favorites with count limit`() = runTest {

            every { prefs.includeFavorites } returns "10"
            every { prefs.apiKey } returns "test-api-key"
            every { prefs.mediaType } returns ProviderMediaType.VIDEOS_PHOTOS

            val assets = (1..20).map { Asset(id = "a$it", type = "IMAGE", originalPath = "/photo$it.jpg") }
            val searchResponse = SearchAssetsResponse(assets = AssetsResult(items = assets))

            coEvery { api.getFavoriteAssets(apiKey = "test-api-key", searchRequest = any()) } returns Response.success(searchResponse)

            val result = repository.getFavoriteAssetsFromAPI()

            assertEquals(10, result.size)
        }

        @Test
        fun `returns empty list when count is empty`() = runTest {

            every { prefs.includeFavorites } returns ""

            val result = repository.getFavoriteAssetsFromAPI()

            assertTrue(result.isEmpty())
        }

        @Test
        fun `throws exception on API error`() = runTest {

            every { prefs.includeFavorites } returns "10"
            every { prefs.apiKey } returns "test-api-key"
            every { prefs.mediaType } returns ProviderMediaType.VIDEOS_PHOTOS

            coEvery { api.getFavoriteAssets(apiKey = "test-api-key", searchRequest = any()) } returns errorResponse(500)

            assertThrows<Exception> {
                repository.getFavoriteAssetsFromAPI()
            }
        }
    }

    // -----------------------------------------------------------------------
    // Rated assets
    // -----------------------------------------------------------------------

    @Nested
    inner class GetRatedAssetsFromAPI {
        @Test
        fun `fetches assets for multiple ratings`() = runTest {

            every { prefs.includeRatings } returns mutableSetOf("3", "4", "5")
            every { prefs.apiKey } returns "test-api-key"
            every { prefs.mediaType } returns ProviderMediaType.VIDEOS_PHOTOS

            val assets3 = listOf(Asset(id = "a1", type = "IMAGE", originalPath = "/photo1.jpg"))
            val assets4 = listOf(Asset(id = "a2", type = "VIDEO", originalPath = "/video1.mp4"))
            val assets5 = listOf(Asset(id = "a3", type = "IMAGE", originalPath = "/photo2.jpg"))

            coEvery { api.getFavoriteAssets(apiKey = "test-api-key", searchRequest = match { it.rating == 3 }) } returns
                Response.success(SearchAssetsResponse(AssetsResult(assets3)))
            coEvery { api.getFavoriteAssets(apiKey = "test-api-key", searchRequest = match { it.rating == 4 }) } returns
                Response.success(SearchAssetsResponse(AssetsResult(assets4)))
            coEvery { api.getFavoriteAssets(apiKey = "test-api-key", searchRequest = match { it.rating == 5 }) } returns
                Response.success(SearchAssetsResponse(AssetsResult(assets5)))

            val result = repository.getRatedAssetsFromAPI()

            assertEquals(3, result.size)
        }

        @Test
        fun `returns empty list when no ratings configured`() = runTest {

            every { prefs.includeRatings } returns mutableSetOf<String>()

            val result = repository.getRatedAssetsFromAPI()

            assertTrue(result.isEmpty())
        }

        @Test
        fun `continues when one rating fails`() = runTest {

            every { prefs.includeRatings } returns mutableSetOf("3", "4")
            every { prefs.apiKey } returns "test-api-key"
            every { prefs.mediaType } returns ProviderMediaType.VIDEOS_PHOTOS

            coEvery { api.getFavoriteAssets(apiKey = "test-api-key", searchRequest = match { it.rating == 3 }) } returns
                errorResponse(500)
            coEvery { api.getFavoriteAssets(apiKey = "test-api-key", searchRequest = match { it.rating == 4 }) } returns
                Response.success(SearchAssetsResponse(AssetsResult(listOf(Asset(id = "a1")))))

            val result = repository.getRatedAssetsFromAPI()

            assertEquals(1, result.size)
        }
    }

    // -----------------------------------------------------------------------
    // Random assets
    // -----------------------------------------------------------------------

    @Nested
    inner class GetRandomAssetsFromAPI {
        @Test
        fun `fetches random assets with count`() = runTest {

            every { prefs.includeRandom } returns "5"
            every { prefs.apiKey } returns "test-api-key"
            every { prefs.mediaType } returns ProviderMediaType.VIDEOS_PHOTOS

            val assets = (1..5).map { Asset(id = "a$it", type = "IMAGE", originalPath = "/photo$it.jpg") }

            coEvery { api.getRandomAssets(apiKey = "test-api-key", searchRequest = any()) } returns Response.success(assets)

            val result = repository.getRandomAssetsFromAPI()

            assertEquals(5, result.size)
        }

        @Test
        fun `returns empty list when count is empty`() = runTest {

            every { prefs.includeRandom } returns ""

            val result = repository.getRandomAssetsFromAPI()

            assertTrue(result.isEmpty())
        }

        @Test
        fun `throws exception on API error`() = runTest {

            every { prefs.includeRandom } returns "5"
            every { prefs.apiKey } returns "test-api-key"
            every { prefs.mediaType } returns ProviderMediaType.VIDEOS_PHOTOS

            coEvery { api.getRandomAssets(apiKey = "test-api-key", searchRequest = any()) } returns errorResponse(500)

            assertThrows<Exception> {
                repository.getRandomAssetsFromAPI()
            }
        }
    }

    // -----------------------------------------------------------------------
    // Recent assets
    // -----------------------------------------------------------------------

    @Nested
    inner class GetRecentAssetsFromAPI {
        @Test
        fun `fetches recent assets with count`() = runTest {

            every { prefs.includeRecent } returns "10"
            every { prefs.apiKey } returns "test-api-key"
            every { prefs.mediaType } returns ProviderMediaType.VIDEOS_PHOTOS

            val assets = (1..10).map { Asset(id = "a$it", type = "IMAGE", originalPath = "/photo$it.jpg") }
            val searchResponse = SearchAssetsResponse(assets = AssetsResult(items = assets))

            coEvery { api.getRecentAssets(apiKey = "test-api-key", searchRequest = any()) } returns Response.success(searchResponse)

            val result = repository.getRecentAssetsFromAPI()

            assertEquals(10, result.size)
        }

        @Test
        fun `returns empty list when count is empty`() = runTest {

            every { prefs.includeRecent } returns ""

            val result = repository.getRecentAssetsFromAPI()

            assertTrue(result.isEmpty())
        }

        @Test
        fun `throws exception on API error`() = runTest {

            every { prefs.includeRecent } returns "10"
            every { prefs.apiKey } returns "test-api-key"
            every { prefs.mediaType } returns ProviderMediaType.VIDEOS_PHOTOS

            coEvery { api.getRecentAssets(apiKey = "test-api-key", searchRequest = any()) } returns errorResponse(500)

            assertThrows<Exception> {
                repository.getRecentAssetsFromAPI()
            }
        }
    }

    // -----------------------------------------------------------------------
    // Album listing (fetchAlbums)
    // -----------------------------------------------------------------------

    @Nested
    inner class FetchAlbums {
        @Test
        fun `fetches and combines regular and shared albums (v2)`() = runTest {

            every { prefs.apiKey } returns "test-api-key"
            coEvery { api.getServerVersion() } returns Response.success(serverVersionResponse(2))

            val regularAlbums = listOf(Album(id = "r1", name = "Regular 1"))
            val sharedAlbums = listOf(Album(id = "s1", name = "Shared 1"))

            coEvery { api.getAlbums(apiKey = "test-api-key") } returns Response.success(regularAlbums)
            coEvery { api.getAlbums(apiKey = "test-api-key", shared = true) } returns Response.success(sharedAlbums)

            val result = repository.fetchAlbums()

            assertTrue(result.isSuccess)
            assertEquals(2, result.getOrNull()?.size)
        }

        @Test
        fun `deduplicates albums by ID (v2)`() = runTest {

            every { prefs.apiKey } returns "test-api-key"
            coEvery { api.getServerVersion() } returns Response.success(serverVersionResponse(2))

            val album = Album(id = "a1", name = "Album 1")
            coEvery { api.getAlbums(apiKey = "test-api-key") } returns Response.success(listOf(album))
            coEvery { api.getAlbums(apiKey = "test-api-key", shared = true) } returns Response.success(listOf(album))

            val result = repository.fetchAlbums()

            assertTrue(result.isSuccess)
            assertEquals(1, result.getOrNull()?.size)
        }

        @Test
        fun `returns failure when regular albums fetch fails (v2)`() = runTest {

            every { prefs.apiKey } returns "test-api-key"
            coEvery { api.getServerVersion() } returns Response.success(serverVersionResponse(2))

            coEvery { api.getAlbums(apiKey = "test-api-key") } returns errorResponse(403)

            val result = repository.fetchAlbums()

            assertTrue(result.isFailure)
        }

        @Test
        fun `continues when shared albums fetch fails (v2)`() = runTest {

            every { prefs.apiKey } returns "test-api-key"
            coEvery { api.getServerVersion() } returns Response.success(serverVersionResponse(2))

            val regularAlbums = listOf(Album(id = "r1", name = "Regular 1"))
            coEvery { api.getAlbums(apiKey = "test-api-key") } returns Response.success(regularAlbums)
            coEvery { api.getAlbums(apiKey = "test-api-key", shared = true) } returns errorResponse(500)

            val result = repository.fetchAlbums()

            assertTrue(result.isSuccess)
            assertEquals(1, result.getOrNull()?.size)
        }

        @Test
        fun `v3 - uses isShared param instead of shared`() = runTest {
            every { prefs.apiKey } returns "test-api-key"
            coEvery { api.getServerVersion() } returns Response.success(serverVersionResponse(3))

            val regularAlbums = listOf(Album(id = "r1", name = "Regular 1"))
            val sharedAlbums = listOf(Album(id = "s1", name = "Shared 1"))

            coEvery { api.getAlbumsV3(apiKey = "test-api-key") } returns Response.success(regularAlbums)
            coEvery { api.getAlbumsV3(apiKey = "test-api-key", isShared = true) } returns Response.success(sharedAlbums)

            val result = repository.fetchAlbums()

            assertTrue(result.isSuccess)
            assertEquals(2, result.getOrNull()?.size)
            // Ensure v2 endpoint was NOT called
            coVerify(exactly = 0) { api.getAlbums(any(), any()) }
        }

        @Test
        fun `v3 - returns failure when regular albums fetch fails`() = runTest {
            every { prefs.apiKey } returns "test-api-key"
            coEvery { api.getServerVersion() } returns Response.success(serverVersionResponse(3))

            coEvery { api.getAlbumsV3(apiKey = "test-api-key") } returns errorResponse(403)

            val result = repository.fetchAlbums()

            assertTrue(result.isFailure)
        }

        @Test
        fun `network timeout escapes coroutineScope - requires Fragment handler`() = runTest {

            every { prefs.apiKey } returns "test-api-key"
            coEvery { api.getServerVersion() } returns Response.success(serverVersionResponse(2))

            coEvery { api.getAlbums(apiKey = "test-api-key") } throws SocketTimeoutException("timed out")
            coEvery { api.getAlbums(apiKey = "test-api-key", shared = true) } throws SocketTimeoutException("timed out")

            // When async blocks throw in coroutineScope, the exception escapes the
            // inner try-catch. This is why ImmichVideosFragment needs a
            // CoroutineExceptionHandler on its lifecycleScope.launch calls.
            assertThrows<SocketTimeoutException> {
                repository.fetchAlbums()
            }
        }

        @Test
        fun `trims whitespace from API key before calling API`() = runTest {
            every { prefs.apiKey } returns "test-api-key\n"
            coEvery { api.getServerVersion() } returns Response.success(serverVersionResponse(2))

            val albums = listOf(Album(id = "a1", name = "Album 1"))
            coEvery { api.getAlbums(apiKey = "test-api-key") } returns Response.success(albums)
            coEvery { api.getAlbums(apiKey = "test-api-key", shared = true) } returns Response.success(emptyList())

            val result = repository.fetchAlbums()

            assertTrue(result.isSuccess)
            coVerify { api.getAlbums(apiKey = "test-api-key") }
        }
    }
}
