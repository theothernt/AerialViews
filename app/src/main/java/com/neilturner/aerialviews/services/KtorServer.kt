package com.neilturner.aerialviews.services

import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.net.BindException

@Serializable
data class MessageResponse(
    val text: String,
    val duration: Int,
    val textSize: String,
    val textWeight: String,
    val success: Boolean,
    val message: String
)

@Serializable
data class ErrorResponse(
    val success: Boolean,
    val error: String
)

class KtorServer {
    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null

    fun start() {
        CoroutineScope(Dispatchers.IO).launch {
            val port = GeneralPrefs.messageApiPort.toIntOrNull() ?: 8080
            try {
                server =
                    embeddedServer(Netty, port) {
                        configureRouting()
                        configurePlugins()
                    }.start(wait = true)
                Timber.Forest.i("Ktor server started on port $port")
            } catch (e: BindException) {
                Timber.Forest.e(e, "Failed to start server: Port $port already in use")
            } catch (e: Exception) {
                Timber.Forest.e(e, "Error starting Ktor server")
            }
        }
    }

    fun stop() {
        server?.stop(1000, 2000)
        Timber.Forest.i("Ktor server stopped")
    }

    private fun Application.configureRouting() {
        routing {
            // get("/") { }

            get("/status") {
                call.respondText("Aerial Views Message API is running", contentType = ContentType.Text.Plain)
            }

            get("/message1") {
                handleMessageRequest(call, 1)
            }

            get("/message2") {
                handleMessageRequest(call, 2)
            }

            get("/message3") {
                handleMessageRequest(call, 3)
            }

            get("/message4") {
                handleMessageRequest(call, 4)
            }
        }
    }

    private suspend fun handleMessageRequest(call: RoutingCall, messageNumber: Int) {
        try {
            val text = call.queryParameters["text"]
            val duration = call.queryParameters["duration"]?.toIntOrNull() ?: 0
            val textSize = call.queryParameters["textSize"] ?: "medium"
            val textWeight = call.queryParameters["textWeight"] ?: "normal"

            if (text == null) {
                call.respond(
                    ErrorResponse(
                        success = false,
                        error = "Required parameter 'text' is missing"
                    )
                )
                return
            }

            // Handle empty text as a clear message command
            val finalText = text.ifEmpty { "" }
            val isClearing = text.isEmpty()

            // Validate text size parameter
            val validSizes = listOf("small", "medium", "large", "xl", "xxl")
            val normalizedTextSize = if (textSize.lowercase() in validSizes) {
                textSize.lowercase()
            } else {
                "medium"
            }

            // Validate text weight parameter
            val validWeights = listOf("light", "normal", "bold", "heavy")
            val normalizedTextWeight = if (textWeight.lowercase() in validWeights) {
                textWeight.lowercase()
            } else {
                "normal"
            }

            // TODO: Here you would typically handle the message display logic for each message slot
            // For now, we'll just log it and return a success response
            val actionType = if (isClearing) "cleared" else "received"
            Timber.i("Message $messageNumber $actionType - Text: '$finalText', Duration: ${duration}s, Size: $normalizedTextSize, Weight: $normalizedTextWeight")

            call.respond(
                MessageResponse(
                    text = finalText,
                    duration = duration,
                    textSize = normalizedTextSize,
                    textWeight = normalizedTextWeight,
                    success = true,
                    message = if (isClearing) "Message $messageNumber cleared successfully" else "Message $messageNumber processed successfully"
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "Error processing message $messageNumber request")
            call.respond(
                ErrorResponse(
                    success = false,
                    error = "Internal server error: ${e.message}"
                )
            )
        }
    }

    private fun Application.configurePlugins() {
        install(ContentNegotiation) {
            json(
                Json {
                    prettyPrint = true
                    isLenient = true
                },
            )
        }
    }
}