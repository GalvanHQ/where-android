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

    // ── Bottom tab destinations ──────────────────────────────────────────────
    // Siblings of every other top-level route in [AppNavGraph]. The bottom bar
    // is shown only when the current destination is one of these four routes.

    @Serializable
    data object MapTab : Screen("tab_map")

    @Serializable
    data object ChatsTab : Screen("tab_chats")

    @Serializable
    data object PeopleTab : Screen("tab_people")

    @Serializable
    data object ProfileTab : Screen("tab_profile")

    // ── Profile sub-screens ───────────────────────────────────────────────────

    @Serializable
    data object EditProfile : Screen("edit_profile")

    @Serializable
    data object MyProfile : Screen("my_profile")

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

    @Serializable
    data class ConversationInfo(val conversationId: String) : Screen("conversation_info/${conversationId}") {
        companion object {
            const val ROUTE = "conversation_info/{conversationId}"
            fun createRoute(conversationId: String) = "conversation_info/$conversationId"
        }
    }

    @Serializable
    data class GroupInfo(val groupId: String) : Screen("group_info/${groupId}") {
        companion object {
            const val ROUTE = "group_info/{groupId}"
            fun createRoute(groupId: String) = "group_info/$groupId"
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

    // ── Search (full-screen) ──────────────────────────────────────────────

    @Serializable
    data class Search(val source: String) : Screen("search/${source}") {
        companion object {
            const val ROUTE = "search/{source}"
            fun createRoute(source: String) = "search/$source"
        }
    }

    // ── Group routes ──────────────────────────────────────────────────────────

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
    data object NewMessage : Screen("new_message")

    @Serializable
    data class AddGroupMembers(val groupId: String) : Screen("add_group_members/${groupId}") {
        companion object {
            const val ROUTE = "add_group_members/{groupId}"
            fun createRoute(groupId: String) = "add_group_members/$groupId"
        }
    }

    @Serializable
    data class GroupMembers(val groupId: String) : Screen("group_members/${groupId}") {
        companion object {
            const val ROUTE = "group_members/{groupId}"
            fun createRoute(groupId: String) = "group_members/$groupId"
        }
    }

    @Serializable
    data class Nicknames(val conversationId: String) : Screen("nicknames/${conversationId}") {
        companion object {
            const val ROUTE = "nicknames/{conversationId}"
            fun createRoute(conversationId: String) = "nicknames/$conversationId"
        }
    }

    @Serializable
    data class MediaGallery(val conversationId: String) : Screen("media_gallery/${conversationId}") {
        companion object {
            const val ROUTE = "media_gallery/{conversationId}"
            fun createRoute(conversationId: String) = "media_gallery/$conversationId"
        }
    }

    // ── Gatekeeper (auth state resolver) ──────────────────────────────────────

    @Serializable
    data object Gatekeeper : Screen("gatekeeper")
}
