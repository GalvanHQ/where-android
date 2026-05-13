package com.ovi.where.domain.usecase.auth

import com.ovi.where.domain.repository.AuthRepository
import javax.inject.Inject

class ResetPasswordUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    operator fun invoke(email: String) = authRepository.resetPassword(email)
}
