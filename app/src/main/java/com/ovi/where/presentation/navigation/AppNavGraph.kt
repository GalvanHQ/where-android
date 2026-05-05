package com.ovi.where.presentation.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ovi.where.presentation.auth.forgotpassword.ForgotPasswordScreen
import com.ovi.where.presentation.auth.login.LoginScreen
import com.ovi.where.presentation.auth.register.RegisterScreen
import com.ovi.where.presentation.chat.ChatScreen
import com.ovi.where.presentation.group.JoinGroupScreen
import com.ovi.where.presentation.group.create.CreateGroupScreen
import com.ovi.where.presentation.group.details.GroupDetailsScreen
import com.ovi.where.presentation.group.edit.EditGroupScreen
import com.ovi.where.presentation.map.MapScreen
import com.ovi.where.presentation.onboarding.OnboardingScreen
import com.ovi.where.presentation.people.FriendRequestsScreen
import com.ovi.where.presentation.people.SearchUsersScreen
import com.ovi.where.presentation.people.UserProfileScreen
import com.ovi.where.presentation.splash.SplashScreen

private const val NAV_ANIM_DURATION = 300

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AppNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    startDestination: String = Screen.Splash.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(NAV_ANIM_DURATION))
        },
        exitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(NAV_ANIM_DURATION))
        },
        popEnterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(NAV_ANIM_DURATION))
        },
        popExitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(NAV_ANIM_DURATION))
        }
    ) {
        // ── Auth flow ────────────────────────────────────────────────────────────
        composable(
            Screen.Splash.route,
            enterTransition = { fadeIn(tween(500)) },
            exitTransition = { fadeOut(tween(300)) }
        ) {
            SplashScreen(
                onNavigateToOnboarding = {
                    navController.navigate(Screen.Onboarding.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToHome = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(
            Screen.Onboarding.route,
            enterTransition = { fadeIn(tween(400)) },
            exitTransition = { fadeOut(tween(300)) }
        ) {
            OnboardingScreen(
                onFinish = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Login.route) {
            LoginScreen(
                onNavigateToRegister = { navController.navigate(Screen.Register.route) },
                onLoginSuccess = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateToForgotPassword = { navController.navigate(Screen.ForgotPassword.route) }
            )
        }

        composable(Screen.Register.route) {
            RegisterScreen(
                onNavigateToLogin = { navController.popBackStack() },
                onRegisterSuccess = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Register.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.ForgotPassword.route) {
            ForgotPasswordScreen(onNavigateBack = { navController.popBackStack() })
        }

        // ── Main scaffold (bottom tabs) ──────────────────────────────────────────
        composable(
            Screen.Main.route,
            enterTransition = { fadeIn(tween(400)) },
            exitTransition = { fadeOut(tween(300)) }
        ) {
            MainScaffold(
                onNavigateToChat = { convId ->
                    navController.navigate(Screen.Chat.createRoute(convId))
                },
                onNavigateToUserProfile = { userId ->
                    navController.navigate(Screen.UserProfile.createRoute(userId))
                },
                onNavigateToGroupDetails = { groupId ->
                    navController.navigate(Screen.GroupDetails.createRoute(groupId))
                },
                onNavigateToGroupMap = { groupId ->
                    navController.navigate(Screen.GroupMap.createRoute(groupId))
                },
                onNavigateToCreateGroup = { navController.navigate(Screen.CreateGroup.route) },
                onNavigateToJoinGroup = { navController.navigate(Screen.JoinGroup.route) },
                onNavigateToFriendRequests = { navController.navigate(Screen.FriendRequests.route) },
                onNavigateToSearchPeople = { navController.navigate(Screen.SearchPeople.route) },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // ── Chat ─────────────────────────────────────────────────────────────────
        composable(
            route = Screen.Chat.route,
            arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId") ?: return@composable
            ChatScreen(
                conversationId = conversationId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToUserProfile = { userId ->
                    navController.navigate(Screen.UserProfile.createRoute(userId))
                },
                onNavigateToGroupMap = { groupId ->
                    navController.navigate(Screen.GroupMap.createRoute(groupId))
                }
            )
        }

        // ── People ───────────────────────────────────────────────────────────────
        composable(
            route = Screen.UserProfile.route,
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: return@composable
            UserProfileScreen(
                userId = userId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToChat = { convId ->
                    navController.navigate(Screen.Chat.createRoute(convId))
                }
            )
        }

        composable(Screen.FriendRequests.route) {
            FriendRequestsScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.SearchPeople.route) {
            SearchUsersScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToUserProfile = { userId ->
                    navController.navigate(Screen.UserProfile.createRoute(userId))
                },
                onNavigateToChat = { convId ->
                    navController.navigate(Screen.Chat.createRoute(convId))
                }
            )
        }

        // ── Groups ───────────────────────────────────────────────────────────────
        composable(
            route = Screen.GroupDetails.route,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: return@composable
            GroupDetailsScreen(
                groupId = groupId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToMap = {
                    navController.navigate(Screen.GroupMap.createRoute(groupId))
                },
                onNavigateToChat = { convId ->
                    navController.navigate(Screen.Chat.createRoute(convId))
                },
                onNavigateToEditGroup = {
                    navController.navigate(Screen.EditGroup.createRoute(groupId))
                }
            )
        }

        composable(
            route = Screen.GroupMap.route,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: return@composable
            MapScreen(
                groupId = groupId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.CreateGroup.route) {
            CreateGroupScreen(
                onNavigateBack = { navController.popBackStack() },
                onGroupCreated = { navController.popBackStack() }
            )
        }

        composable(Screen.JoinGroup.route) {
            JoinGroupScreen(
                onNavigateBack = { navController.popBackStack() },
                onGroupJoined = { groupId ->
                    navController.navigate(Screen.GroupMap.createRoute(groupId)) {
                        popUpTo(Screen.Main.route)
                    }
                }
            )
        }

        composable(
            route = Screen.EditGroup.route,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: return@composable
            EditGroupScreen(
                groupId = groupId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
