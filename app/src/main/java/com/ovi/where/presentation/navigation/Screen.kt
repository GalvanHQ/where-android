package com.ovi.where.presentation.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Login : Screen("login")
    object Register : Screen("register")
    object Home : Screen("home")
    object Map : Screen("map/{groupId}") {
        fun createRoute(groupId: String) = "map/$groupId"
    }
    object GroupDetails : Screen("group_details/{groupId}") {
        fun createRoute(groupId: String) = "group_details/$groupId"
    }
    object CreateGroup : Screen("create_group")
    object JoinGroup : Screen("join_group")
    object Settings : Screen("settings")
}
