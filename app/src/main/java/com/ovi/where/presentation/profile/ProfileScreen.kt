package com.ovi.where.presentation.profile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.ovi.where.R
import com.ovi.where.core.common.UiEvent
import com.ovi.where.core.theme.Dimens
import com.ovi.where.core.utils.BatteryOptimizationUtils
import com.ovi.where.presentation.common.WhereTopAppBar
import com.ovi.where.presentation.common.WhereTabHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    contentPadding: PaddingValues = PaddingValues(),
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit,
    showBackButton: Boolean = true,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var isBatteryOptimized by remember {
        mutableStateOf(!BatteryOptimizationUtils.isBatteryOptimizationDisabled(context))
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.onPhotoSelected(it) } }

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is UiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message.asString(context))
                is UiEvent.Navigate     -> if (event.route == "login") onLogout()
                else -> Unit
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (showBackButton) {
                WhereTopAppBar(
                    title = stringResource(R.string.title_profile),
                    onNavigateBack = onNavigateBack
                )
            } else {
                WhereTabHeader(title = stringResource(R.string.title_profile))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.spaceLarge),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(Dimens.spaceMedium))

            // ── Avatar ────────────────────────────────────────────────────────
            Box(contentAlignment = Alignment.BottomEnd) {
                if (!uiState.profile?.photoUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = uiState.profile?.photoUrl,
                        contentDescription = stringResource(R.string.cd_profile_photo),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(Dimens.avatarSizeXLarge).clip(CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(Dimens.avatarSizeXLarge)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        if (uiState.isUploadingPhoto) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(Dimens.avatarSizeSmall),
                                strokeWidth = Dimens.strokeWidthThin
                            )
                        } else {
                            Text(
                                text = uiState.profile?.displayName?.take(1)?.uppercase() ?: "?",
                                style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .size(Dimens.avatarSizeSmall)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { photoPickerLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.CameraAlt, null,
                        modifier = Modifier.size(Dimens.badgeIconSize),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Spacer(Modifier.height(Dimens.spaceMedium))

            // ── Display name ──────────────────────────────────────────────────
            if (uiState.isEditingName) {
                OutlinedTextField(
                    value = uiState.displayNameInput,
                    onValueChange = viewModel::onDisplayNameChange,
                    label = { Text(stringResource(R.string.label_display_name)) },
                    isError = uiState.displayNameError != null,
                    supportingText = uiState.displayNameError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.spaceXLarge),
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    trailingIcon = {
                        Row {
                            IconButton(onClick = viewModel::onSaveDisplayName) {
                                if (uiState.isSaving)
                                    CircularProgressIndicator(Modifier.size(Dimens.iconSizeMedium), strokeWidth = Dimens.strokeWidthThin)
                                else Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { viewModel.setEditingName(false) }) {
                                Icon(Icons.Default.Close, null)
                            }
                        }
                    }
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = uiState.profile?.displayName ?: stringResource(R.string.status_user_fallback),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(Modifier.width(Dimens.spaceSmall))
                    IconButton(
                        onClick = { viewModel.setEditingName(true) },
                        modifier = Modifier.size(Dimens.avatarSizeSmall)
                    ) {
                        Icon(
                            Icons.Default.Edit, null,
                            modifier = Modifier.size(Dimens.badgeIconSize),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // ── Username ──────────────────────────────────────────────────────
            if (uiState.isEditingUsername) {
                Spacer(Modifier.height(Dimens.spaceSmall))
                OutlinedTextField(
                    value = uiState.usernameInput,
                    onValueChange = viewModel::onUsernameChange,
                    label = { Text("@username") },
                    isError = uiState.usernameError != null,
                    supportingText = uiState.usernameError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.spaceXLarge),
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    trailingIcon = {
                        Row {
                            IconButton(onClick = viewModel::onSaveUsername) {
                                Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { viewModel.setEditingUsername(false) }) {
                                Icon(Icons.Default.Close, null)
                            }
                        }
                    }
                )
            } else {
                val username = uiState.profile?.username
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { viewModel.setEditingUsername(true) }
                ) {
                    Text(
                        text = if (!username.isNullOrEmpty()) "@$username" else "Set username",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (!username.isNullOrEmpty())
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else
                            MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(Dimens.spaceSmall))
                    Icon(
                        Icons.Default.Edit, null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f)
                    )
                }
            }

            // ── Bio ───────────────────────────────────────────────────────────
            Spacer(Modifier.height(Dimens.spaceMedium))
            if (uiState.isEditingBio) {
                OutlinedTextField(
                    value = uiState.bioInput,
                    onValueChange = viewModel::onBioChange,
                    label = { Text("Bio") },
                    placeholder = { Text("Tell people about yourself…") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.spaceXLarge),
                    shape = MaterialTheme.shapes.medium,
                    maxLines = 4,
                    supportingText = { Text("${uiState.bioInput.length}/${Dimens.settingIconContainer}") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    trailingIcon = {
                        Column {
                            IconButton(onClick = viewModel::onSaveBio) {
                                Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                )
            } else {
                val bio = uiState.profile?.bio
                Text(
                    text = if (!bio.isNullOrEmpty()) bio else "Add a bio",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (!bio.isNullOrEmpty())
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(horizontal = Dimens.spaceXLarge)
                        .clickable { viewModel.setEditingBio(true) }
                )
            }

            // ── Stats row ─────────────────────────────────────────────────────
            Spacer(Modifier.height(Dimens.spaceMedium))
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.spaceSmall),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.spaceLarge),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(
                        icon = Icons.Default.People,
                        count = uiState.friendCount,
                        label = "Friends"
                    )
                    StatItem(
                        icon = Icons.Default.Group,
                        count = uiState.groupCount,
                        label = "Groups"
                    )
                    StatItem(
                        icon = Icons.Default.LocationOn,
                        count = 0,
                        label = "Sharing"
                    )
                }
            }

            Spacer(Modifier.height(Dimens.spaceMedium))

            // ── Settings card ─────────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                elevation = CardDefaults.cardElevation(Dimens.cardElevationSubtle)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SettingRow(
                        icon = Icons.Default.Notifications,
                        title = stringResource(R.string.settings_notifications),
                        subtitle = stringResource(R.string.settings_notifications_subtitle),
                        onClick = { BatteryOptimizationUtils.openNotificationSettings(context) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = Dimens.spaceLarge))
                    SettingRow(
                        icon = Icons.Default.LocationOn,
                        title = stringResource(R.string.settings_location),
                        subtitle = if (hasLocationPermission)
                            stringResource(R.string.settings_location_granted)
                        else
                            stringResource(R.string.settings_location_required),
                        onClick = { BatteryOptimizationUtils.openAppSettings(context) }
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && isBatteryOptimized) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = Dimens.spaceLarge))
                        SettingRow(
                            icon = Icons.Default.BatteryAlert,
                            title = stringResource(R.string.settings_battery_optimization),
                            subtitle = stringResource(R.string.settings_battery_subtitle),
                            onClick = { BatteryOptimizationUtils.openBatteryOptimizationSettings(context) }
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = Dimens.spaceLarge))
                    SettingRow(
                        icon = Icons.Default.PrivacyTip,
                        title = stringResource(R.string.settings_privacy),
                        subtitle = stringResource(R.string.settings_privacy_subtitle),
                        onClick = {}
                    )
                }
            }

            Spacer(Modifier.height(Dimens.spaceMedium))

            // ── Sign out ──────────────────────────────────────────────────────
            Button(
                onClick = viewModel::onLogout,
                modifier = Modifier.fillMaxWidth().height(Dimens.buttonHeightSmall),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor   = MaterialTheme.colorScheme.onErrorContainer
                ),
                shape = MaterialTheme.shapes.large
            ) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, null)
                Spacer(Modifier.width(Dimens.spaceSmall))
                Text(stringResource(R.string.action_sign_out), fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(Dimens.spaceMedium))

            Text(
                text = stringResource(R.string.app_version),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(Dimens.spaceLarge))
        }
    }
}

@Composable
private fun StatItem(icon: ImageVector, count: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(Dimens.iconSizeMedium))
        Spacer(Modifier.height(Dimens.spaceSmall))
        Text(text = "$count", style = MaterialTheme.typography.titleMedium)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(Dimens.spaceLarge),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(Dimens.settingIconContainer)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, modifier = Modifier.size(Dimens.iconSizeMedium), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(Dimens.spaceLarge))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
