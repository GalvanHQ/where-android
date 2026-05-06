package com.ovi.where.domain.usecase.location

import com.ovi.where.core.common.Resource
import com.ovi.where.domain.repository.LocationRepository
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StartLocationSharingUseCaseTest {

    private lateinit var locationRepository: LocationRepository
    private lateinit var startLocationSharingUseCase: StartLocationSharingUseCase

    @Before
    fun setup() {
        locationRepository = mockk()
        startLocationSharingUseCase = StartLocationSharingUseCase(locationRepository)
    }

    @Test
    fun `invoke returns success when sharing starts`() = runTest {
        val groupId = "group123"
        val duration = 60L
        
        coEvery { locationRepository.startLocationSharing(groupId, duration) } returns Resource.Success(Unit)

        val result = startLocationSharingUseCase(groupId, duration)

        assertTrue(result is Resource.Success)
        verify { locationRepository.startLocationSharing(groupId, duration) }
    }

    @Test
    fun `invoke returns error when group not found`() = runTest {
        val groupId = "invalid"
        val duration = 60L
        
        coEvery { locationRepository.startLocationSharing(groupId, duration) } returns Resource.Error("Group not found")

        val result = startLocationSharingUseCase(groupId, duration)

        assertTrue(result is Resource.Error)
        assertEquals((result as Resource.Error).message, "Group not found")
    }
}