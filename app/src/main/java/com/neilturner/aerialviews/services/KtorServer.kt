package com.neilturner.aerialviews.services

import android.content.Context
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.data.network.JsonHelper
import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.application.serverConfig
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.Serializable
import timber.log.Timber
import java.net.BindException

/**
 * Process-wide owner of the bundled message API server.
 *
 * The server binds a fixed TCP port, so at most one instance may exist per process. A
 * [com.neilturner.aerialviews.ui.core.ScreenController] is created and destroyed on every
 * screensaver start/stop; an un-synchronised start/stop pair let the outgoing server keep its
 * socket while the incoming one tried to bind the same port, which the CIO engine reports as a
 * [BindException] from a coroutine this app does not own.
 *
 * The start/stop ordering itself lives in [KtorServerLifecycle], which is free of Android and
 * Ktor types so it can be unit tested; this object only supplies the CIO engine and the logging.
 */
object KtorServer {
    private const val DEFAULT_PORT = 8081
    private const val BIND_ATTEMPTS = 5
    private const val BIND_RETRY_DELAY_MS = 400L
    private const val SHUTDOWN_GRACE_MS = 500L
    private const val SHUTDOWN_TIMEOUT_MS = 2000L

    /**
     * Outlives any single screensaver session on purpose: this is a singleton, so its scope
     * must stay usable for the next start.
     */
    private val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("KtorServer"))

    private val lifecycle =
        KtorServerLifecycle<EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>>(
            scope = serverScope,
            portProvider = { resolveServerPort(GeneralPrefs.messageApiPort, DEFAULT_PORT) },
            buildServer = ::buildServer,
            startServer = { it.start(wait = false) },
            stopServer = { it.stop(SHUTDOWN_GRACE_MS, SHUTDOWN_TIMEOUT_MS) },
            discardServer = ::discardServer,
            bindAttempts = BIND_ATTEMPTS,
            bindRetryDelayMs = BIND_RETRY_DELAY_MS,
            onResult = ::logResult,
            onBindRetry = { port, attempt, attempts, cause ->
                Timber.w("Port $port unavailable (attempt $attempt/$attempts): ${cause.message}")
            },
            onStopping = { Timber.i("Stopping Ktor server...") },
            onStopped = { Timber.i("Ktor server stopped, port released") },
        )

    @Volatile
    private var onMessageReceived: (MessageEvent) -> Unit = {}

    private var appContext: Context? = null
    private var validTextSizes: List<Int> = emptyList()
    private var validTextWeights: List<Int> = emptyList()
    private val defaultTextSize = 18
    private val defaultTextWeight = 300

    /**
     * Starts the server, replacing any instance left over from a previous session.
     *
     * Safe to call while a previous [stop] is still in flight: the two are serialised, and the
     * outgoing server is always shut down before the new socket is bound.
     */
    fun start(
        context: Context,
        onMessageReceived: (MessageEvent) -> Unit,
    ) {
        this.onMessageReceived = onMessageReceived
        this.appContext = context.applicationContext
        loadValidationArrays()

        lifecycle.start()
    }

    /**
     * Shuts the server down and releases the port. Safe to call when nothing is running, and
     * safe to call more than once.
     */
    fun stop() {
        lifecycle.stop()
    }

    private fun logResult(result: ServerStartResult) {
        when (result) {
            is ServerStartResult.Started -> {
                Timber.i("Ktor server listening on port ${result.port}")
            }

            is ServerStartResult.PortUnavailable -> {
                Timber.e("Ktor server not started: port ${result.port} unavailable after ${result.attempts} attempts")
            }

            is ServerStartResult.Failed -> {
                Timber.e(result.error, "Error starting Ktor server on port ${result.port}")
            }

            ServerStartResult.Superseded -> {
                Timber.d("Ktor server lifecycle request superseded")
            }
        }
    }

    private fun buildServer(port: Int): EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration> {
        val config =
            serverConfig {
                // The CIO engine rethrows a bind failure from the accept coroutine, which is a
                // root coroutine. Without a handler in its parent context that reaches the
                // default uncaught handler and kills the process.
                parentCoroutineContext =
                    CoroutineExceptionHandler { _, throwable ->
                        Timber.e(throwable, "Ktor server coroutine failure")
                    }
                module {
                    configurePlugins()
                    configureRouting()
                }
            }

        return embeddedServer(CIO, config) {
            connector {
                host = "0.0.0.0"
                this.port = port
            }
        }
    }

    /** Best-effort teardown of a server that failed to start. Never throws. */
    private fun discardServer(candidate: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>) {
        runCatching { candidate.stop(0, 0) }
            .onFailure { Timber.w(it, "Failed to clean up server that did not start") }
    }

    private fun loadValidationArrays() {
        val context = appContext ?: return
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

            val type =
                OverlayType.entries.firstOrNull { it.name == "MESSAGE$messageNumber" } ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = "Invalid message number."))
                    return
                }

            val messageEvent =
                MessageEvent(
                    type,
                    text,
                    duration,
                    textSize,
                    textWeight,
                )
            onMessageReceived(messageEvent)

            val actionType = if (isClearing) "cleared" else "received"
            Timber.i("Message $messageNumber $actionType - Text: '$text', Duration: ${duration}s, Size: $textSize, Weight: $textWeight")

            val successMessage =
                if (isClearing) {
                    "Message $messageNumber cleared successfully"
                } else {
                    "Message $messageNumber processed successfully"
                }
            call.respond(HttpStatusCode.OK, SuccessResponse(message = successMessage))
        } catch (e: Exception) {
            Timber.e(e, "Error processing message $messageNumber request")
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse(error = "An internal server error occurred."))
        }
    }

    private fun Application.configurePlugins() {
        install(ContentNegotiation) { json(JsonHelper.json) }
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
    val type: OverlayType = OverlayType.EMPTY,
    val text: String = "",
    val duration: Int? = null,
    val textSize: Int? = null,
    val textWeight: Int? = null,
)
