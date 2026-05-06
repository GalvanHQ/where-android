package com.ovi.where.data.remote.chat

import com.ovi.where.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.pingInterval
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object KtorApiClient {

    val HTTP_BASE_URL: String = BuildConfig.CHAT_SERVER_HTTP_URL
    val WS_BASE_URL: String   = BuildConfig.CHAT_SERVER_WS_URL

    val httpClient: HttpClient by lazy {
        HttpClient(Android) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                    isLenient = true
                })
            }
            install(Logging) {
                level = LogLevel.HEADERS
            }
            engine {
                connectTimeout = 10_000
                socketTimeout  = 15_000
            }
        }
    }

    val wsClient: HttpClient by lazy {
        HttpClient(Android) {
            install(WebSockets)
            install(Logging) {
                level = LogLevel.INFO
            }
            engine {
                connectTimeout = 10_000
                socketTimeout = 30_000
            }
        }
    }
}
