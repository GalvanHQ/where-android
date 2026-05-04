package com.ovi.where.presentation.group.create

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ovi.where.R
import com.ovi.where.core.common.Resource
import com.ovi.where.core.common.UiEvent
import com.ovi.where.core.common.UiText
import com.ovi.where.domain.usecase.group.CreateGroupUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CreateGroupViewModel @Inject constructor(
    application: Application,
    private val createGroupUseCase: CreateGroupUseCase
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(CreateGroupUiState())
    val uiState: StateFlow<CreateGroupUiState> = _uiState.asStateFlow()

    private val _uiEvent = Channel<UiEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()

    fun onNameChange(name: String) {
        _uiState.value = _uiState.value.copy(
            name = name,
            nameError = null
        )
    }
    
    fun onDescriptionChange(desc: String) {
        _uiState.value = _uiState.value.copy(description = desc)
    }

    fun onCreateGroup() {
        val state = _uiState.value
        
        if (!validateInput(state)) return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            when (val result = createGroupUseCase(state.name, state.description)) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        inviteCode = result.data?.inviteCode ?: ""
                    )
                    _uiEvent.send(UiEvent.NavigateUp)
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message
                    )
                    _uiEvent.send(UiEvent.ShowSnackbar(UiText.DynamicString(result.message ?: getApplication<Application>().getString(R.string.error_failed_create_group))))
                }
                is Resource.Loading -> {
                    _uiState.value = _uiState.value.copy(isLoading = true)
                }
            }
        }
    }
    
    private fun validateInput(state: CreateGroupUiState): Boolean {
        if (state.name.isBlank()) {
            _uiState.value = _uiState.value.copy(nameError = getApplication<Application>().getString(R.string.error_group_name_required))
            return false
        }
        if (state.name.length < 3) {
            _uiState.value = _uiState.value.copy(nameError = getApplication<Application>().getString(R.string.error_group_name_too_short))
            return false
        }
        return true
    }
}

data class CreateGroupUiState(
    val name: String = "",
    val description: String = "",
    val nameError: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val inviteCode: String = ""
)
