package com.ovi.where.presentation.auth.register

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import com.ovi.where.R
import com.ovi.where.core.common.UiEvent
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
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(key1 = true) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is UiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message.asString(context))
                is UiEvent.Navigate    -> if (event.route == "home") onRegisterSuccess()
                is UiEvent.NavigateUp  -> onNavigateToLogin()
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
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
                Spacer(Modifier.height(Dimens.spaceSmall))
                Text(
                    text = stringResource(R.string.msg_create_account_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(Dimens.space3XLarge))

                // Display name
                NameTextField(
                    value = uiState.name,
                    onValueChange = viewModel::onNameChange,
                    label = stringResource(R.string.label_name),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isLoading,
                    errorMessage = uiState.nameError,
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    imeAction = ImeAction.Next
                )

                Spacer(Modifier.height(Dimens.spaceMedium))

                // @username field
                OutlinedTextField(
                    value = uiState.username,
                    onValueChange = viewModel::onUsernameChange,
                    label = { Text("@username") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isLoading,
                    singleLine = true,
                    isError = uiState.usernameError != null,
                    supportingText = {
                        when {
                            uiState.usernameError != null -> Text(
                                uiState.usernameError!!,
                                color = MaterialTheme.colorScheme.error
                            )
                            uiState.isCheckingUsername -> Text(
                                "Checking availability…",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            uiState.username.length >= 3 && uiState.usernameError == null &&
                            !uiState.isCheckingUsername -> Text(
                                "@${uiState.username} is available!",
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    },
                    leadingIcon = {
                        Icon(Icons.Default.AlternateEmail, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    trailingIcon = {
                        when {
                            uiState.isCheckingUsername -> CircularProgressIndicator(
                                modifier = Modifier.size(Dimens.iconSizeMedium),
                                strokeWidth = Dimens.strokeWidthThin
                            )
                            uiState.username.length >= 3 && uiState.usernameError == null ->
                                Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.tertiary)
                            uiState.usernameError != null ->
                                Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error)
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next,
                        capitalization = KeyboardCapitalization.None
                    ),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        errorBorderColor     = MaterialTheme.colorScheme.error
                    )
                )

                Spacer(Modifier.height(Dimens.spaceMedium))

                // Email
                EmailTextField(
                    value = uiState.email,
                    onValueChange = viewModel::onEmailChange,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isLoading,
                    errorMessage = uiState.emailError,
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    imeAction = ImeAction.Next
                )

                Spacer(Modifier.height(Dimens.spaceMedium))

                // Password
                PasswordTextField(
                    value = uiState.password,
                    onValueChange = viewModel::onPasswordChange,
                    label = stringResource(R.string.label_password),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isLoading,
                    errorMessage = uiState.passwordError,
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    imeAction = ImeAction.Next
                )

                Spacer(Modifier.height(Dimens.spaceMedium))

                // Confirm password
                PasswordTextField(
                    value = uiState.confirmPassword,
                    onValueChange = viewModel::onConfirmPasswordChange,
                    label = stringResource(R.string.label_confirm_password),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isLoading,
                    errorMessage = uiState.confirmPasswordError,
                    keyboardActions = KeyboardActions(onDone = {
                        focusManager.clearFocus()
                        viewModel.onRegister()
                    }),
                    imeAction = ImeAction.Done
                )

                uiState.error?.let { error ->
                    Spacer(Modifier.height(Dimens.spaceMedium))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(Modifier.height(Dimens.spaceXLarge))

                PrimaryButton(
                    text = stringResource(R.string.action_create_account),
                    onClick = viewModel::onRegister,
                    modifier = Modifier.fillMaxWidth(),
                    isLoading = uiState.isLoading,
                    enabled = !uiState.isCheckingUsername
                )

                Spacer(Modifier.height(Dimens.spaceLarge))

                TextButton(onClick = onNavigateToLogin) {
                    Text(stringResource(R.string.msg_have_account_sign_in))
                }
            }
        }
    }
}
