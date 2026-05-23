package com.neilturner.aerialviews.models

import com.neilturner.aerialviews.models.videos.AerialMedia
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
    private val fetchChunk: (suspend (offset: Int, limit: Int) -> List<AerialMedia>)? = null,
) {
    private var position = startPosition
    private var _hasReachedEnd = false

    private val windowVideos = ConcurrentHashMap<Int, AerialMedia>()
    @Volatile private var currentWindowOffset = windowOffset
    @Volatile private var currentWindowSize = initialVideos.size

    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        initialVideos.forEachIndexed { index, media ->
            windowVideos[windowOffset + index] = media
        }
    }

    val currentPosition: Int get() = position

    suspend fun nextItem(): AerialMedia {
        position = calculateNext(++position)
        if (position == 0 && size > 0) _hasReachedEnd = true

        Timber.v("MediaPlaylist: nextItem() -> pos $position / $size (window: ${windowVideos.size})")
        checkAndRefillWindow()

        return getItemAt(position)
    }

    suspend fun previousItem(): AerialMedia {
        position = calculateNext(--position)

        Timber.v("MediaPlaylist: previousItem() -> pos $position / $size (window: ${windowVideos.size})")
        checkAndRefillWindow()

        return getItemAt(position)
    }

    private fun checkAndRefillWindow() {
        if (fetchChunk == null) return

        val relativeIndex = position - currentWindowOffset
        val remaining = currentWindowSize - relativeIndex - 1

        // Refill when 5 or fewer items remaining ahead in the window
        if (remaining <= 5 && position + remaining < size - 1) {
            val newOffset = 0.coerceAtLeast(position - 5)
            val limit = 50

            // Only fetch if the window would actually shift
            if (newOffset != currentWindowOffset) {
                Timber.i(
                    "MediaPlaylist: Refilling window. Position: $position, Window: $currentWindowOffset..${currentWindowOffset + currentWindowSize}. New Offset: $newOffset",
                )
                
                // Update optimistically to prevent duplicate fetches from subsequent nextItem() calls
                currentWindowOffset = newOffset 
                
                scope.launch {
                    val freshData = fetchChunk.invoke(newOffset, limit)
                    
                    freshData.forEachIndexed { index, media ->
                        windowVideos[newOffset + index] = media
                    }
                    currentWindowSize = freshData.size
                    
                    val validKeys = newOffset until (newOffset + freshData.size)
                    val keysToRemove = windowVideos.keys.filter { it !in validKeys }
                    keysToRemove.forEach { windowVideos.remove(it) }
                    
                    Timber.d("MediaPlaylist: Window refilled. New range: $newOffset..${newOffset + freshData.size}")
                }
            }
        }
    }

    private suspend fun getItemAt(absoluteIndex: Int): AerialMedia {
        val cached = windowVideos[absoluteIndex]
        if (cached != null) {
            return cached
        }

        // Cache miss fallback (happens if fetch hasn't completed or we jumped significantly)
        if (fetchChunk != null) {
            Timber.w("Sync fetching chunk due to buffer miss at index $absoluteIndex")
            val newOffset = 0.coerceAtLeast(absoluteIndex - 5)
            val freshData = fetchChunk.invoke(newOffset, 50)
            
            currentWindowOffset = newOffset
            currentWindowSize = freshData.size

            freshData.forEachIndexed { index, media ->
                windowVideos[newOffset + index] = media
            }
            
            val validKeys = newOffset until (newOffset + freshData.size)
            val keysToRemove = windowVideos.keys.filter { it !in validKeys }
            keysToRemove.forEach { windowVideos.remove(it) }

            val fallback = windowVideos[absoluteIndex]
            if (fallback != null) {
                return fallback
            }
        }

        return windowVideos.values.firstOrNull() 
            ?: throw IllegalStateException("Playlist is empty and fetch failed")
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
