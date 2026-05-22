package com.ovi.where.di

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
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Network plumbing for the Google Directions API.
 *
 * Kept separate from [NetworkModule] / [AppModule] because:
 *  • The base URL is `https://maps.googleapis.com/`, distinct from our
 *    chat server.
 *  • We want a dedicated [Retrofit]/[OkHttpClient] qualifier so future
 *    Google APIs (Places, Geocoding) can share this same plumbing
 *    without colliding with the chat client's `okHttpClient` singleton.
 */
@Module
@InstallIn(SingletonComponent::class)
object DirectionsModule {

    @Qualifier
    @Retention(AnnotationRetention.BINARY)
    annotation class GoogleApis

    private const val GOOGLE_APIS_BASE_URL = "https://maps.googleapis.com/"

    @Provides
    @Singleton
    @GoogleApis
    fun provideGoogleApisOkHttpClient(): OkHttpClient {
        val logger = HttpLoggingInterceptor().apply {
            // BODY logs the response payload — fine for dev/debug. The
            // payload is the route geometry, no secrets beyond the API key
            // which is request-only.
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .addInterceptor(logger)
            .build()
    }

    @Provides
    @Singleton
    @GoogleApis
    fun provideGoogleApisRetrofit(@GoogleApis client: OkHttpClient): Retrofit {
        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(GOOGLE_APIS_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideDirectionsApi(@GoogleApis retrofit: Retrofit): DirectionsApi =
        retrofit.create(DirectionsApi::class.java)
}
