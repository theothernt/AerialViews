package com.neilturner.aerialviews.ui.core

import android.net.Uri
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.models.videos.AerialMediaMetadata
import com.neilturner.aerialviews.services.MusicEvent
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class ScreensaverControllerTest {
    @Test
    fun `starts with the first item and advances after a finished item`() =
        runTest {
            val playlist = FakePlaylist(listOf(media("a"), media("b")))
            val controller = ScreensaverController(this)
            controller.start(playlist)
            runCurrent()

            assertEquals(ScreensaverPhase.LOADING, controller.state.value.phase)
            assertEquals(
                "a",
                controller.state.value.media
                    ?.metadata
                    ?.shortDescription,
            )
            assertEquals(0, controller.state.value.playlistPosition)

            controller.onMediaVisible()
            assertTrue(controller.state.value.canSkip)
            assertFalse(controller.state.value.canShowOverlays)
            controller.onOverlaysReady()
            assertTrue(controller.state.value.canShowOverlays)

            controller.onItemFinished()
            val transition = controller.state.value.transition
            assertNotNull(transition)
            assertFalse(controller.state.value.canSkip)

            controller.onTransitionCompleted(requireNotNull(transition).id)
            assertEquals(
                "b",
                controller.state.value.media
                    ?.metadata
                    ?.shortDescription,
            )
            assertEquals(1, controller.state.value.playlistPosition)
            assertEquals(ScreensaverPhase.LOADING, controller.state.value.phase)
            controller.release()
        }

    @Test
    fun `skip previous moves backwards and skip next moves forwards`() =
        runTest {
            val playlist = FakePlaylist(listOf(media("a"), media("b"), media("c")))
            val controller = ScreensaverController(this)
            controller.start(playlist)
            runCurrent()
            controller.onMediaVisible()

            controller.skip(previous = true)
            controller.onTransitionCompleted(
                controller.state.value.transition
                    ?.id,
            )
            assertEquals(
                "c",
                controller.state.value.media
                    ?.metadata
                    ?.shortDescription,
            )

            controller.onMediaVisible()
            controller.skip()
            controller.onTransitionCompleted(
                controller.state.value.transition
                    ?.id,
            )
            assertEquals(
                "a",
                controller.state.value.media
                    ?.metadata
                    ?.shortDescription,
            )
            controller.release()
        }

    @Test
    fun `overlay alternation starts unmirrored and flips for each item`() =
        runTest {
            val playlist = FakePlaylist(listOf(media("a"), media("b"), media("c")))
            val controller = ScreensaverController(this)
            controller.start(playlist)
            runCurrent()

            assertFalse(controller.state.value.alternateOverlays)
            controller.onMediaVisible()
            controller.onItemFinished()
            controller.onTransitionCompleted(
                controller.state.value.transition
                    ?.id,
            )
            assertTrue(controller.state.value.alternateOverlays)

            controller.onMediaVisible()
            controller.onItemFinished()
            controller.onTransitionCompleted(
                controller.state.value.transition
                    ?.id,
            )
            assertFalse(controller.state.value.alternateOverlays)
            controller.release()
        }

    @Test
    fun `finished item loops when loop until skipped is enabled`() =
        runTest {
            val playlist = FakePlaylist(listOf(media("a"), media("b")))
            val controller = ScreensaverController(this, ScreensaverConfig(loopUntilSkipped = true))
            controller.start(playlist)
            runCurrent()
            controller.onMediaVisible()
            val generation = controller.state.value.itemGeneration

            controller.onItemFinished()
            controller.onTransitionCompleted(
                controller.state.value.transition
                    ?.id,
            )

            assertEquals(
                "a",
                controller.state.value.media
                    ?.metadata
                    ?.shortDescription,
            )
            assertEquals(generation + 1, controller.state.value.itemGeneration)
            assertEquals(0, controller.state.value.playlistPosition)
            controller.release()
        }

    @Test
    fun `cache resume delay defers the first item`() =
        runTest {
            val playlist = FakePlaylist(listOf(media("a")))
            val controller =
                ScreensaverController(
                    this,
                    ScreensaverConfig(cacheResumeDelayMs = 1_000),
                )
            controller.start(playlist, fromCache = true)
            runCurrent()

            assertNull(controller.state.value.media)
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(
                "a",
                controller.state.value.media
                    ?.metadata
                    ?.shortDescription,
            )
            controller.release()
        }

    @Test
    fun `error while loading advances after the retry delay`() =
        runTest {
            val playlist = FakePlaylist(listOf(media("a"), media("b")))
            val controller = ScreensaverController(this, ScreensaverConfig(errorRetryDelayMs = 1_000))
            controller.start(playlist)
            runCurrent()

            controller.onPlaybackError()
            assertEquals(ScreensaverError.PLAYBACK_ERROR, controller.state.value.error)
            assertNull(controller.state.value.transition)

            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(
                "b",
                controller.state.value.media
                    ?.metadata
                    ?.shortDescription,
            )
            controller.release()
        }

    @Test
    fun `error while visible requests a fade before advancing`() =
        runTest {
            val playlist = FakePlaylist(listOf(media("a"), media("b")))
            val controller = ScreensaverController(this, ScreensaverConfig(errorRetryDelayMs = 1_000))
            controller.start(playlist)
            runCurrent()
            controller.onMediaVisible()

            controller.onPlaybackError()
            advanceTimeBy(1_000)
            runCurrent()
            val transition = requireNotNull(controller.state.value.transition)
            assertEquals(TransitionReason.PLAYBACK_ERROR, transition.reason)

            controller.onTransitionCompleted(transition.id)
            assertEquals(
                "b",
                controller.state.value.media
                    ?.metadata
                    ?.shortDescription,
            )
            controller.release()
        }

    @Test
    fun `blackout pauses music and leaving it resumes and advances`() =
        runTest {
            val playlist = FakePlaylist(listOf(media("a"), media("b")))
            val music = FakeMusic()
            val controller =
                ScreensaverController(
                    this,
                    ScreensaverConfig(backgroundMusicEnabled = true),
                )
            controller.start(playlist, music = music)
            runCurrent()

            assertEquals(MusicMode.PLAYING, controller.state.value.music.mode)
            assertTrue(controller.state.value.music.forcedVideoMute)

            controller.toggleBlackout()
            assertEquals(ScreensaverPhase.BLACKOUT, controller.state.value.phase)
            assertEquals(BlackOutSource.USER, controller.state.value.blackout.source)
            assertEquals(MusicMode.PAUSED, controller.state.value.music.mode)
            assertEquals(1, music.pauses)

            controller.toggleBlackout()
            assertEquals(ScreensaverPhase.LOADING, controller.state.value.phase)
            assertEquals(
                "b",
                controller.state.value.media
                    ?.metadata
                    ?.shortDescription,
            )
            assertEquals(MusicMode.PLAYING, controller.state.value.music.mode)
            assertEquals(1, music.resumes)
            controller.release()
        }

    @Test
    fun `a scheduled blackout cannot be toggled off by the user`() =
        runTest {
            var currentTime = LocalTime.of(23, 0)
            val playlist = FakePlaylist(listOf(media("a"), media("b")))
            val controller =
                ScreensaverController(
                    this,
                    ScreensaverConfig(
                        scheduledBlackoutEnabled = true,
                        scheduledBlackoutStart = LocalTime.of(22, 0),
                        scheduledBlackoutEnd = LocalTime.of(8, 0),
                    ),
                ) { currentTime }
            controller.start(playlist)
            runCurrent()
            assertEquals(BlackOutSource.SCHEDULED, controller.state.value.blackout.source)

            controller.toggleBlackout()
            assertTrue(controller.state.value.blackout.active)
            assertEquals(BlackOutSource.SCHEDULED, controller.state.value.blackout.source)

            currentTime = LocalTime.of(9, 0)
            advanceTimeBy(15_000)
            runCurrent()
            assertFalse(controller.state.value.blackout.active)
            assertEquals(
                "b",
                controller.state.value.media
                    ?.metadata
                    ?.shortDescription,
            )
            controller.release()
        }

    @Test
    fun `sleep timer enters a blackout`() =
        runTest {
            val playlist = FakePlaylist(listOf(media("a")))
            val controller =
                ScreensaverController(
                    this,
                    ScreensaverConfig(sleepTimerMinutes = 1),
                )
            controller.start(playlist)
            runCurrent()

            advanceTimeBy(60_000)
            runCurrent()
            assertTrue(controller.state.value.blackout.active)
            assertEquals(BlackOutSource.SLEEP_TIMER, controller.state.value.blackout.source)
            controller.release()
        }

    @Test
    fun `empty playlist reports an error and still starts music`() =
        runTest {
            val music = FakeMusic()
            val controller =
                ScreensaverController(
                    this,
                    ScreensaverConfig(backgroundMusicEnabled = true),
                )
            controller.start(FakePlaylist(emptyList()), music = music)
            runCurrent()

            assertEquals(ScreensaverPhase.ERROR, controller.state.value.phase)
            assertEquals(ScreensaverError.EMPTY_PLAYLIST, controller.state.value.error)
            assertEquals(1, music.loads)
            assertEquals(1, music.plays)
            controller.release()
        }

    @Test
    fun `release is idempotent and stops the controller`() =
        runTest {
            val music = FakeMusic()
            val playlist = FakePlaylist(listOf(media("a")))
            val controller = ScreensaverController(this, ScreensaverConfig(backgroundMusicEnabled = true))
            controller.start(playlist, music = music)
            runCurrent()
            controller.onMediaVisible()

            controller.release()
            controller.release()

            assertEquals(ScreensaverPhase.STOPPED, controller.state.value.phase)
            assertEquals(1, music.releases)
            assertNull(controller.state.value.transition)
            controller.onItemFinished()
            assertNull(controller.state.value.transition)
        }

    @Test
    fun `now playing is forwarded to the shared overlay state`() =
        runTest {
            val controller = ScreensaverController(this)
            controller.onNowPlaying(MusicEvent(artist = "Artist", song = "Song"))

            assertEquals("Artist", controller.overlayState.value.nowPlaying.event.artist)
            assertEquals("Song", controller.overlayState.value.nowPlaying.event.song)
            controller.release()
        }

    @Test
    fun `track controls fall back to the external listener`() =
        runTest {
            val external = FakeExternalTrackControl()
            val controller = ScreensaverController(this, externalTrackControl = external)

            controller.nextTrack()
            controller.previousTrack()

            assertEquals(1, external.nextCalls)
            assertEquals(1, external.previousCalls)
            controller.release()
        }

    @Test
    fun `music failure silences the controller but keeps video audio muted`() =
        runTest {
            val music = FakeMusic()
            val controller =
                ScreensaverController(
                    this,
                    ScreensaverConfig(backgroundMusicEnabled = true),
                )
            controller.start(FakePlaylist(listOf(media("a"))), music = music)
            runCurrent()

            controller.onMusicPlaybackFailed()

            assertEquals(MusicMode.FAILED, controller.state.value.music.mode)
            assertTrue(controller.state.value.music.forcedVideoMute)
            assertEquals(1, music.releases)
            controller.release()
        }

    @Test
    fun `overlay animations gate user reveals`() =
        runTest {
            val controller = ScreensaverController(this)
            controller.start(FakePlaylist(listOf(media("a"))))
            runCurrent()
            controller.onMediaVisible()
            controller.onOverlaysReady()
            assertTrue(controller.state.value.canShowOverlays)

            controller.onOverlaysBusy()
            assertFalse(controller.state.value.canShowOverlays)
            controller.onOverlaysReady()
            assertTrue(controller.state.value.canShowOverlays)
            controller.release()
        }

    private fun media(label: String): AerialMedia =
        AerialMedia(
            uri = mockk<Uri>(relaxed = true),
            metadata = AerialMediaMetadata(shortDescription = label),
        )

    private class FakePlaylist(
        private val items: List<AerialMedia>,
    ) : PlaylistNavigator {
        private var position = -1

        override val size: Int get() = items.size
        override val currentPosition: Int get() = position

        override fun nextItem(): AerialMedia? {
            if (items.isEmpty()) return null
            position = if (position == items.lastIndex) 0 else position + 1
            return items[position]
        }

        override fun previousItem(): AerialMedia? {
            if (items.isEmpty()) return null
            position = if (position <= 0) items.lastIndex else position - 1
            return items[position]
        }
    }

    private class FakeMusic : MusicPlayback {
        var loads = 0
        var plays = 0
        var pauses = 0
        var resumes = 0
        var releases = 0
        private var index = 0

        override fun load(startTrackIndex: Int) {
            loads++
            index = startTrackIndex
        }

        override fun play() {
            plays++
        }

        override fun pause() {
            pauses++
        }

        override fun resume() {
            resumes++
        }

        override fun nextTrack() {
            index = (index + 1) % 3
        }

        override fun previousTrack() {
            index = if (index == 0) 2 else index - 1
        }

        override fun release() {
            releases++
        }

        override fun hasMusic(): Boolean = true

        override fun currentTrackIndex(): Int = index
    }

    private class FakeExternalTrackControl : ExternalTrackControl {
        var nextCalls = 0
        var previousCalls = 0

        override fun nextTrack() {
            nextCalls++
        }

        override fun previousTrack() {
            previousCalls++
        }
    }
}
