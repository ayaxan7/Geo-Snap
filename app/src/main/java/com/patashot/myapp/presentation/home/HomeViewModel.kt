package com.patashot.myapp.presentation.home

import android.app.Activity
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.patashot.myapp.R
import com.patashot.myapp.core.review.InAppReviewManager
import com.patashot.myapp.core.review.NoOpInAppReviewManager
import com.patashot.myapp.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val inAppReviewManager: InAppReviewManager = NoOpInAppReviewManager()
) : ViewModel() {

    enum class HomeAction(
        @StringRes val labelRes: Int,
        @StringRes val descriptionRes: Int,
        @DrawableRes val iconRes: Int
    ) {
        TAKE_PHOTO(
            R.string.home_take_photo,
            R.string.home_take_photo_desc,
            R.drawable.ic_camera
        ),
        TAKE_VIDEO(
            R.string.home_take_video,
            R.string.home_take_video_desc,
            R.drawable.ic_video
        ),
        QUICK_SHARE(
            R.string.home_quick_share,
            R.string.home_quick_share_desc,
            R.drawable.ic_quick_share
        ),
        GALLERY(
            R.string.home_gallery,
            R.string.home_gallery_desc,
            R.drawable.ic_gallery
        )
    }

    @Immutable
    data class UiState(val actions: List<HomeAction> = HomeAction.entries)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    internal var randomProvider: () -> Float = { Random.nextFloat() }

    companion object {
        /**
         * Tracks whether in-app review check has already executed in this process session,
         * ensuring returning to HomeScreen from other screens does not re-roll.
         */
        var hasCheckedReviewInSession: Boolean = false
            internal set
    }

    /**
     * Called when the home screen is opened.
     * Evaluates whether to request in-app review:
     * - Only once per app session
     * - Not on the first launch (homeLaunchCount must be > 1)
     * - With a probability < 0.2
     */
    fun onHomeScreenOpened(activity: Activity?) {
        if (hasCheckedReviewInSession) return
        hasCheckedReviewInSession = true

        viewModelScope.launch {
            val count = preferencesRepository.incrementHomeLaunchCount()
            if (count > 1 && randomProvider() < 0.2f) {
                inAppReviewManager.launchReviewFlow(activity)
            }
        }
    }
}
