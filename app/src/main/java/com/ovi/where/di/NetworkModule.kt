package com.ovi.where.di

import com.ovi.where.data.remote.chat.ChatWebSocketClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideChatWebSocketClient(): ChatWebSocketClient = ChatWebSocketClient()
}
