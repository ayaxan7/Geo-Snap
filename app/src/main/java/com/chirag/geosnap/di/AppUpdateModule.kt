package com.chirag.geosnap.di

import android.content.Context
import android.util.Log
import com.chirag.geosnap.core.update.AppUpdateType
import com.chirag.geosnap.core.update.InAppUpdateManager
import com.chirag.geosnap.core.update.NoOpUpdateSignalSource
import com.chirag.geosnap.core.update.PlayUpdateSignalSource
import com.chirag.geosnap.core.update.UpdateSignalSource
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Wiring for Google Play in-app updates.
 *
 * This module is the single configuration point for update behaviour:
 * [PREFERRED_UPDATE_TYPE] selects which flow is offered, and
 * [AUTOMATIC_CHECKS_ENABLED] turns startup checking on or off.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppUpdateModule {

    /**
     * Flexible keeps the app usable while Play downloads in the background, so
     * it is the default. Switching to IMMEDIATE would offer Play's blocking
     * full-screen flow instead.
     */
    private val PREFERRED_UPDATE_TYPE = AppUpdateType.FLEXIBLE

    /** Set to false to stop checking for updates on app launch. */
    const val AUTOMATIC_CHECKS_ENABLED = true

    @Provides
    @Singleton
    fun providesUpdateSignalSource(
        @ApplicationContext context: Context
    ): UpdateSignalSource = runCatching {
        PlayUpdateSignalSource(AppUpdateManagerFactory.create(context))
    }.getOrElse { error ->
        // Sideloaded build, or no usable Play Store on this device. Reporting
        // "nothing to do" keeps the rest of the app completely unaffected.
        Log.w(TAG, "Play in-app update service unavailable", error)
        NoOpUpdateSignalSource()
    }

    @Provides
    @Singleton
    fun providesInAppUpdateManager(
        updateSignalSource: UpdateSignalSource
    ): InAppUpdateManager = InAppUpdateManager(
        source = updateSignalSource,
        preferredType = PREFERRED_UPDATE_TYPE,
        automaticChecksEnabled = AUTOMATIC_CHECKS_ENABLED
    )

    private const val TAG = "InAppUpdate"
}