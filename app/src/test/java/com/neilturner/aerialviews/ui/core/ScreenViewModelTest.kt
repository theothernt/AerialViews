package com.neilturner.aerialviews.ui.core

import android.content.Context
import com.neilturner.aerialviews.data.PlaylistCacheRepository
import com.neilturner.aerialviews.models.MediaFetchResult
import com.neilturner.aerialviews.models.MediaPlaylist
import com.neilturner.aerialviews.services.MediaService
import com.neilturner.aerialviews.services.NowPlayingService
import com.neilturner.aerialviews.services.weather.WeatherService
import com.chibatching.kotpref.Kotpref
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.utils.PermissionHelper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScreenViewModelTest {
    private val context = mockk<Context>(relaxed = true)
    private val mediaService = mockk<MediaService>(relaxed = true)
    private val nowPlayingService = mockk<NowPlayingService>(relaxed = true)
    private val weatherService = mockk<WeatherService>(relaxed = true)
    private val cacheRepository = mockk<PlaylistCacheRepository>(relaxed = true)
    
    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setup() {
        Kotpref.init(context)
        mockkObject(GeneralPrefs)
        mockkObject(PermissionHelper)
        every { PermissionHelper.hasNotificationListenerPermission(any()) } returns false
        every { GeneralPrefs.alternateTextPosition } returns true
        every { GeneralPrefs.playlistCache } returns false
        every { GeneralPrefs.sleepTimer } returns "0"
        every { GeneralPrefs.messageApiEnabled } returns false
        every { GeneralPrefs.playsBackgroundMusic } returns false
        
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(GeneralPrefs)
        unmockkObject(PermissionHelper)
        Dispatchers.resetMain()
    }

    @Test
    fun `initialization loads playlist and sets isPlaylistLoaded`() = runTest {
        val mockMedia = mockk<com.neilturner.aerialviews.models.videos.AerialMedia>(relaxed = true)
        val mockPlaylist = MediaPlaylist(listOf(mockMedia))
        val loadingResult = MediaFetchResult(mockPlaylist, null, 0)
        
        coEvery { mediaService.fetchMedia(any()) } returns loadingResult
        
        val viewModel = ScreenViewModel(context, mediaService, nowPlayingService, weatherService, cacheRepository)
        
        advanceUntilIdle()
        
        assertTrue(viewModel.uiState.value.isPlaylistLoaded)
        assertEquals(mockMedia, viewModel.uiState.value.currentMedia)
    }

    @Test
    fun `togglePause updates state`() = runTest {
        val viewModel = ScreenViewModel(context, mediaService, nowPlayingService, weatherService, cacheRepository)
        
        viewModel.togglePause()
        assertTrue(viewModel.uiState.value.isPaused)
        
        viewModel.togglePause()
        assertTrue(!viewModel.uiState.value.isPaused)
    }

    @Test
    fun `toggleBlackOutMode updates state`() = runTest {
        val mockMedia = mockk<com.neilturner.aerialviews.models.videos.AerialMedia>(relaxed = true)
        val mockPlaylist = MediaPlaylist(listOf(mockMedia))
        coEvery { mediaService.fetchMedia(any()) } returns MediaFetchResult(mockPlaylist, null, 0)
        
        val viewModel = ScreenViewModel(context, mediaService, nowPlayingService, weatherService, cacheRepository)
        advanceUntilIdle()
        
        viewModel.toggleBlackOutMode()
        assertTrue(viewModel.uiState.value.blackOutMode)
        
        viewModel.toggleBlackOutMode()
        assertTrue(!viewModel.uiState.value.blackOutMode)
    }
}
