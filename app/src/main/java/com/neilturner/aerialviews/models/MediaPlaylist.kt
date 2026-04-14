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
    private val fetchChunk: ((offset: Int, limit: Int) -> List<AerialMedia>)? = null,
) {
    private var position = startPosition
    private var _hasReachedEnd = false

    private val windowVideos = initialVideos.toMutableList()
    private val windowLock = Any()
    private val scope = CoroutineScope(Dispatchers.IO)

    val currentPosition: Int get() = position
    val hasReachedEnd: Boolean get() = _hasReachedEnd

    fun nextItem(): AerialMedia {
        position = calculateNext(++position)
        if (position == 0 && size > 0) _hasReachedEnd = true
        
        checkAndRefillWindow()
        
        return getItemAt(position)
    }

    fun previousItem(): AerialMedia {
        position = calculateNext(--position)
        
        checkAndRefillWindow()
        
        return getItemAt(position)
    }

    private fun checkAndRefillWindow() {
        if (fetchChunk == null) return
        
        val relativeIndex = position - windowOffset
        
        // Refill when we go down to 5 items remaining in either direction
        if (relativeIndex >= windowVideos.size - 5 || relativeIndex < 5) {
            val newOffset = Math.max(0, position - 5)
            val limit = 50
            
            // Only fetch if offset has moved significantly
            if (Math.abs(newOffset - windowOffset) > 5) {
                Timber.i("Refilling playlist. absolute: $position, offset moving $windowOffset -> $newOffset")
                scope.launch {
                    val freshData = fetchChunk.invoke(newOffset, limit)
                    synchronized(windowLock) {
                        windowOffset = newOffset
                        windowVideos.clear()
                        windowVideos.addAll(freshData)
                    }
                }
            }
        }
    }

    private fun getItemAt(absoluteIndex: Int): AerialMedia {
        synchronized(windowLock) {
            val relativeIndex = absoluteIndex - windowOffset
            if (relativeIndex in 0 until windowVideos.size) {
                return windowVideos[relativeIndex]
            }
            
            // Cache miss fallback (happens if fetch hasn't completed or we jumped significantly)
            if (fetchChunk != null) {
                Timber.w("Sync fetching chunk due to buffer miss at index $absoluteIndex")
                val newOffset = Math.max(0, absoluteIndex - 5)
                val freshData = fetchChunk.invoke(newOffset, 50)
                windowOffset = newOffset
                windowVideos.clear()
                windowVideos.addAll(freshData)
                
                val newRelative = absoluteIndex - windowOffset
                if (newRelative in 0 until windowVideos.size) {
                    return windowVideos[newRelative]
                }
            }
            
            return windowVideos.first()
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
