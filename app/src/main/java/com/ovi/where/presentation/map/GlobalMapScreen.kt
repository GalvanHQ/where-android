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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.rounded.History
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
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
import com.ovi.where.core.utils.LocationUtils
import com.ovi.where.core.utils.showToast
import com.ovi.where.presentation.map.components.DestinationPinMarker
import com.ovi.where.presentation.map.components.MeetupChip
import com.ovi.where.presentation.map.components.MeetupPlaceCardSheet
import com.ovi.where.presentation.map.components.MeetupPlacementActionBar
import com.ovi.where.presentation.map.components.SetMeetupDestinationSheet
import com.ovi.where.presentation.map.components.fanOutOverlappingMarkers
import com.ovi.where.presentation.notification.NotificationsViewModel
import com.ovi.where.presentation.notification.components.NotificationChip
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
    onNavigateToNotifications: () -> Unit = {},
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
    androidx.compose.runtime.DisposableEffect(Unit) {
        val tracker = context.activeMapTracker()
        tracker.setMapVisible(true)
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
                                uiState.meetupDestination != null
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
                                    .takeIf { uiState.meetupDestination != null }
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
                                friend.meetupStatus?.name ?: ""
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Group filter chip — same chip language as the meetup chip.
                    Surface(
                        modifier = Modifier
                            .height(40.dp)
                            .clip(RoundedCornerShape(50))
                            .clickable { viewModel.showGroupPicker(true) },
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 2.dp,
                        tonalElevation = 2.dp,
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 6.dp, end = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Compact 28dp avatar — matches the meetup chip's
                            // active-state inset bubble for visual rhythm.
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                FilterPillAvatar(filter = uiState.activeGroupFilter)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = uiState.activeGroupFilter?.name ?: "All Friends",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 110.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Default.ArrowDropDown,
                                null,
                                modifier = Modifier.size(18.dp),
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

                    // Icon-only notifications chip — drives the in-app inbox.
                    // The unread count is observed via NotificationsViewModel and
                    // appears as a small primary badge on the top-right corner.
                    val notificationsVm: NotificationsViewModel = hiltViewModel()
                    val unreadCount by notificationsVm.unreadCount.collectAsState()
                    NotificationChip(
                        onClick = onNavigateToNotifications,
                        unreadCount = unreadCount
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
                        ?: "Meetup point",
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
                            imageVector = ImageVector.vectorResource(id = R.drawable.layers),
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
                        contentColor = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                        elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(
                            defaultElevation = 6.dp,
                            pressedElevation = 8.dp
                        )
                    ) {
                        Icon(
                            imageVector = ImageVector.vectorResource(id = R.drawable.location_crosshairs),
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
        modifier = Modifier.size(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                modifier = Modifier.size(20.dp),
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
        // ── Two-pill toggle ───────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HomeTabPill(
                label = "Friends sharing",
                count = friends.size,
                selected = homeTab == MapHomeTab.Friends,
                modifier = Modifier.weight(1f),
                onClick = { onTabChange(MapHomeTab.Friends) }
            )
            HomeTabPill(
                label = "My shares",
                count = sharingTargetIds.size,
                selected = homeTab == MapHomeTab.MyShares,
                showLiveDot = isSharing,
                modifier = Modifier.weight(1f),
                onClick = { onTabChange(MapHomeTab.MyShares) }
            )
        }

        Spacer(Modifier.height(8.dp))

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

@Composable
private fun HomeTabPill(
    label: String,
    count: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
    showLiveDot: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurface,
        modifier = modifier.height(40.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (showLiveDot) {
                SharingPulseDot()
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (count > 0) {
                Spacer(Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (selected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.22f)
                    else MaterialTheme.colorScheme.primaryContainer,
                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Text(
                        text = "$count",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }
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
        Text(
            text = "${friends.size} ${if (friends.size == 1) "friend is" else "friends are"} sharing their location with you",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )

        Spacer(Modifier.height(4.dp))
        androidx.compose.foundation.lazy.LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items = friends, key = { "avatar-${it.userId}" }) { friend ->
                FriendAvatarPeek(friend = friend, onClick = { onFriendClick(friend) })
            }
        }

        Spacer(Modifier.height(12.dp))
        androidx.compose.material3.HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )

        LazyColumn(
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = 12.dp,
                bottom = bottomReservedSpace + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "You're not sharing yet",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Pick friends or groups to share your live location with.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onAddShare,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(id = R.drawable.location_arrow),
                        null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Share location", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(bottomReservedSpace))
            }
            return@Column
        }

        // ── Section header ────────────────────────────────────────────────────────
        Text(
            text = "You're sharing your live location with ${active.size} ${if (active.size == 1) "recipient" else "recipients"}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )
        Spacer(Modifier.height(4.dp))

        LazyColumn(
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = 4.dp,
                bottom = 12.dp
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp),
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
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onAddShare,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(id = R.drawable.location_arrow),
                    null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Share more", style = MaterialTheme.typography.labelLarge)
            }
            Button(
                onClick = onStopAll,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(Icons.Rounded.Stop, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "Stop all",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
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
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
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
                    color = MaterialTheme.colorScheme.surface
                )
            }
        }

        Spacer(Modifier.height(Dimens.spaceLarge))

        Text(
            text = friend.displayName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
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
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.Medium
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
            OutlinedButton(
                onClick = onNavigateToUserProfile,
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Default.Person, null, modifier = Modifier.size(Dimens.iconSizeMedium))
                Spacer(Modifier.width(Dimens.spaceSmall))
                Text("Profile")
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
                        color = MaterialTheme.colorScheme.surface
                    )
                } else {
                    Icon(
                        Icons.Default.Groups,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.surface
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
                        color = MaterialTheme.colorScheme.surface
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
                        .background(MaterialTheme.colorScheme.tertiary)
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
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
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
                            color = MaterialTheme.colorScheme.surface
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
                            .background(MaterialTheme.colorScheme.tertiary)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = friend.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (hasMyLocation && friend.latitude != 0.0 && friend.longitude != 0.0) {
                        val distance = LocationUtils.calculateDistance(
                            myLatitude,
                            myLongitude,
                            friend.latitude,
                            friend.longitude
                        )

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = LocationUtils.formatDistance(context, distance),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }

                        friend.etaText?.let { eta ->
                            Spacer(Modifier.width(6.dp))

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                            ) {
                                Text(
                                    text = "~$eta",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(Modifier.width(6.dp))
                    }

                    Text(
                        text = friend.timeAgo.ifEmpty { "Just now" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Surface(
                onClick = onShowOnMap,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = ImageVector.vectorResource(id = R.drawable.navigate_to),
                        contentDescription = "Show ${friend.displayName} on map",
                        modifier = Modifier.size(18.dp),
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
    Box(
        modifier = Modifier
            .size(8.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.tertiary)
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
        Text("Filter by", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            text = "Show locations from a group or specific friend",
            style = MaterialTheme.typography.bodySmall,
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
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = Dimens.spaceMedium, top = 12.dp, bottom = 4.dp)
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
        shape = MaterialTheme.shapes.large,
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
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
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
                    style = MaterialTheme.typography.labelSmall,
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
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterPillAvatar(filter = target)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    target.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SharingPulseDot()
                    Spacer(Modifier.width(6.dp))
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
                        color = MaterialTheme.colorScheme.tertiary,
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
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Flag,
                            contentDescription = "Open meetup card to clear meetup",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            } else {
                Surface(
                    onClick = onStop,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Stop,
                            contentDescription = "Stop sharing with ${target.name}",
                            modifier = Modifier.size(18.dp)
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
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        // ── Header with back button ───────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            SheetBackButton(onCancel)
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Share live location",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (selectedIds.isEmpty()) "Pick friends or groups"
                    else "${selectedIds.size} selected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selectedIds.isNotEmpty()) {
                TextButton(onClick = { selectedIds.clear() }) {
                    Text("Clear")
                }
            }
        }

        Spacer(Modifier.height(16.dp))

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
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
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
            Spacer(Modifier.height(14.dp))
        }

        // ── Friends section ───────────────────────────────────────────────────
        LazyColumn(
            modifier = Modifier.heightIn(max = 360.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
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

        Spacer(Modifier.height(20.dp))

        // ── Duration ──────────────────────────────────────────────────────────
        Text(
            "DURATION",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                title = {
                    Text(
                        "Share until you stop?",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
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

        Spacer(Modifier.height(20.dp))

        // ── Start button ──────────────────────────────────────────────────────
        Button(
            onClick = {
                if (selectedIds.isNotEmpty()) onStart(selectedIds.toList(), selectedDuration)
            },
            enabled = selectedIds.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(id = R.drawable.location_arrow),
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (selectedIds.isEmpty()) "Pick someone to share with"
                else "Share with ${selectedIds.size}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(12.dp))
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
            .padding(horizontal = 4.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Groups,
                contentDescription = null,
                modifier = Modifier.size(30.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "No one to share with yet",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Add friends or create / join a group to start sharing your live location.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(20.dp))

        // Primary action — most likely path is adding friends (DM share).
        Button(
            onClick = onAddFriends,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(Icons.Default.Person, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Add friends",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(8.dp))

        // Secondary actions — group paths.
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = onJoinGroup,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(16.dp),
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
                    .height(48.dp),
                shape = RoundedCornerShape(16.dp),
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

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ShareSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
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
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (selected) androidx.compose.foundation.BorderStroke(
            1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        ) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterPillAvatar(filter = target)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    target.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (target.isDirect) "Direct share" else "Group",
                    style = MaterialTheme.typography.labelSmall,
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
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.height(36.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tiny avatar
            Box(modifier = Modifier.size(28.dp)) {
                FilterPillAvatar(filter = target)
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = target.name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 120.dp)
            )
            Spacer(Modifier.width(4.dp))
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

// ── Marker Fan-Out (prevents overlapping pins) ──────────────────────────────

/** Key used for the user's own marker in the display-positions map. */
private const val MY_MARKER_KEY = "__my_location__"

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
    val avatarImageBitmap = remember(avatarBitmap) {
        avatarBitmap?.asImageBitmap()
    }

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
            if (avatarImageBitmap != null) {
                Image(
                    painter = BitmapPainter(avatarImageBitmap),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = fallbackLabel
                        .trim()
                        .take(1)
                        .uppercase()
                        .ifEmpty { "?" },
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
    avatarBitmap: Bitmap?,
    note: String = "",
    meetupStatus: com.ovi.where.domain.model.MeetupParticipantStatus? = null
) {
    val statusLabel = when {
        note.isNotBlank() -> FriendStatusLabel(text = note, tint = FriendStatusTint.Note)
        meetupStatus == com.ovi.where.domain.model.MeetupParticipantStatus.ARRIVED ->
            FriendStatusLabel(text = "Arrived", tint = FriendStatusTint.Success)

        meetupStatus == com.ovi.where.domain.model.MeetupParticipantStatus.CANT_MAKE_IT ->
            FriendStatusLabel(text = "Can't make it", tint = FriendStatusTint.Error)

        else -> null
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.widthIn(max = 220.dp)
    ) {
        if (statusLabel != null) {
            FriendStatusBubble(
                text = statusLabel.text,
                tint = statusLabel.tint
            )
            Spacer(Modifier.height(2.dp))
        }
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
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.widthIn(max = 220.dp)
    ) {
        // Status bubble — caption-style chip showing the user's free-form
        // meetup note, or the canonical "Arrived" / "Can't make it" label
        // when they're in a terminal status. We keep this above the pin so
        // the avatar stays the focal point.
        val statusLabel = friend.statusBubbleLabel()
        if (statusLabel != null) {
            FriendStatusBubble(
                text = statusLabel.text,
                tint = statusLabel.tint
            )
            Spacer(Modifier.height(2.dp))
        }
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

        else -> null
    }
}

private data class FriendStatusLabel(
    val text: String,
    val tint: FriendStatusTint
)

private enum class FriendStatusTint { Note, Success, Error }

/**
 * Chat-bubble-shaped status caption shown above a friend's pin —
 * rounded body + a small triangular tail that points at the avatar
 * below. Reads like a WhatsApp / Messenger speech bubble so the user's
 * note feels like *their words*, not a plain badge.
 *
 * Tail is a 6dp-tall isoceles triangle drawn with [drawBehind] so we
 * stay on a single composition layer (no nested Surfaces / clips).
 */
@Composable
private fun FriendStatusBubble(text: String, tint: FriendStatusTint) {
    val (bg, fg) = when (tint) {
        FriendStatusTint.Note ->
            MaterialTheme.colorScheme.surface to MaterialTheme.colorScheme.onSurface

        FriendStatusTint.Success ->
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer

        FriendStatusTint.Error ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.widthIn(max = 200.dp)
    ) {
        // ── Bubble body ───────────────────────────────────────────────
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = bg,
            tonalElevation = 2.dp,
            shadowElevation = 3.dp
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = fg,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }

        // ── Tail pointing down at the avatar ──────────────────────────
        // Slight overlap pulls the triangle into the body so the join
        // reads as a single bubble rather than two stacked shapes.
        Box(
            modifier = Modifier
                .offset(y = (-1).dp)
                .size(width = 12.dp, height = 6.dp)
                .drawBehind {
                    val path = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(size.width, 0f)
                        lineTo(size.width / 2f, size.height)
                        close()
                    }
                    drawPath(path, color = bg)
                }
        )
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
                        modifier = Modifier.size(18.dp)
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
