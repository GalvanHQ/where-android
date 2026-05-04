package com.ovi.where.presentation.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ovi.where.R
import com.ovi.where.presentation.home.HomeScreen
import com.ovi.where.presentation.map.MapScreen
import com.ovi.where.presentation.profile.ProfileScreen
import androidx.compose.material3.Scaffold

private sealed class BottomTab(
    val route: String,
    val titleRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Groups : BottomTab(
        "tab_groups", R.string.title_my_groups,
        Icons.Filled.Group, Icons.Outlined.Group
    )
    object Map : BottomTab(
        "tab_map", R.string.title_map,
        Icons.Filled.Map, Icons.Outlined.Map
    )
    object Profile : BottomTab(
        "tab_profile", R.string.title_profile,
        Icons.Filled.Person, Icons.Outlined.Person
    )
}

private val bottomTabs = listOf(BottomTab.Groups, BottomTab.Map, BottomTab.Profile)

@Composable
fun MainScaffold(
    onNavigateToMap: (String) -> Unit,
    onNavigateToCreateGroup: () -> Unit,
    onNavigateToJoinGroup: () -> Unit,
    onNavigateToGroupDetails: (String) -> Unit,
    onLogout: () -> Unit
) {
    val bottomNavController = rememberNavController()
    val navBackStackEntry by bottomNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    var lastMapGroupId by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomTabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            if (currentRoute != tab.route) {
                                bottomNavController.navigate(tab.route) {
                                    popUpTo(BottomTab.Groups.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (currentRoute == tab.route) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = stringResource(tab.titleRes)
                            )
                        },
                        label = { Text(stringResource(tab.titleRes)) }
                    )
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = bottomNavController,
            startDestination = BottomTab.Groups.route,
            enterTransition = { fadeIn(tween(200)) },
            exitTransition = { fadeOut(tween(200)) }
        ) {
            composable(BottomTab.Groups.route) {
                HomeScreen(
                    contentPadding = paddingValues,
                    onNavigateToMap = { groupId ->
                        lastMapGroupId = groupId
                        onNavigateToMap(groupId)
                    },
                    onNavigateToCreateGroup = onNavigateToCreateGroup,
                    onNavigateToJoinGroup = onNavigateToJoinGroup,
                    onNavigateToGroupDetails = onNavigateToGroupDetails
                )
            }
            composable(BottomTab.Map.route) {
                val groupId = lastMapGroupId
                if (groupId != null) {
                    MapScreen(
                        groupId = groupId,
                        onNavigateBack = {
                            bottomNavController.navigate(BottomTab.Groups.route) {
                                popUpTo(BottomTab.Groups.route)
                            }
                        }
                    )
                } else {
                    HomeScreen(
                        contentPadding = paddingValues,
                        onNavigateToMap = { id ->
                            lastMapGroupId = id
                            bottomNavController.navigate(BottomTab.Map.route) {
                                launchSingleTop = true
                            }
                        },
                        onNavigateToCreateGroup = onNavigateToCreateGroup,
                        onNavigateToJoinGroup = onNavigateToJoinGroup,
                        onNavigateToGroupDetails = onNavigateToGroupDetails,
                        showMapHint = true
                    )
                }
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
