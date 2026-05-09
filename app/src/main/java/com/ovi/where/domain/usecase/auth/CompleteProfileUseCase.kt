package com.ovi.where.domain.usecase.auth

import com.ovi.where.core.common.Resource
import com.ovi.where.domain.model.User
import com.ovi.where.domain.repository.AuthRepository
import javax.inject.Inject

class CompleteProfileUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(displayName: String, username: String, photoUrl: String? = null): Resource<User> =
        authRepository.completeProfile(displayName, username, photoUrl)
}
