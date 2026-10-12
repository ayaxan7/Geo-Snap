package com.patashot.myapp.core.update

/** The two update flows Google Play supports, as offered by Play's own UI. */
enum class AppUpdateType {
    /** Non-blocking: Play downloads in the background while the app stays usable. */
    FLEXIBLE,

    /** Blocking full-screen flow; the app is unusable until the update is installed. */
    IMMEDIATE
}

/**
 * Availability and install progress reported by [UpdateSignalSource].
 *
 * Deliberately free of Play Core types so the decision logic in
 * [InAppUpdateManager] can be unit tested on the JVM.
 */
sealed interface UpdateSignal {

    /** No update is available for this app/version/device combination. */
    data object NotAvailable : UpdateSignal

    /**
     * An update exists and Play has permitted [type] for this build. [type] is
     * resolved by the source, so it is never a type the app would refuse to show.
     */
    data class Available(val type: AppUpdateType) : UpdateSignal

    /**
     * A developer-triggered immediate flow is still on screen. Play only reports
     * this for flows it has already started, so it is used purely to resume.
     */
    data object ImmediateInProgress : UpdateSignal

    /** A flexible update finished downloading and is waiting for completion. */
    data object Downloaded : UpdateSignal

    /** A flexible update is currently downloading or installing. */
    data object InProgress : UpdateSignal

    /**
     * Play could not answer. Covers a missing/unmodified Play Store, an app
     * that was never acquired from Play, and offline/service-unavailable cases.
     */
    data class Failed(val reason: String) : UpdateSignal
}

/** Something the app must act on. Everything else is logged and ignored. */
sealed interface InAppUpdateAction {

    /** Launch Play's own update dialog for [type] via the Activity Result launcher. */
    data class Start(val type: AppUpdateType) : InAppUpdateAction

    /** Show the "update ready, restart to install" notification. */
    data object Downloaded : InAppUpdateAction
}