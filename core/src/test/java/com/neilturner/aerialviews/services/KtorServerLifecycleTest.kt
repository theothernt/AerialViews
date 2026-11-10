package com.neilturner.aerialviews.services

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.net.BindException

private class FakeServer(
    val port: Int,
) {
    var startCount = 0
    var stopCount = 0
    var discardCount = 0
}

/**
 * Drives [KtorServerLifecycle] with a fake engine so the start/stop ordering and the generation
 * guard can be exercised deterministically in virtual time.
 */
private class Harness(
    scope: TestScope,
    port: Int = 8081,
    bindAttempts: Int = 5,
) {
    val built = mutableListOf<FakeServer>()
    val events = mutableListOf<String>()
    val results = mutableListOf<ServerStartResult>()
    val retries = mutableListOf<Triple<Int, Int, Int>>()
    var startFailure: (() -> Unit)? = null

    val lifecycle =
        KtorServerLifecycle<FakeServer>(
            scope = scope,
            portProvider = { port },
            buildServer = {
                FakeServer(it).also { server -> built += server }
            },
            startServer = { server ->
                startFailure?.invoke()
                server.startCount++
                events += "start:${server.port}"
            },
            stopServer = { server ->
                server.stopCount++
                events += "stop:${server.port}"
            },
            discardServer = { server ->
                server.discardCount++
                events += "discard:${server.port}"
            },
            bindAttempts = bindAttempts,
            bindRetryDelayMs = 10_000L, // Virtual time: retries must not really wait.
            blockingDispatcher = StandardTestDispatcher(scope.testScheduler),
            onResult = { results += it },
            onBindRetry = { port, attempt, attempts, _ -> retries += Triple(port, attempt, attempts) },
            onStopping = { events += "stopping" },
            onStopped = { events += "stopped" },
        )
}

/** Builds a harness whose engine refuses to bind until [failures] attempts have been made. */
private fun Harness.bindFails(times: Int): Harness {
    var remaining = times
    startFailure = { if (remaining-- > 0) throw BindException("Address already in use") }
    return this
}

@DisplayName("Ktor server lifecycle")
internal class KtorServerLifecycleTest {
    @Nested
    @DisplayName("successful start")
    inner class SuccessfulStart {
        @Test
        @DisplayName("binds the port and reports Started")
        fun `binds the port and reports Started`() =
            runTest {
                val harness = Harness(this)
                harness.lifecycle.start()
                advanceUntilIdle()

                val result = assertInstanceOf(ServerStartResult.Started::class.java, harness.results.single())
                assertEquals(8081, result.port)
                assertSame(harness.built.single(), harness.lifecycle.activeServer)
            }

        @Test
        @DisplayName("stops the previous server before binding the new one")
        fun `stops the previous server before binding the new one`() =
            runTest {
                val harness = Harness(this)

                harness.lifecycle.start()
                advanceUntilIdle()
                harness.events.clear()
                harness.lifecycle.start()
                advanceUntilIdle()

                assertEquals(listOf("stopping", "stop:8081", "stopped", "start:8081"), harness.events)
                assertEquals(1, harness.built.first().stopCount)
                assertEquals(1, harness.built.last().startCount)
            }

        @Test
        @DisplayName("uses the port supplied by the provider")
        fun `uses the port supplied by the provider`() =
            runTest {
                val harness = Harness(this, port = 9000)
                harness.lifecycle.start()
                advanceUntilIdle()

                assertEquals(9000, (harness.results.single() as ServerStartResult.Started).port)
                assertEquals(9000, harness.built.single().port)
            }
    }

    @Nested
    @DisplayName("port binding failures")
    inner class BindFailures {
        @Test
        @DisplayName("retries a taken port and succeeds on a later attempt")
        fun `retries a taken port and succeeds on a later attempt`() =
            runTest {
                val harness = Harness(this).bindFails(2)

                harness.lifecycle.start()
                advanceUntilIdle()

                val result = harness.results.single()
                assertInstanceOf(ServerStartResult.Started::class.java, result)
                assertEquals(2, harness.retries.size)
                // Two discarded candidates, one live server: a CIO engine cannot be restarted.
                assertEquals(3, harness.built.size)
                assertTrue(harness.built.take(2).all { it.startCount == 0 && it.discardCount == 1 })
                assertSame(harness.built.last(), harness.lifecycle.activeServer)
            }

        @Test
        @DisplayName("reports PortUnavailable after exhausting every attempt")
        fun `reports PortUnavailable after exhausting every attempt`() =
            runTest {
                val harness = Harness(this, bindAttempts = 4).bindFails(times = 4)

                harness.lifecycle.start()
                advanceUntilIdle()

                val result = assertInstanceOf(ServerStartResult.PortUnavailable::class.java, harness.results.single())
                assertEquals(8081, result.port)
                assertEquals(4, result.attempts)
                assertEquals(4, harness.built.size)
                assertTrue(harness.built.all { it.discardCount == 1 && it.startCount == 0 })
                assertNull(harness.lifecycle.activeServer)
            }

        @Test
        @DisplayName("retries BindException but not other failures")
        fun `retries BindException but not other failures`() =
            runTest {
                val harness = Harness(this)
                harness.startFailure = { throw IllegalStateException("bad config") }

                harness.lifecycle.start()
                advanceUntilIdle()

                val result = assertInstanceOf(ServerStartResult.Failed::class.java, harness.results.single())
                assertEquals(8081, result.port)
                assertInstanceOf(IllegalStateException::class.java, result.error)
                assertTrue(harness.retries.isEmpty())
                assertEquals(1, harness.built.size)
                assertEquals(1, harness.built.single().discardCount)
                assertNull(harness.lifecycle.activeServer)
            }
    }

    @Nested
    @DisplayName("stop")
    inner class Stop {
        @Test
        @DisplayName("stops a running server and releases the port")
        fun `stops a running server and releases the port`() =
            runTest {
                val harness = Harness(this)
                harness.lifecycle.start()
                advanceUntilIdle()

                harness.lifecycle.stop()
                advanceUntilIdle()

                assertEquals(1, harness.built.single().stopCount)
                assertNull(harness.lifecycle.activeServer)
            }

        @Test
        @DisplayName("is a no-op when nothing is running")
        fun `is a no-op when nothing is running`() =
            runTest {
                val harness = Harness(this)

                harness.lifecycle.stop()
                harness.lifecycle.stop()
                advanceUntilIdle()

                // The second request supersedes the first, but neither may touch a server.
                assertEquals(emptyList<String>(), harness.events)
                assertTrue(harness.built.isEmpty())
                assertNull(harness.lifecycle.activeServer)
            }

        @Test
        @DisplayName("can be called while a start is still in flight")
        fun `can be called while a start is still in flight`() =
            runTest {
                val harness = Harness(this).bindFails(times = 10)

                // The start begins binding and then hits a port that never frees up.
                harness.lifecycle.start()
                runCurrent()
                assertEquals(1, harness.built.size)

                harness.lifecycle.stop()
                advanceUntilIdle()

                // The retry loop is abandoned rather than burned through, and the discarded
                // candidate is never left half-torn-down.
                assertEquals(1, harness.built.size)
                assertTrue(harness.results.contains(ServerStartResult.Superseded))
                assertNull(harness.lifecycle.activeServer)
            }
    }

    @Nested
    @DisplayName("generation guard")
    inner class GenerationGuard {
        @Test
        @DisplayName("a stale start does not bind over a newer stop")
        fun `a stale start does not bind over a newer stop`() =
            runTest {
                val harness = Harness(this)

                harness.lifecycle.start()
                harness.lifecycle.stop()
                advanceUntilIdle()

                assertTrue(harness.built.isEmpty())
                assertEquals(listOf(ServerStartResult.Superseded), harness.results)
                assertNull(harness.lifecycle.activeServer)
            }

        @Test
        @DisplayName("a stale stop does not shut down the server created after it")
        fun `a stale stop does not shut down the server created after it`() =
            runTest {
                val harness = Harness(this)

                // Queued back to back: the middle stop is older than the server the final start
                // creates, so acting on it would take down the new session's server.
                harness.lifecycle.start()
                harness.lifecycle.stop()
                harness.lifecycle.start()
                advanceUntilIdle()

                assertInstanceOf(ServerStartResult.Started::class.java, harness.results.last())
                assertNotNull(harness.lifecycle.activeServer)
                assertEquals(8081, (harness.results.last() as ServerStartResult.Started).port)
                // Exactly one engine ever bound: the stale start/stop pair must not bind or stop.
                assertEquals(1, harness.built.size)
                assertEquals(0, harness.built.single().stopCount)
            }

        @Test
        @DisplayName("abandons an in-flight start when superseded")
        fun `abandons an in-flight start when superseded`() =
            runTest {
                val harness = Harness(this).bindFails(times = 10)

                harness.lifecycle.start()
                // Let the first attempt fail and reach the retry delay before the stop overtakes
                // it, so the guard has to fire part-way through the retry loop.
                runCurrent()
                assertEquals(1, harness.built.size)

                harness.lifecycle.stop()
                advanceUntilIdle()

                // The remaining attempts are abandoned rather than burned.
                assertEquals(1, harness.built.size)
                assertTrue(harness.results.contains(ServerStartResult.Superseded))
                assertNull(harness.lifecycle.activeServer)
            }
    }

    @Nested
    @DisplayName("port resolution")
    inner class PortResolution {
        @Test
        @DisplayName("accepts a valid port")
        fun `accepts a valid port`() {
            assertEquals(9000, resolveServerPort("9000", 8081))
            assertEquals(1, resolveServerPort("1", 8081))
            assertEquals(65535, resolveServerPort("65535", 8081))
        }

        @Test
        @DisplayName("falls back to the default for missing or invalid values")
        fun `falls back to the default for missing or invalid values`() {
            assertEquals(8081, resolveServerPort(null, 8081))
            assertEquals(8081, resolveServerPort("", 8081))
            assertEquals(8081, resolveServerPort("not-a-port", 8081))
        }

        @Test
        @DisplayName("falls back to the default for out-of-range values")
        fun `falls back to the default for out-of-range values`() {
            assertEquals(8081, resolveServerPort("0", 8081))
            assertEquals(8081, resolveServerPort("-1", 8081))
            assertEquals(8081, resolveServerPort("65536", 8081))
            assertEquals(8081, resolveServerPort("99999", 8081))
        }
    }
}
