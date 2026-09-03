package com.example.bamachat.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatScrollPolicyTest {
    @Test
    fun emptyOrUnmeasuredListIsTreatedAsNearBottom() {
        assertTrue(isNearBottom(totalItemsCount = 0))
        assertTrue(isNearBottom(totalItemsCount = 4, lastVisibleItemIndex = null))
    }

    @Test
    fun lastItemWithinThresholdIsNearBottom() {
        assertTrue(
            isNearBottom(
                totalItemsCount = 12,
                lastVisibleItemIndex = 11,
                lastVisibleItemEndOffset = 1_040,
                viewportEndOffset = 1_000,
                thresholdPx = 56
            )
        )
    }

    @Test
    fun earlierItemOrLongTailOutsideThresholdIsNotNearBottom() {
        assertFalse(isNearBottom(totalItemsCount = 12, lastVisibleItemIndex = 9))
        assertFalse(
            isNearBottom(
                totalItemsCount = 12,
                lastVisibleItemIndex = 11,
                lastVisibleItemEndOffset = 1_100,
                viewportEndOffset = 1_000,
                thresholdPx = 56
            )
        )
    }

    @Test
    fun scrollButtonIsHiddenAtBottomAndVisibleAfterUserScrollsUp() {
        assertFalse(
            ChatScrollPolicy.shouldShowScrollButton(
                hasMessages = false,
                isNearBottom = false,
                autoFollowEnabled = false
            )
        )
        assertFalse(
            ChatScrollPolicy.shouldShowScrollButton(
                hasMessages = true,
                isNearBottom = true,
                autoFollowEnabled = true
            )
        )
        assertTrue(
            ChatScrollPolicy.shouldShowScrollButton(
                hasMessages = true,
                isNearBottom = false,
                autoFollowEnabled = false
            )
        )
    }

    @Test
    fun userScrollAwayDisablesAutoFollow() {
        val resolved = ChatScrollPolicy.resolveAutoFollow(
            previousAutoFollow = true,
            isScrollInProgress = true,
            isProgrammaticScroll = false,
            isNearBottom = false
        )

        assertFalse(resolved)
    }

    @Test
    fun newContentDoesNotResumeFollowForIntentionallyScrolledUpUser() {
        val resolved = ChatScrollPolicy.resolveAutoFollow(
            previousAutoFollow = false,
            isScrollInProgress = false,
            isProgrammaticScroll = false,
            isNearBottom = false
        )

        assertFalse(resolved)
    }

    @Test
    fun settledBottomResumesFollowButProgrammaticScrollDoesNotDisableIt() {
        assertTrue(
            ChatScrollPolicy.resolveAutoFollow(
                previousAutoFollow = false,
                isScrollInProgress = false,
                isProgrammaticScroll = false,
                isNearBottom = true
            )
        )
        assertTrue(
            ChatScrollPolicy.resolveAutoFollow(
                previousAutoFollow = true,
                isScrollInProgress = true,
                isProgrammaticScroll = true,
                isNearBottom = false
            )
        )
    }

    @Test
    fun explicitScrollTargetsTheNewestLazyItem() {
        assertEquals(24, ChatScrollPolicy.newestItemIndex(25))
        assertNull(ChatScrollPolicy.newestItemIndex(0))
    }

    @Test
    fun longNewestItemScrollsByItsRemainingViewportDistance() {
        assertEquals(
            640,
            ChatScrollPolicy.remainingScrollToBottomPx(
                totalItemsCount = 8,
                lastVisibleItemIndex = 7,
                lastVisibleItemEndOffset = 1_640,
                viewportEndOffset = 1_000
            )
        )
        assertNull(
            ChatScrollPolicy.remainingScrollToBottomPx(
                totalItemsCount = 8,
                lastVisibleItemIndex = 6,
                lastVisibleItemEndOffset = 1_640,
                viewportEndOffset = 1_000
            )
        )
    }

    private fun isNearBottom(
        totalItemsCount: Int,
        lastVisibleItemIndex: Int? = 3,
        lastVisibleItemEndOffset: Int? = 1_000,
        viewportEndOffset: Int = 1_000,
        thresholdPx: Int = 56
    ): Boolean = ChatScrollPolicy.isNearBottom(
        totalItemsCount = totalItemsCount,
        lastVisibleItemIndex = lastVisibleItemIndex,
        lastVisibleItemEndOffset = lastVisibleItemEndOffset,
        viewportEndOffset = viewportEndOffset,
        thresholdPx = thresholdPx
    )
}
