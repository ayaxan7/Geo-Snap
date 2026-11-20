package com.patashot.myapp.core.review

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * Abstraction over Google Play's in-app review API to allow unit testing and
 * safe fallback on devices without Google Play services.
 */
interface InAppReviewManager {
    /**
     * Attempts to launch the in-app review flow.
     *
     * If [activity] is null, or if the review flow request fails, [onFallback] is invoked.
     */
    fun launchReviewFlow(activity: Activity?, onFallback: () -> Unit = {})
}

/**
 * Production implementation using Google Play In-App Review SDK.
 */
class PlayInAppReviewManager(
    private val reviewManager: ReviewManager
) : InAppReviewManager {

    constructor(context: Context) : this(ReviewManagerFactory.create(context.applicationContext))

    override fun launchReviewFlow(activity: Activity?, onFallback: () -> Unit) {
        if (activity == null) {
            Log.w(TAG, "No activity available to launch in-app review")
            onFallback()
            return
        }

        val request = reviewManager.requestReviewFlow()
        request.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val reviewInfo = task.result
                val flow = reviewManager.launchReviewFlow(activity, reviewInfo)
                flow.addOnCompleteListener {
                    Log.d(TAG, "In-app review flow finished")
                }
            } else {
                Log.w(TAG, "In-app review flow request failed", task.exception)
                onFallback()
            }
        }
    }

    private companion object {
        const val TAG = "InAppReview"
    }
}

/**
 * No-op implementation for testing or devices where Play Services is unavailable.
 */
class NoOpInAppReviewManager : InAppReviewManager {
    override fun launchReviewFlow(activity: Activity?, onFallback: () -> Unit) {
        onFallback()
    }
}

/**
 * Directly opens the app's listing on Google Play Store (app or browser).
 */
fun openPlayStoreListing(context: Context) {
    val url = "https://play.google.com/store/apps/details?id=com.patashot.myapp"
    val playStoreIntent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
        setPackage("com.android.vending")
    }
    try {
        context.startActivity(playStoreIntent)
    } catch (_: Exception) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (e2: Exception) {
            e2.printStackTrace()
        }
    }
}
