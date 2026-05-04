package com.ovi.where.presentation.auth.register

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import com.ovi.where.R
import com.ovi.where.core.theme.Dimens
import com.ovi.where.core.utils.showToast
import com.ovi.where.presentation.common.EmailTextField
import com.ovi.where.presentation.common.NameTextField
import com.ovi.where.presentation.common.PasswordTextField
import com.ovi.where.presentation.common.PrimaryButton
import com.ovi.where.presentation.common.WhereTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onNavigateToLogin: () -> Unit,
    onRegisterSuccess: () -> Unit,
    viewModel: RegisterViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    
    LaunchedEffect(key1 = true) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is com.ovi.where.core.common.UiEvent.ShowToast -> {
                    context.showToast(event.message)
                }
                is com.ovi.where.core.common.UiEvent.ShowSnackbar -> {
                    context.showToast(event.message)
                }
                is com.ovi.where.core.common.UiEvent.Navigate -> {
                    if (event.route == "home") {
                        onRegisterSuccess()
                    }
                }
                is com.ovi.where.core.common.UiEvent.NavigateUp -> {
                    onNavigateToLogin()
                }
                else -> Unit
            }
        }
    }
    
    Scaffold(
        topBar = {
            WhereTopAppBar(
                title = stringResource(R.string.title_create_account),
                onNavigateBack = onNavigateToLogin
            )
        }
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(Dimens.spaceXLarge),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.msg_join_where),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                
                Spacer(modifier = Modifier.height(Dimens.spaceSmall))
                
                Text(
                    text = stringResource(R.string.msg_create_account_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(Dimens.space3XLarge))
                
                NameTextField(
                    value = uiState.name,
                    onValueChange = viewModel::onNameChange,
                    label = stringResource(R.string.label_name),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isLoading,
                    errorMessage = uiState.nameError,
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    imeAction = ImeAction.Next
                )
                
                Spacer(modifier = Modifier.height(Dimens.spaceMedium))
                
                EmailTextField(
                    value = uiState.email,
                    onValueChange = viewModel::onEmailChange,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isLoading,
                    errorMessage = uiState.emailError,
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    imeAction = ImeAction.Next
                )
                
                Spacer(modifier = Modifier.height(Dimens.spaceMedium))
                
                PasswordTextField(
                    value = uiState.password,
                    onValueChange = viewModel::onPasswordChange,
                    label = stringResource(R.string.label_password),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isLoading,
                    errorMessage = uiState.passwordError,
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    imeAction = ImeAction.Next
                )
                
                Spacer(modifier = Modifier.height(Dimens.spaceMedium))
                
                PasswordTextField(
                    value = uiState.confirmPassword,
                    onValueChange = viewModel::onConfirmPasswordChange,
                    label = stringResource(R.string.label_confirm_password),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isLoading,
                    errorMessage = uiState.confirmPasswordError,
                    keyboardActions = KeyboardActions(
                        onDone = { 
                            focusManager.clearFocus()
                            viewModel.onRegister() 
                        }
                    ),
                    imeAction = ImeAction.Done
                )
                
                uiState.error?.let { error ->
                    Spacer(modifier = Modifier.height(Dimens.spaceMedium))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
                
                Spacer(modifier = Modifier.height(Dimens.spaceXLarge))
                
                PrimaryButton(
                    text = stringResource(R.string.action_create_account),
                    onClick = viewModel::onRegister,
                    modifier = Modifier.fillMaxWidth(),
                    isLoading = uiState.isLoading
                )
                
                Spacer(modifier = Modifier.height(Dimens.spaceLarge))
                
                TextButton(onClick = onNavigateToLogin) {
                    Text(stringResource(R.string.msg_have_account_sign_in))
                }
            }
        }
    }
}
