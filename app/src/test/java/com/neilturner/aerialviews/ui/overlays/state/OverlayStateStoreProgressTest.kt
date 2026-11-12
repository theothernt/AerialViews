package com.neilturner.aerialviews.ui.overlays.state

import com.neilturner.aerialviews.ui.controls.ProgressState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The progress emissions the bar depends on, without Compose or a View.
 *
 * Consecutive photos both post `START(0, duration)`, so the two states either side of an item
 * boundary can be equal. `MutableStateFlow` conflates equal values, which is why the store resets
 * on `resetForNextMedia()`: without that the second item's start would never be emitted and the
 * Compose bar would sit at zero for the rest of the playlist.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OverlayStateStoreProgressTest {
    @Test
    fun `a start after a reset emits again`() =
        runTest {
            val store = OverlayStateStore()
            val progress = mutableListOf<ProgressOverlayState>()

            val collector =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    store.uiState.collect { progress += it.progress }
                }

            store.setProgress(ProgressState.START, position = 0, duration = 5_000)
            store.resetForNextMedia()
            store.setProgress(ProgressState.START, position = 0, duration = 5_000)

            collector.cancel()

            // The first entry is the store's initial value; the three after it are the updates.
            // The two starts are equal, so dropping the reset would collect only one of them.
            assertEquals(3, progress.drop(1).size)
            assertEquals(ProgressOverlayState(ProgressState.START, 0, 5_000), progress[1])
            assertEquals(ProgressOverlayState(), progress[2])
            assertEquals(ProgressOverlayState(ProgressState.START, 0, 5_000), progress[3])
        }

    @Test
    fun `a reset also clears the metadata of the previous item`() =
        runTest {
            val store = OverlayStateStore()
            store.setProgress(ProgressState.START, position = 0, duration = 5_000)

            store.resetForNextMedia()

            assertEquals(ProgressOverlayState(), store.uiState.value.progress)
            assertEquals(emptyMap<Any, Any>(), store.uiState.value.metadata)
        }
}
