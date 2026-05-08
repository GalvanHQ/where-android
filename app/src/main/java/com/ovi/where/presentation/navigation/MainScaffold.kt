package com.ovi.where.presentation.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Person
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ovi.where.presentation.chat.ChatsScreen
import com.ovi.where.presentation.map.GlobalMapScreen
import com.ovi.where.presentation.people.PeopleScreen
import com.ovi.where.presentation.profile.ProfileScreen
import coil.compose.AsyncImage

private sealed class BottomTab(
    val route: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val label: String
) {
    object Map : BottomTab("tab_map", Icons.Filled.LocationOn, Icons.Outlined.LocationOn, "Map")
    object Chats : BottomTab("tab_chats", Icons.Filled.ChatBubble, Icons.Outlined.ChatBubbleOutline, "Chats")
    object People : BottomTab("tab_people", Icons.Filled.People, Icons.Outlined.People, "People")
    object Profile : BottomTab("tab_profile", Icons.Filled.Person, Icons.Outlined.Person, "Profile")
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
                    contentPadding = paddingValues,
                    onNavigateBack = {},
                    onLogout = onLogout,
                    showBackButton = false
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
                Surface(
                    onClick = { onTabClick(tab) },
                    modifier = Modifier.size(width = 72.dp, height = 44.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (tab == BottomTab.Profile && !profilePhotoUrl.isNullOrEmpty()) {
                            AsyncImage(
                                model = profilePhotoUrl,
                                contentDescription = tab.label,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(
                                        width = if (selected) 2.dp else 0.dp,
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                        shape = CircleShape
                                    )
                            )
                        } else {
                            Icon(
                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
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
