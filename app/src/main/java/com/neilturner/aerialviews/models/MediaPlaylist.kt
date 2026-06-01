package com.neilturner.aerialviews.models

import com.neilturner.aerialviews.models.videos.AerialMedia
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

class MediaPlaylist(
    initialVideos: List<AerialMedia>,
    startPosition: Int = -1,
    val size: Int = initialVideos.size,
    windowOffset: Int = 0,
    fetchChunk: (suspend (offset: Int, limit: Int) -> List<AerialMedia>)? = null,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private var position = startPosition
    private val inMemoryVideos = initialVideos
    private val windowedPlaylist = fetchChunk?.let { WindowedPlaylist(initialVideos, windowOffset, it, dispatcher) }

    val currentPosition: Int get() = position

    fun nextItem(): AerialMedia {
        position = calculateNext(++position)

        Timber.v("MediaPlaylist: nextItem() -> pos $position / $size")

        return getItemAt(position)
    }

    fun previousItem(): AerialMedia {
        position = calculateNext(--position)

        Timber.v("MediaPlaylist: previousItem() -> pos $position / $size")

        return getItemAt(position)
    }

    internal fun getItemAt(index: Int): AerialMedia =
        windowedPlaylist?.getItemAt(index)
            ?: inMemoryVideos.getOrNull(index)
            ?: throw IllegalStateException("Playlist is empty")

    private fun calculateNext(number: Int): Int {
        if (size == 0) return 0
        val next =
            if (number < 0) {
                size + number
            } else {
                (number).rem(size)
            }
        return next
    }

    private inner class WindowedPlaylist(
        initialVideos: List<AerialMedia>,
        windowOffset: Int,
        private val fetchChunk: suspend (offset: Int, limit: Int) -> List<AerialMedia>,
        dispatcher: CoroutineDispatcher,
    ) {
        private val windowVideos = ConcurrentHashMap<Int, AerialMedia>()
        private val scope = CoroutineScope(dispatcher)

        @Volatile
        private var currentWindowOffset = windowOffset

        @Volatile
        private var currentWindowSize = initialVideos.size

        init {
            initialVideos.forEachIndexed { index, media ->
                windowVideos[windowOffset + index] = media
            }
        }

        fun getItemAt(absoluteIndex: Int): AerialMedia {
            checkAndRefillWindow()

            val cached = windowVideos[absoluteIndex]
            if (cached != null) {
                return cached
            }

            // Cache miss fallback
            Timber.w("MediaPlaylist: Cache miss at index $absoluteIndex, returning first available")

            return windowVideos.values.firstOrNull()
                ?: throw IllegalStateException("Playlist is empty")
        }

        private fun checkAndRefillWindow() {
            val relativeIndex = position - currentWindowOffset
            val remaining = currentWindowSize - relativeIndex - 1

            // Refill when 5 or fewer items remaining ahead in the window
            if (remaining <= 5 && position + remaining < size - 1) {
                val newOffset = 0.coerceAtLeast(position - 5)

                // Only fetch if the window would actually shift
                if (newOffset != currentWindowOffset) {
                    Timber.i(
                        "MediaPlaylist: Refilling window. Position: $position, Window: $currentWindowOffset..${currentWindowOffset + currentWindowSize}. New Offset: $newOffset",
                    )

                    // Update optimistically to prevent duplicate fetches from subsequent nextItem() calls
                    currentWindowOffset = newOffset

                    scope.launch {
                        val freshData = fetchChunk.invoke(newOffset, WINDOW_LIMIT)
                        updateWindow(newOffset, freshData)

                        Timber.d("MediaPlaylist: Window refilled. New range: $newOffset..${newOffset + freshData.size}")
                    }
                }
            }
        }

        private fun updateWindow(
            newOffset: Int,
            freshData: List<AerialMedia>,
        ) {
            currentWindowOffset = newOffset
            currentWindowSize = freshData.size

            freshData.forEachIndexed { index, media ->
                windowVideos[newOffset + index] = media
            }

            val validKeys = newOffset until (newOffset + freshData.size)
            val keysToRemove = windowVideos.keys.filter { it !in validKeys }
            keysToRemove.forEach { windowVideos.remove(it) }
        }
    }

    private companion object {
        const val WINDOW_LIMIT = 50
    }
}
