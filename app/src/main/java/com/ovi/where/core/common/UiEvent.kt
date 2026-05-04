package com.ovi.where.core.common

sealed class UiEvent {
    data class ShowToast(val message: UiText) : UiEvent()
    data class ShowSnackbar(val message: UiText) : UiEvent()
    data class ShowError(val message: UiText) : UiEvent()
    data class Navigate(val route: String) : UiEvent()
    data class NavigateWithArgs(val route: String, val args: Map<String, Any>) : UiEvent()
    object NavigateUp : UiEvent()
    object NavigateBack : UiEvent()
    data class PermissionRequired(val permissions: List<String>) : UiEvent()
    data class OpenUrl(val url: String) : UiEvent()
    data class ShareContent(val title: String, val content: String) : UiEvent()
    object LaunchGoogleSignIn : UiEvent()
}
