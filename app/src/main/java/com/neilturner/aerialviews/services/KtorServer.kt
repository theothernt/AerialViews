package com.neilturner.aerialviews.services

import android.content.Context
import com.neilturner.aerialviews.R
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
import me.kosert.flowbus.GlobalBus
import timber.log.Timber
import java.net.BindException

@Serializable
data class MessageResponse(
    val messageNumber: Int,
    val text: String,
    val duration: Int,
    val textSize: Int,
    val textWeight: Int,
    val success: Boolean,
    val message: String
)

@Serializable
data class ErrorResponse(
    val success: Boolean,
    val error: String
)

data class MessageEvent(
    val messageNumber: Int,
    val text: String = "",
    val duration: Int = 0,
    val textSize: Int = 18,
    val textWeight: Int = 300,
    val timestamp: Long = System.currentTimeMillis()
)

class KtorServer(context: Context) {
    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    private var context: Context? = null

    // Dynamic validation arrays loaded from XML
    private var validTextSizes: List<Int> = emptyList()
    private var validTextWeights: List<Int> = emptyList()
    private val defaultTextSize = 18
    private val defaultTextWeight = 300

    private fun loadValidationArrays() {
        context?.let { ctx ->
            try {
                // Load text size values from XML
                val textSizeArray = ctx.resources.getStringArray(R.array.text_size_values)
                validTextSizes = textSizeArray.mapNotNull { it.toIntOrNull() }

                // Load text weight values from XML
                val textWeightArray = ctx.resources.getStringArray(R.array.text_weight_values)
                validTextWeights = textWeightArray.mapNotNull { it.toIntOrNull() }

                Timber.d("Loaded text size values: $validTextSizes")
                Timber.d("Loaded text weight values: $validTextWeights")
            } catch (e: Exception) {
                Timber.e(e, "Error loading validation arrays from XML, using defaults")
                // Fallback to hardcoded values if XML loading fails
                validTextSizes = listOf(72, 66, 60, 54, 48, 42, 36, 32, 28, 24, 21, 18, 15)
                validTextWeights = listOf(100, 200, 300, 400, 500, 600, 700, 800, 900)
            }
        }
    }

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
            loadValidationArrays()
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
            val textSizeParam = call.queryParameters["textSize"]?.toIntOrNull()
            val textWeightParam = call.queryParameters["textWeight"]?.toIntOrNull()

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
            val normalizedTextSize = if (textSizeParam != null && textSizeParam in validTextSizes) {
                textSizeParam
            } else {
                defaultTextSize
            }

            // Validate text weight parameter
            val normalizedTextWeight = if (textWeightParam != null && textWeightParam in validTextWeights) {
                textWeightParam
            } else {
                defaultTextWeight
            }

            // Log validation warnings if invalid values were provided
            if (textSizeParam != null && textSizeParam !in validTextSizes) {
                Timber.w("Invalid textSize value: $textSizeParam. Using default: $defaultTextSize. Valid values: $validTextSizes")
            }
            if (textWeightParam != null && textWeightParam !in validTextWeights) {
                Timber.w("Invalid textWeight value: $textWeightParam. Using default: $defaultTextWeight. Valid values: $validTextWeights")
            }

            // Post message event to FlowBus for overlay consumption
            val messageEvent = MessageEvent(
                messageNumber = messageNumber,
                text = finalText,
                duration = duration,
                textSize = normalizedTextSize,
                textWeight = normalizedTextWeight
            )

            GlobalBus.post(messageEvent)

            val actionType = if (isClearing) "cleared" else "received"
            Timber.i("Message $messageNumber $actionType - Text: '$finalText', Duration: ${duration}s, Size: $normalizedTextSize, Weight: $normalizedTextWeight")

            call.respond(
                MessageResponse(
                    messageNumber = messageNumber,
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