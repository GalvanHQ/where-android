package com.ovi.where.presentation.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ovi.where.DeepLinkManager
import com.ovi.where.presentation.auth.complete.CompleteProfileScreen
import com.ovi.where.presentation.auth.forgotpassword.ForgotPasswordScreen
import com.ovi.where.presentation.auth.login.LoginScreen
import com.ovi.where.presentation.auth.signup.SignUpScreen
import com.ovi.where.presentation.auth.verification.EmailVerificationScreen
import com.ovi.where.presentation.chat.ChatScreen
import com.ovi.where.presentation.group.JoinGroupScreen
import com.ovi.where.presentation.group.create.CreateGroupScreen
import com.ovi.where.presentation.group.details.GroupDetailsScreen
import com.ovi.where.presentation.group.edit.EditGroupScreen
import com.ovi.where.presentation.map.MapScreen
import com.ovi.where.presentation.navigation.gatekeeper.AuthGatekeeperViewModel
import com.ovi.where.presentation.onboarding.OnboardingScreen
import com.ovi.where.presentation.people.FriendRequestsScreen
import com.ovi.where.presentation.people.SearchUsersScreen
import com.ovi.where.presentation.people.UserProfileScreen
import com.ovi.where.presentation.profile.edit.EditProfileScreen
import com.ovi.where.presentation.settings.SettingsScreen

private const val NAV_ANIM_DURATION = 300
private const val GATEKEEPER_ROUTE = "gatekeeper"

/**
 * Root navigation graph.
 *
 * Replaces the legacy custom SplashScreen with a lightweight gatekeeper composable
 * that resolves auth state using [SplashViewModel] and routes accordingly:
 *   - Not onboarded          → Onboarding
 *   - Not logged in          → Login
 *   - Email not verified     → EmailVerification
 *   - Profile not complete   → CompleteProfile
 *   - All good               → Main (with optional deep-link)
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AppNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    startDestination: String = GATEKEEPER_ROUTE,
    deepLinkRoute: String? = null
) {
    // ── Handle deep links delivered via onNewIntent (app already running) ─────
    LaunchedEffect(Unit) {
        snapshotFlow { DeepLinkManager.pending }.collect { route ->
            if (route != null) {
                DeepLinkManager.pending = null
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
        // ── Gatekeeper (replaces custom SplashScreen) ─────────────────────────
        composable(
            GATEKEEPER_ROUTE,
            enterTransition = { fadeIn(tween(0)) },
            exitTransition  = { fadeOut(tween(300)) }
        ) {
            val viewModel: AuthGatekeeperViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsState()

            LaunchedEffect(Unit) { viewModel.resolve() }

            if (uiState.isLoading) {
                // Minimal loading indicator during auth check
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LaunchedEffect(uiState) {
                    val target = when {
                        !uiState.onboardingComplete -> Screen.Onboarding.route
                        !uiState.isLoggedIn         -> Screen.Login.route
                        !uiState.isEmailVerified    -> Screen.EmailVerification.route
                        !uiState.isProfileComplete  -> Screen.CompleteProfile.route
                        else                        -> Screen.Main.route
                    }
                    navController.navigate(target) {
                        popUpTo(GATEKEEPER_ROUTE) { inclusive = true }
                    }
                    if (target == Screen.Main.route && deepLinkRoute != null) {
                        navigateToDeepLink(navController, deepLinkRoute)
                    }
                }
            }
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
                onNavigateToSignUp = { navController.navigate(Screen.SignUp.route) },
                onLoginSuccess = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                    if (deepLinkRoute != null) {
                        navigateToDeepLink(navController, deepLinkRoute)
                    }
                },
                onNavigateToEmailVerification = {
                    navController.navigate(Screen.EmailVerification.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateToCompleteProfile = {
                    navController.navigate(Screen.CompleteProfile.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateToForgotPassword = { navController.navigate(Screen.ForgotPassword.route) }
            )
        }

        // ── Sign Up ───────────────────────────────────────────────────────────
        composable(Screen.SignUp.route) {
            SignUpScreen(
                onNavigateToLogin = { navController.popBackStack() },
                onNavigateToEmailVerification = {
                    navController.navigate(Screen.EmailVerification.route) {
                        popUpTo(Screen.SignUp.route) { inclusive = true }
                    }
                }
            )
        }

        // ── Forgot Password ───────────────────────────────────────────────────
        composable(Screen.ForgotPassword.route) {
            ForgotPasswordScreen(onNavigateBack = { navController.popBackStack() })
        }

        // ── Email Verification (blocking gate) ────────────────────────────────
        composable(Screen.EmailVerification.route) {
            EmailVerificationScreen(
                onVerified = {
                    // Route through gatekeeper to check profile completeness
                    navController.navigate(GATEKEEPER_ROUTE) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onSignOut = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // ── Complete Profile (blocking gate for Google sign-in) ────────────────
        composable(Screen.CompleteProfile.route) {
            CompleteProfileScreen(
                onProfileComplete = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.CompleteProfile.route) { inclusive = true }
                    }
                }
            )
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
                onNavigateToEditProfile   = { navController.navigate(Screen.EditProfile.route) },
                onNavigateToSettings      = { navController.navigate(Screen.Settings.route) },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // ── Edit Profile ──────────────────────────────────────────────────────
        composable(Screen.EditProfile.route) {
            EditProfileScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ── Settings ──────────────────────────────────────────────────────────
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onSignOut = {
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
 */
private fun navigateToDeepLink(navController: NavHostController, route: String) {
    val segments = route.split("/")
    when {
        segments.size == 2 && segments[0] == "chat" -> {
            navController.navigate(Screen.Chat.createRoute(segments[1]))
        }
        route == "friend_requests" -> {
            navController.navigate(Screen.FriendRequests.route)
        }
        segments.size == 2 && segments[0] == "user_profile" -> {
            navController.navigate(Screen.UserProfile.createRoute(segments[1]))
        }
        segments.size == 2 && segments[0] == "group_map" -> {
            navController.navigate(Screen.GroupMap.createRoute(segments[1]))
        }
        segments.size == 2 && segments[0] == "group_details" -> {
            navController.navigate(Screen.GroupDetails.createRoute(segments[1]))
        }
        else -> { /* Unknown route — no-op */ }
    }
}
