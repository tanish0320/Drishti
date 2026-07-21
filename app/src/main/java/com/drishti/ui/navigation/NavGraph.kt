package com.drishti.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.drishti.permissions.PermissionManager
import com.drishti.ui.AboutViewModel
import com.drishti.ui.HomeViewModel
import com.drishti.ui.PermissionViewModel
import com.drishti.ui.SettingsViewModel
import com.drishti.ui.SplashViewModel
import com.drishti.ui.screens.AboutScreen
import com.drishti.ui.screens.HomeScreen
import com.drishti.ui.screens.PermissionsScreen
import com.drishti.ui.screens.SettingsScreen
import com.drishti.ui.screens.SplashScreen

@Composable
fun DrishtiNavGraph(
    navController: NavHostController,
    permissionManager: PermissionManager,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Destinations.SPLASH,
        modifier = modifier
    ) {
        composable(Destinations.SPLASH) {
            val viewModel: SplashViewModel = hiltViewModel()
            SplashScreen(
                viewModel = viewModel,
                onStartClick = {
                    val hasPermissions = permissionManager.checkPermissions()
                    if (hasPermissions) {
                        navController.navigate(Destinations.HOME) {
                            popUpTo(Destinations.SPLASH) { inclusive = true }
                        }
                    } else {
                        navController.navigate(Destinations.PERMISSIONS) {
                            popUpTo(Destinations.SPLASH) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(Destinations.PERMISSIONS) {
            val viewModel: PermissionViewModel = hiltViewModel()
            PermissionsScreen(
                viewModel = viewModel,
                onSettingsClick = {
                    navController.navigate(Destinations.SETTINGS)
                },
                onPermissionsGranted = {
                    navController.navigate(Destinations.HOME) {
                        popUpTo(Destinations.PERMISSIONS) { inclusive = true }
                    }
                }
            )
        }

        composable(Destinations.HOME) {
            val viewModel: HomeViewModel = hiltViewModel()
            HomeScreen(
                viewModel = viewModel,
                onSettingsClick = {
                    navController.navigate(Destinations.SETTINGS)
                }
            )
        }

        composable(Destinations.SETTINGS) {
            val viewModel: SettingsViewModel = hiltViewModel()
            SettingsScreen(
                viewModel = viewModel,
                onBackClick = {
                    navController.popBackStack()
                },
                onPermissionsClick = {
                    navController.navigate(Destinations.PERMISSIONS)
                },
                onAboutClick = {
                    navController.navigate(Destinations.ABOUT)
                }
            )
        }

        composable(Destinations.ABOUT) {
            val viewModel: AboutViewModel = hiltViewModel()
            AboutScreen(
                viewModel = viewModel,
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}
