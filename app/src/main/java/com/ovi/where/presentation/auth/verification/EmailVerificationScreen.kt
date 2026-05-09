package com.ovi.where.presentation.auth.verification

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ovi.where.core.theme.Dimens

@Composable
fun EmailVerificationScreen(
    onVerified: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: EmailVerificationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()

    // Navigate when verified
    LaunchedEffect(uiState.isVerified) {
        if (uiState.isVerified) onVerified()
    }

    // Show messages
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(Dimens.spaceXLarge),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Premium Logo Header
                Icon(
                    imageVector = Icons.Rounded.LocationOn,
                    contentDescription = "Logo",
                    modifier = Modifier
                        .size(64.dp)
                        .padding(bottom = Dimens.spaceMedium),
                    tint = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "Verify Email",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = Dimens.space3XLarge)
                )

                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(Dimens.space2XLarge),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Icon
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = MaterialTheme.shapes.extraLarge
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.MarkEmailRead,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Spacer(modifier = Modifier.height(Dimens.space2XLarge))

                        Text(
                            text = "Check your inbox",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(Dimens.spaceMedium))

                        Text(
                            text = "We've sent a verification link to your email address. Please click the link to verify your account.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(Dimens.space3XLarge))

                        // Check verification button
                        Button(
                            onClick = { viewModel.checkVerificationStatus() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(Dimens.buttonHeight),
                            enabled = !uiState.isChecking,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            if (uiState.isChecking) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(Dimens.iconSizeMedium),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text("I've verified my email")
                            }
                        }

                        Spacer(modifier = Modifier.height(Dimens.spaceLarge))

                        // Resend button
                        OutlinedButton(
                            onClick = { viewModel.resendVerificationEmail() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(Dimens.buttonHeight),
                            enabled = !uiState.isResending,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            if (uiState.isResending) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(Dimens.iconSizeMedium),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Text("Resend link")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Dimens.space2XLarge))

                // Sign out / use different account
                TextButton(
                    onClick = {
                        viewModel.signOut()
                        onSignOut()
                    }
                ) {
                    Text(
                        text = "Use a different account",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
