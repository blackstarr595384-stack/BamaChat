package com.example.bamachat.ui.screen

internal object ChatScrollPolicy {
    fun isNearBottom(
        totalItemsCount: Int,
        lastVisibleItemIndex: Int?,
        lastVisibleItemEndOffset: Int?,
        viewportEndOffset: Int,
        thresholdPx: Int
    ): Boolean {
        if (totalItemsCount <= 0) return true
        val visibleIndex = lastVisibleItemIndex ?: return true
        if (visibleIndex < totalItemsCount - 1) return false
        val visibleEndOffset = lastVisibleItemEndOffset ?: return true
        return visibleEndOffset <= viewportEndOffset + thresholdPx.coerceAtLeast(0)
    }

    fun resolveAutoFollow(
        previousAutoFollow: Boolean,
        isScrollInProgress: Boolean,
        isProgrammaticScroll: Boolean,
        isNearBottom: Boolean
    ): Boolean = when {
        isNearBottom && !isScrollInProgress -> true
        isScrollInProgress && !isProgrammaticScroll -> false
        else -> previousAutoFollow
    }

    fun shouldShowScrollButton(
        hasMessages: Boolean,
        isNearBottom: Boolean,
        autoFollowEnabled: Boolean
    ): Boolean = hasMessages && !isNearBottom && !autoFollowEnabled

    fun newestItemIndex(totalItemsCount: Int): Int? =
        if (totalItemsCount > 0) totalItemsCount - 1 else null

    fun remainingScrollToBottomPx(
        totalItemsCount: Int,
        lastVisibleItemIndex: Int?,
        lastVisibleItemEndOffset: Int?,
        viewportEndOffset: Int
    ): Int? {
        if (totalItemsCount <= 0 || lastVisibleItemIndex != totalItemsCount - 1) return null
        val visibleEndOffset = lastVisibleItemEndOffset ?: return null
        return (visibleEndOffset - viewportEndOffset).coerceAtLeast(0)
    }
}
