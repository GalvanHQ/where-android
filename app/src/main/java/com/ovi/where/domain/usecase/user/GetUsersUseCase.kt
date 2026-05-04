package com.ovi.where.domain.usecase.user

import com.ovi.where.core.common.Resource
import com.ovi.where.domain.model.User
import com.ovi.where.domain.repository.UserRepository
import javax.inject.Inject

class GetUsersUseCase @Inject constructor(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(userIds: List<String>): Resource<List<User>> {
        if (userIds.isEmpty()) return Resource.Success(emptyList())
        return userRepository.getUsers(userIds)
    }
}
