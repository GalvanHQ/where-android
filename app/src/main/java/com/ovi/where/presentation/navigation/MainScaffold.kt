package com.ovi.where.presentation.navigation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.ovi.where.R

/**
 * Bottom-tab definition. Routes match the [Screen] tab destinations so the
 * bar can compare against `currentBackStackEntry.destination.route` directly.
 */
internal sealed class BottomTab(
    val route: String,
    @DrawableRes val selectedIconResId: Int,
    @DrawableRes val unselectedIconResId: Int,
    val label: String
) {
    object Map : BottomTab(
        route = Screen.MapTab.route,
        selectedIconResId = R.drawable.map_filled,
        unselectedIconResId = R.drawable.map_outlined,
        label = "Map"
    )

    object Chats : BottomTab(
        route = Screen.ChatsTab.route,
        selectedIconResId = R.drawable.chat_filled,
        unselectedIconResId = R.drawable.chat_outlined,
        label = "Chats"
    )

    object People : BottomTab(
        route = Screen.PeopleTab.route,
        selectedIconResId = R.drawable.people_filled,
        unselectedIconResId = R.drawable.people_outlined,
        label = "People"
    )

    object Profile : BottomTab(
        route = Screen.ProfileTab.route,
        selectedIconResId = R.drawable.profile_filled,
        unselectedIconResId = R.drawable.profile_outlined,
        label = "Profile"
    )
}

private val bottomTabs = listOf(
    BottomTab.Map,
    BottomTab.Chats,
    BottomTab.People,
    BottomTab.Profile
)

/**
 * Bottom navigation bar overlaid on top of the main NavHost. Renders only
 * when the current destination is one of the tab routes (gated by the caller
 * in [AppNavGraph]). The Profile tab swaps its icon for the user's avatar.
 */
@Composable
internal fun WhereBottomBar(
    currentRoute: String?,
    onTabClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainScaffoldViewModel = hiltViewModel()
) {
    val profilePhotoUrl by viewModel.profilePhotoUrl.collectAsState()

    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        bottomTabs.forEach { tab ->
            val selected = currentRoute == tab.route
            NavigationBarItem(
                selected = selected,
                onClick = { onTabClick(tab.route) },
                icon = {
                    if (tab == BottomTab.Profile && !profilePhotoUrl.isNullOrEmpty()) {
                        val borderModifier = if (selected) {
                            Modifier.border(
                                width = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape
                            )
                        } else Modifier
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(profilePhotoUrl)
                                .crossfade(true)
                                .memoryCacheKey(profilePhotoUrl)
                                .diskCacheKey(profilePhotoUrl)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .size(128)
                                .build(),
                            contentDescription = tab.label,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .then(borderModifier)
                        )
                    } else {
                        val iconResId = if (selected) tab.selectedIconResId else tab.unselectedIconResId
                        Icon(
                            painter = painterResource(id = iconResId),
                            contentDescription = tab.label,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                label = { Text(text = tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}
