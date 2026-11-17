package com.neilturner.aerialviews.providers

import android.content.Context
import android.content.res.Resources
import android.net.Uri
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.data.storage.FileHelper
import com.neilturner.aerialviews.models.enums.AerialMediaSource
import com.neilturner.aerialviews.models.enums.AerialMediaType
import com.neilturner.aerialviews.models.enums.ProviderMediaType
import com.neilturner.aerialviews.models.enums.ProviderSourceType
import com.neilturner.aerialviews.models.enums.SearchType
import com.neilturner.aerialviews.models.prefs.LocalProviderPreferences
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.utils.filenameWithoutExtension
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

internal class LocalMediaProviderTest {
    @TempDir
    lateinit var tempDir: Path

    private val resources =
        mockk<Resources>().also { res ->
            every { res.getString(R.string.local_videos_legacy_no_volume) } returns "Volume not specified"
            every { res.getString(R.string.local_videos_legacy_no_folder) } returns "Folder not specified"
            every { res.getString(R.string.local_videos_legacy_no_files_found) } returns "No files found"
            every { res.getString(R.string.local_videos_media_store_no_folder) } returns "No folder specified for filter"
            every { res.getString(R.string.local_media_test_summary1) } returns "Files found: %1\$s"
            every { res.getString(R.string.local_media_test_summary2) } returns "Unsupported files: %1\$s"
            every { res.getString(R.string.local_media_test_summary3) } returns "Videos found: %1\$s"
            every { res.getString(R.string.local_media_test_summary4) } returns "Photos found: %1\$s"
            every { res.getString(R.string.local_media_test_summary5) } returns "Excluded by filter: %1\$s"
            every { res.getString(R.string.local_media_test_summary6) } returns "Selected for playback: %1\$s"
        }

    // Deliberately not relaxed: any unstubbed Android call (e.g. a MediaStore query the code
    // should not make) fails the test instead of silently returning an empty stub.
    private val context =
        mockk<Context>().also { ctx ->
            every { ctx.resources } returns resources
        }

    private lateinit var mediaDir: Path

    @BeforeEach
    fun setUp() {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } answers { uriFor(firstArg<String>()) }
        // File.toUri() (used by the music paths) goes through Uri.fromFile(), not Uri.parse().
        every { Uri.fromFile(any()) } answers { uriFor(firstArg<File>().absolutePath) }
        mediaDir = Files.createDirectories(tempDir.resolve("media"))
        listOf("day.mp4", "photo.jpg", "notes.txt", ".hidden.mp4").forEach { Files.writeString(mediaDir.resolve(it), "x") }
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Uri::class)
    }

    private fun uriFor(raw: String): Uri =
        mockk<Uri>(relaxed = true) {
            every { path } returns raw
            // Tests run on Windows too, where File paths use backslashes.
            every { lastPathSegment } returns raw.substringAfterLast('/').substringAfterLast('\\')
        }

    private fun provider(prefs: LocalProviderPreferences = folderPrefs()) = LocalMediaProvider(context, prefs)

    private fun folderPrefs(
        folder: String = "/media",
        volume: String = tempDir.toString(),
        includeVideos: Boolean = true,
        includePhotos: Boolean = true,
        recursive: Boolean = false,
        musicEnabled: Boolean = false,
    ) = FakeLocalPrefs(
        searchType = SearchType.FOLDER_ACCESS,
        legacyVolume = volume,
        legacyFolder = folder,
        legacySearchSubfolders = recursive,
        includeVideos = includeVideos,
        includePhotos = includePhotos,
        musicEnabled = musicEnabled,
    )

    private fun mediaStorePrefs(
        filterEnabled: Boolean = false,
        filterFolder: String = "",
    ) = FakeLocalPrefs(
        searchType = SearchType.MEDIA_STORE,
        filterEnabled = filterEnabled,
        filterFolder = filterFolder,
    )

    private suspend fun withMockedFileHelper(block: suspend () -> Unit) {
        mockkObject(FileHelper)
        try {
            block()
        } finally {
            unmockkObject(FileHelper)
        }
    }

    @Test
    fun `type is local`() {
        assertEquals(ProviderSourceType.LOCAL, provider().type)
    }

    @Test
    fun `enabled follows preferences`() {
        assertTrue(provider(folderPrefs().copy(enabled = true)).enabled)
        assertFalse(provider(folderPrefs().copy(enabled = false)).enabled)
    }

    @Test
    fun `settings hash delegates to preferences`() {
        assertEquals("local-hash", provider().settingsHash())
    }

    @Test
    fun `fetch metadata returns the input list unchanged`() =
        runTest {
            val media = listOf(AerialMedia(Uri.parse("file:///tmp/clip.mp4")))
            assertSame(media, provider().fetchMetadata(media))
        }

    @Test
    fun `fetch music returns nothing when music is disabled`() =
        runTest {
            // searchType MEDIA_STORE would reach contentResolver if the guard were missing.
            assertTrue(provider(mediaStorePrefs().copy(musicEnabled = false)).fetchMusic().isEmpty())
        }

    @Test
    fun `folder access with no volume reports an error`() =
        runTest {
            val success = successFrom(provider(folderPrefs(volume = "")).fetch())
            assertTrue(success.media.isEmpty())
            assertEquals("Volume not specified", success.summary)
        }

    @Test
    fun `folder access with no folder reports an error`() =
        runTest {
            val success = successFrom(provider(folderPrefs(folder = "")).fetch())
            assertTrue(success.media.isEmpty())
            assertEquals("Folder not specified", success.summary)
        }

    @Test
    fun `folder access with no files reports an error`() =
        runTest {
            Files.createDirectories(tempDir.resolve("empty"))
            val success = successFrom(provider(folderPrefs(folder = "/empty")).fetch())
            assertTrue(success.media.isEmpty())
            assertEquals("No files found", success.summary)
        }

    @Test
    fun `folder access selects videos and photos, skipping unsupported and hidden files`() =
        runTest {
            val success = successFrom(provider().fetch())
            assertEquals(2, success.media.size)
            success.media.forEach { assertEquals(AerialMediaSource.LOCAL, it.source) }
            assertEquals(
                setOf(AerialMediaType.VIDEO, AerialMediaType.IMAGE),
                success.media.map { it.type }.toSet(),
            )
            assertTrue(success.summary.contains("Files found: 3"))
            assertTrue(success.summary.contains("Unsupported files: 1"))
            assertTrue(success.summary.contains("Videos found: 1"))
            assertTrue(success.summary.contains("Photos found: 1"))
            assertTrue(success.summary.contains("Selected for playback: 2"))
        }

    @Test
    fun `include videos and include photos control what is selected`() =
        runTest {
            val videosOnly = successFrom(provider(folderPrefs(includePhotos = false)).fetch())
            assertEquals(listOf("day"), videosOnly.media.map { it.uri.filenameWithoutExtension })

            val photosOnly = successFrom(provider(folderPrefs(includeVideos = false)).fetch())
            assertEquals(listOf("photo"), photosOnly.media.map { it.uri.filenameWithoutExtension })
        }

    @Test
    fun `recursive search includes files from subfolders`() =
        runTest {
            Files.createDirectories(mediaDir.resolve("nested"))
            Files.writeString(mediaDir.resolve("nested/night.mp4"), "x")

            val flat = successFrom(provider(folderPrefs()).fetch())
            val recursive = successFrom(provider(folderPrefs(recursive = true)).fetch())

            assertEquals(2, flat.media.size)
            assertEquals(3, recursive.media.size)
            assertTrue(recursive.media.any { it.uri.filenameWithoutExtension == "night" })
        }

    @Test
    fun `media is grouped by type with each group ordered by last modified descending`() =
        runTest {
            Files.createDirectories(mediaDir.resolve("nested"))
            Files.writeString(mediaDir.resolve("nested/night.mp4"), "x")
            Files.writeString(mediaDir.resolve("extra.mp4"), "x")
            mediaDir.resolve("day.mp4").toFile().setLastModified(10_000L)
            mediaDir.resolve("photo.jpg").toFile().setLastModified(20_000L)
            mediaDir.resolve("nested/night.mp4").toFile().setLastModified(30_000L)
            mediaDir.resolve("extra.mp4").toFile().setLastModified(40_000L)

            // The provider selects all videos first, then all photos; files are sorted by
            // last-modified within each of those groups.
            val success = successFrom(provider(folderPrefs(recursive = true)).fetch())
            assertEquals(
                listOf("extra", "night", "day", "photo"),
                success.media.map { it.uri.filenameWithoutExtension },
            )
        }

    @Test
    fun `media store path applies the folder filter`() =
        runTest {
            withMockedFileHelper {
                every { FileHelper.findLocalVideos(context) } returns
                    listOf("/media/keep/match.mp4", "/media/other/skip.mp4")
                every { FileHelper.findLocalImages(context) } returns emptyList()
                // Keep this about the provider honouring the filter, not about FileHelper's
                // platform-specific path-separator handling.
                every { FileHelper.shouldFilter(any(), any()) } answers {
                    firstArg<Uri>().path.toString().contains("skip")
                }

                val success = successFrom(provider(mediaStorePrefs(filterEnabled = true, filterFolder = "/keep")).fetch())
                assertEquals(listOf("match"), success.media.map { it.uri.filenameWithoutExtension })
                assertTrue(success.summary.contains("Excluded by filter: 1"))
            }
        }

    @Test
    fun `media store filter with no folder reports an error`() =
        runTest {
            val success = successFrom(provider(mediaStorePrefs(filterEnabled = true)).fetch())
            assertTrue(success.media.isEmpty())
            assertEquals("No folder specified for filter", success.summary)
        }

    @Test
    fun `folder access music returns only supported audio files`() =
        runTest {
            listOf("song.mp3", "score.flac", ".hidden.mp3").forEach { Files.writeString(mediaDir.resolve(it), "x") }
            val tracks = provider(folderPrefs(musicEnabled = true)).fetchMusic()
            assertEquals(
                setOf("song", "score"),
                tracks.map { it.uri.filenameWithoutExtension }.toSet(),
            )
            assertTrue(tracks.all { it.source == AerialMediaSource.LOCAL })
        }

    private fun successFrom(result: ProviderFetchResult): ProviderFetchResult.Success =
        assertInstanceOf(ProviderFetchResult.Success::class.java, result)

    private class FakeLocalPrefs(
        override val enabled: Boolean = true,
        override var searchType: SearchType? = SearchType.FOLDER_ACCESS,
        override val mediaSelection: Set<String> = emptySet(),
        override val mediaType: ProviderMediaType? = null,
        override val musicEnabled: Boolean = false,
        override val includeVideos: Boolean = true,
        override val includePhotos: Boolean = true,
        override var filterEnabled: Boolean = false,
        override var filterFolder: String = "",
        override var legacyVolumeLabel: String = "Test volume",
        override var legacyVolume: String = "",
        override var legacyFolder: String = "",
        override var legacySearchSubfolders: Boolean = false,
    ) : LocalProviderPreferences {
        override fun settingsHash(): String = "local-hash"

        fun copy(
            enabled: Boolean = this.enabled,
            musicEnabled: Boolean = this.musicEnabled,
        ) = FakeLocalPrefs(
            enabled = enabled,
            searchType = searchType,
            mediaSelection = mediaSelection,
            mediaType = mediaType,
            musicEnabled = musicEnabled,
            includeVideos = includeVideos,
            includePhotos = includePhotos,
            filterEnabled = filterEnabled,
            filterFolder = filterFolder,
            legacyVolumeLabel = legacyVolumeLabel,
            legacyVolume = legacyVolume,
            legacyFolder = legacyFolder,
            legacySearchSubfolders = legacySearchSubfolders,
        )
    }
}
