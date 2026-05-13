package com.ovi.where.di

import com.ovi.where.data.network.ConnectivityObserver
import com.ovi.where.data.network.NetworkConnectivityObserver
import com.ovi.where.data.remote.chat.ChatSocketIoClient
import dagger.Binds
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
    fun provideChatSocketIoClient(): ChatSocketIoClient = ChatSocketIoClient()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ConnectivityModule {

    @Binds
    @Singleton
    abstract fun bindConnectivityObserver(
        impl: NetworkConnectivityObserver
    ): ConnectivityObserver
}
