package com.neilturner.aerialviews.services

import com.neilturner.aerialviews.data.network.JsonHelper
import com.neilturner.aerialviews.models.enums.OverlayType
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * JVM unit tests for the bundled message API HTTP contract ([KtorServer] routing), loaded via
 * [messageApiModule] into Ktor's [testApplication] so no real socket or Android
 * [android.content.Context] is required.
 *
 * The "configured + enabled" surface under test here is the routing module itself — i.e. a server
 * that has been wired up by [com.neilturner.aerialviews.ui.core.ScreenController], which only starts
 * [KtorServer] when a MessageOverlay slot is laid out and `message_api_enabled` is true (see
 * [com.neilturner.aerialviews.ui.core.ScreenController]). That enablement gate needs Android Views, so
 * it is covered by the runtime TestConfig loop ([com.neilturner.aerialviews.testconfig.TestConfigApplier],
 * [docs/testing/README.md]); these tests assert what the HTTP endpoint does once it is running.
 */
internal class MessageApiTest {
    @Nested
    @DisplayName("/status")
    inner class Status {
        @Test
        @DisplayName("GET /status returns 200 and the running banner")
        fun `status returns 200 and the running banner`() =
            testApplication {
                application { messageApiModule(onMessageReceived = {}) }
                val client = createClient { install(ContentNegotiation) { json(JsonHelper.json) } }

                val response = client.get("/status")

                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("Aerial Views message API is running", response.bodyAsText())
            }
    }

    @Nested
    @DisplayName("POST /message/{number}")
    inner class PostMessage {
        @Test
        @DisplayName("accepts a valid message, responds 200 and fires the callback with MESSAGE1")
        fun `accepts a valid message and fires the callback`() =
            testApplication {
                var received: MessageEvent? = null
                application { messageApiModule(onMessageReceived = { received = it }) }
                val client = createClient { install(ContentNegotiation) { json(JsonHelper.json) } }

                val response =
                    client.post("/message/1") {
                        contentType(ContentType.Application.Json)
                        setBody(MessageRequest(text = "Hello", duration = 5, textSize = 24, textWeight = 400))
                    }

                assertEquals(HttpStatusCode.OK, response.status)
                val body = JsonHelper.json.decodeFromString<SuccessResponse>(response.bodyAsText())
                assertEquals(true, body.success)
                assertEquals("Message 1 processed successfully", body.message)

                val event = received
                assertEquals(OverlayType.MESSAGE1, event?.type)
                assertEquals("Hello", event?.text)
                assertEquals(5, event?.duration)
                assertEquals(24, event?.textSize)
                assertEquals(400, event?.textWeight)
            }

        @Test
        @DisplayName("routes message numbers 2, 3 and 4 to MESSAGE2/3/4")
        fun `routes each message number to its overlay type`() =
            testApplication {
                val received = mutableSetOf<OverlayType>()
                application { messageApiModule(onMessageReceived = { received += it.type }) }
                val client = createClient { install(ContentNegotiation) { json(JsonHelper.json) } }

                listOf(2, 3, 4).forEach { number ->
                    client.post("/message/$number") {
                        contentType(ContentType.Application.Json)
                        setBody(MessageRequest(text = "msg$number"))
                    }
                }

                assertEquals(setOf(OverlayType.MESSAGE2, OverlayType.MESSAGE3, OverlayType.MESSAGE4), received)
            }

        @Test
        @DisplayName("rejects a message number outside 1..4 with 400")
        fun `rejects out of range message numbers`() =
            testApplication {
                application { messageApiModule(onMessageReceived = {}) }
                val client = createClient { install(ContentNegotiation) { json(JsonHelper.json) } }

                listOf("0", "5", "abc", "-1").forEach { number ->
                    val response =
                        client.post("/message/$number") {
                            contentType(ContentType.Application.Json)
                            setBody(MessageRequest(text = "x"))
                        }
                    assertEquals(HttpStatusCode.BadRequest, response.status, "expected 400 for /$number")
                    val body = JsonHelper.json.decodeFromString<ErrorResponse>(response.bodyAsText())
                    assertTrue(body.error.contains("Invalid message number"), body.error)
                }
            }

        @Test
        @DisplayName("returns 400 when the 'text' field is absent")
        fun `returns 400 when text is missing`() =
            testApplication {
                application { messageApiModule(onMessageReceived = {}) }
                val client = createClient { install(ContentNegotiation) { json(JsonHelper.json) } }

                // A MessageRequest with text=null serialises to {"duration":5} (kotlinx omits the
                // default-null text field), i.e. a body that carries no message text.
                val response =
                    client.post("/message/1") {
                        contentType(ContentType.Application.Json)
                        setBody(MessageRequest(text = null, duration = 5))
                    }

                assertEquals(HttpStatusCode.BadRequest, response.status)
                val body = JsonHelper.json.decodeFromString<ErrorResponse>(response.bodyAsText())
                assertEquals("Required parameter 'text' is missing.", body.error)
            }

        @Test
        @DisplayName("clears the message (200) when text is empty")
        fun `empty text clears the message`() =
            testApplication {
                application { messageApiModule(onMessageReceived = {}) }
                val client = createClient { install(ContentNegotiation) { json(JsonHelper.json) } }

                val response =
                    client.post("/message/1") {
                        contentType(ContentType.Application.Json)
                        setBody(MessageRequest(text = ""))
                    }

                assertEquals(HttpStatusCode.OK, response.status)
                val body = JsonHelper.json.decodeFromString<SuccessResponse>(response.bodyAsText())
                assertEquals("Message 1 cleared successfully", body.message)
            }

        @Test
        @DisplayName("defaults textSize/textWeight when absent but still invokes the callback")
        fun `defaults size and weight when not provided`() =
            testApplication {
                var received: MessageEvent? = null
                application { messageApiModule(onMessageReceived = { received = it }) }
                val client = createClient { install(ContentNegotiation) { json(JsonHelper.json) } }

                val response =
                    client.post("/message/1") {
                        contentType(ContentType.Application.Json)
                        setBody(MessageRequest(text = "hi"))
                    }

                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals(18, received?.textSize)
                assertEquals(300, received?.textWeight)
            }

        @Test
        @DisplayName("rejects an unknown textSize with 400")
        fun `rejects invalid textSize`() =
            testApplication {
                application { messageApiModule(onMessageReceived = {}) }
                val client = createClient { install(ContentNegotiation) { json(JsonHelper.json) } }

                val response =
                    client.post("/message/1") {
                        contentType(ContentType.Application.Json)
                        setBody(MessageRequest(text = "hi", textSize = 999))
                    }

                assertEquals(HttpStatusCode.BadRequest, response.status)
                val body = JsonHelper.json.decodeFromString<ErrorResponse>(response.bodyAsText())
                assertTrue(body.error.contains("Invalid textSize value: 999"), body.error)
            }

        @Test
        @DisplayName("rejects an unknown textWeight with 400")
        fun `rejects invalid textWeight`() =
            testApplication {
                application { messageApiModule(onMessageReceived = {}) }
                val client = createClient { install(ContentNegotiation) { json(JsonHelper.json) } }

                val response =
                    client.post("/message/1") {
                        contentType(ContentType.Application.Json)
                        setBody(MessageRequest(text = "hi", textWeight = 123))
                    }

                assertEquals(HttpStatusCode.BadRequest, response.status)
                val body = JsonHelper.json.decodeFromString<ErrorResponse>(response.bodyAsText())
                assertTrue(body.error.contains("Invalid textWeight value: 123"), body.error)
            }

        @Test
        @DisplayName("accepts a custom valid textSize and textWeight")
        fun `accepts valid custom size and weight`() =
            testApplication {
                var received: MessageEvent? = null
                application { messageApiModule(onMessageReceived = { received = it }) }
                val client = createClient { install(ContentNegotiation) { json(JsonHelper.json) } }

                val response =
                    client.post("/message/3") {
                        contentType(ContentType.Application.Json)
                        setBody(MessageRequest(text = "big", duration = 2, textSize = 72, textWeight = 900))
                    }

                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals(OverlayType.MESSAGE3, received?.type)
                assertEquals(72, received?.textSize)
                assertEquals(900, received?.textWeight)
                assertEquals(2, received?.duration)
            }

        @Test
        @DisplayName("returns 500 for a request body that cannot be decoded as a message")
        fun `returns 500 for a body that cannot be decoded`() =
            testApplication {
                application { messageApiModule(onMessageReceived = {}) }
                val client = createClient { install(ContentNegotiation) { json(JsonHelper.json) } }

                val response =
                    client.post("/message/1") {
                        contentType(ContentType.Application.Json)
                    }

                assertEquals(HttpStatusCode.InternalServerError, response.status)
                val body = JsonHelper.json.decodeFromString<ErrorResponse>(response.bodyAsText())
                assertEquals("An internal server error occurred.", body.error)
            }
    }
}
