package com.ovi.where.di

import com.ovi.where.domain.repository.AuthRepository
import com.ovi.where.domain.repository.GroupRepository
import com.ovi.where.domain.repository.LocationRepository
import com.ovi.where.domain.usecase.auth.GoogleSignInUseCase
import com.ovi.where.domain.usecase.auth.ObserveCurrentUserUseCase
import com.ovi.where.domain.usecase.auth.RegisterUseCase
import com.ovi.where.domain.usecase.auth.SignInUseCase
import com.ovi.where.domain.usecase.auth.SignOutUseCase
import com.ovi.where.domain.usecase.group.CreateGroupUseCase
import com.ovi.where.domain.usecase.group.GetGroupUseCase
import com.ovi.where.domain.usecase.group.GetUserGroupsUseCase
import com.ovi.where.domain.usecase.group.JoinGroupUseCase
import com.ovi.where.domain.usecase.group.LeaveGroupUseCase
import com.ovi.where.domain.usecase.group.ObserveGroupMembersUseCase
import com.ovi.where.domain.usecase.location.ObserveGroupLocationsUseCase
import com.ovi.where.domain.usecase.location.StartLocationSharingUseCase
import com.ovi.where.domain.usecase.location.StopLocationSharingUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {

    @Provides
    fun provideSignInUseCase(authRepository: AuthRepository) = SignInUseCase(authRepository)

    @Provides
    fun provideRegisterUseCase(authRepository: AuthRepository) = RegisterUseCase(authRepository)

    @Provides
    fun provideObserveCurrentUserUseCase(authRepository: AuthRepository) = ObserveCurrentUserUseCase(authRepository)

    @Provides
    fun provideSignOutUseCase(authRepository: AuthRepository) = SignOutUseCase(authRepository)

    @Provides
    fun provideGoogleSignInUseCase(authRepository: AuthRepository) = GoogleSignInUseCase(authRepository)

    @Provides
    fun provideCreateGroupUseCase(groupRepository: GroupRepository) = CreateGroupUseCase(groupRepository)

    @Provides
    fun provideGetUserGroupsUseCase(groupRepository: GroupRepository) = GetUserGroupsUseCase(groupRepository)

    @Provides
    fun provideGetGroupUseCase(groupRepository: GroupRepository) = GetGroupUseCase(groupRepository)

    @Provides
    fun provideJoinGroupUseCase(groupRepository: GroupRepository) = JoinGroupUseCase(groupRepository)

    @Provides
    fun provideLeaveGroupUseCase(groupRepository: GroupRepository) = LeaveGroupUseCase(groupRepository)

    @Provides
    fun provideObserveGroupMembersUseCase(groupRepository: GroupRepository) = ObserveGroupMembersUseCase(groupRepository)

    @Provides
    fun provideStartLocationSharingUseCase(locationRepository: LocationRepository) = StartLocationSharingUseCase(locationRepository)

    @Provides
    fun provideStopLocationSharingUseCase(locationRepository: LocationRepository) = StopLocationSharingUseCase(locationRepository)

    @Provides
    fun provideObserveGroupLocationsUseCase(locationRepository: LocationRepository) = ObserveGroupLocationsUseCase(locationRepository)
}
