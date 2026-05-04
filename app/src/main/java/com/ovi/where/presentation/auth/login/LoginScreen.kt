package com.ovi.where.presentation.auth.login

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.ovi.where.R
import com.ovi.where.core.common.UiEvent
import com.ovi.where.core.theme.Dimens
import com.ovi.where.presentation.common.AnnotatedClickableText
import com.ovi.where.presentation.common.DividerText
import com.ovi.where.presentation.common.EmailTextField
import com.ovi.where.presentation.common.GoogleSignInButton
import com.ovi.where.presentation.common.PasswordTextField
import com.ovi.where.presentation.common.PrimaryButton

@Composable
fun LoginScreen(
    onNavigateToRegister: () -> Unit,
    onLoginSuccess: () -> Unit,
    onNavigateToForgotPassword: () -> Unit = {},
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(Exception::class.java)
                viewModel.onGoogleSignInResult(account?.idToken)
            } catch (e: Exception) {
                viewModel.onGoogleSignInResult(null)
            }
        } else {
            viewModel.onGoogleSignInResult(null)
        }
    }

    val googleSignInClient = remember {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    LaunchedEffect(key1 = true) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is UiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message.asString(context))
                is UiEvent.ShowToast -> snackbarHostState.showSnackbar(event.message.asString(context))
                is UiEvent.Navigate -> if (event.route == "home") onLoginSuccess()
                is UiEvent.LaunchGoogleSignIn -> googleSignInLauncher.launch(googleSignInClient.signInIntent)
                else -> Unit
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.fillMaxWidth()
            )
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
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(Dimens.spaceSmall))

                Text(
                    text = stringResource(R.string.app_tagline),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(Dimens.space3XLarge))

                EmailTextField(
                    value = uiState.email,
                    onValueChange = viewModel::onEmailChange,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isLoading,
                    errorMessage = uiState.emailError,
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
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
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            viewModel.onSignIn()
                        }
                    ),
                    imeAction = ImeAction.Done
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onNavigateToForgotPassword) {
                        Text(
                            text = stringResource(R.string.label_forgot_password),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                uiState.error?.let { error ->
                    Spacer(modifier = Modifier.height(Dimens.spaceSmall))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(Dimens.spaceMedium))

                PrimaryButton(
                    text = stringResource(R.string.action_sign_in),
                    onClick = viewModel::onSignIn,
                    modifier = Modifier.fillMaxWidth(),
                    isLoading = uiState.isLoading
                )

                Spacer(modifier = Modifier.height(Dimens.spaceLarge))

                DividerText(text = stringResource(R.string.label_or))

                Spacer(modifier = Modifier.height(Dimens.spaceLarge))

                GoogleSignInButton(
                    onClick = viewModel::onGoogleSignIn,
                    modifier = Modifier.fillMaxWidth(),
                    isLoading = uiState.isGoogleLoading,
                    enabled = !uiState.isLoading
                )

                Spacer(modifier = Modifier.height(Dimens.spaceXLarge))

                AnnotatedClickableText(
                    prefix = stringResource(R.string.msg_already_have_account),
                    clickableText = stringResource(R.string.action_sign_up),
                    onClick = onNavigateToRegister
                )
            }
        }
    }
}
