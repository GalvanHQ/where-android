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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.ovi.where.R
import com.ovi.where.core.common.UiEvent
import com.ovi.where.core.theme.Dimens
import com.ovi.where.core.utils.showToast
import com.ovi.where.domain.model.User
import com.ovi.where.presentation.common.PrimaryButton
import com.ovi.where.presentation.common.WhereTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupScreen(
    onNavigateBack: () -> Unit,
    onGroupCreated: (groupId: String) -> Unit = {},
    viewModel: CreateGroupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.onAvatarSelected(it) }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        // Convert bitmap to Uri via a temp file for the ViewModel
        if (bitmap != null) {
            val file = java.io.File(context.cacheDir, "group_avatar_${System.currentTimeMillis()}.jpg")
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
            viewModel.onAvatarSelected(Uri.fromFile(file))
        }
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
                is UiEvent.Navigate -> { /* Navigation handled by parent */ }
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
        if (uiState.isGroupCreated) {
            InviteCodeDisplay(
                inviteCode = uiState.inviteCode,
                onShare = viewModel::onShareInviteCode,
                onNavigateToChat = {
                    val convId = uiState.createdConversationId
                    if (convId != null) onGroupCreated(convId)
                },
                modifier = Modifier.padding(paddingValues)
            )
        } else {
            CreateGroupForm(
                uiState = uiState,
                onNameChange = viewModel::onNameChange,
                onDescriptionChange = viewModel::onDescriptionChange,
                onMemberSearchQueryChange = viewModel::onMemberSearchQueryChange,
                onMemberSelected = viewModel::onMemberSelected,
                onMemberRemoved = viewModel::onMemberRemoved,
                onPickFromGallery = { imagePickerLauncher.launch("image/*") },
                onPickFromCamera = { cameraLauncher.launch(null) },
                onCreateGroup = viewModel::onCreateGroup,
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
}

// ── Group Creation Form ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateGroupForm(
    uiState: CreateGroupUiState,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onMemberSearchQueryChange: (String) -> Unit,
    onMemberSelected: (User) -> Unit,
    onMemberRemoved: (User) -> Unit,
    onPickFromGallery: () -> Unit,
    onPickFromCamera: () -> Unit,
    onCreateGroup: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAvatarSheet by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
    ) {
        // ── Header: Avatar + Name inline (WhatsApp-style) ────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.spaceLarge, vertical = Dimens.spaceXLarge),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable { showAvatarSheet = true },
                contentAlignment = Alignment.Center
            ) {
                if (uiState.avatarUri != null) {
                    AsyncImage(
                        model = uiState.avatarUri,
                        contentDescription = "Group avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(64.dp).clip(CircleShape)
                    )
                } else {
                    Icon(
                        Icons.Default.AddAPhoto,
                        contentDescription = "Add group photo",
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.width(Dimens.spaceLarge))

            // Name field inline
            Column(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = uiState.name,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(R.string.label_group_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isLoading,
                    isError = uiState.nameError != null,
                    singleLine = true,
                    shape = RoundedCornerShape(Dimens.cornerSmall),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
                uiState.nameError?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                    )
                }
            }
        }

        // ── Description ──────────────────────────────────────────────────
        OutlinedTextField(
            value = uiState.description,
            onValueChange = onDescriptionChange,
            label = { Text(stringResource(R.string.label_description_optional)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.spaceLarge),
            enabled = !uiState.isLoading,
            singleLine = false,
            maxLines = 3,
            minLines = 2,
            shape = RoundedCornerShape(Dimens.cornerSmall),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            )
        )

        Spacer(modifier = Modifier.height(Dimens.spaceXLarge))

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            modifier = Modifier.padding(horizontal = Dimens.spaceLarge)
        )

        Spacer(modifier = Modifier.height(Dimens.spaceLarge))

        // ── Members Section ──────────────────────────────────────────────
        Text(
            text = if (uiState.selectedMembers.isNotEmpty())
                "Add Members · ${uiState.selectedMembers.size} selected"
            else "Add Members",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = Dimens.spaceLarge)
        )

        Spacer(modifier = Modifier.height(Dimens.spaceMedium))

        // Search field
        OutlinedTextField(
            value = uiState.memberSearchQuery,
            onValueChange = onMemberSearchQueryChange,
            placeholder = { Text("Search friends") },
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.spaceLarge),
            enabled = !uiState.isLoading,
            singleLine = true,
            shape = RoundedCornerShape(Dimens.cornerMedium),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        )

        uiState.membersError?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = Dimens.spaceLarge, top = Dimens.spaceSmall)
            )
        }

        Spacer(modifier = Modifier.height(Dimens.spaceMedium))

        // Results / friend list
        if (uiState.isSearching) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Dimens.spaceLarge),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(Dimens.iconSizeMedium),
                    strokeWidth = Dimens.strokeWidthThin
                )
            }
        } else if (uiState.searchResults.isNotEmpty()) {
            val selectedIds = uiState.selectedMembers.map { it.id }.toSet()
            Column(modifier = Modifier.fillMaxWidth()) {
                uiState.searchResults.forEach { user ->
                    val isSelected = user.id in selectedIds
                    ContactSelectableRow(
                        user = user,
                        isSelected = isSelected,
                        onClick = {
                            if (isSelected) onMemberRemoved(user) else onMemberSelected(user)
                        }
                    )
                }
            }
        } else if (uiState.memberSearchQuery.isNotBlank()) {
            Text(
                text = "No friends found",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Dimens.spaceLarge, vertical = Dimens.spaceLarge)
            )
        }

        // General error
        uiState.error?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(
                    start = Dimens.spaceLarge,
                    top = Dimens.spaceMedium
                )
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // ── Create Button (pinned to bottom) ─────────────────────────────
        PrimaryButton(
            text = stringResource(R.string.action_create_group),
            onClick = onCreateGroup,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.spaceLarge, vertical = Dimens.spaceLarge),
            isLoading = uiState.isLoading
        )
    }

    // ── Avatar Picker Bottom Sheet ───────────────────────────────────────
    if (showAvatarSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAvatarSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Dimens.space2XLarge)
            ) {
                Text(
                    text = "Choose photo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(
                        horizontal = Dimens.spaceXLarge,
                        vertical = Dimens.spaceLarge
                    )
                )
                ListItem(
                    headlineContent = { Text("Take a photo") },
                    leadingContent = {
                        Icon(Icons.Default.CameraAlt, contentDescription = null)
                    },
                    modifier = Modifier.clickable {
                        showAvatarSheet = false
                        onPickFromCamera()
                    }
                )
                ListItem(
                    headlineContent = { Text("Choose from gallery") },
                    leadingContent = {
                        Icon(Icons.Default.Image, contentDescription = null)
                    },
                    modifier = Modifier.clickable {
                        showAvatarSheet = false
                        onPickFromGallery()
                    }
                )
            }
        }
    }
}

// ── Contact Selectable Row ──────────────────────────────────────────────────────

@Composable
private fun ContactSelectableRow(
    user: User,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                else androidx.compose.ui.graphics.Color.Transparent
            )
            .padding(horizontal = Dimens.spaceLarge, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar with checkmark overlay
        Box {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (user.photoUrl != null) {
                    AsyncImage(
                        model = user.photoUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(44.dp).clip(CircleShape)
                    )
                } else {
                    Text(
                        text = user.displayName.take(1).uppercase(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        Text(
            text = user.displayName,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

// ── Invite Code Display (Post-Creation) ─────────────────────────────────────────

@Composable
private fun InviteCodeDisplay(
    inviteCode: String,
    onShare: () -> Unit,
    onNavigateToChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Success checkmark with animated ring
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Group Created!",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Share the invite code with friends to let them join your group.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Invite code card — tap to copy
        Surface(
            onClick = {
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Invite Code", inviteCode))
                android.widget.Toast.makeText(context, "Code copied!", android.widget.Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp, horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "INVITE CODE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                // Code with letter spacing for readability
                Text(
                    text = inviteCode,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        letterSpacing = 4.sp
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tap to copy",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Share button — primary action
        Button(
            onClick = onShare,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.Share, null, Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                "Share Invite Code",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Go to group — secondary action
        OutlinedButton(
            onClick = onNavigateToChat,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.ChatBubbleOutline, null, Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                "Open Group Chat",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
