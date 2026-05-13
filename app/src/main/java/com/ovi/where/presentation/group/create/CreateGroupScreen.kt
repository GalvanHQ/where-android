package com.ovi.where.presentation.group.create

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.ovi.where.R
import com.ovi.where.core.common.UiEvent
import com.ovi.where.core.theme.Dimens
import com.ovi.where.core.utils.showToast
import com.ovi.where.domain.model.User
import com.ovi.where.presentation.common.PrimaryButton
import com.ovi.where.presentation.common.WhereTextField
import com.ovi.where.presentation.common.WhereTopAppBar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateGroupScreen(
    onNavigateBack: () -> Unit,
    onGroupCreated: (groupId: String) -> Unit = {},
    viewModel: CreateGroupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.onAvatarSelected(it) }
    }

    // Camera launcher (simplified — uses gallery for now)
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.onAvatarSelected(it) }
    }

    LaunchedEffect(key1 = true) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is UiEvent.ShowToast -> context.showToast(event.message)
                is UiEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message.asString(context))
                }
                is UiEvent.ShareContent -> {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, event.title)
                        putExtra(Intent.EXTRA_TEXT, event.content)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, event.title))
                }
                is UiEvent.Navigate -> {
                    // Navigation handled by parent
                }
                is UiEvent.NavigateUp -> onNavigateBack()
                else -> Unit
            }
        }
    }

    Scaffold(
        topBar = {
            WhereTopAppBar(
                title = stringResource(R.string.title_create_group),
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
            if (uiState.isGroupCreated) {
                // ── Post-creation: Invite Code Display ───────────────────────
                InviteCodeDisplay(
                    inviteCode = uiState.inviteCode,
                    onShare = viewModel::onShareInviteCode,
                    onNavigateToChat = {
                        val convId = uiState.createdConversationId
                        if (convId != null) onGroupCreated(convId)
                    }
                )
            } else {
                // ── Group Creation Form ──────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .padding(Dimens.spaceXLarge),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // ── Avatar Picker ────────────────────────────────────────
                    GroupAvatarPicker(
                        avatarUri = uiState.avatarUri,
                        onPickFromGallery = { imagePickerLauncher.launch("image/*") },
                        onPickFromCamera = { cameraLauncher.launch("image/*") },
                        onPickEmoji = { /* Emoji picker placeholder */ }
                    )

                    Spacer(modifier = Modifier.height(Dimens.spaceXLarge))

                    // ── Group Name Input ─────────────────────────────────────
                    WhereTextField(
                        value = uiState.name,
                        onValueChange = viewModel::onNameChange,
                        label = stringResource(R.string.label_group_name),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isLoading,
                        isError = uiState.nameError != null,
                        errorMessage = uiState.nameError,
                        imeAction = ImeAction.Next
                    )

                    Spacer(modifier = Modifier.height(Dimens.spaceMedium))

                    // ── Description Input ────────────────────────────────────
                    WhereTextField(
                        value = uiState.description,
                        onValueChange = viewModel::onDescriptionChange,
                        label = stringResource(R.string.label_description_optional),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isLoading,
                        singleLine = false,
                        maxLines = 3,
                        minLines = 2,
                        imeAction = ImeAction.Next
                    )

                    Spacer(modifier = Modifier.height(Dimens.spaceLarge))

                    // ── Selected Members Chip Row ────────────────────────────
                    if (uiState.selectedMembers.isNotEmpty()) {
                        Text(
                            text = "Members (${uiState.selectedMembers.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = Dimens.spaceMedium)
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMedium),
                            verticalArrangement = Arrangement.spacedBy(Dimens.spaceSmall)
                        ) {
                            uiState.selectedMembers.forEach { member ->
                                InputChip(
                                    selected = true,
                                    onClick = { viewModel.onMemberRemoved(member) },
                                    label = {
                                        Text(
                                            text = member.displayName,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove ${member.displayName}",
                                            modifier = Modifier.size(Dimens.iconSizeSmall)
                                        )
                                    },
                                    colors = InputChipDefaults.inputChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(Dimens.spaceMedium))
                    }

                    // ── Member Search Field ──────────────────────────────────
                    WhereTextField(
                        value = uiState.memberSearchQuery,
                        onValueChange = viewModel::onMemberSearchQueryChange,
                        label = "Search members to add",
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isLoading,
                        imeAction = ImeAction.Done
                    )

                    // Members error
                    uiState.membersError?.let { error ->
                        Spacer(modifier = Modifier.height(Dimens.spaceSmall))
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    // ── Search Results ────────────────────────────────────────
                    if (uiState.isSearching) {
                        Spacer(modifier = Modifier.height(Dimens.spaceMedium))
                        CircularProgressIndicator(
                            modifier = Modifier.size(Dimens.iconSizeMedium),
                            strokeWidth = Dimens.strokeWidthThin
                        )
                    } else if (uiState.searchResults.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(Dimens.spaceMedium))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column {
                                uiState.searchResults.forEach { user ->
                                    MemberSearchResultItem(
                                        user = user,
                                        onClick = { viewModel.onMemberSelected(user) }
                                    )
                                    if (user != uiState.searchResults.last()) {
                                        HorizontalDivider(
                                            thickness = Dimens.dividerThickness,
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── Error Message ─────────────────────────────────────────
                    uiState.error?.let { error ->
                        Spacer(modifier = Modifier.height(Dimens.spaceMedium))
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(modifier = Modifier.height(Dimens.spaceXLarge))

                    // ── Create Button ─────────────────────────────────────────
                    PrimaryButton(
                        text = stringResource(R.string.action_create_group),
                        onClick = viewModel::onCreateGroup,
                        modifier = Modifier.fillMaxWidth(),
                        isLoading = uiState.isLoading
                    )
                }
            }
        }
    }
}

// ── Group Avatar Picker ─────────────────────────────────────────────────────────

@Composable
private fun GroupAvatarPicker(
    avatarUri: Uri?,
    onPickFromGallery: () -> Unit,
    onPickFromCamera: () -> Unit,
    onPickEmoji: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(Dimens.avatarSizeXLarge)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable { showMenu = true },
            contentAlignment = Alignment.Center
        ) {
            if (avatarUri != null) {
                AsyncImage(
                    model = avatarUri,
                    contentDescription = "Group avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(Dimens.avatarSizeXLarge)
                        .clip(CircleShape)
                )
            } else {
                Icon(
                    Icons.Default.AddAPhoto,
                    contentDescription = "Add group photo",
                    modifier = Modifier.size(Dimens.iconSizeLarge),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Camera") },
                onClick = {
                    showMenu = false
                    onPickFromCamera()
                },
                leadingIcon = { Icon(Icons.Default.CameraAlt, null) }
            )
            DropdownMenuItem(
                text = { Text("Gallery") },
                onClick = {
                    showMenu = false
                    onPickFromGallery()
                },
                leadingIcon = { Icon(Icons.Default.Image, null) }
            )
            DropdownMenuItem(
                text = { Text("Emoji") },
                onClick = {
                    showMenu = false
                    onPickEmoji()
                },
                leadingIcon = { Icon(Icons.Default.EmojiEmotions, null) }
            )
        }
    }
}

// ── Member Search Result Item ───────────────────────────────────────────────────

@Composable
private fun MemberSearchResultItem(
    user: User,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = user.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(Dimens.avatarSizeSmall)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = user.displayName.take(1).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

// ── Invite Code Display (Post-Creation) ─────────────────────────────────────────

@Composable
private fun InviteCodeDisplay(
    inviteCode: String,
    onShare: () -> Unit,
    onNavigateToChat: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.spaceXLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Group Created!",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(Dimens.spaceLarge))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.spaceXLarge),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.title_invite_code),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(Dimens.spaceMedium))
                Text(
                    text = inviteCode,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(Dimens.spaceMedium))
                Text(
                    text = stringResource(R.string.msg_share_invite_instruction),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(Dimens.spaceXLarge))

        // Share button
        PrimaryButton(
            text = "Share Invite Code",
            onClick = onShare,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(Dimens.spaceMedium))

        // Go to chat button
        PrimaryButton(
            text = "Go to Group Chat",
            onClick = onNavigateToChat,
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}
