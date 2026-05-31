package com.ovi.where.presentation.profile.edit

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.ovi.where.core.theme.Dimens
import com.ovi.where.presentation.common.WhereTopAppBar

/**
 * Edit Profile — clean, list-style redesign in the spirit of Instagram /
 * Telegram edit screens:
 *   • Flat background, no gradients or heavy cards.
 *   • Centered avatar with a single "Change photo" text link.
 *   • Inline rows: a fixed-width label on the left, a borderless input on
 *     the right, separated by hairline dividers.
 *   • Quiet uppercase section headers group the rows.
 *   • Save lives as a "Done" affordance in the top bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    onNavigateBack: () -> Unit,
    onNavigateToHomePicker: (Double, Double) -> Unit = { _, _ -> },
    pickedHome: Triple<Double, Double, String>? = null,
    viewModel: EditProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Apply a home pick returned from the map picker (via savedStateHandle).
    LaunchedEffect(pickedHome) {
        pickedHome?.let { (lat, lng, label) ->
            viewModel.onHomePicked(lat, lng, label)
        }
    }

    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> viewModel.onPhotoSelected(uri) }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onNavigateBack()
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val canSave = uiState.displayName.isNotBlank() && uiState.isUsernameAvailable != false

    Scaffold(
        topBar = {
            WhereTopAppBar(
                title = "Edit profile",
                onNavigateBack = onNavigateBack,
                actions = {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(Dimens.iconSizeMedium)
                                .padding(end = Dimens.spaceLarge),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        TextButton(
                            onClick = { viewModel.saveProfile() },
                            enabled = canSave
                        ) {
                            Text(
                                "Done",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (canSave)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // ── Avatar + change photo link ───────────────────────────────
            AvatarHeader(
                photoModel = uiState.newPhotoUri ?: uiState.photoUrl,
                onPickPhoto = { photoLauncher.launch("image/*") }
            )

            // ── Public profile ───────────────────────────────────────────
            SectionLabel("Public profile")

            FieldRow(label = "Name") {
                InlineField(
                    value = uiState.displayName,
                    onValueChange = viewModel::onDisplayNameChanged,
                    placeholder = "Your name",
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next
                    )
                )
            }
            RowDivider()

            FieldRow(
                label = "Username",
                supportingText = when {
                    uiState.usernameError != null -> uiState.usernameError
                    uiState.isUsernameAvailable == true -> "Username is available"
                    else -> null
                },
                supportingTextColor = when {
                    uiState.usernameError != null -> MaterialTheme.colorScheme.error
                    uiState.isUsernameAvailable == true -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            ) {
                InlineField(
                    value = uiState.username,
                    onValueChange = viewModel::onUsernameChanged,
                    placeholder = "username",
                    prefix = "@",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    trailing = {
                        when {
                            uiState.isUsernameAvailable == true -> Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(Dimens.iconSizeMedium),
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            uiState.usernameError != null -> Icon(
                                Icons.Filled.Close,
                                contentDescription = null,
                                modifier = Modifier.size(Dimens.iconSizeMedium),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                )
            }
            RowDivider()

            FieldRow(
                label = "Bio",
                alignTop = true,
                supportingText = "${uiState.bio.length}/150",
                supportingTextColor = if (uiState.bio.length > 150)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                InlineField(
                    value = uiState.bio,
                    onValueChange = viewModel::onBioChanged,
                    placeholder = "Tell people about yourself",
                    singleLine = false,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Default
                    )
                )
            }

            // ── Home ─────────────────────────────────────────────────────
            SectionLabel("Home")
            HomeRow(
                hasHome = uiState.hasHome,
                label = uiState.homeLabel,
                onPick = { onNavigateToHomePicker(uiState.homeLatitude, uiState.homeLongitude) },
                onClear = { viewModel.onClearHome() }
            )

            // ── Social links ─────────────────────────────────────────────
            SectionLabel("Social links")

            FieldRow(label = "Facebook", leadingIconRes = com.ovi.where.R.drawable.facebook) {
                InlineField(
                    value = uiState.facebookUrl,
                    onValueChange = viewModel::onFacebookChanged,
                    placeholder = "facebook.com/username",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
            }
            RowDivider(insetStart = 56.dp)

            FieldRow(label = "Instagram", leadingIconRes = com.ovi.where.R.drawable.instagram) {
                InlineField(
                    value = uiState.instagramUrl,
                    onValueChange = viewModel::onInstagramChanged,
                    placeholder = "instagram.com/username",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
            }
            RowDivider(insetStart = 56.dp)

            FieldRow(label = "LinkedIn", leadingIconRes = com.ovi.where.R.drawable.linkedin) {
                InlineField(
                    value = uiState.linkedinUrl,
                    onValueChange = viewModel::onLinkedinChanged,
                    placeholder = "linkedin.com/in/username",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )
            }

            Spacer(modifier = Modifier.height(Dimens.space3XLarge))
        }
    }
}

// ── Avatar header ────────────────────────────────────────────────────────────
@Composable
private fun AvatarHeader(
    photoModel: Any?,
    onPickPhoto: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val avatarSize = Dimens.avatarSizeXLarge // 96dp
    val avatarPixelSize = remember(density, avatarSize) {
        with(density) { avatarSize.roundToPx() }
    }

    val avatarImageRequest = remember(photoModel, avatarPixelSize) {
        if (photoModel == null) null
        else {
            val cacheKey = photoModel.toString()
            ImageRequest.Builder(context)
                .data(photoModel)
                .crossfade(true)
                .size(avatarPixelSize)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .networkCachePolicy(CachePolicy.ENABLED)
                .memoryCacheKey(cacheKey)
                .diskCacheKey(cacheKey)
                .build()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Dimens.spaceXLarge, bottom = Dimens.spaceMedium),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(avatarSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onPickPhoto),
            contentAlignment = Alignment.Center
        ) {
            if (avatarImageRequest != null) {
                AsyncImage(
                    model = avatarImageRequest,
                    contentDescription = "Profile photo",
                    modifier = Modifier
                        .size(avatarSize)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Outlined.Person,
                    contentDescription = null,
                    modifier = Modifier.size(Dimens.iconSizeXLarge),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        TextButton(onClick = onPickPhoto) {
            Text(
                "Change photo",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// ── Quiet section header ──────────────────────────────────────────────────────
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            start = Dimens.spaceLarge,
            end = Dimens.spaceLarge,
            top = Dimens.spaceXLarge,
            bottom = Dimens.spaceMedium
        )
    )
}

// ── Label-left / field-right row ──────────────────────────────────────────────
@Composable
private fun FieldRow(
    label: String,
    alignTop: Boolean = false,
    leadingIconRes: Int? = null,
    supportingText: String? = null,
    supportingTextColor: Color = Color.Unspecified,
    field: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spaceLarge)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Dimens.spaceMedium),
            verticalAlignment = if (alignTop) Alignment.Top else Alignment.CenterVertically
        ) {
            if (leadingIconRes != null) {
                Icon(
                    imageVector = ImageVector.vectorResource(id = leadingIconRes),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(top = if (alignTop) 14.dp else 0.dp)
                        .size(Dimens.iconSizeMedium),
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(Dimens.spaceLarge))
            } else {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .width(96.dp)
                        .padding(top = if (alignTop) 14.dp else 0.dp)
                )
            }
            Box(modifier = Modifier.weight(1f)) { field() }
        }
        if (supportingText != null) {
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = supportingTextColor,
                modifier = Modifier.padding(
                    start = if (leadingIconRes != null) 40.dp else 96.dp,
                    bottom = Dimens.spaceSmall
                )
            )
        }
    }
}

// ── Borderless inline text field ──────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InlineField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    prefix: String? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailing: @Composable (() -> Unit)? = null
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                placeholder,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        },
        prefix = prefix?.let { { Text(it, style = MaterialTheme.typography.bodyLarge) } },
        singleLine = singleLine,
        trailingIcon = trailing,
        textStyle = LocalTextStyle.current.merge(MaterialTheme.typography.bodyLarge),
        keyboardOptions = keyboardOptions,
        modifier = Modifier.fillMaxWidth(),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            errorContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

// ── Hairline divider between rows ─────────────────────────────────────────────
@Composable
private fun RowDivider(insetStart: androidx.compose.ui.unit.Dp = Dimens.spaceLarge) {
    HorizontalDivider(
        modifier = Modifier.padding(start = insetStart),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

// ── Home row ─────────────────────────────────────────────────────────────────
@Composable
private fun HomeRow(
    hasHome: Boolean,
    label: String,
    onPick: () -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPick)
            .padding(horizontal = Dimens.spaceLarge, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(id = com.ovi.where.R.drawable.home_outlined),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(Dimens.iconSizeMedium)
        )
        Spacer(modifier = Modifier.width(Dimens.spaceLarge))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (hasHome) "Home" else "Set home location",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (hasHome) label.ifBlank { "Location pinned" }
                else "Show friends when you're home",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (hasHome) {
            TextButton(onClick = onClear) {
                Text("Remove", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
