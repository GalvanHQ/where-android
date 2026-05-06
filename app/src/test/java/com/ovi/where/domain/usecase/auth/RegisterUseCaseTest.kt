package com.ovi.where.domain.usecase.auth

import com.ovi.where.core.common.Resource
import com.ovi.where.domain.model.User
import com.ovi.where.domain.repository.AuthRepository
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RegisterUseCaseTest {

    private lateinit var authRepository: AuthRepository
    private lateinit var registerUseCase: RegisterUseCase

    @Before
    fun setup() {
        authRepository = mockk()
        registerUseCase = RegisterUseCase(authRepository)
    }

    @Test
    fun `invoke returns success when registration is successful`() = runTest {
        val name = "Test User"
        val email = "test@example.com"
        val password = "password123"
        val user = User(id = "user123", displayName = name, email = email)
        
        coEvery { authRepository.registerWithEmail(name, email, password) } returns Resource.Success(user)

        val result = registerUseCase(name, email, password)

        assertTrue(result is Resource.Success)
        assertEquals((result as Resource.Success).data.id, "user123")
    }

    @Test
    fun `invoke returns error when email already exists`() = runTest {
        val name = "Test User"
        val email = "existing@example.com"
        val password = "password123"
        val errorMessage = "Email already in use"
        
        coEvery { authRepository.registerWithEmail(name, email, password) } returns Resource.Error(errorMessage)

        val result = registerUseCase(name, email, password)

        assertTrue(result is Resource.Error)
        assertEquals((result as Resource.Error).message, errorMessage)
    }
}