package com.example.bamachat.util

import android.app.Activity

/**
 * In-app review is disabled in this build due to removed Play Review dependency.
 * To enable, add com.google.android.play:review-ktx to dependencies.
 */
object InAppReviewManager {
    fun init(activity: Activity) {
        // No-op: Play Review dependency not available
    }

    fun requestReview(activity: Activity) {
        // No-op: Play Review dependency not available
    }
}
