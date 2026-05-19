package com.ovi.where.presentation.map

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.ovi.where.R
import com.ovi.where.core.theme.AvatarColors
import com.ovi.where.core.theme.Dimens
import com.ovi.where.core.utils.LocationUtils
import com.ovi.where.core.utils.showToast
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalMapScreen(
    @Suppress("unused") contentPadding: PaddingValues = PaddingValues(),
    onNavigateToChat: (String) -> Unit = {},
    onNavigateToUserProfile: (String) -> Unit = {},
    onNavigateToGroupMap: (String) -> Unit = {},
    viewModel: GlobalMapViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val navigateToChat by viewModel.navigateToChat.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    fun hasLocationPerms(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    var locationGranted by remember { mutableStateOf(hasLocationPerms()) }

    // Handle navigation to chat
    LaunchedEffect(navigateToChat) {
        navigateToChat?.let { conversationId ->
            onNavigateToChat(conversationId)
            viewModel.onChatNavigated()
        }
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(0.0, 0.0), 2.0f)
    }

    // ── Permission launcher ───────────────────────────────────────────────────
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        locationGranted = granted
        if (granted) viewModel.locateMe()
        else context.showToast(context.getString(R.string.toast_location_denied))
    }

    // ── Camera animation on requestCameraMove flag ────────────────────────────
    LaunchedEffect(uiState.requestCameraMove) {
        if (uiState.requestCameraMove && uiState.hasMyLocation &&
            uiState.myLatitude != 0.0 && uiState.myLongitude != 0.0
        ) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(uiState.myLatitude, uiState.myLongitude), 15f
                ),
                durationMs = 800
            )
            viewModel.onCameraMoveConsumed()
        }
    }

    // ── Snackbar events ───────────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is com.ovi.where.core.common.UiEvent.ShowSnackbar ->
                    snackbarHostState.showSnackbar(event.message.asString(context))

                else -> Unit
            }
        }
    }

    val hapticFeedback = LocalHapticFeedback.current

    // ── Auto-zoom to fit all friends on first load ────────────────────────────
    var hasAutoZoomed by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.friendLocations) {
        if (!hasAutoZoomed && uiState.friendLocations.isNotEmpty()) {
            hasAutoZoomed = true
            val validFriends = uiState.friendLocations.filter {
                it.latitude != 0.0 && it.longitude != 0.0
            }
            if (validFriends.isNotEmpty()) {
                val boundsBuilder = LatLngBounds.Builder()
                validFriends.forEach { friend ->
                    boundsBuilder.include(LatLng(friend.latitude, friend.longitude))
                }
                if (uiState.hasMyLocation && uiState.myLatitude != 0.0 && uiState.myLongitude != 0.0) {
                    boundsBuilder.include(LatLng(uiState.myLatitude, uiState.myLongitude))
                }
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 100),
                    durationMs = 1000
                )
            }
        }
    }

    // ── Friend proximity toast (once per friend per session) ──────────────────
    val notifiedProximityFriends = remember { mutableSetOf<String>() }
    LaunchedEffect(uiState.friendLocations, uiState.hasMyLocation) {
        if (uiState.hasMyLocation && uiState.myLatitude != 0.0 && uiState.myLongitude != 0.0) {
            uiState.friendLocations.forEach { friend ->
                if (friend.latitude != 0.0 && friend.longitude != 0.0 &&
                    !notifiedProximityFriends.contains(friend.userId)
                ) {
                    val distance = LocationUtils.calculateDistance(
                        uiState.myLatitude, uiState.myLongitude,
                        friend.latitude, friend.longitude
                    )
                    if (distance <= 500.0) {
                        notifiedProximityFriends.add(friend.userId)
                        snackbarHostState.showSnackbar("\uD83C\uDF89 ${friend.displayName} is nearby!")
                    }
                }
            }
        }
    }

    // ── Sharing countdown timer ───────────────────────────────────────────────
    var sharingSecondsRemaining by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(uiState.isSharing, uiState.sharingExpiresAt) {
        if (uiState.isSharing && uiState.sharingExpiresAt != null) {
            // Tick every second to update the countdown
            while (true) {
                val remaining = (uiState.sharingExpiresAt!! - System.currentTimeMillis()) / 1000L
                if (remaining <= 0) {
                    sharingSecondsRemaining = null
                    break
                }
                sharingSecondsRemaining = remaining
                delay(1000L)
            }
        } else {
            sharingSecondsRemaining = null
        }
    }

    // ── Quick-share state (remembers last-used target) ──────────────────────
    var lastShareTargetId by remember { mutableStateOf<String?>(null) }
    var lastShareTargetName by remember { mutableStateOf<String?>(null) }
    var lastShareDuration by remember { mutableLongStateOf(60L) }

    // ── Sharing start/stop visual feedback ────────────────────────────────────
    var wasSharing by remember { mutableStateOf(uiState.isSharing) }
    LaunchedEffect(uiState.isSharing) {
        if (uiState.isSharing && !wasSharing) {
            snackbarHostState.showSnackbar("\uD83D\uDCCD Sharing your location")
        } else if (!uiState.isSharing && wasSharing) {
            snackbarHostState.showSnackbar("Location sharing stopped")
        }
        wasSharing = uiState.isSharing
    }

    var mapType by remember { mutableStateOf(MapType.NORMAL) }
    var showMapTypeSheet by remember { mutableStateOf(false) }
    var showMyProfileSheet by remember { mutableStateOf(false) }

    // ── Bottom sheet state ────────────────────────────────────────────────────
    val friendSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val groupPickerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val shareSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val mapTypeSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Sheet peek height — matches Google Maps: just drag handle + title visible
    val sheetPeekHeight = if (uiState.friendLocations.isNotEmpty()) 140.dp else 0.dp

    val bottomSheetScaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded
        )
    )

    BottomSheetScaffold(
        scaffoldState = bottomSheetScaffoldState,
        sheetPeekHeight = sheetPeekHeight,
        sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        sheetContainerColor = MaterialTheme.colorScheme.surfaceDim,
        sheetShadowElevation = 16.dp,
        sheetTonalElevation = 2.dp,
        sheetDragHandle = {
            if (uiState.friendLocations.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f))
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
        },
        sheetContent = {
            if (uiState.friendLocations.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = 500.dp)
                ) {
                    // Header
                    Text(
                        text = "${uiState.friendLocations.size} friend${if (uiState.friendLocations.size != 1) "s" else ""} sharing",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    Spacer(Modifier.height(14.dp))

                    // Vertical list of friends
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(items = uiState.friendLocations, key = { it.userId }) { friend ->
                            FriendAvatarChip(
                                friend = friend,
                                myLatitude = uiState.myLatitude,
                                myLongitude = uiState.myLongitude,
                                hasMyLocation = uiState.hasMyLocation,
                                onClick = {
                                    viewModel.selectFriend(friend)
                                    if (friend.latitude != 0.0) {
                                        scope.launch {
                                            cameraPositionState.animate(
                                                CameraUpdateFactory.newLatLngZoom(
                                                    LatLng(friend.latitude, friend.longitude),
                                                    15f
                                                ),
                                                durationMs = 600
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            } else {
                // Empty placeholder when no friends sharing
                Spacer(Modifier.height(1.dp))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ── Map ───────────────────────────────────────────────────────────
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(
                    mapType = mapType,
                    isMyLocationEnabled = false // we draw our own marker
                ),
                uiSettings = MapUiSettings(
                    myLocationButtonEnabled = false,
                    zoomControlsEnabled = false
                )
            ) {
                // ── My location marker (inside GoogleMap → sticks to LatLng) ──
                if (uiState.hasMyLocation && uiState.myLatitude != 0.0 && uiState.myLongitude != 0.0) {
                    val myMarkerState = remember(uiState.myLatitude, uiState.myLongitude) {
                        MarkerState(position = LatLng(uiState.myLatitude, uiState.myLongitude))
                    }

                    // Pre-load my profile bitmap for marker
                    var myAvatarBitmap by remember { mutableStateOf<Bitmap?>(null) }
                    LaunchedEffect(uiState.myPhotoUrl) {
                        val url = uiState.myPhotoUrl
                        if (!url.isNullOrEmpty()) {
                            val request = ImageRequest.Builder(context)
                                .data(url)
                                .allowHardware(false)
                                .memoryCacheKey(url)
                                .diskCacheKey(url)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .size(128)
                                .build()
                            val result = context.imageLoader.execute(request)
                            if (result is SuccessResult) {
                                myAvatarBitmap = (result.drawable as? BitmapDrawable)?.bitmap
                            }
                        }
                    }

                    MarkerComposable(
                        keys = arrayOf(myAvatarBitmap ?: Unit),
                        state = myMarkerState,
                        title = "My Location",
                        zIndex = 10f,
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            showMyProfileSheet = true
                            true
                        }
                    ) {
                        MyLocationMarkerContent(
                            avatarBitmap = myAvatarBitmap
                        )
                    }
                }

                // ── Friend avatar markers (inside GoogleMap → sticks to LatLng) ──
                val validFriends = uiState.friendLocations.filter {
                    it.latitude != 0.0 && it.longitude != 0.0
                }
                validFriends.forEach { friend ->
                    val friendMarkerState =
                        remember(friend.userId, friend.latitude, friend.longitude) {
                            MarkerState(position = LatLng(friend.latitude, friend.longitude))
                        }

                    // Pre-load friend avatar bitmap for marker
                    var friendAvatarBitmap by remember(friend.userId) { mutableStateOf<Bitmap?>(null) }
                    LaunchedEffect(friend.photoUrl) {
                        val url = friend.photoUrl
                        if (!url.isNullOrEmpty()) {
                            val request = ImageRequest.Builder(context)
                                .data(url)
                                .allowHardware(false)
                                .memoryCacheKey(url)
                                .diskCacheKey(url)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .size(128)
                                .build()
                            val result = context.imageLoader.execute(request)
                            if (result is SuccessResult) {
                                friendAvatarBitmap = (result.drawable as? BitmapDrawable)?.bitmap
                            }
                        }
                    }

                    MarkerComposable(
                        keys = arrayOf(friendAvatarBitmap ?: Unit),
                        state = friendMarkerState,
                        title = friend.displayName,
                        snippet = friend.timeAgo,
                        zIndex = 5f,
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.selectFriend(friend)
                            true
                        }
                    ) {
                        FriendMapMarkerContent(
                            friend = friend,
                            avatarBitmap = friendAvatarBitmap
                        )
                    }
                }
            }


            // ── Group filter pill ─────────────────────────────────────────────
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = Dimens.spaceMedium)
                    .clickable { viewModel.showGroupPicker(true) },
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = Dimens.spaceLarge,
                        vertical = Dimens.spaceMedium
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.LocationOn, null,
                        modifier = Modifier.size(Dimens.iconSizeSmall),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(Dimens.spaceSmall))
                    Text(
                        text = uiState.activeGroupFilter?.name ?: "All Friends",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (uiState.friendLocations.isNotEmpty()) {
                        Spacer(Modifier.width(Dimens.spaceSmall))
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Text(
                                "${uiState.friendLocations.size}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    Spacer(Modifier.width(Dimens.spaceXSmall))
                    Icon(
                        Icons.Default.ArrowDropDown, null,
                        modifier = Modifier.size(Dimens.iconSizeMedium),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Stacked status banners (location off + sharing) ──────────────────
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 56.dp, start = Dimens.spaceLarge, end = Dimens.spaceLarge),
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceSmall)
            ) {
                // ── Location off banner
                if (!uiState.isLocationEnabled) {
                    Card(
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Dimens.spaceLarge, vertical = Dimens.spaceSmall),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.LocationOff,
                                contentDescription = null,
                                modifier = Modifier.size(Dimens.iconSizeMedium),
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(Modifier.width(Dimens.spaceSmall))
                            Text(
                                text = "Location is turned off",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = {
                                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                            }) {
                                Text(
                                    text = "Enable",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                // ── Sharing status banner
                if (uiState.isSharing) {
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = Dimens.spaceLarge,
                                vertical = Dimens.spaceSmall
                            ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SharingPulseDot()
                            Spacer(Modifier.width(Dimens.spaceSmall))
                            val sharingTargetName = (uiState.groups + uiState.directTargets)
                                .firstOrNull { it.id == uiState.sharingGroupId }?.name
                                ?: if (uiState.sharingGroupId?.startsWith("direct:") == true) "friend"
                                else "group"
                            val timerText = if (sharingSecondsRemaining != null) {
                                val mins = sharingSecondsRemaining!! / 60
                                val secs = sharingSecondsRemaining!! % 60
                                " • ${"%02d:%02d".format(mins, secs)} remaining"
                            } else {
                                " • Until you stop"
                            }
                            Text(
                                text = "Sharing live with $sharingTargetName$timerText",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }

            // ── Loading indicator ─────────────────────────────────────────────
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            // ── Location permission dialog ─────────────────────────────────────────────
            if (uiState.showLocationOffDialog && !locationGranted) {
                AlertDialog(
                    onDismissRequest = {
                        viewModel.showLocationOffDialog(false)
                        viewModel.setLocationOffDialogShown()
                    },
                    icon = {
                        Icon(
                            Icons.Rounded.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    title = { Text("Location Permission Needed") },
                    text = { Text("Where needs access to your location to show you on the map and share your position with friends.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                                viewModel.showLocationOffDialog(false)
                            }
                        ) {
                            Text("Allow")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                viewModel.showLocationOffDialog(false)
                                viewModel.setLocationOffDialogShown()
                            }
                        ) {
                            Text("Not now")
                        }
                    }
                )
            }

            // ── FABs (right side) ─────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = Dimens.spaceLarge,
                        bottom = Dimens.spaceLarge
                    ),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceMedium)
            ) {
                // Map type
                SmallFloatingActionButton(
                    onClick = { showMapTypeSheet = true },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(id = R.drawable.layers),
                        contentDescription = "Map type",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(Dimens.iconSizeMedium)
                    )
                }

                // My location
                SmallFloatingActionButton(
                    onClick = {
                        val hasPermission = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED ||
                                ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.ACCESS_COARSE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED
                        if (hasPermission) viewModel.locateMe()
                        else permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(id = R.drawable.location_crosshairs),
                        contentDescription = stringResource(R.string.cd_my_location),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(Dimens.iconSizeMedium)
                    )
                }

                // Share / Stop sharing
                if (uiState.isSharing) {
                    FloatingActionButton(
                        onClick = { viewModel.stopSharing() },
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.large
                    ) {
                        Icon(
                            Icons.Rounded.Stop,
                            contentDescription = stringResource(R.string.cd_stop_sharing),
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(Dimens.iconSizeLarge)
                        )
                    }
                } else {
                    FloatingActionButton(
                        onClick = {
                            if (!locationGranted) {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            } else {
                                viewModel.showShareSheet(true)
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        shape = MaterialTheme.shapes.large
                    ) {
                        Icon(
                            imageVector = ImageVector.vectorResource(id = R.drawable.paper_plane),
                            contentDescription = stringResource(R.string.cd_share_location),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(Dimens.iconSizeLarge)
                        )
                    }
                }
            }

            // ── Empty state when no friends sharing ─────────────────────────
            if (uiState.friendLocations.isEmpty() && !uiState.isLoading) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 120.dp, start = 48.dp, end = 48.dp),
                    shape = MaterialTheme.shapes.large,
                    elevation = CardDefaults.cardElevation(2.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = Dimens.spaceLarge,
                            vertical = Dimens.spaceMedium
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.LocationOff,
                            contentDescription = null,
                            modifier = Modifier.size(Dimens.iconSizeMedium),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(Dimens.spaceSmall))
                        Text(
                            text = "No friends sharing right now",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // ── Friend detail bottom sheet ────────────────────────────────────────────
    if (uiState.showFriendSheet && uiState.selectedFriend != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissFriendSheet() },
            sheetState = friendSheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            FriendDetailSheet(
                friend = uiState.selectedFriend!!,
                onMessage = { viewModel.openOrCreateDm(uiState.selectedFriend!!.userId); viewModel.dismissFriendSheet() },
                onNavigateToUserProfile = onNavigateToUserProfile,
                onNavigateToGroupMap = onNavigateToGroupMap,
                onDismiss = { viewModel.dismissFriendSheet() }
            )
        }
    }

    // ── Group filter bottom sheet ─────────────────────────────────────────────
    if (uiState.showGroupPicker) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.showGroupPicker(false) },
            sheetState = groupPickerSheetState
        ) {
            GroupFilterSheet(
                groups = uiState.groups,
                activeFilter = uiState.activeGroupFilter,
                onSelect = { filter ->
                    viewModel.setGroupFilter(filter)
                    viewModel.showGroupPicker(false)
                }
            )
        }
    }

    // ── Share group picker sheet ──────────────────────────────────────────────
    if (uiState.showShareSheet) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.showShareSheet(false) },
            sheetState = shareSheetState
        ) {
            ShareTargetSheet(
                groups = uiState.groups,
                directTargets = uiState.directTargets,
                lastShareTargetId = lastShareTargetId,
                lastShareTargetName = lastShareTargetName,
                lastShareDuration = lastShareDuration,
                onStart = { targetId, durationMinutes ->
                    // Save last-used target for quick-share
                    val allTargets = uiState.groups + uiState.directTargets
                    lastShareTargetId = targetId
                    lastShareTargetName = allTargets.firstOrNull { it.id == targetId }?.name
                    lastShareDuration = durationMinutes
                    viewModel.showShareSheet(false)
                    viewModel.startSharing(targetId, durationMinutes)
                }
            )
        }
    }

    if (showMapTypeSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMapTypeSheet = false },
            sheetState = mapTypeSheetState
        ) {
            MapTypeSheet(
                selected = mapType,
                onSelect = {
                    mapType = it
                    showMapTypeSheet = false
                }
            )
        }
    }

    // ── My profile bottom sheet (tap own marker) ──────────────────────────────
    if (showMyProfileSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMyProfileSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.spaceXLarge)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Avatar
                if (!uiState.myPhotoUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = uiState.myPhotoUrl,
                        contentDescription = null,
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
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(Dimens.iconSizeLarge),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(Modifier.height(Dimens.spaceLarge))

                Text(
                    text = "You",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(Dimens.spaceSmall))

                // Coordinates
                if (uiState.myLatitude != 0.0 && uiState.myLongitude != 0.0) {
                    Text(
                        text = "%.5f, %.5f".format(uiState.myLatitude, uiState.myLongitude),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(Dimens.spaceMedium))

                // Sharing status
                if (uiState.isSharing) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = Dimens.spaceMedium, vertical = Dimens.spaceSmall),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SharingPulseDot()
                            Spacer(Modifier.width(Dimens.spaceSmall))
                            Text(
                                text = "Sharing live",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Not sharing location",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(Dimens.spaceXLarge))

                // Action: Start/Stop sharing
                if (uiState.isSharing) {
                    OutlinedButton(
                        onClick = {
                            viewModel.stopSharing()
                            showMyProfileSheet = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Rounded.Stop, null, modifier = Modifier.size(Dimens.iconSizeMedium))
                        Spacer(Modifier.width(Dimens.spaceSmall))
                        Text("Stop Sharing")
                    }
                } else {
                    Button(
                        onClick = {
                            showMyProfileSheet = false
                            viewModel.showShareSheet(true)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(Dimens.iconSizeMedium))
                        Spacer(Modifier.width(Dimens.spaceSmall))
                        Text("Share My Location")
                    }
                }

                Spacer(Modifier.height(Dimens.spaceLarge))
            }
        }
    }
}

// ── Sub-composables ───────────────────────────────────────────────────────────

@Composable
private fun SharingPulseDot() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "pulse_scale"
    )
    Box(
        modifier = Modifier
            .size(8.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.tertiary)
    )
}

@Composable
private fun FriendAvatarChip(
    friend: FriendLocationUiModel,
    myLatitude: Double = 0.0,
    myLongitude: Double = 0.0,
    hasMyLocation: Boolean = false,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val color = AvatarColors[friend.userId.hashCode().and(0x7FFFFFFF) % AvatarColors.size]
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            if (!friend.photoUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = friend.photoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(Dimens.avatarSizeMedium)
                        .clip(CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(Dimens.avatarSizeMedium)
                        .clip(CircleShape)
                        .background(color),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = friend.displayName.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.surface
                    )
                }
            }
            if (friend.isActive) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary)
                )
            }
        }
        Spacer(Modifier.height(Dimens.spaceXSmall))
        Text(
            text = friend.displayName.substringBefore(" "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = friend.timeAgo,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        // Distance label
        if (hasMyLocation && friend.latitude != 0.0 && friend.longitude != 0.0) {
            val distance = LocationUtils.calculateDistance(
                myLatitude, myLongitude,
                friend.latitude, friend.longitude
            )
            Text(
                text = LocationUtils.formatDistance(context, distance),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
            // ETA display
            friend.etaText?.let { eta ->
                Text(
                    text = "~$eta",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun FriendDetailSheet(
    friend: FriendLocationUiModel,
    onMessage: () -> Unit,
    onNavigateToUserProfile: (String) -> Unit,
    onNavigateToGroupMap: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val color = AvatarColors[friend.userId.hashCode().and(0x7FFFFFFF) % AvatarColors.size]

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimens.spaceXLarge)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Avatar
        if (!friend.photoUrl.isNullOrEmpty()) {
            AsyncImage(
                model = friend.photoUrl,
                contentDescription = null,
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
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = friend.displayName.take(1).uppercase(),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.surface
                )
            }
        }

        Spacer(Modifier.height(Dimens.spaceLarge))

        Text(text = friend.displayName, style = MaterialTheme.typography.headlineSmall)
        if (friend.username.isNotEmpty()) {
            Text(
                text = "@${friend.username}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(Dimens.spaceMedium))

        // Location status
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (friend.isActive) {
                SharingPulseDot()
                Spacer(Modifier.width(Dimens.spaceSmall))
                Text(
                    text = "Sharing in ${friend.groupName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            } else {
                Icon(
                    Icons.Default.LocationOff, null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(Dimens.spaceSmall))
                Text(
                    text = if (friend.timeAgo.isNotEmpty()) "Last seen ${friend.timeAgo}" else "Location not available",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(Dimens.spaceXLarge))

        // Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMedium)
        ) {
            // Message button
            Button(
                onClick = onMessage,
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(
                    Icons.Default.ChatBubbleOutline,
                    null,
                    modifier = Modifier.size(Dimens.iconSizeMedium)
                )
                Spacer(Modifier.width(Dimens.spaceSmall))
                Text("Message")
            }

            // View profile
            OutlinedButton(
                onClick = { onNavigateToUserProfile(friend.userId); onDismiss() },
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Default.Person, null, modifier = Modifier.size(Dimens.iconSizeMedium))
                Spacer(Modifier.width(Dimens.spaceSmall))
                Text("Profile")
            }
        }

        // View on group map
        FilledTonalButton(
            onClick = { onNavigateToGroupMap(friend.groupId); onDismiss() },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        ) {
            Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(Dimens.iconSizeMedium))
            Spacer(Modifier.width(Dimens.spaceSmall))
            Text("Open ${friend.groupName} Map")
        }

        Spacer(Modifier.height(Dimens.spaceLarge))
    }
}

@Composable
private fun GroupFilterSheet(
    groups: List<GroupFilter>,
    activeFilter: GroupFilter?,
    onSelect: (GroupFilter?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimens.spaceXLarge)
            .navigationBarsPadding()
    ) {
        Text("Filter", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(Dimens.spaceLarge))

        LazyColumn(
            modifier = Modifier.heightIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceXSmall)
        ) {
            item {
                FilterRow(
                    title = "All friends",
                    selected = activeFilter == null,
                    onClick = { onSelect(null) }
                )
            }
            item {
                Spacer(Modifier.height(Dimens.spaceXSmall))
                androidx.compose.material3.HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                Spacer(Modifier.height(Dimens.spaceXSmall))
            }
            items(groups, key = { it.id }) { group ->
                FilterRow(
                    title = group.name,
                    selected = activeFilter?.id == group.id,
                    onClick = { onSelect(group) }
                )
            }
        }

        Spacer(Modifier.height(Dimens.spaceLarge))
    }
}

@Composable
private fun FilterRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.spaceLarge, vertical = Dimens.spaceMedium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Groups, null,
                modifier = Modifier.size(Dimens.iconSizeMedium),
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(Dimens.spaceLarge))
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Icon(
                    Icons.Default.Check, null,
                    modifier = Modifier.size(Dimens.iconSizeMedium),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ShareTargetSheet(
    groups: List<GroupFilter>,
    directTargets: List<GroupFilter>,
    lastShareTargetId: String? = null,
    lastShareTargetName: String? = null,
    lastShareDuration: Long = 60L,
    onStart: (String, Long) -> Unit
) {
    val targets = groups + directTargets
    var selectedTargetId by remember { mutableStateOf(targets.firstOrNull()?.id.orEmpty()) }
    var selectedDuration by remember { mutableLongStateOf(60L) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .navigationBarsPadding()
    ) {
        // ── Header with icon ──────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    "Share Location",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Choose who can see your live location",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Quick-share (if previous target exists) ───────────────────────────
        if (lastShareTargetId != null && lastShareTargetName != null) {
            val durationLabel = when (lastShareDuration) {
                15L -> "15 min"
                60L -> "1 hour"
                240L -> "4 hours"
                0L -> "until stopped"
                else -> "${lastShareDuration}m"
            }
            Surface(
                onClick = { onStart(lastShareTargetId, lastShareDuration) },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.NearMe,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Quick share",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            "$lastShareTargetName • $durationLabel",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            androidx.compose.material3.HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(16.dp))
        }

        // ── Target selection ──────────────────────────────────────────────────
        Text(
            "SHARE WITH",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.heightIn(max = 240.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(targets, key = { it.id }) { target ->
                val selected = selectedTargetId == target.id
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { selectedTargetId = target.id },
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = if (selected) androidx.compose.foundation.BorderStroke(
                        1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    ) else null
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TargetAvatar(target)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                target.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                if (target.isDirect) "Direct message" else "Group",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (selected) {
                            Icon(
                                Icons.Default.Check,
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
            if (targets.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Groups,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "No one to share with yet",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Add friends or join a group first",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (targets.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))

            // ── Duration selection ────────────────────────────────────────────
            Text(
                "DURATION",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DurationSegment("15m", selectedDuration == 15L, Modifier.weight(1f)) { selectedDuration = 15L }
                DurationSegment("1h", selectedDuration == 60L, Modifier.weight(1f)) { selectedDuration = 60L }
                DurationSegment("4h", selectedDuration == 240L, Modifier.weight(1f)) { selectedDuration = 240L }
                DurationSegment("∞", selectedDuration == 0L, Modifier.weight(1f)) { selectedDuration = 0L }
            }

            Spacer(Modifier.height(24.dp))

            // ── Start button ──────────────────────────────────────────────────
            Button(
                onClick = {
                    if (selectedTargetId.isNotBlank()) onStart(selectedTargetId, selectedDuration)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    Icons.Default.NearMe,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Start Sharing",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DurationSegment(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(vertical = Dimens.spaceMedium)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TargetAvatar(target: GroupFilter) {
    if (target.isDirect && !target.photoUrl.isNullOrEmpty()) {
        AsyncImage(
            model = target.photoUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(Dimens.avatarSizeMedium)
                .clip(CircleShape)
        )
    } else {
        Surface(
            modifier = Modifier.size(Dimens.avatarSizeMedium),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (target.isDirect) Icons.Default.Person else Icons.Default.Groups,
                    null,
                    modifier = Modifier.size(Dimens.iconSizeMedium)
                )
            }
        }
    }
}

// ── Map Type Sheet (Redesigned with preview cards) ───────────────────────────

private data class MapStyleOption(
    val type: MapType,
    val label: String,
    @DrawableRes val photoRes: Int,
    val description: String
)

private val mapStyleOptions = listOf(
    MapStyleOption(
        type = MapType.NORMAL,
        label = "Default",
        photoRes = R.drawable.maptype_default_preview,
        description = "Clean streets"
    ),
    MapStyleOption(
        type = MapType.SATELLITE,
        label = "Satellite",
        photoRes = R.drawable.maptype_satellite_preview,
        description = "Aerial imagery"
    )
)

@Composable
private fun MapTypeSheet(
    selected: MapType,
    onSelect: (MapType) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimens.spaceLarge)
            .navigationBarsPadding()
    ) {
        Text(
            text = "Map Style",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(Dimens.spaceSmall))
        Text(
            text = "Choose how the map looks",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Dimens.spaceXLarge))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            mapStyleOptions.forEach { option ->
                Box(modifier = Modifier.weight(1f)) {
                    MapStyleCard(
                        option = option,
                        isSelected = selected == option.type,
                        onClick = { onSelect(option.type) }
                    )
                }
            }
        }

        Spacer(Modifier.height(Dimens.spaceXLarge))
    }
}

@Composable
private fun MapStyleCard(
    option: MapStyleOption,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outlineVariant
    val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceVariant

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = borderColor
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Dimens.spaceLarge),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Preview image area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.4f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Image(
                    painter = painterResource(option.photoRes),
                    contentDescription = option.label,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Crop
                )
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    )
                }
            }

            Spacer(Modifier.height(Dimens.spaceMedium))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = option.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isSelected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Selected",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

// ── Premium Map Pin Marker ───────────────────────────────────────────────────

private val PIN_BODY_SIZE = 52.dp        // Circle avatar area
private val PIN_BORDER_WIDTH = 3.dp      // White border ring
private val PIN_TAIL_WIDTH = 20.dp       // Tail width
private val PIN_TAIL_HEIGHT = 12.dp      // Tail height
private val PIN_INNER_PADDING = 3.5.dp   // Gap between border and avatar

/**
 * Premium drop-pin marker — circular avatar body with a clean white border,
 * smooth bezier-curved tail pointer, accent-colored shadow, and optional
 * avatar bitmap. Pre-loaded bitmap avoids async issues on software canvas.
 */
@Composable
private fun Life360PinMarker(
    avatarBitmap: Bitmap?,
    fallbackLabel: String,
    accentColor: Color,
    borderColor: Color = Color.Unspecified
) {
    val resolvedBorderColor = if (borderColor == Color.Unspecified) {
        MaterialTheme.colorScheme.surface
    } else {
        borderColor
    }
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Circular avatar body ──────────────────────────────────────────
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(PIN_BODY_SIZE)
                .shadow(
                    elevation = 10.dp,
                    shape = CircleShape,
                    ambientColor = accentColor.copy(alpha = 0.25f),
                    spotColor = accentColor.copy(alpha = 0.35f)
                )
                .background(resolvedBorderColor, CircleShape)
                .padding(PIN_BORDER_WIDTH)
                .clip(CircleShape)
                .background(accentColor)
                .padding(PIN_INNER_PADDING)
                .clip(CircleShape)
                .background(surfaceVariant)
        ) {
            if (avatarBitmap != null) {
                Image(
                    painter = BitmapPainter(avatarBitmap.asImageBitmap()),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = fallbackLabel,
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        // ── Smooth curved tail pointer ────────────────────────────────────
        Box(
            modifier = Modifier
                .offset(y = (-2).dp) // Overlap body slightly for seamless join
                .size(width = PIN_TAIL_WIDTH, height = PIN_TAIL_HEIGHT)
                .drawBehind {
                    val path = Path().apply {
                        // Flat top edge
                        moveTo(0f, 0f)
                        lineTo(size.width, 0f)
                        // Right curve down to point
                        cubicTo(
                            size.width * 0.75f, size.height * 0.15f,
                            size.width * 0.6f, size.height * 0.85f,
                            size.width / 2f, size.height
                        )
                        // Left curve back up from point
                        cubicTo(
                            size.width * 0.4f, size.height * 0.85f,
                            size.width * 0.25f, size.height * 0.15f,
                            0f, 0f
                        )
                        close()
                    }
                    drawPath(path, color = resolvedBorderColor)
                }
        )
    }
}

@Composable
private fun MyLocationMarkerContent(
    avatarBitmap: Bitmap?
) {
    Life360PinMarker(
        avatarBitmap = avatarBitmap,
        fallbackLabel = "ME",
        accentColor = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun FriendMapMarkerContent(
    friend: FriendLocationUiModel,
    avatarBitmap: Bitmap?
) {
    Life360PinMarker(
        avatarBitmap = avatarBitmap,
        fallbackLabel = friend.displayName.take(1).uppercase(),
        accentColor = avatarColorFor(friend.userId)
    )
}
