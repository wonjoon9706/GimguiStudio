package com.example.pleosaudioguide

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackQueueTest {
    @Test fun movingForwardKeepsOtherItemsInRelativeOrder() {
        val queue = listOf("A", "B", "C", "D")
        assertEquals(listOf("B", "C", "A", "D"), PlaybackQueue.move(queue, 0, 2))
        assertEquals(listOf("A", "B", "C", "D"), queue)
    }

    @Test fun movingBackwardKeepsAllItemsExactlyOnce() {
        assertEquals(listOf("D", "A", "B", "C"),
            PlaybackQueue.move(listOf("A", "B", "C", "D"), 3, 0))
    }

    @Test fun movingCurrentItemCanRetainItsIdentityAtTheNewIndex() {
        val queue = listOf("A", "B", "C", "D")
        val currentId = queue[1]
        val moved = PlaybackQueue.move(queue, 1, 3)
        assertEquals(3, moved.indexOf(currentId))
        assertEquals("D", moved[moved.indexOf(currentId) - 1])
    }

    @Test fun staleOrInvalidMovesLeaveTheQueueUnchanged() {
        val queue = listOf("A", "B")
        assertEquals(queue, PlaybackQueue.move(queue, -1, 0))
        assertEquals(queue, PlaybackQueue.move(queue, 0, 2))
        assertEquals(queue, PlaybackQueue.move(queue, 1, 1))
    }
}
