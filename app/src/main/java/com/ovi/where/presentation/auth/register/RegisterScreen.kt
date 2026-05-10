package com.ovi.where.presentation.auth.register

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ovi.where.R
import com.ovi.where.core.common.UiEvent
import com.ovi.where.core.theme.Dimens
import com.ovi.where.presentation.common.AnnotatedClickableText
import com.ovi.where.presentation.common.EmailTextField
import com.ovi.where.presentation.common.NameTextField
import com.ovi.where.presentation.common.PasswordTextField
import com.ovi.where.presentation.common.PrimaryButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onNavigateToLogin: () -> Unit,
    onRegisterSuccess: () -> Unit,
    onNavigateToEmailVerification: () -> Unit,
    onNavigateToCompleteProfile: () -> Unit,
    viewModel: RegisterViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    // ── Entrance animation ───────────────────────────────────────────────
    val contentAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        contentAlpha.animateTo(
            1f,
            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
        )
    }

    LaunchedEffect(key1 = true) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is UiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message.asString(context))
                is UiEvent.ShowToast -> snackbarHostState.showSnackbar(event.message.asString(context))
                is UiEvent.Navigate -> when (event.route) {
                    "home" -> onRegisterSuccess()
                    "email_verification" -> onNavigateToEmailVerification()
                    "complete_profile" -> onNavigateToCompleteProfile()
                }
                else -> Unit
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onNavigateToLogin) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = Dimens.spaceXLarge)
                .alpha(contentAlpha.value),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(Dimens.spaceLarge))

            // ── Header ───────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.PersonAdd,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }

            Spacer(modifier = Modifier.height(Dimens.spaceXLarge))

            Text(
                text = stringResource(R.string.action_create_account),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(Dimens.spaceSmall))

            Text(
                text = "Join Where and stay connected with your people.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(Dimens.space2XLarge))

            // ── Floating form fields ─────────────────────────────────────
            NameTextField(
                value = uiState.name,
                onValueChange = viewModel::onNameChange,
                label = "Full Name",
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading,
                errorMessage = uiState.nameError,
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                imeAction = ImeAction.Next
            )

            Spacer(modifier = Modifier.height(Dimens.spaceLarge))

            androidx.compose.material3.OutlinedTextField(
                value = uiState.username,
                onValueChange = viewModel::onUsernameChange,
                label = { Text("Username") },
                prefix = { Text("@") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading,
                isError = uiState.usernameError != null,
                supportingText = {
                    when {
                        uiState.usernameError != null -> Text(
                            text = uiState.usernameError!!,
                            color = MaterialTheme.colorScheme.error
                        )
                        uiState.isCheckingUsername -> Text(
                            text = "Checking availability...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        uiState.isUsernameAvailable == true -> Text(
                            text = "Username is available",
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                },
                trailingIcon = {
                    when {
                        uiState.isCheckingUsername -> androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        uiState.isUsernameAvailable == true -> Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        uiState.usernameError != null -> Icon(
                            Icons.Filled.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                shape = MaterialTheme.shapes.medium,
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Spacer(modifier = Modifier.height(Dimens.spaceLarge))

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

            Spacer(modifier = Modifier.height(Dimens.spaceLarge))

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

            Spacer(modifier = Modifier.height(Dimens.spaceLarge))

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

            // Error message
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

            Spacer(modifier = Modifier.height(Dimens.space3XLarge))

            AnnotatedClickableText(
                prefix = stringResource(R.string.msg_already_have_account),
                clickableText = stringResource(R.string.action_sign_in),
                onClick = onNavigateToLogin
            )

            Spacer(modifier = Modifier.height(Dimens.space2XLarge))
        }
    }
}
