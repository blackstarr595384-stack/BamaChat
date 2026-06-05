package com.example.bamachat.util

import android.app.Activity

object InAppReviewManager {
    
    fun requestReview(activity: Activity) {
        try {
            val manager = com.google.android.play.core.review.ReviewManagerFactory.create(activity)
            val request = manager.requestReviewFlow()
            request.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val reviewInfo = task.result
                    manager.launchReviewFlow(activity, reviewInfo)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("InAppReviewManager", "Review request failed", e)
        }
    }
    
    fun shouldRequestReview(messageCount: Int, lastReviewPromptAt: Long): Boolean {
        val timeSinceLastReview = System.currentTimeMillis() - lastReviewPromptAt
        val thirtyDaysInMs = 30 * 24 * 60 * 60 * 1000L
        return messageCount % 50 == 0 && timeSinceLastReview > thirtyDaysInMs
    }
}
