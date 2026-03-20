package com.example.servicedeskapk.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.servicedeskapk.ui.screens.*

/**
 * Маршруты экранов приложения
 */
sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Register : Screen("register")
    data object ChangePassword : Screen("change_password/{forced}") {
        fun createRoute(forced: Boolean = false) = "change_password/$forced"
    }
    data object Main : Screen("main")
    data object TicketDetail : Screen("ticket_detail/{ticketId}") {
        fun createRoute(ticketId: Int) = "ticket_detail/$ticketId"
    }
    data object CreateTicket : Screen("create_ticket")
    data object Admin : Screen("admin")
    data object Settings : Screen("settings")
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    startDestination: String
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // === Auth ===
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = { mustChangePassword ->
                    if (mustChangePassword) {
                        navController.navigate(Screen.ChangePassword.createRoute(forced = true)) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    } else {
                        navController.navigate(Screen.Main.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    }
                },
                onNavigateToRegister = { navController.navigate(Screen.Register.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(Screen.Register.route) {
            RegisterScreen(
                onRegisterSuccess = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.ChangePassword.route,
            arguments = listOf(navArgument("forced") { type = NavType.BoolType; defaultValue = false })
        ) { backStackEntry ->
            val forced = backStackEntry.arguments?.getBoolean("forced") ?: false
            ChangePasswordScreen(
                onPasswordChanged = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateBack = if (forced) null else {{ navController.popBackStack() }}
            )
        }

        // === Main Screen (Bottom Navigation) ===
        composable(Screen.Main.route) {
            MainScreen(
                onNavigateToTicket = { ticketId ->
                    navController.navigate(Screen.TicketDetail.createRoute(ticketId))
                },
                onNavigateToCreateTicket = {
                    navController.navigate(Screen.CreateTicket.route)
                },
                onNavigateToAdmin = {
                    navController.navigate(Screen.Admin.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToChangePassword = {
                    navController.navigate(Screen.ChangePassword.createRoute(forced = false))
                },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // === Full-screen destinations ===
        composable(
            route = Screen.TicketDetail.route,
            arguments = listOf(navArgument("ticketId") { type = NavType.IntType })
        ) { backStackEntry ->
            val ticketId = backStackEntry.arguments?.getInt("ticketId") ?: return@composable
            TicketDetailScreen(
                ticketId = ticketId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToTicket = { id ->
                    navController.navigate(Screen.TicketDetail.createRoute(id))
                }
            )
        }

        composable(Screen.CreateTicket.route) {
            CreateTicketScreen(
                onTicketCreated = { ticketId ->
                    navController.popBackStack()
                    if (ticketId != null) {
                        navController.navigate(Screen.TicketDetail.createRoute(ticketId))
                    }
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Admin.route) {
            AdminScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
