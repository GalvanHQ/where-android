package com.ovi.where.domain.usecase.location

import com.ovi.where.core.common.Resource
import com.ovi.where.domain.repository.LocationRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StopLocationSharingUseCaseTest {

    private lateinit var locationRepository: LocationRepository
    private lateinit var stopLocationSharingUseCase: StopLocationSharingUseCase

    @Before
    fun setup() {
        locationRepository = mockk()
        stopLocationSharingUseCase = StopLocationSharingUseCase(locationRepository)
    }

    @Test
    fun `invoke returns success when sharing stops`() = runTest {
        val groupId = "group123"

        coEvery { locationRepository.stopLocationSharing(groupId) } returns Resource.Success(Unit)

        val result = stopLocationSharingUseCase(groupId)

        assertTrue(result is Resource.Success)
        coVerify { locationRepository.stopLocationSharing(groupId) }
    }

    @Test
    fun `invoke returns error when stop fails`() = runTest {
        val groupId = "group123"

        coEvery { locationRepository.stopLocationSharing(groupId) } returns Resource.Error("No active sharing session")

        val result = stopLocationSharingUseCase(groupId)

        assertTrue(result is Resource.Error)
        assertEquals("No active sharing session", (result as Resource.Error).message)
    }
}
