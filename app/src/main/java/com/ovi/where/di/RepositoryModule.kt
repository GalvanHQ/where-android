package com.ovi.where.di

import com.ovi.where.data.repository.AuthRepositoryImpl
import com.ovi.where.data.repository.GroupRepositoryImpl
import com.ovi.where.data.repository.LocationRepositoryImpl
import com.ovi.where.data.repository.UserRepositoryImpl
import com.ovi.where.domain.repository.AuthRepository
import com.ovi.where.domain.repository.GroupRepository
import com.ovi.where.domain.repository.LocationRepository
import com.ovi.where.domain.repository.UserRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        authRepositoryImpl: AuthRepositoryImpl
    ): AuthRepository

    @Binds
    @Singleton
    abstract fun bindGroupRepository(
        groupRepositoryImpl: GroupRepositoryImpl
    ): GroupRepository

    @Binds
    @Singleton
    abstract fun bindLocationRepository(
        locationRepositoryImpl: LocationRepositoryImpl
    ): LocationRepository

    @Binds
    @Singleton
    abstract fun bindUserRepository(
        userRepositoryImpl: UserRepositoryImpl
    ): UserRepository
}
