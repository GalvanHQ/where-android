package com.ovi.where.core.common

sealed class UiEvent {
    data class ShowToast(val message: UiText) : UiEvent()
    data class ShowSnackbar(val message: UiText) : UiEvent()
    data class Navigate(val route: String) : UiEvent()
    object NavigateUp : UiEvent()
    data class ShareContent(val title: String, val content: String) : UiEvent()
    object LaunchGoogleSignIn : UiEvent()
}
