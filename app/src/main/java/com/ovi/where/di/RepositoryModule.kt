package com.ovi.where.di

import com.ovi.where.data.repository.AuthRepositoryImpl
import com.ovi.where.data.repository.ConversationRepositoryImpl
import com.ovi.where.data.repository.FriendshipRepositoryImpl
import com.ovi.where.data.repository.GroupRepositoryImpl
import com.ovi.where.data.repository.LocationRepositoryImpl
import com.ovi.where.data.repository.MessageRepositoryImpl
import com.ovi.where.data.repository.UserRepositoryImpl
import com.ovi.where.domain.repository.AuthRepository
import com.ovi.where.domain.repository.ConversationRepository
import com.ovi.where.domain.repository.FriendshipRepository
import com.ovi.where.domain.repository.GroupRepository
import com.ovi.where.domain.repository.LocationRepository
import com.ovi.where.domain.repository.MessageRepository
import com.ovi.where.domain.repository.UserRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository
    @Binds @Singleton abstract fun bindGroupRepository(impl: GroupRepositoryImpl): GroupRepository
    @Binds @Singleton abstract fun bindLocationRepository(impl: LocationRepositoryImpl): LocationRepository
    @Binds @Singleton abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository
    @Binds @Singleton abstract fun bindFriendshipRepository(impl: FriendshipRepositoryImpl): FriendshipRepository
    @Binds @Singleton abstract fun bindConversationRepository(impl: ConversationRepositoryImpl): ConversationRepository
    @Binds @Singleton abstract fun bindMessageRepository(impl: MessageRepositoryImpl): MessageRepository
}
