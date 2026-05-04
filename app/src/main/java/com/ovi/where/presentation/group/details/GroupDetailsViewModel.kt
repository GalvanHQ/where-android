package com.ovi.where.presentation.group.details

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ovi.where.R
import com.ovi.where.core.common.Resource
import com.ovi.where.domain.usecase.group.GetGroupUseCase
import com.ovi.where.domain.usecase.group.LeaveGroupUseCase
import com.ovi.where.domain.usecase.group.ObserveGroupMembersUseCase
import com.ovi.where.presentation.model.GroupMemberUiModel
import com.ovi.where.presentation.model.GroupUiModel
import com.ovi.where.presentation.model.toUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GroupDetailsViewModel @Inject constructor(
    application: Application,
    private val getGroupUseCase: GetGroupUseCase,
    private val observeGroupMembersUseCase: ObserveGroupMembersUseCase,
    private val leaveGroupUseCase: LeaveGroupUseCase
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(GroupDetailsUiState())
    val uiState: StateFlow<GroupDetailsUiState> = _uiState.asStateFlow()

    fun loadGroupDetails(groupId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val result = getGroupUseCase(groupId)) {
                is Resource.Success -> {
                    val group = result.data
                    val resources = getApplication<Application>().resources
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        group = group?.toUiModel(
                            memberCountText = resources.getQuantityString(
                                R.plurals.member_count,
                                group.memberCount,
                                group.memberCount
                            )
                        ),
                        inviteCode = group?.inviteCode ?: "",
                        groupName = group?.name ?: "",
                        error = null
                    )
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message
                    )
                }
                is Resource.Loading -> {
                    _uiState.value = _uiState.value.copy(isLoading = true)
                }
            }
        }
    }

    fun observeMembers(groupId: String) {
        viewModelScope.launch {
            val adminText = getApplication<Application>().getString(R.string.status_admin)
            val memberText = getApplication<Application>().getString(R.string.status_member)
            observeGroupMembersUseCase(groupId).collect { members ->
                _uiState.value = _uiState.value.copy(
                    members = members.map { it.toUiModel(adminText = adminText, memberText = memberText) }
                )
            }
        }
    }
    
    fun leaveGroup(groupId: String) {
        viewModelScope.launch {
            leaveGroupUseCase(groupId)
        }
    }
}

data class GroupDetailsUiState(
    val group: GroupUiModel? = null,
    val members: List<GroupMemberUiModel> = emptyList(),
    val inviteCode: String = "",
    val groupName: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)
