package com.ovi.where.presentation.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ovi.where.DeepLinkManager
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

/**
 * Root navigation graph.
 *
 * @param deepLinkRoute  Optional nav route parsed from the launching Intent
 *                       (notification tap while app was closed). When non-null
 *                       and the user is already authenticated, [SplashScreen]
 *                       navigates to this route instead of [Screen.Main].
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AppNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    startDestination: String = Screen.Splash.route,
    deepLinkRoute: String? = null
) {
    // ── Handle deep links delivered via onNewIntent (app already running) ─────
    LaunchedEffect(Unit) {
        snapshotFlow { DeepLinkManager.pending }.collect { route ->
            if (route != null) {
                DeepLinkManager.pending = null       // consume
                navigateToDeepLink(navController, route)
            }
        }
    }

    NavHost(
        navController    = navController,
        startDestination = startDestination,
        modifier         = modifier,
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
        // ── Splash ────────────────────────────────────────────────────────────
        composable(
            Screen.Splash.route,
            enterTransition = { fadeIn(tween(500)) },
            exitTransition  = { fadeOut(tween(300)) }
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
                    // User is already authenticated —
                    // go to the deep-link target if one was supplied,
                    // otherwise open the normal Main scaffold.
                    if (deepLinkRoute != null) {
                        navController.navigate(Screen.Main.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                        navigateToDeepLink(navController, deepLinkRoute)
                    } else {
                        navController.navigate(Screen.Main.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    }
                }
            )
        }

        // ── Onboarding ────────────────────────────────────────────────────────
        composable(
            Screen.Onboarding.route,
            enterTransition = { fadeIn(tween(400)) },
            exitTransition  = { fadeOut(tween(300)) }
        ) {
            OnboardingScreen(
                onFinish = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        // ── Login ─────────────────────────────────────────────────────────────
        composable(Screen.Login.route) {
            LoginScreen(
                onNavigateToRegister = { navController.navigate(Screen.Register.route) },
                onLoginSuccess = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                    // If a deep-link was pending before the user had to log in, resolve it now
                    if (deepLinkRoute != null) {
                        navigateToDeepLink(navController, deepLinkRoute)
                    }
                },
                onNavigateToForgotPassword = { navController.navigate(Screen.ForgotPassword.route) }
            )
        }

        // ── Register ──────────────────────────────────────────────────────────
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

        // ── Forgot Password ───────────────────────────────────────────────────
        composable(Screen.ForgotPassword.route) {
            ForgotPasswordScreen(onNavigateBack = { navController.popBackStack() })
        }

        // ── Main scaffold (bottom tabs) ───────────────────────────────────────
        composable(
            Screen.Main.route,
            enterTransition = { fadeIn(tween(400)) },
            exitTransition  = { fadeOut(tween(300)) }
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
                onNavigateToCreateGroup   = { navController.navigate(Screen.CreateGroup.route) },
                onNavigateToJoinGroup     = { navController.navigate(Screen.JoinGroup.route) },
                onNavigateToFriendRequests = { navController.navigate(Screen.FriendRequests.route) },
                onNavigateToSearchPeople  = { navController.navigate(Screen.SearchPeople.route) },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // ── Chat ──────────────────────────────────────────────────────────────
        composable(
            route     = Screen.Chat.route,
            arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
        ) { back ->
            val conversationId = back.arguments?.getString("conversationId") ?: return@composable
            ChatScreen(
                conversationId       = conversationId,
                onNavigateBack       = { navController.popBackStack() },
                onNavigateToUserProfile = { userId ->
                    navController.navigate(Screen.UserProfile.createRoute(userId))
                },
                onNavigateToGroupMap = { groupId ->
                    navController.navigate(Screen.GroupMap.createRoute(groupId))
                }
            )
        }

        // ── UserProfile ───────────────────────────────────────────────────────
        composable(
            route     = Screen.UserProfile.route,
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { back ->
            val userId = back.arguments?.getString("userId") ?: return@composable
            UserProfileScreen(
                userId         = userId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToChat = { convId ->
                    navController.navigate(Screen.Chat.createRoute(convId))
                }
            )
        }

        // ── Friend Requests ───────────────────────────────────────────────────
        composable(Screen.FriendRequests.route) {
            FriendRequestsScreen(onNavigateBack = { navController.popBackStack() })
        }

        // ── Search People ─────────────────────────────────────────────────────
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

        // ── Group Details ─────────────────────────────────────────────────────
        composable(
            route     = Screen.GroupDetails.route,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { back ->
            val groupId = back.arguments?.getString("groupId") ?: return@composable
            GroupDetailsScreen(
                groupId             = groupId,
                onNavigateBack      = { navController.popBackStack() },
                onNavigateToMap     = { navController.navigate(Screen.GroupMap.createRoute(groupId)) },
                onNavigateToChat    = { convId -> navController.navigate(Screen.Chat.createRoute(convId)) },
                onNavigateToEditGroup = { navController.navigate(Screen.EditGroup.createRoute(groupId)) }
            )
        }

        // ── Group Map ─────────────────────────────────────────────────────────
        composable(
            route     = Screen.GroupMap.route,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { back ->
            val groupId = back.arguments?.getString("groupId") ?: return@composable
            MapScreen(
                groupId        = groupId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ── Create Group ──────────────────────────────────────────────────────
        composable(Screen.CreateGroup.route) {
            CreateGroupScreen(
                onNavigateBack  = { navController.popBackStack() },
                onGroupCreated  = { navController.popBackStack() }
            )
        }

        // ── Join Group ────────────────────────────────────────────────────────
        composable(Screen.JoinGroup.route) {
            JoinGroupScreen(
                onNavigateBack = { navController.popBackStack() },
                onGroupJoined  = { groupId ->
                    navController.navigate(Screen.GroupMap.createRoute(groupId)) {
                        popUpTo(Screen.Main.route)
                    }
                }
            )
        }

        // ── Edit Group ────────────────────────────────────────────────────────
        composable(
            route     = Screen.EditGroup.route,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { back ->
            val groupId = back.arguments?.getString("groupId") ?: return@composable
            EditGroupScreen(
                groupId        = groupId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

// ── Deep-link router ──────────────────────────────────────────────────────────

/**
 * Parses a plain route string (e.g. "chat/CONV_ID", "friend_requests") and
 * navigates to the corresponding destination in the back stack.
 *
 * Supported routes (matching FCM payload values):
 *  • chat/{conversationId}
 *  • friend_requests
 *  • user_profile/{userId}
 *  • group_map/{groupId}
 *  • group_details/{groupId}
 */
private fun navigateToDeepLink(navController: NavHostController, route: String) {
    val segments = route.split("/")
    when {
        // chat/CONV_ID
        segments.size == 2 && segments[0] == "chat" -> {
            navController.navigate(Screen.Chat.createRoute(segments[1]))
        }
        // friend_requests
        route == "friend_requests" -> {
            navController.navigate(Screen.FriendRequests.route)
        }
        // user_profile/USER_ID
        segments.size == 2 && segments[0] == "user_profile" -> {
            navController.navigate(Screen.UserProfile.createRoute(segments[1]))
        }
        // group_map/GROUP_ID
        segments.size == 2 && segments[0] == "group_map" -> {
            navController.navigate(Screen.GroupMap.createRoute(segments[1]))
        }
        // group_details/GROUP_ID
        segments.size == 2 && segments[0] == "group_details" -> {
            navController.navigate(Screen.GroupDetails.createRoute(segments[1]))
        }
        else -> {
            // Unknown route — no-op; app stays on Main
        }
    }
}
