package com.ovi.where.domain.usecase.group

import com.ovi.where.core.common.Resource
import com.ovi.where.domain.model.Group
import com.ovi.where.domain.repository.GroupRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CreateGroupUseCaseTest {

    private lateinit var groupRepository: GroupRepository
    private lateinit var createGroupUseCase: CreateGroupUseCase

    @Before
    fun setup() {
        groupRepository = mockk()
        createGroupUseCase = CreateGroupUseCase(groupRepository)
    }

    @Test
    fun `invoke returns success when group is created`() = runTest {
        val name = "Test Group"
        val description = "Test Description"
        val group = Group(
            id = "group123",
            name = name,
            description = description,
            createdBy = "user123"
        )
        
        coEvery { groupRepository.createGroup(name, description, null) } returns Resource.Success(group)

        val result = createGroupUseCase(name, description)

        assertTrue(result is Resource.Success)
        assertEquals((result as Resource.Success).data?.name, name)
        coVerify { groupRepository.createGroup(name, description, null) }
    }

    @Test
    fun `invoke returns error when creation fails`() = runTest {
        val name = "Test Group"
        val description = "Test Description"
        
        coEvery { groupRepository.createGroup(name, description, null) } returns Resource.Error("Name required")

        val result = createGroupUseCase(name, description)

        assertTrue(result is Resource.Error)
        assertEquals((result as Resource.Error).message, "Name required")
    }

    @Test
    fun `invoke returns error on exception`() = runTest {
        val name = "Test Group"
        val description = "Test Description"
        
        coEvery { groupRepository.createGroup(name, description, null) } returns Resource.Error("Network error")

        val result = createGroupUseCase(name, description)

        assertTrue(result is Resource.Error)
    }
}
