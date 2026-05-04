package com.ovi.where.presentation.group.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ovi.where.core.common.Resource
import com.ovi.where.core.common.UiEvent
import com.ovi.where.core.common.UiText
import com.ovi.where.domain.usecase.group.GetGroupUseCase
import com.ovi.where.domain.usecase.group.UpdateGroupUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.ovi.where.R

@HiltViewModel
class EditGroupViewModel @Inject constructor(
    private val getGroupUseCase: GetGroupUseCase,
    private val updateGroupUseCase: UpdateGroupUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditGroupUiState())
    val uiState: StateFlow<EditGroupUiState> = _uiState.asStateFlow()

    private val _uiEvent = Channel<UiEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()

    fun loadGroup(groupId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            when (val result = getGroupUseCase(groupId)) {
                is Resource.Success -> {
                    val group = result.data
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        name = group?.name ?: "",
                        description = group?.description ?: ""
                    )
                }
                is Resource.Error -> _uiState.value = _uiState.value.copy(isLoading = false)
                else -> {}
            }
        }
    }

    fun onNameChange(name: String) {
        _uiState.value = _uiState.value.copy(name = name, nameError = null)
    }

    fun onDescriptionChange(description: String) {
        _uiState.value = _uiState.value.copy(description = description)
    }

    fun onSave(groupId: String) {
        val name = _uiState.value.name.trim()
        if (name.isBlank()) {
            _uiState.value = _uiState.value.copy(nameError = "Group name is required")
            return
        }
        if (name.length < 3) {
            _uiState.value = _uiState.value.copy(nameError = "Name must be at least 3 characters")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            when (updateGroupUseCase(groupId, name, _uiState.value.description)) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    _uiEvent.send(UiEvent.ShowSnackbar(UiText.DynamicString("Group updated")))
                    _uiEvent.send(UiEvent.NavigateBack)
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    _uiEvent.send(UiEvent.ShowSnackbar(UiText.DynamicString("Failed to update group")))
                }
                else -> {}
            }
        }
    }
}

data class EditGroupUiState(
    val name: String = "",
    val description: String = "",
    val nameError: String? = null,
    val isLoading: Boolean = false
)
