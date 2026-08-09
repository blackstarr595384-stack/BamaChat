package com.example.bamachat.service

import com.example.bamachat.util.UserErrorMessage

object ChatErrorRecoveryPolicy {
    fun buildErrorDisplayText(message: UserErrorMessage): String {
        return "${message.title}: ${message.description}\n\n💡 ${message.suggestion}"
    }

    fun shouldEnableRetry(isRetryable: Boolean, pendingUserMessage: String?): Boolean {
        return isRetryable && isValidRetryCandidate(pendingUserMessage)
    }

    fun isValidRetryCandidate(message: String?): Boolean {
        return !message.isNullOrBlank()
    }
}
