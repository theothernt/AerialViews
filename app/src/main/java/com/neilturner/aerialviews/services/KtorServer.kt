package com.neilturner.aerialviews.services

import android.content.Context
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.utils.JsonHelper
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import me.kosert.flowbus.GlobalBus
import timber.log.Timber
import java.net.BindException

class KtorServer(
    val context: Context,
) {
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private var validTextSizes: List<Int> = emptyList()
    private var validTextWeights: List<Int> = emptyList()
    private val defaultTextSize = 18
    private val defaultTextWeight = 300

    init {
        loadValidationArrays()
    }

    private fun loadValidationArrays() {
        try {
            // Load text size values from XML
            val textSizeArray = context.resources.getStringArray(R.array.text_size_values)
            validTextSizes = textSizeArray.mapNotNull { it.toIntOrNull() }

            // Load text weight values from XML
            val textWeightArray = context.resources.getStringArray(R.array.text_weight_values)
            validTextWeights = textWeightArray.mapNotNull { it.toIntOrNull() }

            Timber.d("Loaded text size & weight values")
        } catch (e: Exception) {
            Timber.e(e, "Error loading validation arrays from XML, using defaults")
        }
    }

    fun start() {
        CoroutineScope(Dispatchers.IO).launch {
            Timber.i("Attempting to start Ktor server...")
            val port = GeneralPrefs.messageApiPort.toIntOrNull() ?: 8080
            try {
                server =
                    embeddedServer(CIO, port) {
                        configureRouting()
                        configurePlugins()
                    }.start(wait = false)
                Timber.i("Ktor server started on port $port")
            } catch (e: BindException) {
                Timber.e(e, "Failed to start server: Port $port already in use")
            } catch (e: Exception) {
                Timber.e(e, "Error starting Ktor server")
            }
        }
    }

    fun stop() {
        server?.stop(1000, 3000)
        Timber.i("Ktor server stopped")
    }

    private fun Application.configureRouting() {
        routing {
            get("/status") {
                call.respondText("Aerial Views message API is running", ContentType.Text.Plain)
            }

            post("/message/{messageNumber}") {
                val messageNumber = call.parameters["messageNumber"]?.toIntOrNull()
                if (messageNumber == null || messageNumber !in 1..4) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(error = "Invalid message number. Must be between 1 and 4."),
                    )
                    return@post
                }
                handleMessageRequest(call, messageNumber)
            }
        }
    }

    private suspend fun handleMessageRequest(
        call: ApplicationCall,
        messageNumber: Int,
    ) {
        try {
            val request = call.receive<MessageRequest>()

            val text = request.text
            if (text == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = "Required parameter 'text' is missing."))
                return
            }

            val isClearing = text.isEmpty()
            val duration = request.duration ?: 0

            // Validate textSize, if provided
            val textSize = request.textSize ?: defaultTextSize
            if (request.textSize != null && request.textSize !in validTextSizes) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(error = "Invalid textSize value: ${request.textSize}. Valid values are: $validTextSizes"),
                )
                return
            }

            // Validate textWeight, if provided
            val textWeight = request.textWeight ?: defaultTextWeight
            if (request.textWeight != null && request.textWeight !in validTextWeights) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(error = "Invalid textWeight value: ${request.textWeight}. Valid values are: $validTextWeights"),
                )
                return
            }

            val messageEvent =
                MessageEvent(
                    messageNumber,
                    text,
                    duration,
                    textSize,
                    textWeight,
                )
            GlobalBus.post(messageEvent)

            val actionType = if (isClearing) "cleared" else "received"
            Timber.i("Message $messageNumber $actionType - Text: '$text', Duration: ${duration}s, Size: $textSize, Weight: $textWeight")

            val successMessage = if (isClearing) "Message $messageNumber cleared successfully" else "Message $messageNumber processed successfully"
            call.respond(HttpStatusCode.OK, SuccessResponse(message = successMessage))
        } catch (e: Exception) {
            Timber.e(e, "Error processing message $messageNumber request")
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse(error = "An internal server error occurred."))
        }
    }

    private fun Application.configurePlugins() {
        install(ContentNegotiation) { JsonHelper.json }
    }
}

@Serializable
data class MessageRequest(
    val text: String?,
    val duration: Int? = null,
    val textSize: Int? = null,
    val textWeight: Int? = null,
)

@Serializable
data class SuccessResponse(
    val success: Boolean = true,
    val message: String,
)

@Serializable
data class ErrorResponse(
    val success: Boolean = false,
    val error: String,
)

data class MessageEvent(
    val messageNumber: Int = 1,
    val text: String = "",
    val duration: Int? = null,
    val textSize: Int? = null,
    val textWeight: Int? = null,
)
