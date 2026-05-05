package com.ovi.where.presentation.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ovi.where.presentation.chat.ChatsScreen
import com.ovi.where.presentation.map.GlobalMapScreen
import com.ovi.where.presentation.people.PeopleScreen
import com.ovi.where.presentation.profile.ProfileScreen

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
    onLogout: () -> Unit
) {
    val bottomNavController = rememberNavController()
    val navBackStackEntry by bottomNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                bottomTabs.forEach { tab ->
                    val selected = currentRoute == tab.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (currentRoute != tab.route) {
                                bottomNavController.navigate(tab.route) {
                                    popUpTo(BottomTab.Map.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.label,
                                modifier = Modifier.size(26.dp)
                            )
                        },
                        // No label — Instagram style
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
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
                    onNavigateBack = {},
                    onLogout = onLogout
                )
            }
        }
    }
}
