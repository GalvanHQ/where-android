package com.ovi.where.presentation.navigation

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.ovi.where.DeepLinkManager
import com.ovi.where.core.crash.ActiveScreenTracker
import com.ovi.where.presentation.auth.complete.CompleteProfileScreen
import com.ovi.where.presentation.auth.forgotpassword.ForgotPasswordScreen
import com.ovi.where.presentation.auth.login.LoginScreen
import com.ovi.where.presentation.auth.signup.SignUpScreen
import com.ovi.where.presentation.auth.verification.EmailVerificationScreen
import com.ovi.where.presentation.chat.ChatScreen
import com.ovi.where.presentation.chat.ChatsScreen
import com.ovi.where.presentation.chat.ConversationInfoScreen
import com.ovi.where.presentation.chat.GroupInfoScreen
import com.ovi.where.presentation.chat.MediaGalleryScreen
import com.ovi.where.presentation.chat.NewMessageScreen
import com.ovi.where.presentation.chat.NicknamesScreen
import com.ovi.where.presentation.group.create.CreateGroupScreen
import com.ovi.where.presentation.group.join.JoinGroupScreen
import com.ovi.where.presentation.map.GlobalMapScreen
import com.ovi.where.presentation.map.MapNavBarHeight
import com.ovi.where.presentation.navigation.gatekeeper.AuthGatekeeperViewModel
import com.ovi.where.presentation.onboarding.OnboardingScreen
import com.ovi.where.presentation.people.FriendRequestsScreen
import com.ovi.where.presentation.people.PeopleScreen
import com.ovi.where.presentation.people.UserProfileScreen
import com.ovi.where.presentation.profile.ProfileScreen
import com.ovi.where.presentation.profile.edit.EditProfileScreen
import com.ovi.where.presentation.search.SearchScreen
import com.ovi.where.presentation.settings.AboutScreen
import com.ovi.where.presentation.settings.AppearanceScreen
import com.ovi.where.presentation.settings.DataStorageScreen
import com.ovi.where.presentation.settings.DevelopersScreen
import com.ovi.where.presentation.settings.HelpScreen
import com.ovi.where.presentation.settings.NotificationPreferencesScreen
import com.ovi.where.presentation.settings.PrivacyPolicyScreen
import com.ovi.where.presentation.settings.PrivacyScreen
import com.ovi.where.presentation.settings.SecurityScreen
import com.ovi.where.presentation.settings.SettingsScreen
import com.ovi.where.presentation.settings.TermsOfServiceScreen

private const val NAV_ANIM_DURATION = 300
private const val TAB_FADE_DURATION = 180

/** Routes that show the bottom tab bar. Single source of truth. */
internal val BottomTabRoutes = setOf(
    Screen.MapTab.route,
    Screen.ChatsTab.route,
    Screen.PeopleTab.route,
    Screen.ProfileTab.route
)

/**
 * Routes shown before the user is authenticated / has a complete
 * profile. The persistent map backdrop is hidden on these so we don't
 * leak chrome from the post-auth experience into onboarding/login.
 */
private val PreAuthRoutes = setOf(
    Screen.Gatekeeper.route,
    Screen.Onboarding.route,
    Screen.Login.route,
    Screen.SignUp.route,
    Screen.ForgotPassword.route,
    Screen.EmailVerification.route,
    Screen.CompleteProfile.route
)

/**
 * True when both endpoints of a navigation are tab routes — i.e. the user is
 * switching between bottom tabs. Tabs are siblings (not a stack), so a quick
 * cross-fade is the right feel; the slide animation is reserved for actual
 * forward/back stack transitions like Chat ↔ UserProfile.
 */
private fun isTabSwitch(fromRoute: String?, toRoute: String?): Boolean =
    fromRoute in BottomTabRoutes && toRoute in BottomTabRoutes

/**
 * Root navigation graph.
 *
 * Single NavController for the whole app — including the bottom tabs. The
 * tabs are siblings of every other top-level destination, which means any
 * call to `navController.navigate(Screen.MapTab.route)` from anywhere works,
 * and Compose Navigation handles back-stack + state restoration via
 * `popUpTo + saveState + restoreState`. Same pattern as Now in Android.
 *
 * The bottom bar is rendered by [WhereBottomBar] in [MainScaffold] and shows
 * itself only when the current destination is in [BottomTabRoutes].
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

    // ── Bottom-bar visibility derived from the current top-level destination ──
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in BottomTabRoutes

    // ── Padding the tab content needs to leave for the overlaid bottom bar ────
    val systemBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val totalNavBarHeight = MapNavBarHeight + systemBottomInset
    val nonMapContentPadding = PaddingValues(bottom = totalNavBarHeight)

    // ── Persistent map backdrop ─────────────────────────────────────────────
    // GlobalMapScreen is hoisted out of the NavHost so the underlying
    // GoogleMap (a native MapView with its own GL surface and tile cache)
    // never gets destroyed and re-created on every tab switch. Without
    // this, navigating Map → Chats → Map flashes a blank/white frame for
    // ~250 ms while the new MapView spins up its renderer.
    //
    // Layering (back to front):
    //   1. background color
    //   2. GlobalMapScreen — always composed once the user is past auth.
    //      Its `isMapTabActive` flag mirrors the current route so it can
    //      stop emitting "map foregrounded" signals (FCM suppression,
    //      camera autozooms) when the user is on another tab.
    //   3. NavHost — covers the map with each non-map destination's
    //      solid background. The MapTab destination renders an empty
    //      Spacer so the map below shows through.
    //   4. Bottom bar overlay.
    //
    // First visit to MapTab still pays the one-time MapView init cost
    // (unavoidable cold-start). Every subsequent tab switch is flash-free.
    val isMapTabActive = currentRoute == Screen.MapTab.route
    val mapBackdropVisible = currentRoute != null && currentRoute !in PreAuthRoutes

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // ── Persistent map backdrop ───────────────────────────────────────────
        // Always composed once the user is post-auth. When the user is
        // not on the map tab, the NavHost above paints a solid background
        // which covers it. Switching back to the map tab simply removes
        // that cover — the GoogleMap stays warm in memory (matches the
        // Google Maps app pattern: no re-init, no GL surface flash).
        if (mapBackdropVisible) {
            GlobalMapScreen(
                isActiveTab = isMapTabActive,
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
                onNavigateToAddFriends = {
                    navController.navigate(Screen.Search.createRoute("people")) {
                        launchSingleTop = true
                    }
                },
                onNavigateToNotifications = {
                    navController.navigate(Screen.Notifications.route) {
                        launchSingleTop = true
                    }
                }
            )
        }

        NavHost(
            navController    = navController,
            startDestination = startDestination,
            modifier         = Modifier
                .fillMaxSize()
                // On the map tab the NavHost is transparent so the
                // persistent map backdrop above shows through. On every
                // other destination the NavHost paints a solid theme
                // background, fully covering the map. This is what makes
                // the persistent backdrop pattern work without leaks.
                .then(
                    if (isMapTabActive) {
                        Modifier
                    } else {
                        Modifier.background(MaterialTheme.colorScheme.background)
                    }
                ),
            enterTransition = {
                if (isTabSwitch(initialState.destination.route, targetState.destination.route)) {
                    // Tab ↔ tab: tabs are siblings, not a stack — a quick fade
                    // feels instant without being abrupt.
                    fadeIn(animationSpec = tween(durationMillis = TAB_FADE_DURATION))
                } else {
                    slideInHorizontally(
                        initialOffsetX = { fullWidth -> fullWidth },
                        animationSpec = tween(durationMillis = NAV_ANIM_DURATION, easing = EaseInOut)
                    ) + fadeIn(animationSpec = tween(durationMillis = NAV_ANIM_DURATION, easing = EaseInOut))
                }
            },
            exitTransition = {
                if (isTabSwitch(initialState.destination.route, targetState.destination.route)) {
                    fadeOut(animationSpec = tween(durationMillis = TAB_FADE_DURATION))
                } else {
                    slideOutHorizontally(
                        targetOffsetX = { fullWidth -> -fullWidth / 4 },
                        animationSpec = tween(durationMillis = NAV_ANIM_DURATION, easing = EaseInOut)
                    ) + fadeOut(animationSpec = tween(durationMillis = NAV_ANIM_DURATION, easing = EaseInOut))
                }
            },
            popEnterTransition = {
                if (isTabSwitch(initialState.destination.route, targetState.destination.route)) {
                    fadeIn(animationSpec = tween(durationMillis = TAB_FADE_DURATION))
                } else {
                    slideInHorizontally(
                        initialOffsetX = { fullWidth -> -fullWidth / 4 },
                        animationSpec = tween(durationMillis = NAV_ANIM_DURATION, easing = EaseInOut)
                    ) + fadeIn(animationSpec = tween(durationMillis = NAV_ANIM_DURATION, easing = EaseInOut))
                }
            },
            popExitTransition = {
                if (isTabSwitch(initialState.destination.route, targetState.destination.route)) {
                    fadeOut(animationSpec = tween(durationMillis = TAB_FADE_DURATION))
                } else {
                    slideOutHorizontally(
                        targetOffsetX = { fullWidth -> fullWidth },
                        animationSpec = tween(durationMillis = NAV_ANIM_DURATION, easing = EaseInOut)
                    ) + fadeOut(animationSpec = tween(durationMillis = NAV_ANIM_DURATION, easing = EaseInOut))
                }
            }
        ) {
            // ── Gatekeeper (auth resolver) ────────────────────────────────────
            composable(
                Screen.Gatekeeper.route,
                enterTransition = { fadeIn(tween(0)) },
                exitTransition  = { fadeOut(tween(300)) }
            ) {
                val viewModel: AuthGatekeeperViewModel = hiltViewModel()
                val uiState by viewModel.uiState.collectAsState()

                LaunchedEffect(Unit) { viewModel.resolve() }

                if (uiState.isLoading) {
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
                            else                        -> Screen.MapTab.route
                        }
                        navController.navigate(target) {
                            popUpTo(Screen.Gatekeeper.route) { inclusive = true }
                            launchSingleTop = true
                        }
                        if (target == Screen.MapTab.route && deepLinkRoute != null) {
                            navigateToDeepLink(navController, deepLinkRoute)
                        }
                    }
                }
            }

            // ── Onboarding ────────────────────────────────────────────────────
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

            // ── Login ─────────────────────────────────────────────────────────
            composable(Screen.Login.route) {
                LoginScreen(
                    onNavigateToSignUp = {
                        navController.navigate(Screen.SignUp.route) { launchSingleTop = true }
                    },
                    onLoginSuccess = {
                        navController.navigate(Screen.MapTab.route) {
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

            composable(Screen.ForgotPassword.route) {
                ForgotPasswordScreen(onNavigateBack = { navController.popBackStack() })
            }

            composable(Screen.EmailVerification.route) {
                EmailVerificationScreen(
                    onVerified = {
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

            composable(Screen.CompleteProfile.route) {
                CompleteProfileScreen(
                    onProfileComplete = {
                        navController.navigate(Screen.MapTab.route) {
                            popUpTo(Screen.CompleteProfile.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

            // ── Bottom-tab destinations ───────────────────────────────────────
            // Each is a sibling of every other top-level destination. When the
            // user taps a tab, navigate(...) with `popUpTo(MapTab) { saveState }`
            // and `restoreState = true` — Compose Navigation handles tab state
            // preservation for free.

            composable(Screen.MapTab.route) {
                // Empty placeholder — the actual GlobalMapScreen lives
                // outside the NavHost as a persistent backdrop (see the
                // surrounding Box). This destination exists only so the
                // bottom bar's selected-state derivation, deep-link
                // routing, and back-stack restoration logic continue to
                // see a "real" Map route. Rendering a transparent
                // Spacer lets the persistent map below show through.
                androidx.compose.foundation.layout.Spacer(
                    modifier = Modifier.fillMaxSize()
                )
            }

            composable(Screen.ChatsTab.route) {
                ChatsScreen(
                    contentPadding = nonMapContentPadding,
                    onNavigateToChat = { convId ->
                        navController.navigate(Screen.Chat.createRoute(convId)) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToNewMessage = {
                        navController.navigate(Screen.NewMessage.route) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToSearch = {
                        navController.navigate(Screen.Search.createRoute("chats")) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(Screen.PeopleTab.route) {
                PeopleScreen(
                    contentPadding = nonMapContentPadding,
                    onNavigateToUserProfile = { userId ->
                        navController.navigate(Screen.UserProfile.createRoute(userId)) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToChat = { convId ->
                        navController.navigate(Screen.Chat.createRoute(convId)) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToFriendRequests = {
                        navController.navigate(Screen.FriendRequests.route) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToBlockedUsers = {
                        navController.navigate(Screen.BlockedUsers.route) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToSearch = {
                        navController.navigate(Screen.Search.createRoute("people")) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(Screen.ProfileTab.route) {
                ProfileScreen(
                    contentPadding = nonMapContentPadding,
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
                    onNavigateToMessages = {
                        navController.navigateToTab(Screen.ChatsTab.route)
                    },
                    onNavigateToLocationSharing = {
                        navController.navigateToTab(Screen.MapTab.route)
                    }
                )
            }

            // ── Edit Profile ──────────────────────────────────────────────────
            composable(Screen.EditProfile.route) {
                EditProfileScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.MyProfile.route) {
                Box(modifier = Modifier.fillMaxSize()) {
                    ProfileScreen(
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
                        onNavigateToMessages = { navController.popBackStack() },
                        onNavigateToLocationSharing = { navController.popBackStack() },
                        contentPadding = PaddingValues(top = 48.dp)
                    )
                    androidx.compose.material3.IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .statusBarsPadding()
                            .padding(start = 4.dp, top = 4.dp)
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

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
                    onNavigateToPermissions = {
                        navController.navigate(Screen.Permissions.route) {
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
                    },
                    onNavigateToDevelopers = {
                        navController.navigate(Screen.Developers.route) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(Screen.NotificationPreferences.route) {
                NotificationPreferencesScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(Screen.Appearance.route) {
                AppearanceScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(Screen.DataStorage.route) {
                DataStorageScreen(onNavigateBack = { navController.popBackStack() })
            }
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
            composable(Screen.Privacy.route) {
                PrivacyScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(Screen.Help.route) {
                HelpScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(Screen.About.route) {
                AboutScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToTerms = {
                        navController.navigate(Screen.TermsOfService.route) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToPrivacyPolicy = {
                        navController.navigate(Screen.PrivacyPolicy.route) {
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable(Screen.TermsOfService.route) {
                TermsOfServiceScreen(onNavigateBack = { navController.popBackStack() })
            }

            composable(Screen.PrivacyPolicy.route) {
                PrivacyPolicyScreen(onNavigateBack = { navController.popBackStack() })
            }

            composable(Screen.Developers.route) {
                DevelopersScreen(onNavigateBack = { navController.popBackStack() })
            }

            composable(Screen.Notifications.route) {
                com.ovi.where.presentation.notification.NotificationsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Permissions.route) {
                com.ovi.where.presentation.settings.PermissionsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // ── Chat ──────────────────────────────────────────────────────────
            composable(
                route     = Screen.Chat.ROUTE,
                arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
            ) { back ->
                val conversationId = back.arguments?.getString("conversationId") ?: return@composable
                val searchTrigger = back.savedStateHandle.get<Boolean>("activate_search") ?: false
                if (searchTrigger) {
                    back.savedStateHandle["activate_search"] = false
                }

                ChatScreen(
                    conversationId       = conversationId,
                    startInSearchMode    = searchTrigger,
                    onNavigateBack       = { navController.popBackStack() },
                    onNavigateToUserProfile = { userId ->
                        navController.navigate(Screen.UserProfile.createRoute(userId)) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToGroupInfo = { groupId ->
                        navController.navigate(Screen.GroupInfo.createRoute(groupId)) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToGroupMap = { groupId ->
                        // Map is the home — pop back to it instead of
                        // pushing a fresh map on top of chat. Stack
                        // becomes `[MapTab]`, back exits the app.
                        navController.navigateToTab(Screen.MapTab.route)
                    },
                    onNavigateToGlobalMap = {
                        navController.navigateToTab(Screen.MapTab.route)
                    },
                    onNavigateToConversationInfo = { convId ->
                        navController.navigate(Screen.ConversationInfo.createRoute(convId)) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToChat = { convId ->
                        navController.navigate(Screen.Chat.createRoute(convId)) {
                            popUpTo(Screen.Chat.ROUTE) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(
                route     = Screen.ConversationInfo.ROUTE,
                arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
            ) { back ->
                val conversationId = back.arguments?.getString("conversationId") ?: return@composable
                ConversationInfoScreen(
                    onNavigateBack         = { navController.popBackStack() },
                    onNavigateToMediaGallery = {
                        navController.navigate(Screen.MediaGallery.createRoute(conversationId)) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToUserProfile = { userId ->
                        navController.navigate(Screen.UserProfile.createRoute(userId)) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToChat = {
                        navController.previousBackStackEntry?.savedStateHandle?.set("activate_search", true)
                        navController.popBackStack()
                    },
                    onNavigateToNicknames = {
                        navController.navigate(Screen.Nicknames.createRoute(conversationId)) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(
                route     = Screen.Nicknames.ROUTE,
                arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
            ) {
                NicknamesScreen(onNavigateBack = { navController.popBackStack() })
            }

            composable(
                route     = Screen.AddGroupMembers.ROUTE,
                arguments = listOf(navArgument("groupId") { type = NavType.StringType })
            ) { back ->
                val groupId = back.arguments?.getString("groupId") ?: return@composable
                com.ovi.where.presentation.group.AddGroupMembersScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route     = Screen.GroupMembers.ROUTE,
                arguments = listOf(navArgument("groupId") { type = NavType.StringType })
            ) { back ->
                val groupId = back.arguments?.getString("groupId") ?: return@composable
                com.ovi.where.presentation.group.GroupMembersScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToAddMembers = {
                        navController.navigate(Screen.AddGroupMembers.createRoute(groupId)) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToUserProfile = { userId ->
                        navController.navigate(Screen.UserProfile.createRoute(userId)) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToProfile = {
                        navController.navigate(Screen.MyProfile.route) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(
                route     = "group_nicknames/{groupId}",
                arguments = listOf(navArgument("groupId") { type = NavType.StringType })
            ) { back ->
                val groupId = back.arguments?.getString("groupId") ?: return@composable
                com.ovi.where.presentation.group.GroupNicknamesScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route     = Screen.GroupInfo.ROUTE,
                arguments = listOf(navArgument("groupId") { type = NavType.StringType })
            ) { back ->
                val groupId = back.arguments?.getString("groupId") ?: return@composable
                GroupInfoScreen(
                    onNavigateBack         = { navController.popBackStack() },
                    onNavigateToMediaGallery = {
                        navController.navigate(Screen.MediaGallery.createRoute(groupId)) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToAddMembers = {
                        navController.navigate(Screen.AddGroupMembers.createRoute(groupId)) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToMembers = {
                        navController.navigate(Screen.GroupMembers.createRoute(groupId)) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToNicknames = {
                        navController.navigate("group_nicknames/$groupId") {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToSearch = {
                        navController.previousBackStackEntry?.savedStateHandle?.set("activate_search", true)
                        navController.popBackStack()
                    },
                    onNavigateToGroupMap = {
                        // Map is the persistent home — pop back to it
                        // (saveState preserves any chain of group-info
                        // screens we leave behind). Back from MapTab
                        // exits, matching the rest of the app.
                        navController.navigateToTab(Screen.MapTab.route)
                    }
                )
            }

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

            composable(Screen.FriendRequests.route) {
                FriendRequestsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToUserProfile = { userId ->
                        navController.navigate(Screen.UserProfile.createRoute(userId)) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(Screen.BlockedUsers.route) {
                com.ovi.where.presentation.people.BlockedUsersScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

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

            // ── Group Map (legacy redirect) ───────────────────────────────────
            composable(
                route     = Screen.GroupMap.ROUTE,
                arguments = listOf(navArgument("groupId") { type = NavType.StringType })
            ) {
                LaunchedEffect(Unit) {
                    navController.navigateToTab(Screen.MapTab.route)
                }
            }

            composable(Screen.CreateGroup.route) {
                CreateGroupScreen(
                    onNavigateBack  = { navController.popBackStack() },
                    // After "Open Group Chat", land in the chat for the new
                    // group and drop the create flow off the back stack so
                    // back from chat returns to where the user started
                    // (People / Map), not the success card.
                    onGroupCreated  = { conversationId ->
                        navController.navigate(Screen.Chat.createRoute(conversationId)) {
                            popUpTo(Screen.CreateGroup.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(Screen.JoinGroup.route) {
                JoinGroupScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onGroupJoined  = {
                        navController.navigateToTab(Screen.MapTab.route)
                    }
                )
            }

            composable(Screen.NewMessage.route) {
                NewMessageScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToNewChat = {
                        navController.navigate(Screen.Search.createRoute("chats")) {
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
                    }
                )
            }

            composable(
                route     = Screen.MediaGallery.ROUTE,
                arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
            ) {
                MediaGalleryScreen(onNavigateBack = { navController.popBackStack() })
            }
        }

        // ── Bottom bar overlay ───────────────────────────────────────────────
        // Only renders on tab destinations. Centralising visibility here means
        // every other top-level screen (Chat, Settings, etc.) automatically
        // hides it without any per-screen plumbing.
        if (showBottomBar) {
            WhereBottomBar(
                currentRoute = currentRoute,
                onTabClick = { route -> navController.navigateToTab(route) },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

/**
 * Standard "select bottom tab" navigation, matching the NowInAndroid pattern.
 *
 * Anchors `popUpTo` at the graph's start destination so each tab's saved state
 * is keyed by its own destination, not folded under another tab. With
 * `saveState = true` + `restoreState = true`, Compose Navigation handles
 * tab state preservation (scroll position, ViewModel state) automatically.
 */
/**
 * Navigates to a top-level destination keeping **MapTab as the persistent
 * root** of the back stack. Any tab nav (or any nav that wants to "go home
 * to the map") unwinds to MapTab and pushes the target on top of it.
 *
 * Mental model: the map is the home. Back from anywhere should unwind
 * toward the map; back from the map exits the app. This pairs with the
 * persistent map backdrop (TASK 11) — the map is always alive underneath
 * the NavHost, and the back stack mirrors that by always pinning MapTab
 * at the bottom.
 *
 * Behaviour by case:
 *  • Already on MapTab, calling with MapTab → no-op (singleTop).
 *  • On AnyTab/Screen, calling with MapTab → pops everything above MapTab,
 *    leaving stack `[MapTab]`. Back from MapTab exits.
 *  • On MapTab, calling with ChatsTab → pushes ChatsTab. Stack
 *    `[MapTab, ChatsTab]`. Back returns to MapTab.
 *  • On `[MapTab, ChatsTab, Chat]`, calling with PeopleTab → pops above
 *    MapTab (saveState saves ChatsTab+Chat), pushes PeopleTab.
 *    Stack `[MapTab, PeopleTab]`. Back returns to MapTab.
 *
 * `saveState` + `restoreState` preserve scroll / VM state for tabs we've
 * popped, so re-tapping ChatsTab restores its previous state instead of
 * cold-starting it.
 */
internal fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(Screen.MapTab.route) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

// ── Deep-link router ──────────────────────────────────────────────────────────

/**
 * Resolves a route string into a NavController navigation. Routes are the
 * lower-cased, slash-separated form used everywhere notifications, the
 * `where://` URI scheme, and the in-app inbox flow:
 *
 *   chat/{conversationId}            → Chat screen
 *   conversation_info/{id}           → Conversation info
 *   group_info/{groupId}             → Group info
 *   group_details/{groupId}          → (alias of group_info, kept for back-compat)
 *   group_map/{groupId}              → Group map
 *   group_members/{groupId}          → Group members list
 *   user_profile/{userId}            → Other user's profile
 *   media_gallery/{conversationId}   → Media gallery
 *   nicknames/{conversationId}       → Group nicknames
 *   friend_requests                  → Friend requests
 *   notifications                    → In-app notifications inbox
 *   permissions                      → Settings → Permissions
 *   notification_preferences         → Settings → Notification preferences
 *   tab_map / tab_chats / tab_people / tab_profile  → bottom-tab switch
 *
 * Unrecognized or empty routes fall through to the Notifications inbox so
 * the tap *always* takes the user somewhere meaningful — better UX than a
 * silent dismissal, which felt like a broken notification.
 */
internal fun navigateToDeepLink(navController: NavHostController, route: String) {
    val trimmed = route.trim().trim('/')
    if (trimmed.isEmpty()) {
        navController.fallbackToNotifications()
        return
    }

    val segments = trimmed.split("/").filter { it.isNotBlank() }
    val head = segments.firstOrNull() ?: run {
        navController.fallbackToNotifications()
        return
    }

    when {
        // ── Chat surfaces ───────────────────────────────────────────────────
        head == "chat" && segments.getOrNull(1)?.isNotBlank() == true -> {
            navController.navigate(Screen.Chat.createRoute(segments[1])) {
                launchSingleTop = true
            }
        }
        head == "conversation_info" && segments.getOrNull(1)?.isNotBlank() == true -> {
            navController.navigate(Screen.ConversationInfo.createRoute(segments[1])) {
                launchSingleTop = true
            }
        }
        head == "media_gallery" && segments.getOrNull(1)?.isNotBlank() == true -> {
            navController.navigate(Screen.MediaGallery.createRoute(segments[1])) {
                launchSingleTop = true
            }
        }
        head == "nicknames" && segments.getOrNull(1)?.isNotBlank() == true -> {
            navController.navigate(Screen.Nicknames.createRoute(segments[1])) {
                launchSingleTop = true
            }
        }

        // ── Group surfaces ──────────────────────────────────────────────────
        // group_details is an older alias kept around for back-compat with
        // notifications emitted before group_info became canonical.
        (head == "group_info" || head == "group_details") &&
            segments.getOrNull(1)?.isNotBlank() == true -> {
            navController.navigate(Screen.GroupInfo.createRoute(segments[1])) {
                launchSingleTop = true
            }
        }
        head == "group_map" && segments.getOrNull(1)?.isNotBlank() == true -> {
            navController.navigate(Screen.GroupMap.createRoute(segments[1])) {
                launchSingleTop = true
            }
        }
        head == "group_members" && segments.getOrNull(1)?.isNotBlank() == true -> {
            navController.navigate(Screen.GroupMembers.createRoute(segments[1])) {
                launchSingleTop = true
            }
        }

        // ── People surfaces ─────────────────────────────────────────────────
        head == "user_profile" && segments.getOrNull(1)?.isNotBlank() == true -> {
            navController.navigate(Screen.UserProfile.createRoute(segments[1])) {
                launchSingleTop = true
            }
        }
        trimmed == "friend_requests" -> {
            navController.navigate(Screen.FriendRequests.route) { launchSingleTop = true }
        }

        // ── Settings + inbox ───────────────────────────────────────────────
        trimmed == "notifications" -> {
            navController.navigate(Screen.Notifications.route) { launchSingleTop = true }
        }
        trimmed == "permissions" -> {
            navController.navigate(Screen.Permissions.route) { launchSingleTop = true }
        }
        trimmed == "notification_preferences" -> {
            navController.navigate(Screen.NotificationPreferences.route) { launchSingleTop = true }
        }

        // ── Bottom tabs ─────────────────────────────────────────────────────
        trimmed == Screen.MapTab.route -> navController.navigateToTab(Screen.MapTab.route)
        trimmed == Screen.ChatsTab.route -> navController.navigateToTab(Screen.ChatsTab.route)
        trimmed == Screen.PeopleTab.route -> navController.navigateToTab(Screen.PeopleTab.route)
        trimmed == Screen.ProfileTab.route -> navController.navigateToTab(Screen.ProfileTab.route)

        // ── Fallback ───────────────────────────────────────────────────────
        // Always land somewhere useful instead of dropping the tap.
        else -> navController.fallbackToNotifications()
    }
}

/**
 * Fallback target when a notification carries an unrecognized or empty
 * deep-link route. The Notifications inbox is the single screen guaranteed
 * to have context for the missed event, so it's the safest landing pad.
 */
private fun NavHostController.fallbackToNotifications() {
    navigate(Screen.Notifications.route) {
        launchSingleTop = true
    }
}
