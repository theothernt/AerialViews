package com.neilturner.aerialviews.models

import com.neilturner.aerialviews.models.videos.AerialMedia
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

class MediaPlaylist(
    initialVideos: List<AerialMedia>,
    startPosition: Int = -1,
    val size: Int = initialVideos.size,
    private var windowOffset: Int = 0,
    private val fetchChunk: (suspend (offset: Int, limit: Int) -> List<AerialMedia>)? = null,
) {
    private var position = startPosition
    private var _hasReachedEnd = false
    var onPositionChanged: ((Int) -> Unit)? = null

    private val windowVideos = initialVideos.toMutableList()
    private val windowLock = Any()
    private val scope = CoroutineScope(Dispatchers.IO)

    val currentPosition: Int get() = position

    fun nextItem(): AerialMedia {
        position = calculateNext(++position)
        onPositionChanged?.invoke(position)
        if (position == 0 && size > 0) _hasReachedEnd = true

        Timber.v("MediaPlaylist: nextItem() -> pos $position / $size (window: ${windowVideos.size})")
        checkAndRefillWindow()

        return getItemAt(position)
    }

    fun previousItem(): AerialMedia {
        position = calculateNext(--position)
        onPositionChanged?.invoke(position)

        Timber.v("MediaPlaylist: previousItem() -> pos $position / $size (window: ${windowVideos.size})")
        checkAndRefillWindow()

        return getItemAt(position)
    }

    private fun checkAndRefillWindow() {
        if (fetchChunk == null) return

        val isOutOfBounds = position < windowOffset || position >= windowOffset + windowVideos.size
        val relativeIndex = position - windowOffset
        val remaining = windowVideos.size - relativeIndex - 1

        val isNearEnd = remaining <= 5 && (windowOffset + windowVideos.size < size)
        val isNearStart = relativeIndex <= 5 && windowOffset > 0

        if (isOutOfBounds || isNearEnd || isNearStart) {
            val newOffset = 0.coerceAtLeast(position - 5)
            val limit = 50

            if (newOffset != windowOffset) {
                if (isOutOfBounds) {
                    refillWindowSync(newOffset)
                } else {
                    Timber.i(
                        "MediaPlaylist: Refilling window. Position: $position, Window: $windowOffset..${windowOffset + windowVideos.size}. New Offset: $newOffset",
                    )
                    scope.launch {
                        val freshData = fetchChunk.invoke(newOffset, limit)
                        synchronized(windowLock) {
                            windowOffset = newOffset
                            windowVideos.clear()
                            windowVideos.addAll(freshData)
                            Timber.d("MediaPlaylist: Window refilled. New range: $windowOffset..${windowOffset + windowVideos.size}")
                        }
                    }
                }
            }
        }
    }

    private fun refillWindowSync(newOffset: Int) {
        if (fetchChunk == null) return
        val limit = 50
        try {
            val freshData = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                fetchChunk.invoke(newOffset, limit)
            }
            synchronized(windowLock) {
                windowOffset = newOffset
                windowVideos.clear()
                windowVideos.addAll(freshData)
                Timber.d("MediaPlaylist: Window refilled synchronously. New range: $windowOffset..${windowOffset + windowVideos.size}")
            }
        } catch (e: Exception) {
            Timber.e(e, "MediaPlaylist: Error refilling window synchronously")
        }
    }

    private fun getItemAt(absoluteIndex: Int): AerialMedia {
        synchronized(windowLock) {
            val relativeIndex = absoluteIndex - windowOffset
            if (relativeIndex in windowVideos.indices) {
                return windowVideos[relativeIndex]
            }
        }

        if (fetchChunk != null) {
            Timber.w("MediaPlaylist: Cache miss at index $absoluteIndex, attempting synchronous refill")
            refillWindowSync(0.coerceAtLeast(absoluteIndex - 5))
            synchronized(windowLock) {
                val relativeIndex = absoluteIndex - windowOffset
                if (relativeIndex in windowVideos.indices) {
                    return windowVideos[relativeIndex]
                }
            }
        }

        Timber.w("MediaPlaylist: Cache miss at index $absoluteIndex, returning first available")

        synchronized(windowLock) {
            return windowVideos.firstOrNull()
                ?: throw IllegalStateException("Playlist is empty")
        }
    }

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
}
