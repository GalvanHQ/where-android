package com.ovi.where.presentation.navigation

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.ovi.where.core.crash.ActiveScreenTracker
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
import com.ovi.where.presentation.people.UserProfileScreen
import com.ovi.where.presentation.profile.edit.EditProfileScreen
import com.ovi.where.presentation.search.SearchScreen
import com.ovi.where.presentation.settings.AppearanceScreen
import com.ovi.where.presentation.settings.DataStorageScreen
import com.ovi.where.presentation.settings.NotificationPreferencesScreen
import com.ovi.where.presentation.settings.SecurityScreen
import com.ovi.where.presentation.settings.SettingsScreen
import com.ovi.where.presentation.settings.PrivacyScreen
import com.ovi.where.presentation.settings.HelpScreen
import com.ovi.where.presentation.settings.AboutScreen

private const val NAV_ANIM_DURATION = 300

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
    startDestination: String = Screen.Gatekeeper.route,
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

    // ── Track active screen route for crash reporting ─────────────────────────
    LaunchedEffect(navController) {
        navController.currentBackStackEntryFlow.collect { entry ->
            val route = entry.destination.route ?: "unknown"
            ActiveScreenTracker.setActiveRoute(route)
        }
    }

    NavHost(
        navController    = navController,
        startDestination = startDestination,
        modifier         = modifier.background(MaterialTheme.colorScheme.background),
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(durationMillis = NAV_ANIM_DURATION, easing = EaseInOut)
            ) + fadeIn(animationSpec = tween(durationMillis = NAV_ANIM_DURATION, easing = EaseInOut))
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> -fullWidth / 4 },
                animationSpec = tween(durationMillis = NAV_ANIM_DURATION, easing = EaseInOut)
            ) + fadeOut(animationSpec = tween(durationMillis = NAV_ANIM_DURATION, easing = EaseInOut))
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { fullWidth -> -fullWidth / 4 },
                animationSpec = tween(durationMillis = NAV_ANIM_DURATION, easing = EaseInOut)
            ) + fadeIn(animationSpec = tween(durationMillis = NAV_ANIM_DURATION, easing = EaseInOut))
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(durationMillis = NAV_ANIM_DURATION, easing = EaseInOut)
            ) + fadeOut(animationSpec = tween(durationMillis = NAV_ANIM_DURATION, easing = EaseInOut))
        }
    ) {
        // ── Gatekeeper (replaces custom SplashScreen) ─────────────────────────
        composable(
            Screen.Gatekeeper.route,
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
                        popUpTo(Screen.Gatekeeper.route) { inclusive = true }
                        launchSingleTop = true
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
                        launchSingleTop = true
                    }
                }
            )
        }

        // ── Login ─────────────────────────────────────────────────────────────
        composable(Screen.Login.route) {
            LoginScreen(
                onNavigateToSignUp = {
                    navController.navigate(Screen.SignUp.route) {
                        launchSingleTop = true
                    }
                },
                onLoginSuccess = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                        launchSingleTop = true
                    }
                    if (deepLinkRoute != null) {
                        navigateToDeepLink(navController, deepLinkRoute)
                    }
                },
                onNavigateToEmailVerification = {
                    navController.navigate(Screen.EmailVerification.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onNavigateToCompleteProfile = {
                    navController.navigate(Screen.CompleteProfile.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onNavigateToForgotPassword = {
                    navController.navigate(Screen.ForgotPassword.route) {
                        launchSingleTop = true
                    }
                }
            )
        }

        // ── Sign Up ───────────────────────────────────────────────────────────
        composable(Screen.SignUp.route) {
            SignUpScreen(
                onNavigateToLogin = { navController.popBackStack() },
                onNavigateToEmailVerification = {
                    navController.navigate(Screen.EmailVerification.route) {
                        popUpTo(Screen.SignUp.route) { inclusive = true }
                        launchSingleTop = true
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
                    navController.navigate(Screen.Gatekeeper.route) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onSignOut = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
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
                        launchSingleTop = true
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
                    navController.navigate(Screen.Chat.createRoute(convId)) {
                        launchSingleTop = true
                    }
                },
                onNavigateToUserProfile = { userId ->
                    navController.navigate(Screen.UserProfile.createRoute(userId)) {
                        launchSingleTop = true
                    }
                },
                onNavigateToGroupDetails = { groupId ->
                    navController.navigate(Screen.GroupDetails.createRoute(groupId)) {
                        launchSingleTop = true
                    }
                },
                onNavigateToGroupMap = { groupId ->
                    navController.navigate(Screen.GroupMap.createRoute(groupId)) {
                        launchSingleTop = true
                    }
                },
                onNavigateToCreateGroup = {
                    navController.navigate(Screen.CreateGroup.route) {
                        launchSingleTop = true
                    }
                },
                onNavigateToJoinGroup = {
                    navController.navigate(Screen.JoinGroup.route) {
                        launchSingleTop = true
                    }
                },
                onNavigateToFriendRequests = {
                    navController.navigate(Screen.FriendRequests.route) {
                        launchSingleTop = true
                    }
                },
                onNavigateToSearch = { source ->
                    navController.navigate(Screen.Search.createRoute(source)) {
                        launchSingleTop = true
                    }
                },
                onNavigateToEditProfile = {
                    navController.navigate(Screen.EditProfile.route) {
                        launchSingleTop = true
                    }
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route) {
                        launchSingleTop = true
                    }
                },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
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
                        launchSingleTop = true
                    }
                },
                onNavigateToNotificationPreferences = {
                    navController.navigate(Screen.NotificationPreferences.route) {
                        launchSingleTop = true
                    }
                },
                onNavigateToAppearance = {
                    navController.navigate(Screen.Appearance.route) {
                        launchSingleTop = true
                    }
                },
                onNavigateToDataStorage = {
                    navController.navigate(Screen.DataStorage.route) {
                        launchSingleTop = true
                    }
                },
                onNavigateToSecurity = {
                    navController.navigate(Screen.Security.route) {
                        launchSingleTop = true
                    }
                },
                onNavigateToPrivacy = {
                    navController.navigate(Screen.Privacy.route) {
                        launchSingleTop = true
                    }
                },
                onNavigateToHelp = {
                    navController.navigate(Screen.Help.route) {
                        launchSingleTop = true
                    }
                },
                onNavigateToAbout = {
                    navController.navigate(Screen.About.route) {
                        launchSingleTop = true
                    }
                }
            )
        }

        // ── Notification Preferences ──────────────────────────────────────────
        composable(Screen.NotificationPreferences.route) {
            NotificationPreferencesScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ── Appearance ────────────────────────────────────────────────────────
        composable(Screen.Appearance.route) {
            AppearanceScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ── Data & Storage ────────────────────────────────────────────────────
        composable(Screen.DataStorage.route) {
            DataStorageScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ── Security ──────────────────────────────────────────────────────────
        composable(Screen.Security.route) {
            SecurityScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToOnboarding = {
                    navController.navigate(Screen.Onboarding.route) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        // ── Privacy ───────────────────────────────────────────────────────────
        composable(Screen.Privacy.route) {
            PrivacyScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ── Help & Support ────────────────────────────────────────────────────
        composable(Screen.Help.route) {
            HelpScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ── About ─────────────────────────────────────────────────────────────
        composable(Screen.About.route) {
            AboutScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ── Chat ──────────────────────────────────────────────────────────────
        composable(
            route     = Screen.Chat.ROUTE,
            arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
        ) { back ->
            val conversationId = back.arguments?.getString("conversationId") ?: return@composable
            ChatScreen(
                conversationId       = conversationId,
                onNavigateBack       = { navController.popBackStack() },
                onNavigateToUserProfile = { userId ->
                    navController.navigate(Screen.UserProfile.createRoute(userId)) {
                        launchSingleTop = true
                    }
                },
                onNavigateToGroupInfo = { groupId ->
                    navController.navigate(Screen.GroupDetails.createRoute(groupId)) {
                        launchSingleTop = true
                    }
                },
                onNavigateToGroupMap = { groupId ->
                    navController.navigate(Screen.GroupMap.createRoute(groupId)) {
                        launchSingleTop = true
                    }
                }
            )
        }

        // ── UserProfile ───────────────────────────────────────────────────────
        composable(
            route     = Screen.UserProfile.ROUTE,
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { back ->
            val userId = back.arguments?.getString("userId") ?: return@composable
            UserProfileScreen(
                userId         = userId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToChat = { convId ->
                    navController.navigate(Screen.Chat.createRoute(convId)) {
                        launchSingleTop = true
                    }
                }
            )
        }

        // ── Friend Requests ───────────────────────────────────────────────────
        composable(Screen.FriendRequests.route) {
            FriendRequestsScreen(onNavigateBack = { navController.popBackStack() })
        }

        // ── Full-Screen Search (People / Chats) ──────────────────────────────
        composable(
            route = Screen.Search.ROUTE,
            arguments = listOf(navArgument("source") { type = NavType.StringType })
        ) { backStackEntry ->
            val source = backStackEntry.arguments?.getString("source") ?: "people"
            SearchScreen(
                source = source,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToUserProfile = { userId ->
                    navController.navigate(Screen.UserProfile.createRoute(userId)) {
                        launchSingleTop = true
                    }
                },
                onNavigateToChat = { conversationId ->
                    navController.navigate(Screen.Chat.createRoute(conversationId)) {
                        launchSingleTop = true
                    }
                }
            )
        }

        // ── Group Details ─────────────────────────────────────────────────────
        composable(
            route     = Screen.GroupDetails.ROUTE,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { back ->
            val groupId = back.arguments?.getString("groupId") ?: return@composable
            GroupDetailsScreen(
                groupId             = groupId,
                onNavigateBack      = { navController.popBackStack() },
                onNavigateToMap     = {
                    navController.navigate(Screen.GroupMap.createRoute(groupId)) {
                        launchSingleTop = true
                    }
                },
                onNavigateToChat    = { convId ->
                    navController.navigate(Screen.Chat.createRoute(convId)) {
                        launchSingleTop = true
                    }
                },
                onNavigateToEditGroup = {
                    navController.navigate(Screen.EditGroup.createRoute(groupId)) {
                        launchSingleTop = true
                    }
                }
            )
        }

        // ── Group Map ─────────────────────────────────────────────────────────
        composable(
            route     = Screen.GroupMap.ROUTE,
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
                        launchSingleTop = true
                    }
                }
            )
        }

        // ── Edit Group ────────────────────────────────────────────────────────
        composable(
            route     = Screen.EditGroup.ROUTE,
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
 * Registered deep link URI patterns (scheme "where://"):
 *   - chat/{id}           → Chat screen
 *   - user_profile/{id}   → UserProfile screen
 *   - group_details/{id}  → GroupDetails screen
 *   - group_map/{id}      → GroupMap screen
 *   - friend_requests     → FriendRequests screen
 *
 * **Authentication gatekeeper**: This function is only called AFTER the
 * [AuthGatekeeperViewModel] resolves successfully and the user reaches the
 * Main screen. Deep links received before auth completes are held in
 * [deepLinkRoute] parameter or [DeepLinkManager.pending] and processed
 * only after navigation to Main.
 *
 * **Unrecognized URIs**: Any route that does not match the registered patterns
 * is silently discarded (no crash, no navigation change).
 */
internal fun navigateToDeepLink(navController: NavHostController, route: String) {
    val segments = route.split("/")
    when {
        segments.size == 2 && segments[0] == "chat" && segments[1].isNotBlank() -> {
            navController.navigate(Screen.Chat.createRoute(segments[1])) {
                launchSingleTop = true
            }
        }
        route == "friend_requests" -> {
            navController.navigate(Screen.FriendRequests.route) {
                launchSingleTop = true
            }
        }
        segments.size == 2 && segments[0] == "user_profile" && segments[1].isNotBlank() -> {
            navController.navigate(Screen.UserProfile.createRoute(segments[1])) {
                launchSingleTop = true
            }
        }
        segments.size == 2 && segments[0] == "group_map" && segments[1].isNotBlank() -> {
            navController.navigate(Screen.GroupMap.createRoute(segments[1])) {
                launchSingleTop = true
            }
        }
        segments.size == 2 && segments[0] == "group_details" && segments[1].isNotBlank() -> {
            navController.navigate(Screen.GroupDetails.createRoute(segments[1])) {
                launchSingleTop = true
            }
        }
        else -> {
            // Unrecognized deep link URI — discard silently without crashing.
            // Other navigation operations continue to function normally.
        }
    }
}
