package com.ovi.where.domain.usecase.auth

import com.ovi.where.core.common.Resource
import com.ovi.where.domain.model.User
import com.ovi.where.domain.repository.AuthRepository
import javax.inject.Inject

class UpdateBioUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(bio: String): Resource<User> = authRepository.updateBio(bio)
}

class UpdateUsernameUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(username: String): Resource<User> = authRepository.updateUsername(username)
}

class CheckUsernameAvailableUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(username: String): Boolean = authRepository.checkUsernameAvailable(username)
}
