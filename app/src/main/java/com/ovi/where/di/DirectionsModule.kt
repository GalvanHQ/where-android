package com.ovi.where.di

import com.ovi.where.BuildConfig
import com.ovi.where.data.remote.directions.DirectionsApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Singleton

/**
 * Network plumbing for the directions feature.
 *
 * Routes through **our** Cloud Run backend (the same host the chat API
 * uses). The server proxies Google's Routes API, which keeps the
 * Routes-API key off-device entirely and avoids the Android-restricted
 * key auth headache.
 */
@Module
@InstallIn(SingletonComponent::class)
object DirectionsModule {

    @Provides
    @Singleton
    fun provideDirectionsOkHttpClient(): OkHttpClient {
        val logger = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .addInterceptor(logger)
            .build()
    }

    @Provides
    @Singleton
    fun provideDirectionsRetrofit(client: OkHttpClient): Retrofit {
        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            // Same Cloud Run base URL as the chat client — single
            // server, single auth source, single CORS / observability
            // story to maintain.
            .baseUrl(BuildConfig.CHAT_SERVER_HTTP_URL.trimEnd('/') + "/")
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideDirectionsApi(retrofit: Retrofit): DirectionsApi =
        retrofit.create(DirectionsApi::class.java)
}
