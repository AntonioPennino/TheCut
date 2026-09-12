package com.antoniopennino.thecut.utils

import com.antoniopennino.thecut.models.EditOperation.KeyframePoint

/**
 * Pure, stateless linear interpolation over animation keyframes.
 *
 * Extracted from VideoEditingActivity, ImageOverlayView and TextOverlayView,
 * which each carried an identical private copy of this logic. Keeping a single
 * shared implementation removes that duplication and makes the math unit-testable.
 */
object KeyframeInterpolator {

    /**
     * Interpolates a single scalar value (opacity, speed, mask size, ...) at [timeMs].
     * Before the first / after the last keyframe the value is clamped to that keyframe.
     * Returns [defaultValue] when there are no keyframes.
     */
    fun value(
        keyframes: List<KeyframePoint>,
        timeMs: Long,
        defaultValue: Float
    ): Float {
        if (keyframes.isEmpty()) return defaultValue
        val sorted = keyframes.sortedBy { it.timeMs }
        if (timeMs <= sorted.first().timeMs) return sorted.first().valueX
        if (timeMs >= sorted.last().timeMs) return sorted.last().valueX
        for (i in 0 until sorted.size - 1) {
            val k1 = sorted[i]
            val k2 = sorted[i + 1]
            if (timeMs >= k1.timeMs && timeMs <= k2.timeMs) {
                val progress = (timeMs - k1.timeMs).toFloat() / (k2.timeMs - k1.timeMs)
                return k1.valueX + progress * (k2.valueX - k1.valueX)
            }
        }
        return defaultValue
    }

    /**
     * Interpolates a 2D position (valueX, valueY) at [timeMs], with the same
     * clamping behaviour as [value]. Returns ([defaultX], [defaultY]) when empty.
     */
    fun position(
        keyframes: List<KeyframePoint>,
        timeMs: Long,
        defaultX: Float,
        defaultY: Float
    ): Pair<Float, Float> {
        if (keyframes.isEmpty()) return Pair(defaultX, defaultY)
        val sorted = keyframes.sortedBy { it.timeMs }
        if (timeMs <= sorted.first().timeMs) return Pair(sorted.first().valueX, sorted.first().valueY)
        if (timeMs >= sorted.last().timeMs) return Pair(sorted.last().valueX, sorted.last().valueY)
        for (i in 0 until sorted.size - 1) {
            val k1 = sorted[i]
            val k2 = sorted[i + 1]
            if (timeMs >= k1.timeMs && timeMs <= k2.timeMs) {
                val progress = (timeMs - k1.timeMs).toFloat() / (k2.timeMs - k1.timeMs)
                val x = k1.valueX + progress * (k2.valueX - k1.valueX)
                val y = k1.valueY + progress * (k2.valueY - k1.valueY)
                return Pair(x, y)
            }
        }
        return Pair(defaultX, defaultY)
    }
}
