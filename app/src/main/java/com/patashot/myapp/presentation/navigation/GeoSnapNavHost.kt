package com.patashot.myapp.presentation.navigation

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
import android.app.Activity
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.patashot.myapp.R
import com.patashot.myapp.domain.model.GeoMedia
import com.patashot.myapp.presentation.camera.CameraMode
import com.patashot.myapp.presentation.camera.CameraScreen
import com.patashot.myapp.presentation.gallery.EditMediaScreen
import com.patashot.myapp.presentation.gallery.GalleryScreen
import com.patashot.myapp.presentation.gallery.MediaViewerScreen
import com.patashot.myapp.presentation.home.HomeScreen
import com.patashot.myapp.presentation.main.MainViewModel
import com.patashot.myapp.presentation.onboarding.LanguageScreen
import com.patashot.myapp.presentation.onboarding.PermissionsScreen
import com.patashot.myapp.presentation.settings.HelpAndSupportScreen
import com.patashot.myapp.presentation.settings.PrivacyPolicyScreen
import com.patashot.myapp.presentation.settings.SettingsScreen
import com.patashot.myapp.presentation.settings.TermsAndConditionsScreen
import com.patashot.myapp.presentation.templates.TemplatesScreen

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
                if (allGranted) Destination.Home.route else Destination.Permissions.route
            } else {
                Destination.Permissions.route
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
        composable(Destination.Permissions.route) {
            PermissionsScreen(
                onAllPermissionsGranted = {
                    if (isAlreadyOnboarded) {
                        navController.navigateToHome()
                    } else {
                        navController.navigateToLanguage()
                    }
                }
            )
        }

        composable(Destination.Language.route) {
            val activity = LocalContext.current as? Activity
            LanguageScreen(
                onBack = navController::popBackStack,
                onCompleted = {
                    navController.navigateToHome()
                    // Recreate so the just-chosen language is applied app-wide.
                    activity?.recreate()
                }
            )
        }

        composable(Destination.Home.route) {
            HomeScreen(
                onTakePhoto = { navController.navigateToCamera(CameraMode.PHOTO) },
                onTakeVideo = { navController.navigateToCamera(CameraMode.VIDEO) },
                onQuickShare = { navController.navigateToCamera(CameraMode.QUICK_SHARE) },
                onOpenGallery = navController::navigateToGallery,
                onOpenSettings = navController::navigateToSettings
            )
        }
        
        composable(Destination.Settings.route) {
            SettingsScreen(
                onBack = navController::popBackStack,
                onLanguageClick = navController::navigateToLanguage,
                onTemplatesClick = navController::navigateToTemplates,
                onTermsClick = navController::navigateToTermsAndConditions,
                onPrivacyPolicyClick = navController::navigateToPrivacyPolicy,
                onHelpSupportClick = navController::navigateToHelpAndSupport
            )
        }

        composable(Destination.PrivacyPolicy.route) {
            PrivacyPolicyScreen(onBack = navController::popBackStack)
        }

        composable(Destination.TermsAndConditions.route) {
            TermsAndConditionsScreen(
                onBack = navController::popBackStack,
                onPrivacyPolicyClick = navController::navigateToPrivacyPolicy
            )
        }

        composable(Destination.HelpAndSupport.route) {
            HelpAndSupportScreen(onBack = navController::popBackStack)
        }

        composable(Destination.Templates.route) {
            TemplatesScreen(onBack = navController::popBackStack)
        }

        composable(Destination.Gallery.route) {
            GalleryScreen(
                onBack = navController::popBackStack,
                onItemClick = navController::navigateToViewer
            )
        }

        composable(
            route = Destination.GalleryViewer.ROUTE_PATTERN,
            arguments = listOf(
                navArgument(Destination.GalleryViewer.ARGUMENT_INDEX) { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val index = backStackEntry.arguments
                ?.getInt(Destination.GalleryViewer.ARGUMENT_INDEX)
                ?.coerceAtLeast(0)
                ?: 0
            MediaViewerScreen(
                initialIndex = index,
                onBack = navController::popBackStack,
                onEdit = { media ->
                    navController.navigateToEdit(media.id)
                }
            )
        }

        composable(
            route = Destination.EditMedia.ROUTE_PATTERN,
            arguments = listOf(
                navArgument(Destination.EditMedia.ARGUMENT_ID) { type = NavType.LongType }
            )
        ) {
            EditMediaScreen(onBack = navController::popBackStack)
        }

        composable(
            route = Destination.Camera.ROUTE_PATTERN,
            arguments = listOf(
                navArgument(Destination.Camera.ARGUMENT_MODE) { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val mode = CameraMode.fromRoute(
                backStackEntry.arguments?.getString(Destination.Camera.ARGUMENT_MODE)
            ) ?: CameraMode.PHOTO
            CameraScreen(
                mode = mode,
                onBack = navController::popBackStack,
                onGallery = navController::navigateToGallery,
                onTemplatesClick = navController::navigateToTemplates
            )
        }
    }
}