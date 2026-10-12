package com.patashot.myapp.presentation.navigation

import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder
import com.patashot.myapp.presentation.camera.CameraMode

/**
 * Typed navigation destinations.
 *
 * Every screen gets one entry that stores its own route string, so there are no
 * hardcoded route literals scattered around the app. Parameterised destinations
 * (e.g. [Camera]) derive their route from their arguments and expose the route
 * pattern + argument keys needed to register them in a [NavHost].
 */
sealed class Destination(val route: String) {

    data object Permissions : Destination(route = PermissionsRoute)
    data object Language : Destination(route = LanguageRoute)
    data object Home : Destination(route = HomeRoute)
    data object Gallery : Destination(route = GalleryRoute)
    data object Settings : Destination(route = SettingsRoute)
    data object Templates : Destination(route = TemplatesRoute)

    /** Full-screen media viewer, opened at the tapped gallery [index]. */
    data class GalleryViewer(val index: Int) : Destination(
        route = "$GalleryViewerBaseRoute/${index}"
    ) {
        companion object {
            const val GalleryViewerBaseRoute = "gallery/viewer"
            const val ARGUMENT_INDEX = "index"

            /** Registered in the NavHost; runtime navigation uses [GalleryViewer.route]. */
            const val ROUTE_PATTERN = "$GalleryViewerBaseRoute/{$ARGUMENT_INDEX}"

            /** Resolves a navigated route string back to a [GalleryViewer] destination. */
            fun fromRoute(route: String?): GalleryViewer? {
                val prefix = "$GalleryViewerBaseRoute/"
                val indexValue = route
                    ?.takeIf { it.startsWith(prefix) }
                    ?.removePrefix(prefix)
                val index = indexValue?.toIntOrNull() ?: return null
                return GalleryViewer(index)
            }
        }
    }

    /** The single reusable camera screen, parameterised by [mode]. */
    data class Camera(val mode: CameraMode) : Destination(
        route = "$CameraBaseRoute/${mode.routeName}"
    ) {
        companion object {
            const val CameraBaseRoute = "camera"
            const val ARGUMENT_MODE = "mode"

            /** Registered in the NavHost; runtime navigation uses [Camera.route]. */
            const val ROUTE_PATTERN = "$CameraBaseRoute/{$ARGUMENT_MODE}"

            /** Resolves a navigated route string back to a [Camera] destination. */
            fun fromRoute(route: String?): Camera? {
                val prefix = "$CameraBaseRoute/"
                val modeValue = route
                    ?.takeIf { it.startsWith(prefix) }
                    ?.removePrefix(prefix)
                val mode = modeValue?.let(CameraMode::fromRoute) ?: return null
                return Camera(mode)
            }
        }
    }
}

private const val PermissionsRoute = "permissions"
private const val LanguageRoute = "language"
private const val HomeRoute = "home"
private const val GalleryRoute = "gallery"
private const val SettingsRoute = "settings"
private const val TemplatesRoute = "templates"

/** Central navigation entry points for the app. */
fun NavController.navigateTo(destination: Destination, builder: NavOptionsBuilder.() -> Unit = {}) {
    navigate(destination.route, builder)
}

fun NavController.navigateToPermissions() {
    navigateTo(Destination.Permissions) { launchSingleTop = true }
}

fun NavController.navigateToLanguage() {
    navigateTo(Destination.Language) { launchSingleTop = true }
}

fun NavController.navigateToSettings() {
    navigateTo(Destination.Settings) { launchSingleTop = true }
}

fun NavController.navigateToTemplates() {
    navigateTo(Destination.Templates) { launchSingleTop = true }
}

fun NavController.navigateToHome() {
    navigateTo(Destination.Home) {
        popUpTo(Destination.Permissions.route) { inclusive = true }
        launchSingleTop = true
    }
}

fun NavController.navigateToGallery() {
    navigateTo(Destination.Gallery) { launchSingleTop = true }
}

fun NavController.navigateToViewer(index: Int) {
    navigateTo(Destination.GalleryViewer(index)) { launchSingleTop = true }
}

fun NavController.navigateToCamera(mode: CameraMode) {
    navigateTo(Destination.Camera(mode)) { launchSingleTop = true }
}