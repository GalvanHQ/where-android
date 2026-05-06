package com.ovi.where.data.remote.chat

import com.ovi.where.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

object KtorApiClient {

    val HTTP_BASE_URL: String = BuildConfig.CHAT_SERVER_HTTP_URL
    val WS_BASE_URL: String   = BuildConfig.CHAT_SERVER_WS_URL

    private val okHttpClient = OkHttpClient()

    val httpClient: HttpClient by lazy {
        HttpClient(OkHttp) {
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
                preconfigured = okHttpClient
            }
        }
    }

    val wsClient: HttpClient by lazy {
        HttpClient(OkHttp) {
            install(WebSockets)
            install(Logging) {
                level = LogLevel.INFO
            }
            engine {
                preconfigured = okHttpClient
            }
        }
    }
}
