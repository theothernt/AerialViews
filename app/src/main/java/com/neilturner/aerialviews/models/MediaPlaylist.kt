package com.neilturner.aerialviews.models

import com.neilturner.aerialviews.models.videos.AerialMedia
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber

/**
 * A cycling media playlist backed by a sliding window of items.
 *
 * When [fetchChunk] is supplied the playlist does not hold every item: it holds a window of
 * [WINDOW_LIMIT] items starting at an absolute [size] index, and pulls further chunks on demand.
 * The chunk source and [size] must therefore describe the *same* index space. If a chunk fetch
 * proves the source is shorter than [size], the playlist reduces its own size to what the source
 * can actually serve rather than running off the end of the window.
 */
class MediaPlaylist(
    initialVideos: List<AerialMedia>,
    startPosition: Int = -1,
    size: Int = initialVideos.size,
    private var windowOffset: Int = 0,
    private val fetchChunk: (suspend (offset: Int, limit: Int) -> List<AerialMedia>)? = null,
) {
    private var position = startPosition

    private val windowVideos = initialVideos.toMutableList()
    private val windowLock = Any()
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * How many items the chunk source can actually serve. Starts at the declared [size] and is
     * lowered when a chunk fetch proves the source is exhausted. Volatile because the async refill
     * path can lower it from an IO thread.
     */
    @Volatile
    private var servableSize = size

    /** Monotonic guard so a slow refill cannot overwrite a newer window. Guarded by [windowLock]. */
    private var refillGeneration = 0

    val currentPosition: Int get() = position

    val size: Int get() = servableSize

    fun nextItem(): AerialMedia? {
        if (servableSize == 0) {
            Timber.w("MediaPlaylist: nextItem() called on an empty playlist")
            return null
        }

        position = calculateNext(++position)
        Timber.v("MediaPlaylist: nextItem() -> pos $position / $size (window: $windowSize)")
        checkAndRefillWindow()

        // A refill may have discovered the chunk source is shorter than declared, so re-wrap
        // before resolving rather than serving an index the source cannot produce.
        position = calculateNext(position)
        return getItemAt(position) ?: skipForward()
    }

    fun previousItem(): AerialMedia? {
        if (servableSize == 0) {
            Timber.w("MediaPlaylist: previousItem() called on an empty playlist")
            return null
        }

        position = calculateNext(--position)
        Timber.v("MediaPlaylist: previousItem() -> pos $position / $size (window: $windowSize)")
        checkAndRefillWindow()

        position = calculateNext(position)
        return getItemAt(position) ?: skipForward()
    }

    /** Resolves [absoluteIndex] from the window, refilling synchronously on a hard miss. */
    private fun getItemAt(absoluteIndex: Int): AerialMedia? {
        synchronized(windowLock) {
            val relativeIndex = absoluteIndex - windowOffset
            if (relativeIndex in windowVideos.indices) {
                return windowVideos[relativeIndex]
            }
        }

        if (fetchChunk == null) {
            Timber.w("MediaPlaylist: Index $absoluteIndex is outside an in-memory playlist of $servableSize items")
            return null
        }

        Timber.w("MediaPlaylist: Cache miss at index $absoluteIndex, attempting synchronous refill")
        refillWindowSync((absoluteIndex - PREFETCH_MARGIN).coerceAtLeast(0))

        synchronized(windowLock) {
            val relativeIndex = absoluteIndex - windowOffset
            if (relativeIndex in windowVideos.indices) {
                return windowVideos[relativeIndex]
            }
        }

        return synchronized(windowLock) {
            Timber.e(
                "MediaPlaylist: Unable to serve index $absoluteIndex " +
                    "(size $servableSize, window $windowOffset..${windowOffset + windowVideos.size})",
            )
            null
        }
    }

    /**
     * Walks forward a bounded number of positions when the current one cannot be resolved, so a
     * single unservable index does not stop playback. Bounded because the source is exhausted
     * rather than missing, so a full walk would only loop.
     */
    private fun skipForward(): AerialMedia? {
        repeat(MAX_SKIP) {
            position = calculateNext(++position)
            getItemAt(position)?.let { return it }
        }
        Timber.e("MediaPlaylist: No item could be served from a playlist of $servableSize items")
        return null
    }

    private fun planRefill(): RefillPlan? =
        synchronized(windowLock) {
            val relativeIndex = position - windowOffset
            val isOutOfBounds = position < windowOffset || position >= windowOffset + windowVideos.size
            val remaining = windowVideos.size - relativeIndex - 1

            val isNearEnd = remaining <= PREFETCH_MARGIN && (windowOffset + windowVideos.size < servableSize)
            val isNearStart = relativeIndex <= PREFETCH_MARGIN && windowOffset > 0

            if (!isOutOfBounds && !isNearEnd && !isNearStart) return@synchronized null

            val newOffset = (position - PREFETCH_MARGIN).coerceAtLeast(0)
            if (newOffset == windowOffset) return@synchronized null

            RefillPlan(newOffset = newOffset, synchronous = isOutOfBounds)
        }

    private fun checkAndRefillWindow() {
        if (fetchChunk == null) return

        val plan = planRefill() ?: return

        if (plan.synchronous) {
            refillWindowSync(plan.newOffset)
            return
        }

        val generation =
            synchronized(windowLock) {
                ++refillGeneration
            }

        Timber.i(
            "MediaPlaylist: Refilling window. Position: $position, New Offset: ${plan.newOffset}",
        )
        scope.launch {
            val freshData = fetchChunk.invoke(plan.newOffset, WINDOW_LIMIT)
            synchronized(windowLock) {
                if (generation != refillGeneration) {
                    Timber.d(
                        "MediaPlaylist: Discarding stale window refill (gen $generation, current $refillGeneration)",
                    )
                    return@synchronized
                }
                recordSourceEnd(plan.newOffset, freshData.size)
                windowOffset = plan.newOffset
                windowVideos.clear()
                windowVideos.addAll(freshData)
                Timber.d(
                    "MediaPlaylist: Window refilled. New range: $plan.newOffset..${plan.newOffset + freshData.size}",
                )
            }
        }
    }

    private fun refillWindowSync(newOffset: Int) {
        val chunkFetcher = fetchChunk ?: return

        val freshData =
            try {
                runBlocking(Dispatchers.IO) {
                    chunkFetcher.invoke(newOffset, WINDOW_LIMIT)
                }
            } catch (e: Exception) {
                Timber.e(e, "MediaPlaylist: Error refilling window at offset $newOffset")
                return
            }

        synchronized(windowLock) {
            // Invalidate any async refill still in flight so it cannot clobber this window.
            refillGeneration++
            recordSourceEnd(newOffset, freshData.size)
            windowOffset = newOffset
            windowVideos.clear()
            windowVideos.addAll(freshData)
            Timber.d("MediaPlaylist: Window refilled synchronously. New range: $newOffset..${newOffset + freshData.size}")
        }
    }

    /**
     * A page shorter than the requested limit means the chunk source has no rows past
     * `newOffset + returned`. Lower the servable size to that point so a `size` that overstates the
     * underlying data degrades into a shorter playlist instead of running off the end of the
     * window. Callers must hold [windowLock].
     */
    private fun recordSourceEnd(
        newOffset: Int,
        returned: Int,
    ) {
        if (returned >= WINDOW_LIMIT) return
        val trueEnd = newOffset + returned
        if (trueEnd < servableSize) {
            Timber.w("MediaPlaylist: Chunk source exhausted at $trueEnd; reducing playlist size $servableSize -> $trueEnd")
            servableSize = trueEnd
        }
    }

    private val windowSize: Int
        get() = synchronized(windowLock) { windowVideos.size }

    private fun calculateNext(number: Int): Int {
        if (servableSize == 0) return 0
        return if (number < 0) {
            servableSize + number
        } else {
            number.rem(servableSize)
        }
    }

    private data class RefillPlan(
        val newOffset: Int,
        val synchronous: Boolean,
    )

    private companion object {
        const val WINDOW_LIMIT = 50
        const val PREFETCH_MARGIN = 5
        const val MAX_SKIP = 5
    }
}
