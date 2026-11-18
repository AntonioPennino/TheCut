package com.antoniopennino.thecut

import com.antoniopennino.thecut.models.EditOperation
import com.antoniopennino.thecut.models.EditOperation.KeyframePoint
import com.antoniopennino.thecut.models.TextPosition
import com.antoniopennino.thecut.utils.KeyframeCues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyframeCuesTest {

    private fun text(
        position: List<KeyframePoint> = emptyList(),
        opacity: List<KeyframePoint> = emptyList(),
        start: Long? = null,
        end: Long? = null
    ) = EditOperation.AddText(
        text = "hi",
        fontSize = 24,
        position = TextPosition.values().first(),
        positionKeyframes = position,
        opacityKeyframes = opacity,
        startTimeMs = start,
        endTimeMs = end
    )

    @Test
    fun textWithoutKeyframes_producesNoTrack() {
        assertNull(KeyframeCues.forOperation(text(), 10_000L))
    }

    @Test
    fun nonOverlayOperation_producesNoTrack() {
        assertNull(KeyframeCues.forOperation(EditOperation.Trim(startMs = 0L, endMs = 1000L), 10_000L))
    }

    @Test
    fun textKeyframes_areMergedAndDeduplicatedInOrder() {
        val op = text(
            position = listOf(KeyframePoint(500L, 0f), KeyframePoint(1500L, 0f)),
            opacity = listOf(KeyframePoint(1500L, 1f), KeyframePoint(200L, 1f)),
            start = 1000L,
            end = 4000L
        )
        val track = KeyframeCues.forOperation(op, 10_000L)!!
        assertEquals(listOf(500L, 1500L, 200L), track.cueTimesMs)
        assertTrue(track.isText)
        assertEquals(1000L, track.startMs)
        assertEquals(4000L, track.endMs)
    }

    @Test
    fun missingRange_defaultsToWholeSequence() {
        val op = text(position = listOf(KeyframePoint(100L, 0f)))
        val track = KeyframeCues.forOperation(op, 7_000L)!!
        assertEquals(0L, track.startMs)
        assertEquals(7_000L, track.endMs)
    }

    @Test
    fun collect_skipsOperationsWithoutKeyframes() {
        val withCues = text(opacity = listOf(KeyframePoint(300L, 0.5f)))
        val tracks = KeyframeCues.collect(listOf(text(), withCues, EditOperation.Trim(0L, 10L)), 5_000L)
        assertEquals(1, tracks.size)
        assertEquals(listOf(300L), tracks.single().cueTimesMs)
    }
}
