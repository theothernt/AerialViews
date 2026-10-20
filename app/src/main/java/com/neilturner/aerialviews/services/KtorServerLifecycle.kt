package com.neilturner.aerialviews.services

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.BindException
import java.util.concurrent.atomic.AtomicInteger

private const val DEFAULT_BIND_ATTEMPTS = 5
private const val DEFAULT_BIND_RETRY_DELAY_MS = 400L
private const val MIN_PORT = 1
private const val MAX_PORT = 65535

/**
 * Normalises a user-supplied port value, falling back to [defaultPort] when it is absent,
 * non-numeric, or outside the valid port range.
 */
fun resolveServerPort(
    raw: String?,
    defaultPort: Int,
): Int = raw?.toIntOrNull()?.takeIf { it in MIN_PORT..MAX_PORT } ?: defaultPort

/** Terminal outcome of a single [KtorServerLifecycle.start] request. */
sealed interface ServerStartResult {
    /** The socket is bound and serving. */
    data class Started(
        val port: Int,
    ) : ServerStartResult

    /** Every attempt failed because the port was still held by something else. */
    data class PortUnavailable(
        val port: Int,
        val attempts: Int,
    ) : ServerStartResult

    /** The engine refused to start for a reason other than the port being taken. */
    data class Failed(
        val port: Int,
        val error: Throwable,
    ) : ServerStartResult

    /** A newer start/stop request arrived first, so this one did nothing. */
    data object Superseded : ServerStartResult
}

/**
 * Start/stop lifecycle for a server that binds a fixed TCP port, with no dependency on Android,
 * Ktor, or logging so the concurrency can be unit tested directly.
 *
 * The port is held by at most one instance at a time. A screensaver session creates and destroys
 * its owner repeatedly, so an un-synchronised start/stop pair used to let the outgoing server keep
 * its socket while the incoming one tried to bind the same port.
 *
 * Every lifecycle change is funnelled through [mutex] and stamped with a monotonically increasing
 * [generation], so a stale request can never act on — or shut down — a server created after it.
 *
 * [T] is the engine handle. It only has to be buildable, startable and stoppable; the CIO engine
 * cannot be restarted, so each bind attempt is given a fresh instance by [buildServer].
 */
class KtorServerLifecycle<T>(
    private val scope: CoroutineScope,
    private val portProvider: () -> Int,
    private val buildServer: (Int) -> T,
    private val startServer: suspend (T) -> Unit,
    private val stopServer: suspend (T) -> Unit,
    private val discardServer: (T) -> Unit = {},
    private val bindAttempts: Int = DEFAULT_BIND_ATTEMPTS,
    private val bindRetryDelayMs: Long = DEFAULT_BIND_RETRY_DELAY_MS,
    private val blockingDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val onResult: (ServerStartResult) -> Unit = {},
    private val onBindRetry: (port: Int, attempt: Int, attempts: Int, cause: BindException) -> Unit = { _, _, _, _ -> },
    private val onStopping: () -> Unit = {},
    private val onStopped: () -> Unit = {},
) {
    private val mutex = Mutex()
    private val generation = AtomicInteger(0)

    /** The currently bound engine, or null when nothing is serving. */
    @Volatile
    var activeServer: T? = null
        private set

    /**
     * Starts the server, replacing any instance left over from a previous session.
     *
     * Safe to call while a previous [stop] is still in flight: the two are serialised, and the
     * outgoing server is always shut down before the new socket is bound.
     */
    fun start() {
        val requested = generation.incrementAndGet()
        scope.launch { startInternal(requested) }
    }

    /**
     * Shuts the server down and releases the port. Safe to call when nothing is running, and
     * safe to call more than once.
     */
    fun stop() {
        val requested = generation.incrementAndGet()
        scope.launch { stopInternal(requested) }
    }

    private suspend fun startInternal(requested: Int) {
        mutex.withLock {
            // A newer start()/stop() arrived while this request was queued.
            if (superseded(requested)) return@withLock

            // Release the previous instance first: this is the step that was previously racing the
            // new bind, leaving the port in use.
            stopLocked()

            if (superseded(requested)) return@withLock

            val port = portProvider()
            bindWithRetries(requested, port)
        }
    }

    private suspend fun bindWithRetries(
        requested: Int,
        port: Int,
    ) {
        for (attempt in 1..bindAttempts) {
            if (superseded(requested)) return

            // A CIOApplicationEngine cannot be restarted, so each attempt needs a fresh one.
            val candidate = buildServer(port)
            try {
                // wait = false still suspends internally until the engine's startup job settles, so
                // a bind failure is reported on this call rather than on a coroutine we do not own.
                startServer(candidate)
            } catch (e: BindException) {
                onBindRetry(port, attempt, bindAttempts, e)
                discardServer(candidate)
                if (attempt < bindAttempts) delay(bindRetryDelayMs)
                continue
            } catch (e: Exception) {
                discardServer(candidate)
                onResult(ServerStartResult.Failed(port, e))
                return
            }

            activeServer = candidate
            onResult(ServerStartResult.Started(port))
            return
        }

        onResult(ServerStartResult.PortUnavailable(port, bindAttempts))
    }

    private suspend fun stopInternal(requested: Int) {
        mutex.withLock {
            // Superseded by a newer request; that request owns the lifecycle now.
            if (superseded(requested)) return@withLock
            stopLocked()
        }
    }

    /** Must be called while holding [mutex]. */
    private suspend fun stopLocked() {
        val current = activeServer ?: return
        activeServer = null
        onStopping()
        // stop() blocks, so keep it off whatever dispatcher the caller happened to be on.
        withContext(blockingDispatcher) { stopServer(current) }
        onStopped()
    }

    /**
     * Reports [ServerStartResult.Superseded] and returns true when a newer start()/stop() request
     * has overtaken [requested], meaning this request must not touch the server.
     */
    private fun superseded(requested: Int): Boolean {
        if (requested == generation.get()) return false
        onResult(ServerStartResult.Superseded)
        return true
    }
}
