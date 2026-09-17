package com.chirag.geosnap.presentation.home

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import com.chirag.geosnap.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class HomeViewModel @Inject constructor() : ViewModel() {

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
}