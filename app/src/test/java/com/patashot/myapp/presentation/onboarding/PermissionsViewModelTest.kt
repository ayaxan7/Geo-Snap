package com.patashot.myapp.presentation.onboarding

import com.patashot.myapp.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionsViewModelTest {

    private val camera = PermissionsViewModel.PermissionRequirement(
        permission = "android.permission.CAMERA",
        label = R.string.permission_camera_label,
        description = R.string.permission_camera_description,
        iconRes = R.drawable.ic_camera
    )
    private val location = PermissionsViewModel.PermissionRequirement(
        permission = "android.permission.ACCESS_FINE_LOCATION",
        label = R.string.permission_location_label,
        description = R.string.permission_location_description,
        iconRes = R.drawable.ic_location
    )
    private val notifications = PermissionsViewModel.PermissionRequirement(
        permission = "android.permission.POST_NOTIFICATIONS",
        label = R.string.permission_notifications_label,
        description = R.string.permission_notifications_description,
        iconRes = R.drawable.ic_notification,
        isOptional = true
    )
    private val requirements = listOf(camera, location)

    @Test
    fun `all granted upfront produces allGranted and nothing requestable`() {
        val vm = PermissionsViewModel()
        vm.initialize(
            requirements,
            currentGrants = mapOf(camera.permission to true, location.permission to true)
        )

        assertTrue(vm.uiState.value.allGranted)
        assertTrue(vm.requestablePermissions.isEmpty())
    }

    @Test
    fun `initialize with no grants then granting all completes`() {
        val vm = PermissionsViewModel()
        vm.initialize(requirements, currentGrants = emptyMap())

        assertFalse(vm.uiState.value.allGranted)
        assertEquals(2, vm.requestablePermissions.size)

        vm.onPermissionResults(
            results = mapOf(camera.permission to true, location.permission to true),
            permanentlyDenied = emptySet()
        )

        assertTrue(vm.uiState.value.allGranted)
    }

    @Test
    fun `permanently denied permission loses requestability and surfaces settings`() {
        val vm = PermissionsViewModel()
        vm.initialize(requirements, currentGrants = emptyMap())

        vm.onPermissionResults(
            results = mapOf(camera.permission to false, location.permission to true),
            permanentlyDenied = setOf(camera.permission)
        )

        assertTrue(vm.requestablePermissions.isEmpty())
        assertTrue(vm.uiState.value.canOpenSettings)
        assertFalse(vm.uiState.value.allGranted)
    }

    @Test
    fun `refresh grants allows settings-enabled permission to complete onboarding`() {
        val vm = PermissionsViewModel()
        vm.initialize(requirements, currentGrants = emptyMap())

        vm.onPermissionResults(
            results = mapOf(camera.permission to false, location.permission to false),
            permanentlyDenied = setOf(camera.permission)
        )
        assertFalse(vm.uiState.value.allGranted)

        vm.refreshGrants(
            mapOf(camera.permission to true, location.permission to true)
        )

        assertTrue(vm.uiState.value.allGranted)
    }

    @Test
    fun `denied optional permission still satisfies onboarding`() {
        val vm = PermissionsViewModel()
        vm.initialize(
            requirements + notifications,
            currentGrants = mapOf(camera.permission to true, location.permission to true)
        )

        assertFalse(vm.uiState.value.items.first { it.permission == notifications.permission }.granted)
        assertTrue(vm.uiState.value.allGranted)
    }

    @Test
    fun `denied optional permission is not requestable`() {
        val vm = PermissionsViewModel()
        vm.initialize(requirements + notifications, currentGrants = emptyMap())

        assertFalse(vm.requestablePermissions.contains(notifications.permission))
    }

    @Test
    fun `granted optional permission does not break allGranted`() {
        val vm = PermissionsViewModel()
        vm.initialize(
            requirements + notifications,
            currentGrants = mapOf(
                camera.permission to true,
                location.permission to true,
                notifications.permission to true
            )
        )

        assertTrue(vm.uiState.value.allGranted)
    }

    @Test
    fun `permanently denied optional permission does not surface the settings prompt`() {
        val vm = PermissionsViewModel()
        vm.initialize(
            requirements + notifications,
            currentGrants = mapOf(camera.permission to true, location.permission to true)
        )

        vm.onPermissionResults(
            results = mapOf(notifications.permission to false),
            permanentlyDenied = setOf(notifications.permission)
        )

        assertTrue(vm.uiState.value.allGranted)
        assertFalse(vm.uiState.value.canOpenSettings)
    }
}