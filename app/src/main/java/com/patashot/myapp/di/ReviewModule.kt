package com.patashot.myapp.di

import android.content.Context
import android.util.Log
import com.patashot.myapp.core.review.InAppReviewManager
import com.patashot.myapp.core.review.NoOpInAppReviewManager
import com.patashot.myapp.core.review.PlayInAppReviewManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ReviewModule {

    @Provides
    @Singleton
    fun provideInAppReviewManager(
        @ApplicationContext context: Context
    ): InAppReviewManager {
        return runCatching {
            PlayInAppReviewManager(context)
        }.getOrElse { error ->
            Log.w("InAppReview", "Play in-app review service unavailable", error)
            NoOpInAppReviewManager()
        }
    }
}
