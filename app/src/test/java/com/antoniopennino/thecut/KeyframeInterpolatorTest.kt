package com.antoniopennino.thecut

import com.antoniopennino.thecut.models.EditOperation.KeyframePoint
import com.antoniopennino.thecut.utils.KeyframeInterpolator
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyframeInterpolatorTest {

    private val eps = 1e-4f

    @Test
    fun value_emptyKeyframes_returnsDefault() {
        assertEquals(0.42f, KeyframeInterpolator.value(emptyList(), 1000L, 0.42f), eps)
    }

    @Test
    fun value_beforeFirst_clampsToFirst() {
        val kfs = listOf(KeyframePoint(1000L, 0.2f), KeyframePoint(2000L, 0.8f))
        assertEquals(0.2f, KeyframeInterpolator.value(kfs, 0L, 1f), eps)
    }

    @Test
    fun value_afterLast_clampsToLast() {
        val kfs = listOf(KeyframePoint(1000L, 0.2f), KeyframePoint(2000L, 0.8f))
        assertEquals(0.8f, KeyframeInterpolator.value(kfs, 5000L, 1f), eps)
    }

    @Test
    fun value_midpoint_interpolatesLinearly() {
        val kfs = listOf(KeyframePoint(1000L, 0.0f), KeyframePoint(2000L, 1.0f))
        assertEquals(0.5f, KeyframeInterpolator.value(kfs, 1500L, 0f), eps)
        assertEquals(0.25f, KeyframeInterpolator.value(kfs, 1250L, 0f), eps)
    }

    @Test
    fun value_unsortedInput_isSortedBeforeInterpolating() {
        val kfs = listOf(KeyframePoint(2000L, 1.0f), KeyframePoint(1000L, 0.0f))
        assertEquals(0.5f, KeyframeInterpolator.value(kfs, 1500L, 0f), eps)
    }

    @Test
    fun position_midpoint_interpolatesBothAxes() {
        val kfs = listOf(
            KeyframePoint(0L, 0.0f, 0.0f),
            KeyframePoint(1000L, 1.0f, 0.5f)
        )
        val (x, y) = KeyframeInterpolator.position(kfs, 500L, 0.9f, 0.9f)
        assertEquals(0.5f, x, eps)
        assertEquals(0.25f, y, eps)
    }

    @Test
    fun position_emptyKeyframes_returnsDefaults() {
        val (x, y) = KeyframeInterpolator.position(emptyList(), 500L, 0.3f, 0.7f)
        assertEquals(0.3f, x, eps)
        assertEquals(0.7f, y, eps)
    }
}
