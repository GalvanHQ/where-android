package com.ovi.where.presentation.navigation

sealed class Screen(val route: String) {
    // Auth flow
    object Onboarding : Screen("onboarding")
    object Login : Screen("login")
    object Register : Screen("register")
    object ForgotPassword : Screen("forgot_password")
    object EmailVerification : Screen("email_verification")
    object CompleteProfile : Screen("complete_profile")

    // Main scaffold (bottom tabs)
    object Main : Screen("main")

    // Profile sub-screens
    object EditProfile : Screen("edit_profile")
    object Settings : Screen("settings")

    // Chat routes
    object Chat : Screen("chat/{conversationId}") {
        fun createRoute(conversationId: String) = "chat/$conversationId"
    }

    // People routes
    object UserProfile : Screen("user_profile/{userId}") {
        fun createRoute(userId: String) = "user_profile/$userId"
    }
    object FriendRequests : Screen("friend_requests")
    object SearchPeople : Screen("search_people")

    // Group routes
    object GroupDetails : Screen("group_details/{groupId}") {
        fun createRoute(groupId: String) = "group_details/$groupId"
    }
    object GroupMap : Screen("group_map/{groupId}") {
        fun createRoute(groupId: String) = "group_map/$groupId"
    }
    object CreateGroup : Screen("create_group")
    object JoinGroup : Screen("join_group")
    object EditGroup : Screen("edit_group/{groupId}") {
        fun createRoute(groupId: String) = "edit_group/$groupId"
    }
}
