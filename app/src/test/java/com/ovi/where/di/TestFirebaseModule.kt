package com.ovi.where.di

import com.google.firebase.functions.FirebaseFunctions
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import io.mockk.mockk
import javax.inject.Singleton

/**
 * Replaces [AppModule]'s `FirebaseFunctions` provider in Hilt-based unit tests.
 * Inject the mock via `@Inject lateinit var functions: FirebaseFunctions` in
 * your test class and configure behaviour with MockK.
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [AppModule::class]
)
object TestFirebaseModule {

    @Provides
    @Singleton
    fun provideFirebaseFunctions(): FirebaseFunctions = mockk(relaxed = true)
}
