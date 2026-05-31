package com.ovi.where.presentation.profile.edit

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Geocoder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import com.ovi.where.R
import com.ovi.where.core.theme.Dimens
import com.ovi.where.presentation.common.WhereTopAppBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Full-screen map picker for choosing a home location.
 *
 * Mirrors the meetup placement-mode UX from `GlobalMapScreen`: a fixed
 * crosshair pin sits at the screen center, the map pans beneath it, and the
 * centered coordinate is reverse-geocoded into a readable address shown in a
 * bottom card. A "my location" FAB (same affordance as the map screen)
 * re-centers on the user's current GPS fix, requesting permission on demand.
 * Confirming returns the picked lat/lng + address to the caller.
 *
 * @param initialLatitude seed coordinate (current home, or last-known GPS, or 0)
 * @param initialLongitude seed coordinate
 * @param onConfirm invoked with (lat, lng, address) when the user confirms
 * @param onNavigateBack pop without a result
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomePickerScreen(
    initialLatitude: Double,
    initialLongitude: Double,
    onConfirm: (Double, Double, String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val hasSeed = initialLatitude != 0.0 || initialLongitude != 0.0
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            LatLng(
                if (hasSeed) initialLatitude else 0.0,
                if (hasSeed) initialLongitude else 0.0
            ),
            if (hasSeed) 16f else 2f
        )
    }

    var address by remember { mutableStateOf("") }
    var isResolving by remember { mutableStateOf(false) }
    var isLocating by remember { mutableStateOf(false) }

    val fusedClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // Centers the camera on the current GPS fix.
    @SuppressLint("MissingPermission")
    fun locateMe() {
        scope.launch {
            isLocating = true
            val loc = try {
                fusedClient.lastLocation.await()
            } catch (_: Exception) {
                null
            }
            if (loc != null) {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 16f),
                    durationMs = 700
                )
            }
            isLocating = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) locateMe()
    }

    fun onLocateClick() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            locateMe()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // Reverse-geocode the centered point whenever the camera settles.
    LaunchedEffect(cameraPositionState.isMoving) {
        if (!cameraPositionState.isMoving) {
            val target = cameraPositionState.position.target
            if (target.latitude == 0.0 && target.longitude == 0.0) return@LaunchedEffect
            isResolving = true
            address = geocodeAddress(context, target.latitude, target.longitude) ?: ""
            isResolving = false
        }
    }

    androidx.compose.material3.Scaffold(
        topBar = {
            WhereTopAppBar(
                title = "Set Home",
                onNavigateBack = onNavigateBack
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                mapColorScheme = ComposeMapColorScheme.FOLLOW_SYSTEM,
                properties = MapProperties(isMyLocationEnabled = false),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = false,
                    myLocationButtonEnabled = false,
                    mapToolbarEnabled = false
                )
            )

            // ── Centered crosshair pin ────────────────────────────────────
            // Offset up by half the pin height so the tip points at center.
            Icon(
                imageVector = Icons.Filled.LocationOn,
                contentDescription = "Home location",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp)
                    .offset(y = (-24).dp)
            )

            // ── My-location FAB ───────────────────────────────────────────
            // Mirrors the map screen's locate affordance. Sits above the
            // bottom action card.
            SmallFloatingActionButton(
                onClick = { onLocateClick() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = Dimens.spaceLarge, bottom = 168.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 6.dp,
                    pressedElevation = 8.dp
                )
            ) {
                if (isLocating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(Dimens.iconSizeMedium),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Icon(
                        imageVector = ImageVector.vectorResource(id = R.drawable.location_crosshairs_outlined),
                        contentDescription = "My location",
                        modifier = Modifier.size(Dimens.iconSizeMedium)
                    )
                }
            }

            // ── Bottom action card ────────────────────────────────────────
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(Dimens.spaceLarge),
                shape = RoundedCornerShape(Dimens.cornerMedium),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                shadowElevation = 6.dp
            ) {
                Column(modifier = Modifier.padding(Dimens.spaceLarge)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(Dimens.iconSizeMedium)
                        )
                        Spacer(Modifier.size(Dimens.spaceMedium))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Drag the map to place your home",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (isResolving) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMedium)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Text(
                                        "Finding address…",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            } else {
                                Text(
                                    text = address.ifBlank { "Dropped pin" },
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(Dimens.spaceLarge))

                    Button(
                        onClick = {
                            val target = cameraPositionState.position.target
                            onConfirm(target.latitude, target.longitude, address)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(Dimens.cornerSmall),
                        enabled = !(cameraPositionState.position.target.latitude == 0.0 &&
                                cameraPositionState.position.target.longitude == 0.0)
                    ) {
                        Text("Set as Home", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

/**
 * Reverse-geocode helper. Best-effort: returns null on failure / timeout.
 * Uses the async overload on Android 33+ and the deprecated sync call on
 * older versions, both off the main thread.
 */
private suspend fun geocodeAddress(
    context: android.content.Context,
    latitude: Double,
    longitude: Double
): String? {
    if (!Geocoder.isPresent()) return null
    val geocoder = Geocoder(context, java.util.Locale.getDefault())
    return try {
        withTimeoutOrNull(4_000L) {
            val addresses = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine<List<android.location.Address>?> { cont ->
                    geocoder.getFromLocation(latitude, longitude, 1) { result ->
                        if (cont.isActive) cont.resumeWith(Result.success(result))
                    }
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(latitude, longitude, 1)
                }
            }
            addresses?.firstOrNull()?.let { addr ->
                addr.getAddressLine(0)
                    ?: addr.locality
                    ?: addr.subLocality
                    ?: addr.featureName
                    ?: addr.adminArea
            }
        }
    } catch (_: Exception) {
        null
    }
}
