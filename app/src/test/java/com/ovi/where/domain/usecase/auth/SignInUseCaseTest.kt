package com.ovi.where.domain.usecase.auth

import com.ovi.where.core.common.Resource
import com.ovi.where.domain.model.User
import com.ovi.where.domain.repository.AuthRepository
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SignInUseCaseTest {

    private lateinit var authRepository: AuthRepository
    private lateinit var signInUseCase: SignInUseCase

    @Before
    fun setup() {
        authRepository = mockk()
        signInUseCase = SignInUseCase(authRepository)
    }

    @Test
    fun `invoke returns success when login is successful`() = runTest {
        val email = "test@example.com"
        val password = "password123"
        val user = User(id = "user123", displayName = "Test User", email = email)
        
        coEvery { authRepository.signInWithEmail(email, password) } returns Resource.Success(user)

        val result = signInUseCase(email, password)

        assert(result is Resource.Success)
        assertEquals((result as Resource.Success).data.id, "user123")
        verify { authRepository.signInWithEmail(email, password) }
    }

    @Test
    fun `invoke returns error when login fails`() = runTest {
        val email = "test@example.com"
        val password = "wrongpassword"
        val errorMessage = "Invalid credentials"
        
        coEvery { authRepository.signInWithEmail(email, password) } returns Resource.Error(errorMessage)

        val result = signInUseCase(email, password)

        assert(result is Resource.Error)
        assertEquals((result as Resource.Error).message, errorMessage)
    }

    @Test
    fun `invoke returns error when exception occurs`() = runTest {
        val email = "test@example.com"
        val password = "password123"
        
        coEvery { authRepository.signInWithEmail(email, password) } returns Resource.Error("Network error")

        val result = signInUseCase(email, password)

        assert(result is Resource.Error)
    }
}