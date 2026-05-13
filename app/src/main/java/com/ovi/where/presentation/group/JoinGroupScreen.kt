package com.ovi.where.presentation.group

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ovi.where.R
import com.ovi.where.core.theme.Dimens
import com.ovi.where.core.utils.showToast
import com.ovi.where.presentation.common.PrimaryButton
import com.ovi.where.presentation.common.WhereTopAppBar
import kotlinx.coroutines.delay

private const val INVITE_CODE_LENGTH = 8

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinGroupScreen(
    onNavigateBack: () -> Unit,
    onGroupJoined: (String) -> Unit,
    viewModel: JoinGroupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    var inviteCode by remember { mutableStateOf("") }
    var showSuccess by remember { mutableStateOf(false) }
    var joinedGroupId by remember { mutableStateOf<String?>(null) }

    // Check clipboard for a valid 8-char code
    val clipboardText = clipboardManager.getText()?.text?.trim()?.uppercase()
    val hasValidClipboard = clipboardText != null &&
        clipboardText.length == INVITE_CODE_LENGTH &&
        clipboardText.all { it.isLetterOrDigit() } &&
        clipboardText != inviteCode

    LaunchedEffect(key1 = true) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is com.ovi.where.core.common.UiEvent.ShowToast -> {
                    context.showToast(event.message)
                }
                is com.ovi.where.core.common.UiEvent.Navigate -> {
                    val groupId = event.route.substringAfterLast("/")
                    joinedGroupId = groupId
                    showSuccess = true
                    delay(1500L)
                    onGroupJoined(groupId)
                }
                else -> Unit
            }
        }
    }

    // Auto-focus the input
    LaunchedEffect(Unit) {
        delay(300L)
        focusRequester.requestFocus()
    }

    Scaffold(
        topBar = {
            WhereTopAppBar(
                title = stringResource(R.string.title_join_group),
                onNavigateBack = onNavigateBack
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
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .padding(Dimens.spaceXLarge),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(Dimens.space2XLarge))

                    // ── Hero Icon ────────────────────────────────────────────
                    Box(
                        modifier = Modifier
                            .size(Dimens.avatarCircle)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Link,
                            contentDescription = null,
                            modifier = Modifier.size(Dimens.iconSizeXLarge),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(Dimens.spaceXLarge))

                    // ── Headline ─────────────────────────────────────────────
                    Text(
                        text = stringResource(R.string.msg_enter_invite_code),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(modifier = Modifier.height(Dimens.spaceMedium))

                    Text(
                        text = stringResource(R.string.msg_invite_code_instruction),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(Dimens.space2XLarge))

                    // ── Segmented Code Input ────────────────────────────────
                    SegmentedCodeInput(
                        value = inviteCode,
                        onValueChange = { newValue ->
                            if (newValue.length <= INVITE_CODE_LENGTH) {
                                inviteCode = newValue.uppercase()
                            }
                        },
                        isError = uiState.error != null,
                        focusRequester = focusRequester,
                        onDone = {
                            if (inviteCode.length == INVITE_CODE_LENGTH) {
                                keyboardController?.hide()
                                viewModel.joinGroup(inviteCode)
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(Dimens.spaceMedium))

                    // ── Error Card ───────────────────────────────────────────
                    AnimatedVisibility(visible = uiState.error != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text(
                                text = uiState.error ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(Dimens.spaceLarge)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(Dimens.spaceMedium))

                    // ── Paste from Clipboard Chip ────────────────────────────
                    if (hasValidClipboard) {
                        AssistChip(
                            onClick = {
                                inviteCode = clipboardText ?: ""
                            },
                            label = { Text("Paste from clipboard") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.ContentPaste,
                                    contentDescription = "Paste",
                                    modifier = Modifier.size(AssistChipDefaults.IconSize)
                                )
                            }
                        )
                        Spacer(modifier = Modifier.height(Dimens.spaceLarge))
                    }

                    Spacer(modifier = Modifier.height(Dimens.spaceLarge))

                    // ── Join Button ──────────────────────────────────────────
                    PrimaryButton(
                        text = stringResource(R.string.action_join_group),
                        onClick = {
                            keyboardController?.hide()
                            viewModel.joinGroup(inviteCode)
                        },
                        isLoading = uiState.isLoading,
                        enabled = inviteCode.length == INVITE_CODE_LENGTH,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // ── Success Overlay ─────────────────────────────────────────
                AnimatedVisibility(
                    visible = showSuccess,
                    enter = fadeIn() + scaleIn(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    ),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Dimens.space2XLarge),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Dimens.spaceXLarge),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "Success",
                                    modifier = Modifier.size(Dimens.iconSizeXLarge),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(Dimens.spaceLarge))
                                Text(
                                    text = "Joined Successfully!",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.height(Dimens.spaceMedium))
                                Text(
                                    text = "Taking you to the group…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Segmented Code Input ────────────────────────────────────────────────────────

@Composable
private fun SegmentedCodeInput(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    focusRequester: FocusRequester,
    onDone: () -> Unit
) {
    BasicTextField(
        value = value,
        onValueChange = { newValue ->
            // Only allow alphanumeric characters
            val filtered = newValue.filter { it.isLetterOrDigit() }
            if (filtered.length <= INVITE_CODE_LENGTH) {
                onValueChange(filtered)
            }
        },
        modifier = Modifier.focusRequester(focusRequester),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Done,
            capitalization = KeyboardCapitalization.Characters
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        singleLine = true,
        decorationBox = { _ ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(INVITE_CODE_LENGTH) { index ->
                    val char = value.getOrNull(index)
                    val isFocused = index == value.length
                    val borderColor = when {
                        isError -> MaterialTheme.colorScheme.error
                        isFocused -> MaterialTheme.colorScheme.primary
                        char != null -> MaterialTheme.colorScheme.outline
                        else -> MaterialTheme.colorScheme.outlineVariant
                    }
                    val bgColor = if (char != null) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    }

                    Box(
                        modifier = Modifier
                            .size(width = 38.dp, height = 48.dp)
                            .clip(RoundedCornerShape(Dimens.cornerSmall))
                            .background(bgColor)
                            .border(
                                width = if (isFocused) 2.dp else 1.dp,
                                color = borderColor,
                                shape = RoundedCornerShape(Dimens.cornerSmall)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = char?.toString() ?: "",
                            style = TextStyle(
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                textAlign = TextAlign.Center
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Add a small gap between characters, wider gap at midpoint
                    if (index < INVITE_CODE_LENGTH - 1) {
                        Spacer(
                            modifier = Modifier.width(
                                if (index == 3) Dimens.spaceMedium else Dimens.spaceSmall
                            )
                        )
                    }
                }
            }
        }
    )
}
