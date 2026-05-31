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

class UpdateHomeUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(
        latitude: Double,
        longitude: Double,
        label: String
    ): Resource<User> = authRepository.updateHome(latitude, longitude, label)
}

class UpdateSocialLinksUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(
        facebookUrl: String,
        instagramUrl: String,
        linkedinUrl: String
    ): Resource<User> = authRepository.updateSocialLinks(facebookUrl, instagramUrl, linkedinUrl)
}
