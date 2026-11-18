package com.antoniopennino.thecut.utils

import com.antoniopennino.thecut.models.EditOperation
import com.antoniopennino.thecut.models.id

/** Keyframe cue markers to draw on the timeline for one overlay operation. */
data class KeyframeCueTrack(
    val operationId: String,
    val isText: Boolean,
    /** Distinct keyframe times (ms, relative to the overlay start), in first-seen order. */
    val cueTimesMs: List<Long>,
    val startMs: Long,
    val endMs: Long
)

/**
 * Collects the keyframe cue tracks shown under the timeline. Text overlays
 * contribute position and opacity keyframes; image/video overlays also
 * contribute speed and all mask keyframes. Operations without keyframes, and
 * operations of any other type, produce no track.
 *
 * Pure logic extracted from VideoEditingActivity's keyframe track rendering.
 */
object KeyframeCues {

    fun collect(operations: List<EditOperation>, totalDurationMs: Long): List<KeyframeCueTrack> =
        operations.mapNotNull { forOperation(it, totalDurationMs) }

    fun forOperation(op: EditOperation, totalDurationMs: Long): KeyframeCueTrack? {
        val keyframes = mutableListOf<Long>()
        val startMs: Long
        val endMs: Long
        when (op) {
            is EditOperation.AddText -> {
                keyframes.addAll(op.positionKeyframes.map { it.timeMs })
                keyframes.addAll(op.opacityKeyframes.map { it.timeMs })
                startMs = op.startTimeMs ?: 0L
                endMs = op.endTimeMs ?: totalDurationMs
            }
            is EditOperation.AddImageOverlay -> {
                keyframes.addAll(op.positionKeyframes.map { it.timeMs })
                keyframes.addAll(op.opacityKeyframes.map { it.timeMs })
                keyframes.addAll(op.speedKeyframes.map { it.timeMs })
                keyframes.addAll(op.maskConfig.positionKeyframes.map { it.timeMs })
                keyframes.addAll(op.maskConfig.sizeKeyframes.map { it.timeMs })
                keyframes.addAll(op.maskConfig.rotationKeyframes.map { it.timeMs })
                keyframes.addAll(op.maskConfig.featherKeyframes.map { it.timeMs })
                startMs = op.startTimeMs ?: 0L
                endMs = op.endTimeMs ?: totalDurationMs
            }
            else -> return null
        }
        val distinct = keyframes.distinct()
        if (distinct.isEmpty()) return null
        return KeyframeCueTrack(
            operationId = op.id,
            isText = op is EditOperation.AddText,
            cueTimesMs = distinct,
            startMs = startMs,
            endMs = endMs
        )
    }
}
