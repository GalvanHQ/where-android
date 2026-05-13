package com.ovi.where.presentation.group.edit

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.ovi.where.R
import com.ovi.where.core.common.UiEvent
import com.ovi.where.core.theme.Dimens
import com.ovi.where.presentation.common.PrimaryButton
import com.ovi.where.presentation.common.WhereTextField
import com.ovi.where.presentation.common.WhereTopAppBar

private const val NAME_MAX_LENGTH = 50
private const val NAME_MIN_LENGTH = 3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditGroupScreen(
    groupId: String,
    onNavigateBack: () -> Unit,
    viewModel: EditGroupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Track original values for unsaved changes detection
    var originalName by remember { mutableStateOf<String?>(null) }
    var originalDescription by remember { mutableStateOf<String?>(null) }
    var avatarChanged by remember { mutableStateOf(false) }
    var selectedAvatarUri by remember { mutableStateOf<Uri?>(null) }

    // Capture original values once loaded
    LaunchedEffect(uiState.name, uiState.description) {
        if (originalName == null && !uiState.isLoading && uiState.name.isNotEmpty()) {
            originalName = uiState.name
            originalDescription = uiState.description
        }
    }

    val hasUnsavedChanges = originalName != null && (
        uiState.name != originalName ||
            uiState.description != originalDescription ||
            avatarChanged
        )

    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedAvatarUri = it
            avatarChanged = true
        }
    }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedAvatarUri = it
            avatarChanged = true
        }
    }

    LaunchedEffect(groupId) { viewModel.loadGroup(groupId) }

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is UiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message.asString(context))
                is UiEvent.NavigateBack -> onNavigateBack()
                else -> Unit
            }
        }
    }

    Scaffold(
        topBar = {
            WhereTopAppBar(
                title = stringResource(R.string.title_edit_group),
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
            if (uiState.isLoading && originalName == null) {
                // ── Loading Skeleton ─────────────────────────────────────────
                EditGroupSkeleton()
            } else {
                // ── Edit Form ────────────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .padding(horizontal = Dimens.spaceLarge, vertical = Dimens.spaceLarge),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // ── Avatar Editor ────────────────────────────────────────
                    EditGroupAvatarPicker(
                        currentAvatarUrl = uiState.avatarUrl,
                        selectedUri = selectedAvatarUri,
                        onPickFromGallery = { imagePickerLauncher.launch("image/*") },
                        onPickFromCamera = { cameraLauncher.launch("image/*") },
                        onPickEmoji = { /* Emoji picker placeholder */ }
                    )

                    Spacer(modifier = Modifier.height(Dimens.spaceXLarge))

                    // ── Section Header: Group Info ───────────────────────────
                    Text(
                        text = "Group Info",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = Dimens.spaceMedium)
                    )

                    // ── Group Name Input with Character Counter ──────────────
                    WhereTextField(
                        value = uiState.name,
                        onValueChange = { newValue ->
                            if (newValue.length <= NAME_MAX_LENGTH) {
                                viewModel.onNameChange(newValue)
                            }
                        },
                        label = stringResource(R.string.label_group_name),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isLoading,
                        isError = uiState.nameError != null,
                        errorMessage = uiState.nameError,
                        singleLine = true,
                        imeAction = ImeAction.Next
                    )

                    // Character counter
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Dimens.spaceSmall, end = Dimens.spaceSmall),
                        horizontalArrangement = Arrangement.End
                    ) {
                        val charCount = uiState.name.length
                        val counterColor = when {
                            charCount < NAME_MIN_LENGTH -> MaterialTheme.colorScheme.error
                            charCount > NAME_MAX_LENGTH - 5 -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Text(
                            text = "${charCount}/$NAME_MAX_LENGTH",
                            style = MaterialTheme.typography.bodySmall,
                            color = counterColor
                        )
                    }

                    Spacer(modifier = Modifier.height(Dimens.spaceMedium))

                    // ── Description Input ────────────────────────────────────
                    WhereTextField(
                        value = uiState.description,
                        onValueChange = viewModel::onDescriptionChange,
                        label = stringResource(R.string.label_description_optional),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isLoading,
                        singleLine = false,
                        maxLines = 4,
                        imeAction = ImeAction.Done
                    )

                    Spacer(modifier = Modifier.height(Dimens.space2XLarge))

                    // ── Save Button (disabled when no changes) ───────────────
                    PrimaryButton(
                        text = stringResource(R.string.action_save),
                        onClick = { viewModel.onSave(groupId) },
                        modifier = Modifier.fillMaxWidth(),
                        isLoading = uiState.isLoading,
                        enabled = hasUnsavedChanges && uiState.name.length >= NAME_MIN_LENGTH
                    )
                }
            }
        }
    }
}

// ── Avatar Picker for Edit Screen ───────────────────────────────────────────────

@Composable
private fun EditGroupAvatarPicker(
    currentAvatarUrl: String?,
    selectedUri: Uri?,
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
            when {
                selectedUri != null -> {
                    AsyncImage(
                        model = selectedUri,
                        contentDescription = "Selected group avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(Dimens.avatarSizeXLarge)
                            .clip(CircleShape)
                    )
                }
                !currentAvatarUrl.isNullOrEmpty() -> {
                    AsyncImage(
                        model = currentAvatarUrl,
                        contentDescription = "Current group avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(Dimens.avatarSizeXLarge)
                            .clip(CircleShape)
                    )
                }
                else -> {
                    Icon(
                        Icons.Default.AddAPhoto,
                        contentDescription = "Add group photo",
                        modifier = Modifier.size(Dimens.iconSizeLarge),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Camera overlay badge
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(Dimens.avatarSizeSmall)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.CameraAlt,
                    contentDescription = "Change photo",
                    modifier = Modifier.size(Dimens.badgeIconSize),
                    tint = MaterialTheme.colorScheme.onPrimary
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

// ── Loading Skeleton ────────────────────────────────────────────────────────────

@Composable
private fun EditGroupSkeleton() {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerTranslate by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )
    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        start = Offset(shimmerTranslate - 200f, 0f),
        end = Offset(shimmerTranslate, 0f)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.spaceXLarge),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(Dimens.spaceLarge))

        // Avatar placeholder
        Box(
            modifier = Modifier
                .size(Dimens.avatarSizeXLarge)
                .clip(CircleShape)
                .background(shimmerBrush)
        )

        Spacer(modifier = Modifier.height(Dimens.spaceXLarge))

        // Section header placeholder
        Box(
            modifier = Modifier
                .fillMaxWidth(0.3f)
                .height(Dimens.shimmerBarHeightL)
                .clip(MaterialTheme.shapes.small)
                .background(shimmerBrush)
                .align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(Dimens.spaceLarge))

        // Name field placeholder
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.buttonHeight)
                .clip(MaterialTheme.shapes.medium)
                .background(shimmerBrush)
        )

        Spacer(modifier = Modifier.height(Dimens.spaceLarge))

        // Description field placeholder
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(shimmerBrush)
        )

        Spacer(modifier = Modifier.height(Dimens.space2XLarge))

        // Button placeholder
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.buttonHeight)
                .clip(MaterialTheme.shapes.medium)
                .background(shimmerBrush)
        )
    }
}
