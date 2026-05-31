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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SocialDistance
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
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
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.ovi.where.R
import com.ovi.where.core.notification.activeMapTracker
import com.ovi.where.core.theme.AvatarColors
import com.ovi.where.core.theme.Dimens
import com.ovi.where.core.theme.LocationActive
import com.ovi.where.core.utils.LocationUtils
import com.ovi.where.core.utils.showToast
import com.ovi.where.presentation.map.components.DestinationPinMarker
import com.ovi.where.presentation.map.components.MeetupChip
import com.ovi.where.presentation.map.components.MeetupPlaceCardSheet
import com.ovi.where.presentation.map.components.MeetupPlacementActionBar
import com.ovi.where.presentation.map.components.SetMeetupDestinationSheet
import com.ovi.where.presentation.map.components.fanOutOverlappingMarkers
import kotlinx.coroutines.launch

/**
 * Bottom navigation bar content height (excludes system insets).
 * Exported so the parent scaffold and the map screen agree on padding math.
 */
val MapNavBarHeight = 80.dp


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalMapScreen(
    onNavigateToChat: (String) -> Unit = {},
    onNavigateToUserProfile: (String) -> Unit = {},
    onNavigateToCreateGroup: () -> Unit = {},
    onNavigateToJoinGroup: () -> Unit = {},
    onNavigateToAddFriends: () -> Unit = {},
    /**
     * True when the Map tab is the currently selected destination.
     *
     * `GlobalMapScreen` is rendered as a persistent backdrop in
     * [com.ovi.where.presentation.navigation.AppNavGraph] so the
     * underlying `GoogleMap` stays warm across tab switches and never
     * flashes a blank frame on re-entry. When this flag is `false` the
     * screen suppresses its chrome (chips, FABs, BottomSheet, dialogs)
     * so it can't bleed through to whatever tab the user is actually
     * viewing — only the map itself stays composed.
     *
     * Defaults to `true` for callers (tests / previews) that don't yet
     * use the persistent-backdrop pattern.
     */
    isActiveTab: Boolean = true,
    viewModel: GlobalMapViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val navigateToChat by viewModel.navigateToChat.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Notification suppression — while the global map is foregrounded the
    // user already sees live-location and meetup events on screen, so the
    // FCM service drops their system-tray pushes. The inbox still gets
    // populated so taps from notifications shade after backgrounding work.
    //
    // Tied to [isActiveTab] now (not just composition) because the screen
    // stays composed as a persistent backdrop across tab switches. We
    // only want push-suppression while the user is *looking* at the map.
    androidx.compose.runtime.DisposableEffect(isActiveTab) {
        val tracker = context.activeMapTracker()
        tracker.setMapVisible(isActiveTab)
        onDispose { tracker.setMapVisible(false) }
    }

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
        // Seeded from the user's last-known GPS coords (persisted in DataStore).
        // Falls back to (0,0) zoom 2 on first-ever launch.
        position = CameraPosition.fromLatLngZoom(
            LatLng(uiState.initialCameraLat, uiState.initialCameraLng),
            uiState.initialCameraZoom
        )
    }

    // The ViewModel reads last-known coords from DataStore asynchronously, so the
    // remember lambda above usually runs with defaults still in place. Once the
    // read finishes, snap the camera to the restored position. We also gate
    // GoogleMap composition on `cameraRestored` below, so the map's first frame
    // is always the correct one — even when location services are off.
    LaunchedEffect(uiState.cameraRestored) {
        if (uiState.cameraRestored &&
            (uiState.initialCameraLat != 0.0 || uiState.initialCameraLng != 0.0)
        ) {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(
                LatLng(uiState.initialCameraLat, uiState.initialCameraLng),
                uiState.initialCameraZoom
            )
        }
    }

    // ── Permission launcher ───────────────────────────────────────────────────
    val locationDeniedMessage = stringResource(R.string.toast_location_denied)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        locationGranted = granted
        if (granted) viewModel.locateMe()
        else context.showToast(locationDeniedMessage)
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
    // Uses ViewModel state so it persists across tab switches (like Life360/Google Maps).
    LaunchedEffect(uiState.friendLocations, uiState.hasAutoZoomed) {
        if (!uiState.hasAutoZoomed && uiState.friendLocations.isNotEmpty()) {
            viewModel.onAutoZoomConsumed()
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
    var showMeetupClearConfirm by remember { mutableStateOf(false) }
    var showMeetupStatusEditor by remember { mutableStateOf(false) }


    // Single persistent bottom sheet that switches views.
    // Replaces multiple modal bottom sheets for cleaner UX.
    var sheetView by remember { mutableStateOf(MapSheetView.Home) }
    // Home view has two tabs: friends sharing with me / my active shares.
    var homeTab by remember { mutableStateOf(MapHomeTab.Friends) }

    // ── Bottom sheet state ────────────────────────────────────────────────────
    val groupPickerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val mapTypeSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Google Maps style: map fills the entire screen behind the overlaid nav bar.
    // The sheet's peek includes the nav bar height so its rounded corners sit
    // ABOVE the nav bar (not behind it). A bottom Spacer in the sheet content
    // keeps content from being hidden behind the nav bar.
    val systemBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val totalNavBarHeight = MapNavBarHeight + systemBottomInset
    // Peek shows: drag handle + hero header (~64dp) + avatars row (~80dp) when friends exist.
    // In placement mode we collapse the sheet to just its drag handle (~32dp) so the map
    // gets max vertical area for the user to find their meetup spot.
    val contentPeek = 250.dp
    val placementPeek = 32.dp
    val sheetPeekHeight = if (uiState.isMeetupPlacementMode) {
        placementPeek + totalNavBarHeight
    } else {
        contentPeek + totalNavBarHeight
    }

    val bottomSheetScaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded
        )
    )

    // Auto-expand sheet when switching to a non-Home view (detail/share)
    LaunchedEffect(sheetView) {
        if (sheetView != MapSheetView.Home) {
            scope.launch { bottomSheetScaffoldState.bottomSheetState.expand() }
        }
    }

    // Collapse the sheet to peek the moment placement mode starts so the map
    // gets max vertical space to pick a spot from.
    LaunchedEffect(uiState.isMeetupPlacementMode) {
        if (uiState.isMeetupPlacementMode) {
            bottomSheetScaffoldState.bottomSheetState.partialExpand()
        }
    }

    BottomSheetScaffold(
        scaffoldState = bottomSheetScaffoldState,
        sheetPeekHeight = sheetPeekHeight,
        sheetShape = RoundedCornerShape(topStart = 38.dp, topEnd = 38.dp),
        sheetContainerColor = MaterialTheme.colorScheme.surface,
        sheetShadowElevation = 12.dp,
        sheetTonalElevation = 1.dp,
        sheetDragHandle = {
            // Always-visible drag handle — signals to user that the sheet is draggable
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                )
                Spacer(Modifier.height(8.dp))
            }
        },
        sheetContent = {
            // Sync the active view with state changes
            LaunchedEffect(uiState.selectedFriend?.userId) {
                if (uiState.selectedFriend != null) {
                    sheetView = MapSheetView.FriendDetail
                }
            }

            AnimatedContent(
                targetState = sheetView,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    (slideInHorizontally(
                        initialOffsetX = { fullWidth -> if (forward) fullWidth else -fullWidth },
                        animationSpec = tween(300)
                    ) togetherWith slideOutHorizontally(
                        targetOffsetX = { fullWidth -> if (forward) -fullWidth else fullWidth },
                        animationSpec = tween(300)
                    )).using(SizeTransform(clip = true))
                },
                label = "sheetViewAnim"
            ) { currentView ->
                when (currentView) {
                    MapSheetView.FriendDetail -> {
                        val friend = uiState.selectedFriend
                        if (friend != null) {
                            FriendDetailSheetContent(
                                friend = friend,
                                bottomReservedSpace = totalNavBarHeight,
                                onMessage = {
                                    viewModel.openOrCreateDm(friend.userId)
                                    viewModel.dismissFriendSheet()
                                    sheetView = MapSheetView.Home
                                },
                                onNavigateToUserProfile = {
                                    onNavigateToUserProfile(friend.userId)
                                    viewModel.dismissFriendSheet()
                                    sheetView = MapSheetView.Home
                                },
                                onBack = {
                                    viewModel.dismissFriendSheet()
                                    sheetView = MapSheetView.Home
                                }
                            )
                        } else {
                            // Stale state — fall back to home
                            sheetView = MapSheetView.Home
                            Spacer(Modifier.height(1.dp))
                        }
                    }

                    MapSheetView.Share -> {
                        ShareTargetSheet(
                            groups = uiState.groups,
                            directTargets = uiState.directTargets,
                            excludeTargetIds = uiState.sharingTargetIds,
                            bottomReservedSpace = totalNavBarHeight,
                            onCancel = { sheetView = MapSheetView.Home },
                            onCreateGroup = {
                                sheetView = MapSheetView.Home
                                onNavigateToCreateGroup()
                            },
                            onJoinGroup = {
                                sheetView = MapSheetView.Home
                                onNavigateToJoinGroup()
                            },
                            onAddFriends = {
                                sheetView = MapSheetView.Home
                                onNavigateToAddFriends()
                            },
                            onStart = { targetIds, durationMinutes ->
                                if (uiState.isSharing) {
                                    targetIds.forEach { id ->
                                        viewModel.addSharingTarget(id, durationMinutes)
                                    }
                                } else {
                                    viewModel.startSharing(targetIds, durationMinutes)
                                }
                                // Land back on the My Shares tab to show what was just started.
                                homeTab = MapHomeTab.MyShares
                                sheetView = MapSheetView.Home
                            }
                        )
                    }

                    MapSheetView.Home -> {
                        HomeSheetContent(
                            homeTab = homeTab,
                            onTabChange = { homeTab = it },
                            friends = uiState.friendLocations,
                            myLatitude = uiState.myLatitude,
                            myLongitude = uiState.myLongitude,
                            hasMyLocation = uiState.hasMyLocation,
                            isSharing = uiState.isSharing,
                            activeFilter = uiState.activeGroupFilter,
                            sharingTargetIds = uiState.sharingTargetIds,
                            sharingTargetExpiries = uiState.sharingTargetExpiries,
                            meetupOwnedShareGroupIds = uiState.meetupOwnedShareGroupIds,
                            hostedMeetupGroupIds = uiState.hostedMeetupGroupIds,
                            groups = uiState.groups,
                            directTargets = uiState.directTargets,
                            bottomReservedSpace = totalNavBarHeight,
                            onFriendClick = { friend ->
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
                            },
                            onShowOnMap = { friend ->
                                // Collapse the sheet so the map is fully visible, then animate
                                // camera to the friend with a smoother, deeper zoom.
                                if (friend.latitude != 0.0 && friend.longitude != 0.0) {
                                    scope.launch {
                                        bottomSheetScaffoldState.bottomSheetState.partialExpand()
                                        cameraPositionState.animate(
                                            CameraUpdateFactory.newLatLngZoom(
                                                LatLng(friend.latitude, friend.longitude),
                                                17f
                                            ),
                                            durationMs = 900
                                        )
                                    }
                                }
                            },
                            onAddShare = {
                                if (!locationGranted) {
                                    permissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                } else {
                                    sheetView = MapSheetView.Share
                                }
                            },
                            onStopOne = { targetId ->
                                viewModel.removeSharingTarget(targetId)
                            },
                            onStopAll = {
                                viewModel.stopSharing()
                            },
                            onOpenMeetupCard = {
                                viewModel.openMeetupPlaceCard()
                            }
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        // innerPadding.bottom = sheetPeekHeight (sheet peek + nav bar height).
        // Map fills the full Box behind everything; FABs and overlays use innerPadding
        // so they sit above the sheet peek and bottom nav.
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Backdrop tuned to match Google Maps' official dark/light tile
                // colors. Painted under the GoogleMap so the brief mount-to-
                // first-tile window blends into the theme instead of flashing.


        ) {
            // ── Map (full screen — behind sheet, nav bar, and FABs) ───────────
            // Wait for the saved camera position to load from DataStore before
            // composing the map. This is a few-ms delay (invisible to the user)
            // and guarantees the very first frame is the correct location.
            // Without this gate, GoogleMap composes at (0,0) zoom 2 and stays
            // there if location is off (no live fix to override it).
            if (uiState.cameraRestored) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    contentPadding = innerPadding,
                    onMapLongClick = { latLng ->
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.onMapLongClick(latLng.latitude, latLng.longitude)
                    },
                    // mapColorScheme is the SDK's native dark mode and is applied
                    // *before* the first frame, eliminating the light-tile flash
                    // we used to see while the JSON style was being applied. We
                    // keep mapStyleOptions for fine-tuning POI visibility etc.
                    mapColorScheme = ComposeMapColorScheme.FOLLOW_SYSTEM,
                    properties = MapProperties(
                        mapType = mapType,
                        isMyLocationEnabled = false
                    ),
                    uiSettings = MapUiSettings(
                        myLocationButtonEnabled = false,
                        zoomControlsEnabled = false
                    )
                ) {
                    // ── Compute display positions (fan-out overlapping markers) ────
                    // When markers (me + friends) sit at the same spot, spread them out
                    // in a small circle around their centroid so all avatars are visible.
                    val validFriends = uiState.friendLocations.filter {
                        it.latitude != 0.0 && it.longitude != 0.0
                    }
                    val displayPositions = remember(
                        uiState.hasMyLocation,
                        uiState.myLatitude,
                        uiState.myLongitude,
                        validFriends
                    ) {
                        val all = mutableListOf<Pair<String, LatLng>>()
                        if (uiState.hasMyLocation && uiState.myLatitude != 0.0 && uiState.myLongitude != 0.0) {
                            all.add(
                                MY_MARKER_KEY to LatLng(
                                    uiState.myLatitude,
                                    uiState.myLongitude
                                )
                            )
                        }
                        validFriends.forEach {
                            all.add(it.userId to LatLng(it.latitude, it.longitude))
                        }
                        fanOutOverlappingMarkers(all)
                    }

                    // ── My location marker (inside GoogleMap → sticks to LatLng) ──
                    if (uiState.hasMyLocation && uiState.myLatitude != 0.0 && uiState.myLongitude != 0.0) {
                        val myDisplayPos = displayPositions[MY_MARKER_KEY]
                            ?: LatLng(uiState.myLatitude, uiState.myLongitude)
                        val myMarkerState =
                            remember(myDisplayPos.latitude, myDisplayPos.longitude) {
                                MarkerState(position = myDisplayPos)
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
                            keys = arrayOf(
                                myAvatarBitmap ?: Unit,
                                uiState.selfMeetupNote,
                                uiState.selfMeetupStatus,
                                uiState.meetupDestination != null,
                                uiState.selfIsAtHome
                            ),
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
                                avatarBitmap = myAvatarBitmap,
                                note = uiState.selfMeetupNote,
                                meetupStatus = uiState.selfMeetupStatus
                                    .takeIf { uiState.meetupDestination != null },
                                isAtHome = uiState.selfIsAtHome
                            )
                        }
                    }

                    // ── Friend avatar markers (inside GoogleMap → sticks to LatLng) ──
                    validFriends.forEach { friend ->
                        val friendDisplayPos = displayPositions[friend.userId]
                            ?: LatLng(friend.latitude, friend.longitude)
                        val friendMarkerState =
                            remember(
                                friend.userId,
                                friendDisplayPos.latitude,
                                friendDisplayPos.longitude
                            ) {
                                MarkerState(position = friendDisplayPos)
                            }

                        // Pre-load friend avatar bitmap for marker
                        var friendAvatarBitmap by remember(friend.userId) {
                            mutableStateOf<Bitmap?>(
                                null
                            )
                        }
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
                                    friendAvatarBitmap =
                                        (result.drawable as? BitmapDrawable)?.bitmap
                                }
                            }
                        }

                        MarkerComposable(
                            keys = arrayOf(
                                friendAvatarBitmap ?: Unit,
                                friend.meetupNote,
                                friend.meetupStatus?.name ?: "",
                                friend.isAtHome
                            ),
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

                    // ── Meetup destination marker ─────────────────────────────────
                    val destination = uiState.meetupDestination
                    if (destination != null && destination.hasValidLocation && destination.isActive) {
                        val destinationLatLng =
                            remember(destination.latitude, destination.longitude) {
                                LatLng(destination.latitude, destination.longitude)
                            }
                        val destinationMarkerState = remember(destinationLatLng) {
                            MarkerState(position = destinationLatLng)
                        }
                        MarkerComposable(
                            state = destinationMarkerState,
                            title = destination.name.ifEmpty { "Meetup point" },
                            snippet = destination.address.takeIf { it.isNotBlank() },
                            zIndex = 4f,
                            onClick = {
                                viewModel.openMeetupPlaceCard()
                                true
                            }
                        ) {
                            DestinationPinMarker()
                        }
                    }

                    // ── Home pin (tapped from a profile's Home section) ───────────
                    val homePin = uiState.homePin
                    if (homePin != null) {
                        val homeLatLng = remember(homePin.latitude, homePin.longitude) {
                            LatLng(homePin.latitude, homePin.longitude)
                        }
                        val homeMarkerState = remember(homeLatLng) {
                            MarkerState(position = homeLatLng)
                        }
                        // Pre-load the home owner's avatar for the pin.
                        var homeAvatarBitmap by remember(homePin.userId) {
                            mutableStateOf<Bitmap?>(null)
                        }
                        LaunchedEffect(homePin.photoUrl) {
                            val url = homePin.photoUrl
                            if (!url.isNullOrEmpty()) {
                                val request = ImageRequest.Builder(context)
                                    .data(url)
                                    .allowHardware(false)
                                    .size(128)
                                    .build()
                                val result = context.imageLoader.execute(request)
                                if (result is SuccessResult) {
                                    homeAvatarBitmap = (result.drawable as? BitmapDrawable)?.bitmap
                                }
                            }
                        }
                        MarkerComposable(
                            keys = arrayOf(homePin.userId, homeAvatarBitmap ?: Unit),
                            state = homeMarkerState,
                            title = homePin.label.ifEmpty { "${homePin.displayName}'s home" },
                            snippet = homePin.label.takeIf { it.isNotBlank() },
                            zIndex = 6f,
                            onClick = {
                                viewModel.dismissHomePin()
                                true
                            }
                        ) {
                            HomePinMarkerContent(
                                ownerName = homePin.displayName,
                                avatarBitmap = homeAvatarBitmap
                            )
                        }
                    }

                    // ── In-app navigation polyline ────────────────────────────────
                    // Drawn over the existing GoogleMap when the user taps
                    // "Directions" on the meetup place card. Lives next to
                    // the destination marker so they share z-stacking.
                    uiState.navigationRoute?.points?.takeIf { it.size >= 2 }?.let { points ->
                        com.google.maps.android.compose.Polyline(
                            points = points,
                            color = MaterialTheme.colorScheme.primary,
                            width = 14f,
                            geodesic = false,
                            zIndex = 1f
                        )
                    }
                }
            }


            // ── Top-of-map chip strip ─────────────────────────────────────────
            // Sits at the top center like Google Maps' chip strip. Both chips
            // share the same chip language: 40dp height, 50% radius, single-
            // line label, leading icon/avatar. Group filter on the left,
            // meetup chip on the right.
            //
            // Hidden while in-app navigation is active — that mode owns
            // the top of the map with its own status surface.
            if (!uiState.isMeetupNavigating) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 12.dp)
                        .padding(horizontal = Dimens.spaceMedium)
                        // Safety net: if the active meetup chip's label, the
                        // group-filter name, and the notification chip can't
                        // all fit on the smallest screen, let the strip
                        // scroll horizontally rather than clipping the
                        // notification chip off the right edge.
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Group filter chip — white pill, soft shadow, no border,
                    // bare leading glyph + bold label + dropdown chevron.
                    Surface(
                        modifier = Modifier
                            .height(40.dp)
                            .padding(start = 16.dp)
                            .softDropShadow(
                                color = Color.Black.copy(alpha = 0.12f),
                                blurRadius = 16.dp,
                                offsetY = 6.dp
                            )
                            .clip(RoundedCornerShape(50))
                            .clickable { viewModel.showGroupPicker(true) },
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        shadowElevation = 0.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 12.dp, end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val activeFilter = uiState.activeGroupFilter
                            if (activeFilter == null) {
                                // Default "All Friends" — bare warm people glyph.
                                Icon(
                                    imageVector = ImageVector.vectorResource(id = R.drawable.group_outlined),
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                // Specific group / friend — show their avatar.
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    FilterPillAvatar(filter = activeFilter)
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = activeFilter?.name ?: "All Friends",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 110.dp)
                            )
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    MeetupChip(
                        destination = uiState.meetupDestination,
                        distanceText = uiState.meetupDestinationDistanceText,
                        onIdleClick = { viewModel.enterMeetupPlacement() },
                        onActiveClick = { viewModel.openMeetupPlaceCard() }
                    )
                }
            }

            // ── Navigation status card (in-app navigation overlay) ───────────
            // Replaces the chip strip while navigating. Shows distance + ETA
            // along with Recenter / Stop controls, sits at top-center so the
            // user's eye doesn't have to relocate from the chip strip's spot.
            if (uiState.isMeetupNavigating) {
                NavigationStatusCard(
                    destinationName = uiState.meetupDestination?.name?.takeIf { it.isNotBlank() }
                        ?: "Meetup Point",
                    distanceLabel = uiState.navigationDistanceLabel,
                    etaLabel = uiState.navigationEtaLabel,
                    isLoading = uiState.navigationLoading,
                    errorMessage = uiState.navigationError,
                    onRecenter = { viewModel.refreshMeetupRoute() },
                    onStop = { viewModel.stopMeetupNavigation() },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(horizontal = Dimens.spaceMedium, vertical = 12.dp)
                )
            }

            // ── Stacked status banners (location off + sharing) ──────────────────
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 84.dp, start = Dimens.spaceLarge, end = Dimens.spaceLarge),
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
                                .padding(
                                    horizontal = Dimens.spaceLarge,
                                    vertical = Dimens.spaceSmall
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.LocationOff,
                                contentDescription = null,
                                modifier = Modifier.size(Dimens.iconSizeMedium),
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(Modifier.width(Dimens.spaceMedium))
                            Text(
                                text = "Location is turned off",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = {
                                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                            }) {
                                Text(
                                    text = "Enable",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }

            // ── Loading indicator ─────────────────────────────────────────────
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            // ── Permission onboarding sheet (first launch) ───────────────────────────
            //
            // Replaces the legacy single-permission AlertDialog with a full
            // bottom sheet that surfaces every permission the app needs in
            // one polished educational flow. Matches the pattern used by
            // Life360, Google Maps, and Discord — a single onboarding
            // moment, not a chain of system dialogs.
            if (uiState.showPermissionOnboarding) {
                com.ovi.where.presentation.permission.PermissionOnboardingSheet(
                    onDismiss = { viewModel.dismissPermissionOnboarding() },
                    onComplete = { viewModel.dismissPermissionOnboarding() },
                )
            }

            // ── FABs (right side) ─────────────────────────────────────────────
            // FABs use innerPadding to sit above the sheet peek (which already
            // includes the bottom nav bar height). When the user pulls the sheet
            // up, it slides over the FABs — exactly like Google Maps.
            // Hidden while placement mode is active to keep the placement
            // crosshair + action bar the only visible chrome.
            if (!uiState.isMeetupPlacementMode) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(innerPadding)
                        .padding(
                            end = Dimens.spaceLarge,
                            bottom = 20.dp
                        ),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(Dimens.spaceMedium)
                ) {
                    // Map type
                    SmallFloatingActionButton(
                        onClick = { showMapTypeSheet = true },
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        shape = CircleShape,
                        elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(
                            defaultElevation = 6.dp,
                            pressedElevation = 8.dp
                        )
                    ) {
                        Icon(
                            imageVector = ImageVector.vectorResource(id = R.drawable.layers_outlined),
                            contentDescription = "Map type",
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
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        shape = CircleShape,
                        elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(
                            defaultElevation = 6.dp,
                            pressedElevation = 8.dp
                        )
                    ) {
                        Icon(
                            imageVector = ImageVector.vectorResource(id = R.drawable.location_crosshairs_outlined),
                            contentDescription = stringResource(R.string.cd_my_location),
                            modifier = Modifier.size(Dimens.iconSizeMedium)
                        )
                    }

                    // Share / Stop sharing — primary FAB
                    if (uiState.isSharing) {
                        FloatingActionButton(
                            onClick = {
                                sheetView = MapSheetView.Home
                                homeTab = MapHomeTab.MyShares
                                scope.launch { bottomSheetScaffoldState.bottomSheetState.expand() }
                            },
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            shape = RoundedCornerShape(24.dp),
                            elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(
                                defaultElevation = 8.dp,
                                pressedElevation = 12.dp
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Stop,
                                contentDescription = stringResource(R.string.cd_stop_sharing),
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
                                    sheetView = MapSheetView.Share
                                    scope.launch { bottomSheetScaffoldState.bottomSheetState.expand() }
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            shape = RoundedCornerShape(24.dp),
                            elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(
                                defaultElevation = 8.dp,
                                pressedElevation = 12.dp
                            )
                        ) {
                            Icon(
                                imageVector = ImageVector.vectorResource(id = R.drawable.location_arrow),
                                contentDescription = stringResource(R.string.cd_share_location),
                                modifier = Modifier.size(Dimens.iconSizeLarge)
                            )
                        }
                    }
                }
            } // end if (!uiState.isMeetupPlacementMode) — FAB column

            // ── Meetup placement overlay ─────────────────────────────────────
            // When the user enters placement mode we render:
            //   • An animated crosshair anchored to the visible map center
            //     (accounting for the chip strip on top and the action bar /
            //     sheet peek on the bottom).
            //   • A top hint pill.
            //   • A compact bottom action bar.
            // The home sheet is collapsed to a tiny peek so the user has
            // maximum map real estate to pan / zoom.
            if (uiState.isMeetupPlacementMode) {
                // Visible-area aware crosshair centering. Top reserved space
                // accounts for the status bar + chip strip (~70dp). Bottom
                // reserved space is the sheet peek + nav bar + action bar
                // height. We shift the crosshair up by half the difference
                // so it lands in the middle of what the user can actually
                // see, not the absolute screen center.
                val statusBarTop = WindowInsets.statusBars
                    .asPaddingValues().calculateTopPadding()
                val chipStripHeight = 70.dp
                val actionBarHeight = 90.dp
                val topReserved = statusBarTop + chipStripHeight
                val bottomReserved = sheetPeekHeight + actionBarHeight
                val crosshairYOffset = (topReserved - bottomReserved) / 2

                com.ovi.where.presentation.map.components.PlacementCrosshair(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = crosshairYOffset)
                )

                // Top hint pill — slides in from above.
                androidx.compose.animation.AnimatedVisibility(
                    visible = true,
                    enter = androidx.compose.animation.slideInVertically(
                        initialOffsetY = { -it },
                        animationSpec = androidx.compose.animation.core.tween(350)
                    ) + androidx.compose.animation.fadeIn(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 72.dp)
                        .padding(horizontal = Dimens.spaceLarge)
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                        tonalElevation = 4.dp,
                        shadowElevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.inverseOnSurface,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Move and zoom the map to pick a spot",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Compact bottom action bar — sits just above the collapsed
                // sheet peek so the map dominates the screen.
                MeetupPlacementActionBar(
                    address = uiState.placementAddress,
                    isResolving = uiState.isResolvingPlacementAddress,
                    onCancel = { viewModel.cancelMeetupPlacement() },
                    onConfirm = {
                        val center = cameraPositionState.position.target
                        viewModel.confirmMeetupPlacement(center.latitude, center.longitude)
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = sheetPeekHeight + 8.dp)
                        .padding(horizontal = 12.dp)
                )

                // Reverse-geocode the centered point each time the camera idles.
                LaunchedEffect(cameraPositionState.isMoving) {
                    if (!cameraPositionState.isMoving) {
                        val target = cameraPositionState.position.target
                        viewModel.onPlacementCameraIdle(target.latitude, target.longitude)
                    }
                }

                // System back exits placement mode.
                androidx.activity.compose.BackHandler(enabled = true) {
                    viewModel.cancelMeetupPlacement()
                }
            }

            // ── Frame the camera onto the active destination on demand ─────
            // Triggered by the place-card sheet's "Show on map" action and
            // by confirmDestinationPick after a successful write. Keyed on
            // both the request flag AND the destination state so we still
            // fire correctly when the destination is set just-in-time (the
            // Firestore listener attaches after the request flag flips).
            LaunchedEffect(uiState.requestDestinationFocus, uiState.meetupDestination) {
                val destination = uiState.meetupDestination
                if (uiState.requestDestinationFocus && destination != null && destination.hasValidLocation) {
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(destination.latitude, destination.longitude), 16f
                        ),
                        durationMs = 700
                    )
                    viewModel.onDestinationFocusConsumed()
                }
            }

            // ── Frame the camera onto a tapped Home pin ────────────────────
            // Triggered when a user taps the Home section on a profile. The
            // home pin is published via HomePinEventBus and the VM flips
            // requestHomePinFocus; we animate the camera once, then consume.
            LaunchedEffect(uiState.requestHomePinFocus, uiState.homePin) {
                val pin = uiState.homePin
                if (uiState.requestHomePinFocus && pin != null) {
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(pin.latitude, pin.longitude), 16f
                        ),
                        durationMs = 700
                    )
                    viewModel.onHomePinFocusConsumed()
                }
            }

            // ── Navigation camera fitting ────────────────────────────────
            // Fits the camera around (origin + destination) on every
            // recenter request — tied to a long timestamp so it fires
            // exactly once per request even if the route refetches in
            // between.
            LaunchedEffect(uiState.navigationRecenterRequest) {
                if (uiState.navigationRecenterRequest == 0L) return@LaunchedEffect
                if (!uiState.isMeetupNavigating) return@LaunchedEffect
                val destination = uiState.meetupDestination ?: return@LaunchedEffect
                if (!destination.hasValidLocation || !uiState.hasMyLocation) return@LaunchedEffect
                val bounds = LatLngBounds.builder()
                    .include(LatLng(uiState.myLatitude, uiState.myLongitude))
                    .include(LatLng(destination.latitude, destination.longitude))
                    .build()
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngBounds(bounds, 180),
                    durationMs = 700
                )
            }
        }
    }

    // ── Group filter bottom sheet ─────────────────────────────────────────────
    if (uiState.showGroupPicker) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.showGroupPicker(false) },
            sheetState = groupPickerSheetState,
            shape = RoundedCornerShape(topStart = 38.dp, topEnd = 38.dp)
        ) {
            GroupFilterSheet(
                groups = uiState.groups,
                directTargets = uiState.directTargets,
                activeFilter = uiState.activeGroupFilter,
                onSelect = { filter ->
                    viewModel.setGroupFilter(filter)
                    viewModel.showGroupPicker(false)
                }
            )
        }
    }

    // Map Type bottom sheet ───────────────────────────────────────────────────
    if (showMapTypeSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMapTypeSheet = false },
            sheetState = mapTypeSheetState,
            shape = RoundedCornerShape(topStart = 38.dp, topEnd = 38.dp)
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
            onDismissRequest = { showMyProfileSheet = false },
            shape = RoundedCornerShape(topStart = 38.dp, topEnd = 38.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.spaceXLarge)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val context = LocalContext.current
                val density = LocalDensity.current

                val myAvatarPixelSize = remember(density) {
                    with(density) { Dimens.avatarSizeXLarge.roundToPx() }
                }

                val myAvatarRequest = remember(uiState.myPhotoUrl, myAvatarPixelSize) {
                    if (uiState.myPhotoUrl.isNullOrBlank()) {
                        null
                    } else {
                        ImageRequest.Builder(context)
                            .data(uiState.myPhotoUrl)
                            .crossfade(true)
                            .size(myAvatarPixelSize)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .networkCachePolicy(CachePolicy.ENABLED)
                            .memoryCacheKey(uiState.myPhotoUrl)
                            .diskCacheKey(uiState.myPhotoUrl)
                            .build()
                    }
                }

                // Avatar
                if (myAvatarRequest != null) {
                    AsyncImage(
                        model = myAvatarRequest,
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
                    fontWeight = FontWeight.ExtraBold
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
                        color = LocationActive.copy(alpha = 0.14f)
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = Dimens.spaceMedium,
                                vertical = Dimens.spaceSmall
                            ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SharingPulseDot()
                            Spacer(Modifier.width(Dimens.spaceSmall))
                            Text(
                                text = "Sharing live",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = LocationActive
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
                            showMyProfileSheet = false
                            sheetView = MapSheetView.Home
                            homeTab = MapHomeTab.MyShares
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(
                            Icons.Rounded.Stop,
                            null,
                            modifier = Modifier.size(Dimens.iconSizeMedium)
                        )
                        Spacer(Modifier.width(Dimens.spaceSmall))
                        Text("Manage active shares")
                    }
                } else {
                    Button(
                        onClick = {
                            showMyProfileSheet = false
                            sheetView = MapSheetView.Share
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            null,
                            modifier = Modifier.size(Dimens.iconSizeMedium)
                        )
                        Spacer(Modifier.width(Dimens.spaceSmall))
                        Text("Share My Location")
                    }
                }

                Spacer(Modifier.height(Dimens.spaceLarge))
            }
        }
    }

    // ── Place-card sheet for the active meetup destination ──────────────────
    // Opened by tapping the active top-of-map MeetupChip OR the destination
    // marker on the map (Google-Maps-style "tap a place" pattern).
    val activeMeetup = uiState.meetupDestination
    if (uiState.showMeetupPlaceCard && activeMeetup != null && activeMeetup.hasValidLocation) {
        MeetupPlaceCardSheet(
            destination = activeMeetup,
            groupName = uiState.meetupDestinationGroupId
                ?.let { id -> uiState.groups.firstOrNull { it.id == id }?.name },
            distanceText = uiState.meetupDestinationDistanceText,
            etaText = uiState.meetupDestinationEtaText,
            participants = uiState.meetupParticipants,
            isCreator = uiState.isMeetupCreator,
            selfStatus = uiState.selfMeetupStatus,
            selfNote = uiState.selfMeetupNote,
            onShowOnMap = {
                viewModel.dismissMeetupPlaceCard()
                viewModel.requestDestinationFocus()
            },
            onGetDirections = {
                // In-app navigation overlay: draws a route polyline + status
                // bar over THIS Global Map (no separate destination, no
                // back-stack push). The user stays in their familiar map
                // context with all the friend pins still visible.
                viewModel.dismissMeetupPlaceCard()
                uiState.meetupDestinationGroupId?.let { groupId ->
                    viewModel.startMeetupNavigation(groupId)
                }
            },
            onCantMakeIt = {
                viewModel.dismissMeetupPlaceCard()
                uiState.meetupDestinationGroupId?.let { groupId ->
                    viewModel.markCantMakeIt(groupId)
                }
            },
            onClear = {
                viewModel.dismissMeetupPlaceCard()
                showMeetupClearConfirm = true
            },
            onEditStatus = {
                viewModel.dismissMeetupPlaceCard()
                showMeetupStatusEditor = true
            },
            onDismiss = { viewModel.dismissMeetupPlaceCard() }
        )
    }

    // ── Status editor (custom-status entry sheet) ────────────────────────────
    if (showMeetupStatusEditor) {
        com.ovi.where.presentation.map.components.MeetupStatusEditorSheet(
            initialNote = uiState.selfMeetupNote,
            onSave = { newNote ->
                showMeetupStatusEditor = false
                viewModel.setMeetupNote(newNote)
            },
            onClear = {
                showMeetupStatusEditor = false
                viewModel.setMeetupNote("")
            },
            onDismiss = { showMeetupStatusEditor = false }
        )
    }

    // ── Clear meetup destination confirm dialog ──────────────────────────────
    if (showMeetupClearConfirm) {
        // Resolve the meetup's actual group name (not the active filter,
        // which may be a different group or "All Friends" since meetups
        // are observed across all groups).
        val meetupGroupName = uiState.meetupDestinationGroupId
            ?.let { id -> uiState.groups.firstOrNull { it.id == id }?.name }
            ?: "the group"
        AlertDialog(
            onDismissRequest = { showMeetupClearConfirm = false },
            title = { Text("Clear meetup point?") },
            text = {
                Text(
                    "Everyone in $meetupGroupName will stop seeing it on the map."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showMeetupClearConfirm = false
                    viewModel.clearMeetupDestination()
                }) {
                    Text(
                        "Clear",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showMeetupClearConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ── Set meetup destination sheet (long-press on map) ──────────────────────
    if (uiState.showSetDestinationSheet && uiState.pendingDestinationPick != null) {
        SetMeetupDestinationSheet(
            pick = uiState.pendingDestinationPick!!,
            groups = uiState.groups,
            preferredGroupId = uiState.activeGroupFilter?.takeIf { !it.isDirect }?.id,
            onConfirm = { groupId, name ->
                viewModel.confirmDestinationPick(groupId, name)
            },
            onCreateGroup = {
                viewModel.dismissSetDestinationSheet()
                onNavigateToCreateGroup()
            },
            onJoinGroup = {
                viewModel.dismissSetDestinationSheet()
                onNavigateToJoinGroup()
            },
            onDismiss = { viewModel.dismissSetDestinationSheet() }
        )
    }
}

// ── Sub-composables ───────────────────────────────────────────────────────────

/** Top-level views in the persistent bottom sheet. */
private enum class MapSheetView { Home, FriendDetail, Share }

/** Tabs inside the Home view, toggled with two pill chips. */
private enum class MapHomeTab { Friends, MyShares }

/** Reusable circular back button used in detail/share/manage views. */
@Composable
private fun SheetBackButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(Dimens.iconSizeXLarge)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                modifier = Modifier.size(Dimens.iconSizeSmall),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Home view of the bottom sheet — always shows two pill chips at the top:
 * "Friends sharing" / "My shares". Each tab shows its own list (or empty state).
 */
@Composable
private fun HomeSheetContent(
    homeTab: MapHomeTab,
    onTabChange: (MapHomeTab) -> Unit,
    friends: List<FriendLocationUiModel>,
    myLatitude: Double,
    myLongitude: Double,
    hasMyLocation: Boolean,
    isSharing: Boolean,
    activeFilter: GroupFilter?,
    sharingTargetIds: List<String>,
    sharingTargetExpiries: Map<String, Long>,
    meetupOwnedShareGroupIds: Set<String>,
    hostedMeetupGroupIds: Set<String>,
    groups: List<GroupFilter>,
    directTargets: List<GroupFilter>,
    bottomReservedSpace: androidx.compose.ui.unit.Dp,
    onFriendClick: (FriendLocationUiModel) -> Unit,
    onShowOnMap: (FriendLocationUiModel) -> Unit,
    onAddShare: () -> Unit,
    onStopOne: (String) -> Unit,
    onStopAll: () -> Unit,
    onOpenMeetupCard: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
            .heightIn(min = 160.dp, max = 660.dp)
    ) {
        // ── Segmented toggle ──────────────────────────────────────────────────
        // Both tabs live inside a single pale track pill. The selected
        // segment fills with brand gold and lifts on a soft shadow; the
        // other stays transparent so the track shows through.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.spaceLarge, vertical = Dimens.spaceSmall)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(Dimens.spaceSmall),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall)
        ) {
            HomeTabSegment(
                label = "Friends sharing",
                count = friends.size,
                selected = homeTab == MapHomeTab.Friends,
                modifier = Modifier.weight(1f),
                onClick = { onTabChange(MapHomeTab.Friends) }
            )
            HomeTabSegment(
                label = "My shares",
                count = sharingTargetIds.size,
                selected = homeTab == MapHomeTab.MyShares,
                showLiveDot = isSharing,
                modifier = Modifier.weight(1f),
                onClick = { onTabChange(MapHomeTab.MyShares) }
            )
        }

        Spacer(Modifier.height(Dimens.spaceMedium))

        AnimatedContent(
            targetState = homeTab,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                (slideInHorizontally(
                    initialOffsetX = { fullWidth -> if (forward) fullWidth else -fullWidth },
                    animationSpec = tween(250)
                ) togetherWith slideOutHorizontally(
                    targetOffsetX = { fullWidth -> if (forward) -fullWidth else fullWidth },
                    animationSpec = tween(250)
                )).using(SizeTransform(clip = true))
            },
            label = "homeTabAnim"
        ) { currentTab ->
            when (currentTab) {
                MapHomeTab.Friends -> FriendsTabContent(
                    friends = friends,
                    myLatitude = myLatitude,
                    myLongitude = myLongitude,
                    hasMyLocation = hasMyLocation,
                    activeFilter = activeFilter,
                    bottomReservedSpace = bottomReservedSpace,
                    onFriendClick = onFriendClick,
                    onShowOnMap = onShowOnMap
                )

                MapHomeTab.MyShares -> MySharesTabContent(
                    sharingTargetIds = sharingTargetIds,
                    sharingTargetExpiries = sharingTargetExpiries,
                    meetupOwnedShareGroupIds = meetupOwnedShareGroupIds,
                    hostedMeetupGroupIds = hostedMeetupGroupIds,
                    groups = groups,
                    directTargets = directTargets,
                    bottomReservedSpace = bottomReservedSpace,
                    onAddShare = onAddShare,
                    onStopOne = onStopOne,
                    onStopAll = onStopAll,
                    onOpenMeetupCard = onOpenMeetupCard
                )
            }
        }
    }
}

/**
 * One segment of the home segmented control. Selected = filled brand gold
 * on a soft shadow; unselected = transparent so the pale track shows
 * through. A bold label carries it; "My shares" gets a live dot when the
 * user is actively sharing.
 */
@Composable
private fun HomeTabSegment(
    label: String,
    count: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
    showLiveDot: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        shadowElevation = if (selected) 3.dp else 0.dp,
        modifier = modifier.height(38.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.spaceMedium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (showLiveDot) {
                SharingPulseDot()
                Spacer(Modifier.width(Dimens.spaceSmall))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (count > 0) {
                Spacer(Modifier.width(Dimens.spaceSmall))
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                )
            }
        }
    }
}

/**
 * Strong, consistent section header for the map sheet. A short ExtraBold
 * title carries the hierarchy; an optional caption gives quiet context.
 * Replaces the old weak full-sentence titleSmall headers so the sheet
 * reads with a clear type rhythm instead of flat body text.
 */
@Composable
private fun MapSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    caption: String? = null
) {
    Column(
        modifier = modifier.padding(
            horizontal = Dimens.spaceXLarge,
            vertical = Dimens.spaceSmall
        )
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (!caption.isNullOrBlank()) {
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FriendsTabContent(
    friends: List<FriendLocationUiModel>,
    myLatitude: Double,
    myLongitude: Double,
    hasMyLocation: Boolean,
    activeFilter: GroupFilter?,
    bottomReservedSpace: androidx.compose.ui.unit.Dp,
    onFriendClick: (FriendLocationUiModel) -> Unit,
    onShowOnMap: (FriendLocationUiModel) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (friends.isEmpty()) {
            TabEmptyState(
                icon = Icons.Default.LocationOff,
                title = "No friends sharing",
                subtitle = if (activeFilter != null)
                    "No one in ${activeFilter.name} is sharing live location."
                else "When friends share their location, they'll appear here.",
                bottomReservedSpace = bottomReservedSpace
            )
            return@Column
        }

        // ── Section header ────────────────────────────────────────────────────────
        MapSectionHeader(
            title = if (friends.size == 1) "1 friend nearby" else "${friends.size} friends nearby",
            caption = "Sharing their live location with you"
        )

        Spacer(Modifier.height(Dimens.spaceMedium))
        androidx.compose.foundation.lazy.LazyRow(
            contentPadding = PaddingValues(horizontal = Dimens.spaceLarge),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMedium)
        ) {
            items(items = friends, key = { "avatar-${it.userId}" }) { friend ->
                FriendAvatarPeek(friend = friend, onClick = { onFriendClick(friend) })
            }
        }

        Spacer(Modifier.height(Dimens.spaceLarge))
        androidx.compose.material3.HorizontalDivider(
            modifier = Modifier.padding(horizontal = Dimens.spaceXLarge),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )

        LazyColumn(
            contentPadding = PaddingValues(
                start = Dimens.spaceMedium,
                end = Dimens.spaceMedium,
                top = Dimens.spaceLarge,
                bottom = bottomReservedSpace + Dimens.spaceLarge
            ),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceMedium)
        ) {
            items(items = friends, key = { "card-${it.userId}" }) { friend ->
                FriendCard(
                    friend = friend,
                    myLatitude = myLatitude,
                    myLongitude = myLongitude,
                    hasMyLocation = hasMyLocation,
                    onClick = { onFriendClick(friend) },
                    onShowOnMap = { onShowOnMap(friend) }
                )
            }
        }
    }
}

@Composable
private fun MySharesTabContent(
    sharingTargetIds: List<String>,
    sharingTargetExpiries: Map<String, Long>,
    meetupOwnedShareGroupIds: Set<String>,
    hostedMeetupGroupIds: Set<String>,
    groups: List<GroupFilter>,
    directTargets: List<GroupFilter>,
    bottomReservedSpace: androidx.compose.ui.unit.Dp,
    onAddShare: () -> Unit,
    onStopOne: (String) -> Unit,
    onStopAll: () -> Unit,
    onOpenMeetupCard: () -> Unit
) {
    val allTargets = (directTargets + groups).associateBy { it.id }
    val active = sharingTargetIds.mapNotNull { id ->
        allTargets[id] ?: GroupFilter(id = id, name = id, isDirect = id.startsWith("direct:"))
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (active.isEmpty()) {
            // Empty state with primary CTA to start sharing
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.spaceXLarge, vertical = Dimens.spaceLarge),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(LocationActive.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        null,
                        modifier = Modifier.size(Dimens.iconSizeLarge),
                        tint = LocationActive
                    )
                }
                Spacer(Modifier.height(Dimens.spaceLarge))
                Text(
                    "You're not sharing yet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(Dimens.spaceSmall))
                Text(
                    "Pick friends or groups to share your live location with.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(Dimens.spaceXLarge))
                Button(
                    onClick = onAddShare,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Dimens.buttonHeightSmall),
                    shape = RoundedCornerShape(Dimens.cornerMedium)
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(id = R.drawable.location_arrow),
                        null,
                        modifier = Modifier.size(Dimens.iconSizeSmall)
                    )
                    Spacer(Modifier.width(Dimens.spaceMedium))
                    Text("Share location", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.height(bottomReservedSpace))
            }
            return@Column
        }

        // ── Section header ────────────────────────────────────────────────────────
        MapSectionHeader(
            title = if (active.size == 1) "Sharing with 1 place" else "Sharing with ${active.size} places",
            caption = "Your live location is on for these recipients"
        )
        Spacer(Modifier.height(Dimens.spaceSmall))

        LazyColumn(
            contentPadding = PaddingValues(
                start = Dimens.spaceMedium,
                end = Dimens.spaceMedium,
                top = Dimens.spaceSmall,
                bottom = Dimens.spaceLarge
            ),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceMedium),
            modifier = Modifier.heightIn(max = 380.dp)
        ) {
            items(active, key = { "active-${it.id}" }) { target ->
                ActiveShareRow(
                    target = target,
                    expiry = sharingTargetExpiries[target.id],
                    isMeetupShare = target.id in meetupOwnedShareGroupIds,
                    isHostedMeetup = target.id in hostedMeetupGroupIds,
                    onStop = { onStopOne(target.id) },
                    onOpenMeetupCard = onOpenMeetupCard
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Footer actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.spaceLarge),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMedium)
        ) {
            OutlinedButton(
                onClick = onAddShare,
                modifier = Modifier
                    .weight(1f)
                    .height(Dimens.buttonHeightSmall),
                shape = RoundedCornerShape(Dimens.cornerMedium)
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(id = R.drawable.location_arrow),
                    null,
                    modifier = Modifier.size(Dimens.iconSizeSmall)
                )
                Spacer(Modifier.width(Dimens.spaceMedium))
                Text("Share more", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = onStopAll,
                modifier = Modifier
                    .weight(1f)
                    .height(Dimens.buttonHeightSmall),
                shape = RoundedCornerShape(Dimens.cornerMedium),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(Icons.Rounded.Stop, null, modifier = Modifier.size(Dimens.iconSizeSmall))
                Spacer(Modifier.width(Dimens.spaceMedium))
                Text(
                    "Stop all",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
        Spacer(Modifier.height(bottomReservedSpace))
    }
}

@Composable
private fun TabEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    bottomReservedSpace: androidx.compose.ui.unit.Dp
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spaceXLarge, vertical = Dimens.spaceXLarge),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                null,
                modifier = Modifier.size(Dimens.iconSizeLarge),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(Dimens.spaceLarge))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(Dimens.spaceSmall))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(bottomReservedSpace))
    }
}

/** Friend detail view embedded in the persistent sheet (replaces the modal). */
@Composable
private fun FriendDetailSheetContent(
    friend: FriendLocationUiModel,
    bottomReservedSpace: androidx.compose.ui.unit.Dp,
    onMessage: () -> Unit,
    onNavigateToUserProfile: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val color = AvatarColors[friend.userId.hashCode().and(0x7FFFFFFF) % AvatarColors.size]

    val avatarPixelSize = remember(density) {
        with(density) { Dimens.avatarSizeXLarge.roundToPx() }
    }

    val avatarRequest = remember(friend.photoUrl, avatarPixelSize) {
        if (friend.photoUrl.isNullOrBlank()) {
            null
        } else {
            ImageRequest.Builder(context)
                .data(friend.photoUrl)
                .crossfade(true)
                .size(avatarPixelSize)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .networkCachePolicy(CachePolicy.ENABLED)
                .memoryCacheKey(friend.photoUrl)
                .diskCacheKey(friend.photoUrl)
                .build()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SheetBackButton(onBack)
            Spacer(Modifier.weight(1f))
        }

        Spacer(Modifier.height(8.dp))

        // Avatar
        if (avatarRequest != null) {
            AsyncImage(
                model = avatarRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(Dimens.avatarSizeXLarge)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
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
                    text = friend.displayName
                        .trim()
                        .take(1)
                        .uppercase()
                        .ifEmpty { "?" },
                    style = MaterialTheme.typography.displaySmall,
                    color = avatarContentColorFor(color)
                )
            }
        }

        Spacer(Modifier.height(Dimens.spaceLarge))

        Text(
            text = friend.displayName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (friend.username.isNotEmpty()) {
            Text(
                text = "@${friend.username}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(Dimens.spaceMedium))

        // Live status
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (friend.isActive) {
                SharingPulseDot()
                Spacer(Modifier.width(Dimens.spaceSmall))
                Text(
                    text = "Sharing live${if (friend.groupName.isNotEmpty()) " • ${friend.groupName}" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocationActive,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.History, null,
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
            Button(
                onClick = onMessage,
                modifier = Modifier
                    .weight(1f)
                    .height(Dimens.buttonHeightSmall),
                shape = RoundedCornerShape(Dimens.cornerMedium)
            ) {
                Icon(
                    Icons.Default.ChatBubbleOutline,
                    null,
                    modifier = Modifier.size(Dimens.iconSizeSmall)
                )
                Spacer(Modifier.width(Dimens.spaceMedium))
                Text("Message", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
            }
            OutlinedButton(
                onClick = onNavigateToUserProfile,
                modifier = Modifier
                    .weight(1f)
                    .height(Dimens.buttonHeightSmall),
                shape = RoundedCornerShape(Dimens.cornerMedium)
            ) {
                Icon(Icons.Default.Person, null, modifier = Modifier.size(Dimens.iconSizeSmall))
                Spacer(Modifier.width(Dimens.spaceMedium))
                Text("Profile", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(Dimens.spaceLarge))
        Spacer(Modifier.height(bottomReservedSpace))
    }
}

/** Smaller avatar used in the top filter pill. */
@Composable
private fun FilterPillAvatar(filter: GroupFilter?) {
    val size = 36.dp

    val context = LocalContext.current
    val density = LocalDensity.current

    val avatarPixelSize = remember(density) {
        with(density) { size.roundToPx() }
    }

    val avatarRequest = remember(filter?.photoUrl, avatarPixelSize) {
        if (filter?.photoUrl.isNullOrBlank()) {
            null
        } else {
            ImageRequest.Builder(context)
                .data(filter.photoUrl)
                .crossfade(true)
                .size(avatarPixelSize)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .networkCachePolicy(CachePolicy.ENABLED)
                .memoryCacheKey(filter.photoUrl)
                .diskCacheKey(filter.photoUrl)
                .build()
        }
    }

    when {
        filter == null -> {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Groups,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        avatarRequest != null -> {
            AsyncImage(
                model = avatarRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }

        else -> {
            val color = AvatarColors[filter.id.hashCode().and(0x7FFFFFFF) % AvatarColors.size]

            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                if (filter.isDirect) {
                    Text(
                        text = filter.name
                            .trim()
                            .take(1)
                            .uppercase()
                            .ifEmpty { "?" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = avatarContentColorFor(color)
                    )
                } else {
                    Icon(
                        Icons.Default.Groups,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = avatarContentColorFor(color)
                    )
                }
            }
        }
    }
}

/** Compact avatar shown in the horizontal peek row. Tap zooms to that friend. */
@Composable
private fun FriendAvatarPeek(
    friend: FriendLocationUiModel,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val color = AvatarColors[friend.userId.hashCode().and(0x7FFFFFFF) % AvatarColors.size]

    val avatarPixelSize = remember(density) {
        with(density) { 52.dp.roundToPx() }
    }

    val avatarRequest = remember(friend.photoUrl, avatarPixelSize) {
        if (friend.photoUrl.isNullOrBlank()) {
            null
        } else {
            ImageRequest.Builder(context)
                .data(friend.photoUrl)
                .crossfade(true)
                .size(avatarPixelSize)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .networkCachePolicy(CachePolicy.ENABLED)
                .memoryCacheKey(friend.photoUrl)
                .diskCacheKey(friend.photoUrl)
                .build()
        }
    }

    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
            .width(64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            if (avatarRequest != null) {
                AsyncImage(
                    model = avatarRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(color),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = friend.displayName
                            .trim()
                            .take(1)
                            .uppercase()
                            .ifEmpty { "?" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = avatarContentColorFor(color)
                    )
                }
            }

            if (friend.isActive) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(LocationActive)
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = friend.displayName
                .trim()
                .substringBefore(" ")
                .ifEmpty { "Unknown" },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Premium friend card. Tap row → focus map. Tap message icon → open chat.
 */
@Composable
private fun FriendCard(
    friend: FriendLocationUiModel,
    myLatitude: Double,
    myLongitude: Double,
    hasMyLocation: Boolean,
    onClick: () -> Unit,
    onShowOnMap: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val color = AvatarColors[friend.userId.hashCode().and(0x7FFFFFFF) % AvatarColors.size]

    val avatarPixelSize = remember(density) {
        with(density) { 48.dp.roundToPx() }
    }

    val avatarRequest = remember(friend.photoUrl, avatarPixelSize) {
        if (friend.photoUrl.isNullOrBlank()) {
            null
        } else {
            ImageRequest.Builder(context)
                .data(friend.photoUrl)
                .crossfade(true)
                .size(avatarPixelSize)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .networkCachePolicy(CachePolicy.ENABLED)
                .memoryCacheKey(friend.photoUrl)
                .diskCacheKey(friend.photoUrl)
                .build()
        }
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(Dimens.cornerLarge),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Dimens.spaceMedium, vertical = Dimens.spaceMedium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                if (avatarRequest != null) {
                    AsyncImage(
                        model = avatarRequest,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(color),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = friend.displayName
                                .trim()
                                .take(1)
                                .uppercase()
                                .ifEmpty { "?" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = avatarContentColorFor(color)
                        )
                    }
                }

                if (friend.isActive) {
                    Box(
                        modifier = Modifier
                            .size(13.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(LocationActive)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = friend.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(Dimens.spaceSmall))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (hasMyLocation && friend.latitude != 0.0 && friend.longitude != 0.0) {
                        val distance = LocationUtils.calculateDistance(
                            myLatitude,
                            myLongitude,
                            friend.latitude,
                            friend.longitude
                        )

                        Surface(
                            shape = RoundedCornerShape(Dimens.cornerSmall),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = LocationUtils.formatDistance(context, distance),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = Dimens.spaceMedium, vertical = 3.dp)
                            )
                        }

                        friend.etaText?.let { eta ->
                            Spacer(Modifier.width(Dimens.spaceSmall))

                            Surface(
                                shape = RoundedCornerShape(Dimens.cornerSmall),
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Text(
                                    text = "~$eta",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = Dimens.spaceMedium, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(Modifier.width(Dimens.spaceSmall))
                    }

                    Text(
                        text = friend.timeAgo.ifEmpty { "Just now" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            Spacer(Modifier.width(Dimens.spaceMedium))

            Surface(
                onClick = onShowOnMap,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Dimens.iconSizeXLarge)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = ImageVector.vectorResource(id = R.drawable.navigate_to),
                        contentDescription = "Show ${friend.displayName} on map",
                        modifier = Modifier.size(Dimens.iconSizeSmall),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun SharingPulseDot() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "pulse_scale"
    )
    // LocationActive (warm orange) is the brand's dedicated "alive / sharing
    // now" accent — it reads as energy and stays distinct from the yellow
    // primary and teal tertiary so a live pulse never looks like a button.
    Box(
        modifier = Modifier
            .size(8.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(LocationActive)
    )
}

@Composable
private fun GroupFilterSheet(
    groups: List<GroupFilter>,
    directTargets: List<GroupFilter>,
    activeFilter: GroupFilter?,
    onSelect: (GroupFilter?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimens.spaceXLarge)
            .navigationBarsPadding()
    ) {
        Text("Filter by", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(Dimens.spaceXSmall))
        Text(
            text = "Show locations from a group or specific friend",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Dimens.spaceLarge))

        LazyColumn(
            modifier = Modifier.heightIn(max = 480.dp),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceXSmall)
        ) {
            item {
                FilterRow(
                    filter = null,
                    title = "All friends",
                    selected = activeFilter == null,
                    onClick = { onSelect(null) }
                )
            }

            if (groups.isNotEmpty()) {
                item {
                    FilterSectionHeader("GROUPS")
                }
                items(groups, key = { "g-${it.id}" }) { group ->
                    FilterRow(
                        filter = group,
                        title = group.name,
                        selected = activeFilter?.id == group.id,
                        onClick = { onSelect(group) }
                    )
                }
            }

            if (directTargets.isNotEmpty()) {
                item {
                    FilterSectionHeader("FRIENDS")
                }
                items(directTargets, key = { "d-${it.id}" }) { target ->
                    FilterRow(
                        filter = target,
                        title = target.name,
                        selected = activeFilter?.id == target.id,
                        onClick = { onSelect(target) }
                    )
                }
            }
        }

        Spacer(Modifier.height(Dimens.spaceLarge))
    }
}

@Composable
private fun FilterSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = Dimens.spaceMedium, top = Dimens.spaceMedium, bottom = Dimens.spaceSmall)
    )
}

@Composable
private fun FilterRow(
    filter: GroupFilter?,
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(Dimens.cornerLarge),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.spaceMedium, vertical = Dimens.spaceMedium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterPillAvatar(filter = filter)
            Spacer(Modifier.width(Dimens.spaceMedium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = when {
                        filter == null -> "Everyone sharing with you"
                        filter.isDirect -> "Direct share"
                        else -> "Group"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
private fun ActiveShareRow(
    target: GroupFilter,
    expiry: Long?,
    isMeetupShare: Boolean = false,
    isHostedMeetup: Boolean = false,
    onStop: () -> Unit,
    onOpenMeetupCard: () -> Unit = {}
) {
    Surface(
        shape = RoundedCornerShape(Dimens.cornerLarge),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Dimens.spaceMedium, vertical = Dimens.spaceMedium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterPillAvatar(filter = target)
            Spacer(Modifier.width(Dimens.spaceMedium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    target.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SharingPulseDot()
                    Spacer(Modifier.width(Dimens.spaceMedium))
                    val typeLabel = if (target.isDirect) "Direct" else "Group"
                    // Meetup-owned shares auto-stop on arrival; the timer is
                    // only a safety ceiling. Show the lifecycle, not the
                    // raw timer, so users understand it'll end on its own.
                    val timerLabel = when {
                        isHostedMeetup -> "Hosting · clear meetup to stop"
                        isMeetupShare -> "Until you arrive"
                        else -> formatExpiryLabel(expiry)
                    }
                    Text(
                        text = "$typeLabel • $timerLabel",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            // Per-recipient action: hosts get a flag (open place-card to
            // clear meetup) instead of a stop button — they can't end the
            // share with a single tap because that would orphan everyone
            // else's meetup. Tapping the flag opens the place-card sheet
            // where "Clear meetup" lives.
            if (isHostedMeetup) {
                Surface(
                    onClick = onOpenMeetupCard,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(Dimens.iconSizeXLarge)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Flag,
                            contentDescription = "Open meetup card to clear meetup",
                            modifier = Modifier.size(Dimens.iconSizeSmall)
                        )
                    }
                }
            } else {
                Surface(
                    onClick = onStop,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(Dimens.iconSizeXLarge)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Stop,
                            contentDescription = "Stop sharing with ${target.name}",
                            modifier = Modifier.size(Dimens.iconSizeSmall)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Formats a per-target expiry into a short label like "Until you stop" or "12m left".
 *
 * Delegates to [com.ovi.where.presentation.common.SharingTimeFormatter] so the
 * label always matches what the chat screen shows for the same expiry.
 */
private fun formatExpiryLabel(expiry: Long?): String {
    return com.ovi.where.presentation.common.SharingTimeFormatter
        .formatRemainingWithSuffix(expiry)
}

@Composable
private fun ShareTargetSheet(
    groups: List<GroupFilter>,
    directTargets: List<GroupFilter>,
    excludeTargetIds: List<String> = emptyList(),
    initialDuration: Long = 60L,
    bottomReservedSpace: androidx.compose.ui.unit.Dp = 0.dp,
    onCancel: () -> Unit = {},
    onCreateGroup: () -> Unit = {},
    onJoinGroup: () -> Unit = {},
    onAddFriends: () -> Unit = {},
    onStart: (List<String>, Long) -> Unit
) {
    val visibleGroups = remember(groups, excludeTargetIds) {
        groups.filter { it.id !in excludeTargetIds }
    }
    val visibleDirect = remember(directTargets, excludeTargetIds) {
        directTargets.filter { it.id !in excludeTargetIds }
    }
    val targets = visibleDirect + visibleGroups
    val selectedIds = remember { mutableStateListOf<String>() }
    var selectedDuration by remember { mutableLongStateOf(initialDuration) }
    var showInfiniteConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spaceXLarge, vertical = Dimens.spaceLarge)
    ) {
        // ── Header with back button ───────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            SheetBackButton(onCancel)
            Spacer(Modifier.width(Dimens.spaceMedium))
            Box(
                modifier = Modifier
                    .size(Dimens.iconSizeXLarge)
                    .clip(CircleShape)
                    .background(LocationActive.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = LocationActive
                )
            }
            Spacer(Modifier.width(Dimens.spaceMedium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Share live location",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = if (selectedIds.isEmpty()) "Pick friends or groups"
                    else "${selectedIds.size} selected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selectedIds.isNotEmpty()) {
                TextButton(onClick = { selectedIds.clear() }) {
                    Text("Clear", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(Modifier.height(Dimens.spaceLarge))

        if (targets.isEmpty()) {
            ShareEmptyState(
                onCreateGroup = onCreateGroup,
                onJoinGroup = onJoinGroup,
                onAddFriends = onAddFriends
            )
            Spacer(Modifier.height(bottomReservedSpace))
            return@Column
        }

        // ── Selected chips row (only visible when something is selected) ──────
        if (selectedIds.isNotEmpty()) {
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMedium),
                contentPadding = PaddingValues(horizontal = Dimens.spaceXSmall)
            ) {
                items(
                    items = selectedIds.mapNotNull { id -> targets.firstOrNull { it.id == id } },
                    key = { "chip-${it.id}" }
                ) { target ->
                    SelectedTargetChip(
                        target = target,
                        onRemove = { selectedIds.remove(target.id) }
                    )
                }
            }
            Spacer(Modifier.height(Dimens.spaceLarge))
        }

        // ── Friends section ───────────────────────────────────────────────────
        LazyColumn(
            modifier = Modifier.heightIn(max = 360.dp),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceSmall)
        ) {
            if (visibleDirect.isNotEmpty()) {
                item { ShareSectionHeader("FRIENDS") }
                items(visibleDirect, key = { "f-${it.id}" }) { target ->
                    SelectableTargetRow(
                        target = target,
                        selected = target.id in selectedIds,
                        onToggle = {
                            if (target.id in selectedIds) selectedIds.remove(target.id)
                            else selectedIds.add(target.id)
                        }
                    )
                }
            }
            if (visibleGroups.isNotEmpty()) {
                item { ShareSectionHeader("GROUPS") }
                items(visibleGroups, key = { "g-${it.id}" }) { target ->
                    SelectableTargetRow(
                        target = target,
                        selected = target.id in selectedIds,
                        onToggle = {
                            if (target.id in selectedIds) selectedIds.remove(target.id)
                            else selectedIds.add(target.id)
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(Dimens.spaceXLarge))

        // ── Duration ──────────────────────────────────────────────────────────
        Text(
            "DURATION",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.2.sp
        )
        Spacer(Modifier.height(Dimens.spaceMedium))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMedium)
        ) {
            DurationSegment(
                "15m",
                selectedDuration == 15L,
                Modifier.weight(1f)
            ) { selectedDuration = 15L }
            DurationSegment("1h", selectedDuration == 60L, Modifier.weight(1f)) {
                selectedDuration = 60L
            }
            DurationSegment(
                "4h",
                selectedDuration == 240L,
                Modifier.weight(1f)
            ) { selectedDuration = 240L }
            DurationSegment("∞", selectedDuration == 0L, Modifier.weight(1f)) {
                // Confirm continuous sharing — it has privacy implications and never auto-stops
                showInfiniteConfirm = true
            }
        }

        if (showInfiniteConfirm) {
            AlertDialog(
                onDismissRequest = { showInfiniteConfirm = false },
                icon = {
                    Icon(
                        Icons.Rounded.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(Dimens.iconSizeLarge),
                        tint = LocationActive
                    )
                },
                title = {
                    Text(
                        "Share until you stop?",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                },
                text = {
                    Column {
                        Text(
                            "Your live location will keep streaming with no time limit. " +
                                    "It will only stop when you tap Stop or remove this share manually.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(12.dp))
                        InfiniteShareBullet("Drains more battery than a timed share")
                        InfiniteShareBullet("Recipients keep seeing your location until you stop it")
                        InfiniteShareBullet("You can stop anytime from the My shares tab")
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            selectedDuration = 0L
                            showInfiniteConfirm = false
                        }
                    ) {
                        Text("Use no time limit", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showInfiniteConfirm = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        Spacer(Modifier.height(Dimens.spaceXLarge))

        // ── Start button ──────────────────────────────────────────────────────
        Button(
            onClick = {
                if (selectedIds.isNotEmpty()) onStart(selectedIds.toList(), selectedDuration)
            },
            enabled = selectedIds.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.buttonHeight),
            shape = RoundedCornerShape(Dimens.cornerMedium),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(id = R.drawable.location_arrow),
                contentDescription = null,
                modifier = Modifier.size(Dimens.iconSizeSmall)
            )
            Spacer(Modifier.width(Dimens.spaceMedium))
            Text(
                text = if (selectedIds.isEmpty()) "Pick someone to share with"
                else "Share with ${selectedIds.size}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Spacer(Modifier.height(Dimens.spaceMedium))
        Spacer(Modifier.height(bottomReservedSpace))
    }
}

@Composable
private fun ShareEmptyState(
    onCreateGroup: () -> Unit = {},
    onJoinGroup: () -> Unit = {},
    onAddFriends: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spaceSmall, vertical = Dimens.spaceLarge),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Groups,
                contentDescription = null,
                modifier = Modifier.size(Dimens.iconSizeLarge),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(Dimens.spaceLarge))
        Text(
            "No one to share with yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(Dimens.spaceSmall))
        Text(
            "Add friends or create / join a group to start sharing your live location.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = Dimens.spaceLarge)
        )
        Spacer(Modifier.height(Dimens.spaceXLarge))

        // Primary action — most likely path is adding friends (DM share).
        Button(
            onClick = onAddFriends,
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.buttonHeight),
            shape = RoundedCornerShape(Dimens.cornerMedium),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(Icons.Default.Person, null, Modifier.size(Dimens.iconSizeSmall))
            Spacer(Modifier.width(Dimens.spaceMedium))
            Text(
                "Add friends",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Spacer(Modifier.height(Dimens.spaceMedium))

        // Secondary actions — group paths.
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMedium),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = onJoinGroup,
                modifier = Modifier
                    .weight(1f)
                    .height(Dimens.buttonHeightSmall),
                shape = RoundedCornerShape(Dimens.cornerMedium),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text(
                    "Join group",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Button(
                onClick = onCreateGroup,
                modifier = Modifier
                    .weight(1f)
                    .height(Dimens.buttonHeightSmall),
                shape = RoundedCornerShape(Dimens.cornerMedium),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text(
                    "Create group",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.height(Dimens.spaceLarge))
    }
}

@Composable
private fun ShareSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = Dimens.spaceSmall, top = Dimens.spaceMedium, bottom = Dimens.spaceSmall)
    )
}

@Composable
private fun SelectableTargetRow(
    target: GroupFilter,
    selected: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onToggle,
        shape = RoundedCornerShape(Dimens.cornerLarge),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (selected) androidx.compose.foundation.BorderStroke(
            1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        ) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Dimens.spaceMedium, vertical = Dimens.spaceMedium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterPillAvatar(filter = target)
            Spacer(Modifier.width(Dimens.spaceMedium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    target.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (target.isDirect) "Direct share" else "Group",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Material checkbox-like indicator
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .border(
                            width = 1.5.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            shape = CircleShape
                        )
                )
            }
        }
    }
}

@Composable
private fun SelectedTargetChip(
    target: GroupFilter,
    onRemove: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(Dimens.cornerLarge),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.height(Dimens.avatarSizeSmall + Dimens.spaceSmall)
    ) {
        Row(
            modifier = Modifier.padding(start = Dimens.spaceSmall, end = Dimens.spaceMedium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tiny avatar
            Box(modifier = Modifier.size(28.dp)) {
                FilterPillAvatar(filter = target)
            }
            Spacer(Modifier.width(Dimens.spaceMedium))
            Text(
                text = target.name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 120.dp)
            )
            Spacer(Modifier.width(Dimens.spaceSmall))
            Surface(
                onClick = onRemove,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                modifier = Modifier.size(20.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove ${target.name}",
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun InfiniteShareBullet(text: String) {
    Row(
        modifier = Modifier.padding(top = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            "•",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DurationSegment(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(Dimens.buttonHeightSmall),
        shape = RoundedCornerShape(Dimens.cornerMedium),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        border = if (selected) androidx.compose.foundation.BorderStroke(
            1.5.dp, MaterialTheme.colorScheme.primary
        ) else null
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                maxLines = 1
            )
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
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(modifier = Modifier.height(Dimens.spaceXSmall))
        Text(
            text = "Choose how the map looks",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Dimens.spaceXLarge))
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMedium),
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
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = option.description,
                        style = MaterialTheme.typography.bodySmall,
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

// ── Marker Fan-Out (prevents overlapping pins) ──────────────────────────────

/** Key used for the user's own marker in the display-positions map. */
private const val MY_MARKER_KEY = "__my_location__"

// ── Premium Map Pin Marker ───────────────────────────────────────────────────

private val PIN_BODY_SIZE = 62.dp        // Rounded-square avatar area
private val PIN_BORDER_WIDTH = 3.5.dp      // White border ring
private val PIN_CORNER_RADIUS = 26.dp    // Rounded corners (squircle feel)
private val PIN_TAIL_WIDTH = 14.dp       // Tail width
private val PIN_TAIL_HEIGHT = 10.dp      // Tail height

/**
 * Map pin marker — a speech-bubble shape: a rounded square (squircle) with
 * the avatar clipped inside, plus a small triangular pointer at the bottom
 * center. White border ring, soft shadow. Matches the Life360 / reference
 * design where the pin is NOT a circle but a rounded rectangle.
 */
@Composable
private fun Life360PinMarker(
    avatarBitmap: Bitmap?,
    fallbackLabel: String,
    accentColor: Color,
    borderColor: Color = Color.Unspecified,
    modifier: Modifier = Modifier
) {
    val resolvedBorderColor = if (borderColor == Color.Unspecified) {
        MaterialTheme.colorScheme.surface
    } else {
        borderColor
    }

    val avatarImageBitmap = remember(avatarBitmap) { avatarBitmap?.asImageBitmap() }
    val innerClipShape = RoundedCornerShape(PIN_CORNER_RADIUS - PIN_BORDER_WIDTH)

    val markerShape = remember {
        MapMarkerShape(
            cornerRadius = PIN_CORNER_RADIUS,
            tailWidth = PIN_TAIL_WIDTH,
            tailHeight = PIN_TAIL_HEIGHT
        )
    }

    // 1. Outer wrapper prevents the drop shadow from being clipped by the Map Bitmap
    Box(
        modifier = modifier.padding(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = 4.dp // Kept small so the map pin anchor stays locked to the tail tip
        )
    ) {
        // 2. The Pin Shape
        Box(
            modifier = Modifier
                .size(
                    width = PIN_BODY_SIZE,
                    height = PIN_BODY_SIZE + PIN_TAIL_HEIGHT
                )
                .drawBehind {
                    // FIX: Draw the drop shadow ONLY for the squircle body, ignoring the tail
                    val paint = Paint()
                    val frameworkPaint = paint.asFrameworkPaint()
                    frameworkPaint.color = android.graphics.Color.BLACK
                    frameworkPaint.setShadowLayer(
                        12.dp.toPx(), // Blur
                        0f,           // Offset X
                        8.dp.toPx(),  // Offset Y
                        android.graphics.Color.argb(70, 0, 0, 0) // ~28% alpha black
                    )

                    drawIntoCanvas { canvas ->
                        canvas.drawRoundRect(
                            left = 0f,
                            top = 0f,
                            right = size.width,
                            bottom = size.height - PIN_TAIL_HEIGHT.toPx(),
                            radiusX = PIN_CORNER_RADIUS.toPx(), // ⬅️ Changed from rx
                            radiusY = PIN_CORNER_RADIUS.toPx(), // ⬅️ Changed from ry
                            paint = paint
                        )
                    }
                }
                .background(resolvedBorderColor, markerShape)
                .padding(bottom = PIN_TAIL_HEIGHT)
                .padding(PIN_BORDER_WIDTH)
        ) {
            // ── Inner Avatar ────────────────────────────────────────────
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(innerClipShape)
                    .background(accentColor)
            ) {
                if (avatarImageBitmap != null) {
                    Image(
                        painter = BitmapPainter(avatarImageBitmap),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = fallbackLabel.trim().take(1).uppercase().ifEmpty { "?" },
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun MyLocationMarkerContent(
    avatarBitmap: Bitmap?,
    note: String = "",
    meetupStatus: com.ovi.where.domain.model.MeetupParticipantStatus? = null,
    isAtHome: Boolean = false
) {
    val statusLabel = when {
        note.isNotBlank() -> FriendStatusLabel(text = note, tint = FriendStatusTint.Note)
        meetupStatus == com.ovi.where.domain.model.MeetupParticipantStatus.ARRIVED ->
            FriendStatusLabel(text = "Arrived", tint = FriendStatusTint.Success)
        meetupStatus == com.ovi.where.domain.model.MeetupParticipantStatus.CANT_MAKE_IT ->
            FriendStatusLabel(text = "Can't make it", tint = FriendStatusTint.Error)
        isAtHome -> FriendStatusLabel(text = "At Home", tint = FriendStatusTint.Home)
        else -> null
    }
    if (statusLabel != null) {
        // Row: pin + bubble with negative margin so bubble overlaps pin's right edge.
        Row(verticalAlignment = Alignment.Top) {
            Life360PinMarker(
                avatarBitmap = avatarBitmap,
                fallbackLabel = "ME",
                accentColor = MaterialTheme.colorScheme.primary
            )
            FriendStatusBubble(
                text = statusLabel.text,
                tint = statusLabel.tint,
                modifier = Modifier.offset(x = (-40).dp, y = 5.dp)
            )
        }
    } else {
        Life360PinMarker(
            avatarBitmap = avatarBitmap,
            fallbackLabel = "ME",
            accentColor = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun FriendMapMarkerContent(
    friend: FriendLocationUiModel,
    avatarBitmap: Bitmap?
) {
    val statusLabel = friend.statusBubbleLabel()
    if (statusLabel != null) {
        Row(verticalAlignment = Alignment.Top) {
            Life360PinMarker(
                avatarBitmap = avatarBitmap,
                fallbackLabel = friend.displayName.take(1).uppercase(),
                accentColor = avatarColorFor(friend.userId)
            )
            FriendStatusBubble(
                text = statusLabel.text,
                tint = statusLabel.tint,
                modifier = Modifier.offset(x = (-40).dp, y = 5.dp)
            )
        }
    } else {
        Life360PinMarker(
            avatarBitmap = avatarBitmap,
            fallbackLabel = friend.displayName.take(1).uppercase(),
            accentColor = avatarColorFor(friend.userId)
        )
    }
}

/**
 * Pre-computed bubble label for a friend's pin. Returns null when the
 * friend has nothing worth showing (no note + on the way / no meetup).
 */
private fun FriendLocationUiModel.statusBubbleLabel(): FriendStatusLabel? {
    // Note takes precedence — it's the user's own words.
    if (meetupNote.isNotBlank()) {
        return FriendStatusLabel(
            text = meetupNote,
            tint = FriendStatusTint.Note
        )
    }
    return when (meetupStatus) {
        com.ovi.where.domain.model.MeetupParticipantStatus.ARRIVED ->
            FriendStatusLabel(text = "Arrived", tint = FriendStatusTint.Success)

        com.ovi.where.domain.model.MeetupParticipantStatus.CANT_MAKE_IT ->
            FriendStatusLabel(text = "Can't make it", tint = FriendStatusTint.Error)

        else ->
            // No meetup context — fall back to the home presence badge.
            if (isAtHome) FriendStatusLabel(text = "At Home", tint = FriendStatusTint.Home)
            else null
    }
}

private data class FriendStatusLabel(
    val text: String,
    val tint: FriendStatusTint
)

private enum class FriendStatusTint { Note, Success, Error, Home }

/**
 * Status bubble shown next to a map pin: white rounded pill with an
 * image or icon on the left and text on the right. No badge box, no caption.
 */
@Composable
private fun FriendStatusBubble(
    text: String,
    tint: FriendStatusTint,
    modifier: Modifier = Modifier
) {
    val textColor: Color
    val bubbleColor: Color
    val iconTint: Color
    val iconVector: ImageVector?
    val imageRes: Int?
    when (tint) {
        FriendStatusTint.Success -> {
            bubbleColor = Color(0xFF4CAF50)
            textColor = Color.White
            iconTint = Color.White
            iconVector = Icons.Rounded.CheckCircle
            imageRes = null
        }
        FriendStatusTint.Error -> {
            bubbleColor = Color(0xFFE53935)
            textColor = Color.White
            iconTint = Color.White
            iconVector = Icons.Rounded.Cancel
            imageRes = null
        }
        FriendStatusTint.Home -> {
            bubbleColor = Color(0xFF7C4DFF)
            textColor = Color.White
            iconTint = Color.Unspecified
            iconVector = null
            imageRes = R.drawable.mansion
        }
        FriendStatusTint.Note -> {
            bubbleColor = Color(0xFF1E88E5)
            textColor = Color.White
            iconTint = Color.White
            iconVector = Icons.Rounded.ChatBubble
            imageRes = null
        }
    }

    Surface(
        modifier = modifier.widthIn(min = 44.dp),
        shape = RoundedCornerShape(50),
        color = bubbleColor,
        shadowElevation = 6.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            if (imageRes != null) {
                Image(
                    painter = painterResource(id = imageRes),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            } else if (iconVector != null) {
                Icon(
                    imageVector = iconVector,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(12.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = textColor,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 100.dp)
            )
        }
    }
}

/**
 * Top-of-map status card for the in-app meetup navigation overlay.
 *
 * Replaces the chip strip while [GlobalMapUiState.isMeetupNavigating] is
 * true. Shows the destination name, live distance + ETA, a primary
 * "Recenter" affordance (also doubles as retry on error), and a Stop
 * button to exit navigation.
 */
@Composable
private fun NavigationStatusCard(
    destinationName: String,
    distanceLabel: String?,
    etaLabel: String?,
    isLoading: Boolean,
    errorMessage: String?,
    onRecenter: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(
                start = 12.dp,
                end = 8.dp,
                top = 10.dp,
                bottom = 12.dp
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(id = R.drawable.navigate_to),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "NAVIGATING TO",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = destinationName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(18.dp)
                            .padding(end = 4.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (errorMessage != null) {
                    androidx.compose.material3.IconButton(onClick = onRecenter) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = "Retry",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    androidx.compose.material3.IconButton(onClick = onRecenter) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = "Recenter",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                androidx.compose.material3.IconButton(onClick = onStop) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Stop navigation",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // ── Metric strip: distance + ETA ─────────────────────────────
            if (distanceLabel != null || etaLabel != null || errorMessage != null) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (distanceLabel != null) {
                        NavMetricChip(
                            icon = Icons.Rounded.SocialDistance,
                            label = "Distance",
                            value = distanceLabel,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (etaLabel != null) {
                        NavMetricChip(
                            icon = Icons.Rounded.Schedule,
                            label = "ETA",
                            value = etaLabel,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                if (errorMessage != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun NavMetricChip(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ── Home pin marker (shown when a profile's Home section is tapped) ──────────
@Composable
private fun HomePinMarkerContent(
    ownerName: String,
    avatarBitmap: Bitmap?
) {
    Row(verticalAlignment = Alignment.Top) {
        Life360PinMarker(
            avatarBitmap = avatarBitmap,
            fallbackLabel = ownerName.take(1).uppercase(),
            accentColor = MaterialTheme.colorScheme.primary
        )
        FriendStatusBubble(
            text = "$ownerName lives here",
            tint = FriendStatusTint.Home,
            modifier = Modifier.offset(x = (-40).dp, y = 5.dp)
        )
    }
}

/**
 * A custom shape that merges the rounded rectangular body and the triangular pointer.
 * This guarantees the shadow drops evenly behind the *entire* pin, including the tail.
 */
private class MapMarkerShape(
    private val cornerRadius: Dp,
    private val tailWidth: Dp,
    private val tailHeight: Dp
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline = with(density) {
        val cr = cornerRadius.toPx()
        val tw = tailWidth.toPx()
        val th = tailHeight.toPx()

        val bodyHeight = size.height - th
        val center = size.width / 2f
        val halfTail = tw / 2f

        val path = Path().apply {
            // 1. Draw the main squircle body
            addRoundRect(
                RoundRect(
                    left = 0f,
                    top = 0f,
                    right = size.width,
                    bottom = bodyHeight,
                    cornerRadius = CornerRadius(cr, cr)
                )
            )

            // 2. Draw the tail seamlessly appended to the bottom
            // Drawn clockwise to match addRoundRect's winding direction for a solid fill
            moveTo(center - halfTail, bodyHeight)
            lineTo(center + halfTail, bodyHeight) // Cross over to the right side of the tail base

            // Curve down to the bottom tip
            cubicTo(
                center + halfTail * 0.7f, bodyHeight + th * 0.2f,
                center + halfTail * 0.4f, bodyHeight + th * 0.8f,
                center, size.height
            )

            // Curve back up to the left side
            cubicTo(
                center - halfTail * 0.4f, bodyHeight + th * 0.8f,
                center - halfTail * 0.7f, bodyHeight + th * 0.2f,
                center - halfTail, bodyHeight
            )
            close()
        }

        Outline.Generic(path)
    }
}

fun Modifier.softDropShadow(
    color: Color = Color(0x26000000), // 15% black
    blurRadius: Dp = 12.dp,
    offsetY: Dp = 4.dp,
    offsetX: Dp = 0.dp,
    cornerRadius: Dp = 20.dp // Half of your 40.dp height to make a perfect pill
) = this.drawBehind {
    val shadowColor = color.toArgb()
    val transparent = Color.Transparent.toArgb()

    val paint = Paint().apply {
        asFrameworkPaint().apply {
            this.color = transparent
            setShadowLayer(
                blurRadius.toPx(),
                offsetX.toPx(),
                offsetY.toPx(),
                shadowColor
            )
        }
    }

    drawIntoCanvas { canvas ->
        canvas.drawRoundRect(
            left = 0f,
            top = 0f,
            right = size.width,
            bottom = size.height,
            radiusX = cornerRadius.toPx(),
            radiusY = cornerRadius.toPx(),
            paint = paint
        )
    }
}