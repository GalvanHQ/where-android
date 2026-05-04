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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.ovi.where.R
import com.ovi.where.core.common.UiEvent
import com.ovi.where.core.theme.Dimens
import com.ovi.where.core.utils.BatteryOptimizationUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit,
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
    ) { uri ->
        uri?.let { viewModel.onPhotoSelected(it) }
    }

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is UiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message.asString(context))
                is UiEvent.Navigate -> if (event.route == "login") onLogout()
                else -> Unit
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_profile)) },
                navigationIcon = {
                    if (onNavigateBack != {}) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Surface(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(Dimens.spaceMedium)
            ) {
                // Profile card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(2.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(Dimens.spaceLarge),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Avatar with change button
                        Box(contentAlignment = Alignment.BottomEnd) {
                            if (!uiState.profile?.photoUrl.isNullOrEmpty()) {
                                AsyncImage(
                                    model = uiState.profile?.photoUrl,
                                    contentDescription = stringResource(R.string.cd_profile_photo),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(Dimens.avatarSizeXLarge)
                                        .clip(CircleShape)
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
                                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                    } else {
                                        Text(
                                            text = uiState.profile?.displayName?.take(1)?.uppercase() ?: "?",
                                            style = MaterialTheme.typography.headlineLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                            // Camera button
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .clickable { photoPickerLauncher.launch("image/*") },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.CameraAlt, null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }

                        Spacer(Modifier.height(Dimens.spaceLarge))

                        // Display name row
                        if (uiState.isEditingName) {
                            OutlinedTextField(
                                value = uiState.displayNameInput,
                                onValueChange = viewModel::onDisplayNameChange,
                                label = { Text(stringResource(R.string.label_display_name)) },
                                isError = uiState.displayNameError != null,
                                supportingText = uiState.displayNameError?.let { { Text(it) } },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = {
                                    Row {
                                        IconButton(onClick = viewModel::onSaveProfile) {
                                            if (uiState.isSaving) CircularProgressIndicator(Modifier.size(20.dp))
                                            else Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                                        }
                                        IconButton(onClick = { viewModel.setEditingName(false) }) {
                                            Icon(Icons.Default.Close, null)
                                        }
                                    }
                                }
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = uiState.profile?.displayName ?: stringResource(R.string.status_user_fallback),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.width(Dimens.spaceSmall))
                                IconButton(
                                    onClick = { viewModel.setEditingName(true) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Edit, null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        Text(
                            text = uiState.profile?.email ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(Dimens.spaceLarge))

                // Settings section
                Text(
                    text = stringResource(R.string.settings_app_section),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = Dimens.spaceSmall, vertical = Dimens.spaceSmall)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(1.dp),
                    shape = MaterialTheme.shapes.large
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

                Spacer(Modifier.height(Dimens.spaceLarge))

                // Sign out
                Button(
                    onClick = viewModel::onLogout,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
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
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(Modifier.height(Dimens.spaceLarge))
            }
        }
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
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon, contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.width(Dimens.spaceLarge))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
