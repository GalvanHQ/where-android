package com.ovi.where.domain.usecase.auth

import com.ovi.where.core.common.Resource
import com.ovi.where.domain.repository.AuthRepository
import javax.inject.Inject

class SendEmailVerificationUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(): Resource<Unit> =
        authRepository.sendEmailVerification()
}
