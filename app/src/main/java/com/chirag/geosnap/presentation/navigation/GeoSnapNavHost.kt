package com.chirag.geosnap.presentation.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.chirag.geosnap.R
import com.chirag.geosnap.presentation.camera.CameraMode
import com.chirag.geosnap.presentation.camera.CameraScreen
import com.chirag.geosnap.presentation.gallery.GalleryScreen
import com.chirag.geosnap.presentation.home.HomeScreen
import com.chirag.geosnap.presentation.main.MainViewModel
import com.chirag.geosnap.presentation.onboarding.LanguageScreen
import com.chirag.geosnap.presentation.onboarding.PermissionsScreen

/**
 * App root: observes the persisted onboarding state asynchronously and only
 * renders the navigation graph once we know where to start, so there is no
 * flash of the wrong screen and no navigation race.
 */
@Composable
fun GeoSnapAppRoot(viewModel: MainViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var resolvedStartDestination by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(uiState.loading, uiState.onboardingCompleted) {
        if (resolvedStartDestination == null && !uiState.loading) {
            resolvedStartDestination = if (uiState.onboardingCompleted == true) {
                val requiredPermissions = listOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
                val allGranted = requiredPermissions.all {
                    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                }
                if (allGranted) Routes.HOME else Routes.PERMISSIONS
            } else {
                Routes.PERMISSIONS
            }
        }
    }

    val startDestination = resolvedStartDestination
    if (startDestination == null) {
        LoadingScreen()
    } else {
        GeoSnapNavHost(startDestination = startDestination, isAlreadyOnboarded = uiState.onboardingCompleted == true)
    }
}

@Composable
internal fun LoadingScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Text(
            text = stringResource(R.string.loading),
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

@Composable
internal fun GeoSnapNavHost(startDestination: String, isAlreadyOnboarded: Boolean = false) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = startDestination, modifier = Modifier.padding(PaddingValues(0.dp))) {
        composable(Routes.PERMISSIONS) {
            PermissionsScreen(
                onAllPermissionsGranted = {
                    if (isAlreadyOnboarded) {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.PERMISSIONS) { inclusive = true }
                            launchSingleTop = true
                        }
                    } else {
                        navController.navigate(Routes.LANGUAGE) {
                            launchSingleTop = true
                        }
                    }
                }
            )
        }

        composable(Routes.LANGUAGE) {
            LanguageScreen(
                onBack = navController::popBackStack,
                onCompleted = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.PERMISSIONS) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                onTakePhoto = {
                    navController.navigate(Routes.cameraRoute(CameraMode.PHOTO)) {
                        launchSingleTop = true
                    }
                },
                onTakeVideo = {
                    navController.navigate(Routes.cameraRoute(CameraMode.VIDEO)) {
                        launchSingleTop = true
                    }
                },
                onQuickShare = {
                    navController.navigate(Routes.cameraRoute(CameraMode.QUICK_SHARE)) {
                        launchSingleTop = true
                    }
                },
                onOpenGallery = {
                    navController.navigate(Routes.GALLERY) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Routes.GALLERY) {
            GalleryScreen(onBack = navController::popBackStack)
        }

        composable(
            route = "camera/{${Routes.CAMERA_ARGUMENT}}",
            arguments = listOf(
                navArgument(Routes.CAMERA_ARGUMENT) { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val mode = CameraMode.fromRoute(
                backStackEntry.arguments?.getString(Routes.CAMERA_ARGUMENT)
            ) ?: CameraMode.PHOTO
            CameraScreen(mode = mode, onBack = navController::popBackStack)
        }
    }
}