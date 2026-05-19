package com.ovi.where.presentation.navigation

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.ovi.where.R
import com.ovi.where.presentation.chat.ChatsScreen
import com.ovi.where.presentation.map.GlobalMapScreen
import com.ovi.where.presentation.people.PeopleScreen
import com.ovi.where.presentation.profile.ProfileScreen

private sealed class BottomTab(
    val route: String,
    @DrawableRes val selectedIconResId: Int,
    @DrawableRes val unselectedIconResId: Int,
    val label: String
) {
    object Map : BottomTab(
        route = "tab_map",
        selectedIconResId = R.drawable.map_filled,
        unselectedIconResId = R.drawable.map_outlined,
        label = "Map"
    )

    object Chats : BottomTab(
        route = "tab_chats",
        selectedIconResId = R.drawable.chat_filled,
        unselectedIconResId = R.drawable.chat_outlined,
        label = "Chats"
    )

    object People : BottomTab(
        route = "tab_people",
        selectedIconResId = R.drawable.people_filled,
        unselectedIconResId = R.drawable.people_outlined,
        label = "People"
    )

    object Profile : BottomTab(
        route = "tab_profile",
        selectedIconResId = R.drawable.profile_filled,
        unselectedIconResId = R.drawable.profile_outlined,
        label = "Profile"
    )
}

private val bottomTabs = listOf(BottomTab.Map, BottomTab.Chats, BottomTab.People, BottomTab.Profile)

@Composable
fun MainScaffold(
    onNavigateToChat: (String) -> Unit,
    onNavigateToUserProfile: (String) -> Unit,
    onNavigateToGroupDetails: (String) -> Unit,
    onNavigateToGroupMap: (String) -> Unit,
    onNavigateToCreateGroup: () -> Unit,
    onNavigateToJoinGroup: () -> Unit,
    onNavigateToNewMessage: () -> Unit,
    onNavigateToFriendRequests: () -> Unit,
    onNavigateToSearch: (String) -> Unit,
    onNavigateToEditProfile: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onLogout: () -> Unit,
    viewModel: MainScaffoldViewModel = hiltViewModel()
) {
    val bottomNavController = rememberNavController()
    val navBackStackEntry by bottomNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val profilePhotoUrl by viewModel.profilePhotoUrl.collectAsState()

    // Google Maps style: no parent Scaffold wrapping the content.
    // The map screen uses BottomSheetScaffold as its own root.
    // The bottom nav is overlaid on top with highest z-order.
    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = bottomNavController,
            startDestination = BottomTab.Map.route,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            enterTransition = { fadeIn(tween(200)) },
            exitTransition = { fadeOut(tween(200)) }
        ) {
            composable(BottomTab.Map.route) {
                GlobalMapScreen(
                    onNavigateToChat = onNavigateToChat,
                    onNavigateToUserProfile = onNavigateToUserProfile,
                    onNavigateToGroupMap = onNavigateToGroupMap
                )
            }
            composable(BottomTab.Chats.route) {
                ChatsScreen(
                    contentPadding = PaddingValues(bottom = 80.dp),
                    onNavigateToChat = onNavigateToChat,
                    onNavigateToNewMessage = onNavigateToNewMessage,
                    onNavigateToSearch = { onNavigateToSearch("chats") }
                )
            }
            composable(BottomTab.People.route) {
                PeopleScreen(
                    contentPadding = PaddingValues(bottom = 80.dp),
                    onNavigateToUserProfile = onNavigateToUserProfile,
                    onNavigateToChat = onNavigateToChat,
                    onNavigateToFriendRequests = onNavigateToFriendRequests,
                    onNavigateToSearch = { onNavigateToSearch("people") }
                )
            }
            composable(BottomTab.Profile.route) {
                ProfileScreen(
                    onNavigateToEditProfile = onNavigateToEditProfile,
                    onNavigateToSettings = onNavigateToSettings,
                    contentPadding = PaddingValues(bottom = 80.dp),
                    onNavigateToMessages = {
                        bottomNavController.navigate(BottomTab.Chats.route) {
                            popUpTo(BottomTab.Map.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToGroups = {
                        bottomNavController.navigate(BottomTab.People.route) {
                            popUpTo(BottomTab.Map.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToLocationSharing = {
                        bottomNavController.navigate(BottomTab.Map.route) {
                            popUpTo(BottomTab.Map.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }

        // Bottom nav overlaid on top (highest z-order, like Google Maps)
        WhereBottomBar(
            currentRoute = currentRoute,
            profilePhotoUrl = profilePhotoUrl,
            onTabClick = { tab ->
                if (currentRoute != tab.route) {
                    bottomNavController.navigate(tab.route) {
                        popUpTo(BottomTab.Map.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun WhereBottomBar(
    currentRoute: String?,
    profilePhotoUrl: String?,
    onTabClick: (BottomTab) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        bottomTabs.forEach { tab ->
            val selected = currentRoute == tab.route
            NavigationBarItem(
                selected = selected,
                onClick = { onTabClick(tab) },
                icon = {
                    if (tab == BottomTab.Profile && !profilePhotoUrl.isNullOrEmpty()) {
                        val selectedBorderModifier =
                            if (selected) {
                                Modifier.border(
                                    width = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = CircleShape
                                )
                            } else {
                                Modifier
                            }
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
                                .then(selectedBorderModifier)
                        )
                    } else {
                        val iconResId =
                            if (selected) tab.selectedIconResId else tab.unselectedIconResId
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
