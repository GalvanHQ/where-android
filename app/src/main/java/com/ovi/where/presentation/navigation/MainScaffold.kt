package com.ovi.where.presentation.navigation

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
    onNavigateToFriendRequests: () -> Unit,
    onNavigateToSearchPeople: () -> Unit,
    onNavigateToEditProfile: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onLogout: () -> Unit,
    viewModel: MainScaffoldViewModel = hiltViewModel()
) {
    val bottomNavController = rememberNavController()
    val navBackStackEntry by bottomNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val profilePhotoUrl by viewModel.profilePhotoUrl.collectAsState()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
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
                }
            )
        }
    ) { paddingValues ->
        NavHost(
            navController = bottomNavController,
            startDestination = BottomTab.Map.route,
            enterTransition = { fadeIn(tween(200)) },
            exitTransition = { fadeOut(tween(200)) }
        ) {
            composable(BottomTab.Map.route) {
                GlobalMapScreen(
                    contentPadding = paddingValues,
                    onNavigateToChat = onNavigateToChat,
                    onNavigateToUserProfile = onNavigateToUserProfile,
                    onNavigateToGroupMap = onNavigateToGroupMap
                )
            }
            composable(BottomTab.Chats.route) {
                ChatsScreen(
                    contentPadding = paddingValues,
                    onNavigateToChat = onNavigateToChat,
                    onNavigateToSearchPeople = onNavigateToSearchPeople,
                    onNavigateToCreateGroup = onNavigateToCreateGroup,
                    onNavigateToJoinGroup = onNavigateToJoinGroup
                )
            }
            composable(BottomTab.People.route) {
                PeopleScreen(
                    contentPadding = paddingValues,
                    onNavigateToUserProfile = onNavigateToUserProfile,
                    onNavigateToChat = onNavigateToChat,
                    onNavigateToFriendRequests = onNavigateToFriendRequests,
                    onNavigateToSearchPeople = onNavigateToSearchPeople
                )
            }
            composable(BottomTab.Profile.route) {
                ProfileScreen(
                    onNavigateToEditProfile = onNavigateToEditProfile,
                    onNavigateToSettings = onNavigateToSettings
                )
            }
        }
    }
}

@Composable
private fun WhereBottomBar(
    currentRoute: String?,
    profilePhotoUrl: String?,
    onTabClick: (BottomTab) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(60.dp)
                .padding(horizontal = 18.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            bottomTabs.forEach { tab ->
                val selected = currentRoute == tab.route
                val containerColor =
                    if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                val contentColor =
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                Surface(
                    onClick = { onTabClick(tab) },
                    modifier = Modifier.size(width = 72.dp, height = 44.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = containerColor,
                    contentColor = contentColor
                ) {
                    Box(contentAlignment = Alignment.Center) {
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
                                    .size(30.dp)
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
                                modifier = Modifier.size(25.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
