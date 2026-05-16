package com.ovi.where.domain.usecase.location

import com.ovi.where.core.common.Resource
import com.ovi.where.domain.model.SharedLocation
import com.ovi.where.domain.repository.LocationRepository
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ObserveGroupLocationsUseCaseTest {

    private lateinit var locationRepository: LocationRepository
    private lateinit var observeGroupLocationsUseCase: ObserveGroupLocationsUseCase

    @Before
    fun setup() {
        locationRepository = mockk()
        observeGroupLocationsUseCase = ObserveGroupLocationsUseCase(locationRepository)
    }

    @Test
    fun `invoke returns flow of locations`() = runTest {
        val groupId = "group123"
        val locations = listOf(
            SharedLocation(
                id = "loc1",
                userId = "user1",
                displayName = "User 1",
                latitude = 40.7128,
                longitude = -74.0060,
                timestamp = System.currentTimeMillis()
            )
        )
        
        coEvery { locationRepository.observeGroupLocations(groupId) } returns flowOf(locations)

        val result = observeGroupLocationsUseCase(groupId)

        result.collect { collectedLocations ->
            assertTrue(collectedLocations.isNotEmpty())
            assertTrue(collectedLocations.first().userId == "user1")
        }
        verify { locationRepository.observeGroupLocations(groupId) }
    }

    @Test
    fun `invoke returns empty list when no locations`() = runTest {
        val groupId = "group123"
        
        coEvery { locationRepository.observeGroupLocations(groupId) } returns flowOf(emptyList())

        val result = observeGroupLocationsUseCase(groupId)

        result.collect { collectedLocations ->
            assertTrue(collectedLocations.isEmpty())
        }
    }
}