package com.vamora.vano.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vamora.vano.data.VanoViewModel
import com.vamora.vano.ui.theme.VanoBackground
import com.vamora.vano.ui.theme.VanoGreen
import com.vamora.vano.ui.screens.ChatListScreen
import com.vamora.vano.ui.screens.ChatScreen
import com.vamora.vano.ui.screens.DownloadScreen
import com.vamora.vano.ui.screens.SettingsScreen

sealed class Route(val route: String) {
    object Download : Route("download")
    object Chats : Route("chats")
    object Chat : Route("chat/{chatId}") {
        fun create(chatId: String) = "chat/$chatId"
    }
    object Settings : Route("settings")
}

@Composable
fun AppNavHost(viewModel: VanoViewModel) {
    val navController = rememberNavController()
    val available by viewModel.modelManager.available.collectAsState()
    val chatsLoaded by viewModel.chatsLoaded.collectAsState()

    if (!chatsLoaded) {
        // Dark loading to avoid white flashbang before model check
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            CircularProgressIndicator(color = VanoGreen)
        }
        return
    }

    val hasModel = available.isNotEmpty()
    val start = if (hasModel) Route.Chats.route else Route.Download.route
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = currentRoute == Route.Chats.route || currentRoute == Route.Settings.route

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    NavigationBarItem(
                        selected = currentRoute == Route.Chats.route,
                        onClick = {
                            navController.navigate(Route.Chats.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Default.ChatBubble, contentDescription = null) },
                        label = { Text("Chats") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = VanoBackground,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = VanoGreen,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    NavigationBarItem(
                        selected = currentRoute == Route.Settings.route,
                        onClick = {
                            navController.navigate(Route.Settings.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text("Settings") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = VanoBackground,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = VanoGreen,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = start,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {
            composable(Route.Download.route) {
                DownloadScreen(viewModel = viewModel, onContinue = {
                    navController.navigate(Route.Chats.route) {
                        popUpTo(Route.Download.route) { inclusive = true }
                    }
                })
            }
            composable(Route.Chats.route) {
                ChatListScreen(
                    viewModel = viewModel,
                    onChatClick = { id -> navController.navigate(Route.Chat.create(id)) },
                    onSettingsClick = { navController.navigate(Route.Settings.route) }
                )
            }
            composable(Route.Chat.route) { backStack ->
                val chatId = backStack.arguments?.getString("chatId") ?: return@composable
                ChatScreen(chatId = chatId, viewModel = viewModel, onBack = { navController.popBackStack() })
            }
            composable(Route.Settings.route) {
                SettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }
        }
    }

    LaunchedEffect(hasModel) {
        val current = navController.currentDestination?.route
        if (!hasModel && current != Route.Download.route) {
            navController.navigate(Route.Download.route) {
                popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
            }
        }
    }
}
