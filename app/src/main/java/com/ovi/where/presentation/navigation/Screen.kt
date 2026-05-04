package com.ovi.where.presentation.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Onboarding : Screen("onboarding")
    object Login : Screen("login")
    object Register : Screen("register")
    object ForgotPassword : Screen("forgot_password")
    object Main : Screen("main")
    object Home : Screen("home")
    object Map : Screen("map/{groupId}") {
        fun createRoute(groupId: String) = "map/$groupId"
    }
    object GroupDetails : Screen("group_details/{groupId}") {
        fun createRoute(groupId: String) = "group_details/$groupId"
    }
    object CreateGroup : Screen("create_group")
    object JoinGroup : Screen("join_group")
    object EditGroup : Screen("edit_group/{groupId}") {
        fun createRoute(groupId: String) = "edit_group/$groupId"
    }
    object Settings : Screen("settings")
    object Profile : Screen("profile")
}
