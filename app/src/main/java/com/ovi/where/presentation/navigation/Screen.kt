package com.ovi.where.presentation.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation destinations for the Where app.
 *
 * All 19 destinations use @Serializable annotations for compile-time type safety
 * with Navigation Compose 2.8+. Route arguments (conversationId, userId, groupId)
 * are type-safe parameters on data classes.
 *
 * The [route] property is retained for backward compatibility with string-based
 * NavHost composable() calls during migration.
 */
@Serializable
sealed class Screen(val route: String) {

    // ── Auth flow ─────────────────────────────────────────────────────────────

    @Serializable
    data object Onboarding : Screen("onboarding")

    @Serializable
    data object Login : Screen("login")

    @Serializable
    data object SignUp : Screen("register")

    @Serializable
    data object ForgotPassword : Screen("forgot_password")

    @Serializable
    data object EmailVerification : Screen("email_verification")

    @Serializable
    data object CompleteProfile : Screen("complete_profile")

    // ── Main scaffold (bottom tabs) ───────────────────────────────────────────

    @Serializable
    data object Main : Screen("main")

    // ── Profile sub-screens ───────────────────────────────────────────────────

    @Serializable
    data object EditProfile : Screen("edit_profile")

    @Serializable
    data object Settings : Screen("settings")

    @Serializable
    data object DataStorage : Screen("data_storage")

    @Serializable
    data object Appearance : Screen("appearance")

    // ── Settings sub-screens ──────────────────────────────────────────────────

    @Serializable
    data object NotificationPreferences : Screen("notification_preferences")

    @Serializable
    data object Security : Screen("security")

    @Serializable
    data object Privacy : Screen("privacy")

    @Serializable
    data object Help : Screen("help")

    @Serializable
    data object About : Screen("about")

    // ── Chat routes ───────────────────────────────────────────────────────────

    @Serializable
    data class Chat(val conversationId: String) : Screen("chat/${conversationId}") {
        companion object {
            const val ROUTE = "chat/{conversationId}"
            fun createRoute(conversationId: String) = "chat/$conversationId"
        }
    }

    // ── People routes ─────────────────────────────────────────────────────────

    @Serializable
    data class UserProfile(val userId: String) : Screen("user_profile/${userId}") {
        companion object {
            const val ROUTE = "user_profile/{userId}"
            fun createRoute(userId: String) = "user_profile/$userId"
        }
    }

    @Serializable
    data object FriendRequests : Screen("friend_requests")

    @Serializable
    data object SearchPeople : Screen("search_people")

    // ── Group routes ──────────────────────────────────────────────────────────

    @Serializable
    data class GroupDetails(val groupId: String) : Screen("group_details/${groupId}") {
        companion object {
            const val ROUTE = "group_details/{groupId}"
            fun createRoute(groupId: String) = "group_details/$groupId"
        }
    }

    @Serializable
    data class GroupMap(val groupId: String) : Screen("group_map/${groupId}") {
        companion object {
            const val ROUTE = "group_map/{groupId}"
            fun createRoute(groupId: String) = "group_map/$groupId"
        }
    }

    @Serializable
    data object CreateGroup : Screen("create_group")

    @Serializable
    data object JoinGroup : Screen("join_group")

    @Serializable
    data class EditGroup(val groupId: String) : Screen("edit_group/${groupId}") {
        companion object {
            const val ROUTE = "edit_group/{groupId}"
            fun createRoute(groupId: String) = "edit_group/$groupId"
        }
    }

    // ── Gatekeeper (auth state resolver) ──────────────────────────────────────

    @Serializable
    data object Gatekeeper : Screen("gatekeeper")
}
