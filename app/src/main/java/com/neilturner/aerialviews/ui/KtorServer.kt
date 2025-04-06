package com.neilturner.aerialviews.ui

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.net.BindException

class KtorServer {
    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null

    fun start() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                server =
                    embeddedServer(Netty, port = 8080) {
                        configureRouting()
                        configurePlugins()
                    }.start(wait = true)
                Timber.i("Ktor server started on port 8080")
            } catch (e: BindException) {
                Timber.e(e, "Failed to start server: Port 8080 already in use")
            } catch (e: Exception) {
                Timber.e(e, "Error starting Ktor server")
            }
        }
    }

    fun stop() {
        server?.stop(1000, 2000)
        Timber.i("Ktor server stopped")
    }

    private fun Application.configureRouting() {
        routing {
            get("/") {
                var params = ""
                call.queryParameters.forEach { key, value ->
                    params += "$key = $value \n"
                }
                call.respondText("Hello from Android Ktor Server!\n$params")
            }

            get("/status") {
                call.respondText("Server is running")
            }

            // Add more routes as needed
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
