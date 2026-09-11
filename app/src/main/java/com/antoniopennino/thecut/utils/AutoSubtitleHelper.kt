package com.antoniopennino.thecut.utils

import android.content.Context
import android.net.Uri
import com.antoniopennino.thecut.models.SubtitleCue

object AutoSubtitleHelper {

    /**
     * Automatically generates timestamped subtitle cues by analyzing the audio for speech segments.
     * Long speech segments (> 3.5s) are cleanly divided into readable sentences/phrases.
     */
    fun generateAutoSubtitles(
        context: Context,
        mediaUri: Uri,
        totalDurationMs: Long,
        thresholdDb: Int = -34,
        minDurationSec: Float = 0.35f,
        onComplete: (List<SubtitleCue>) -> Unit
    ) {
        SilenceDetector.detectSilences(
            context = context,
            mediaUri = mediaUri,
            totalDurationMs = totalDurationMs,
            thresholdDb = thresholdDb,
            minDurationSec = minDurationSec
        ) { silences ->
            val speechSegments = SilenceDetector.calculateSpeechSegments(
                clipStartMs = 0L,
                clipEndMs = totalDurationMs,
                silences = silences,
                paddingMs = 80L
            )

            val cues = mutableListOf<SubtitleCue>()
            val maxCueDurationMs = 3200L // Optimal subtitle display duration (3.2 seconds)

            for ((segmentIndex, speech) in speechSegments.withIndex()) {
                val duration = speech.durationMs
                if (duration <= maxCueDurationMs) {
                    cues.add(
                        SubtitleCue(
                            startTimeMs = speech.startMs,
                            endTimeMs = speech.endMs,
                            text = context.getString(com.antoniopennino.thecut.R.string.str_caption_placeholder, cues.size + 1)
                        )
                    )
                } else {
                    // Split long speech segment into proportional chunks
                    val numChunks = Math.ceil(duration.toDouble() / maxCueDurationMs).toInt().coerceAtLeast(1)
                    val chunkDuration = duration / numChunks
                    for (i in 0 until numChunks) {
                        val start = speech.startMs + (i * chunkDuration)
                        val end = if (i == numChunks - 1) speech.endMs else start + chunkDuration
                        cues.add(
                            SubtitleCue(
                                startTimeMs = start,
                                endTimeMs = end,
                                text = context.getString(com.antoniopennino.thecut.R.string.str_caption_placeholder, cues.size + 1)
                            )
                        )
                    }
                }
            }

            if (cues.isEmpty()) {
                // Fallback: create a single cue spanning the beginning of video
                cues.add(
                    SubtitleCue(
                        startTimeMs = 0L,
                        endTimeMs = minOf(3000L, totalDurationMs),
                        text = context.getString(com.antoniopennino.thecut.R.string.str_caption_placeholder, 1)
                    )
                )
            }

            onComplete(cues)
        }
    }

    /**
     * Shifts all subtitle timestamps by a delta in milliseconds (e.g. +200ms or -200ms).
     */
    fun shiftAllCues(cues: List<SubtitleCue>, deltaMs: Long, maxDurationMs: Long = Long.MAX_VALUE): List<SubtitleCue> {
        return cues.map { cue ->
            val newStart = (cue.startTimeMs + deltaMs).coerceAtLeast(0L)
            val newEnd = (cue.endTimeMs + deltaMs).coerceIn(newStart + 100L, maxDurationMs)
            cue.copy(startTimeMs = newStart, endTimeMs = newEnd)
        }
    }

    /**
     * Performs a batch Find & Replace across all subtitle texts.
     */
    fun replaceTextInAllCues(cues: List<SubtitleCue>, search: String, replacement: String, caseSensitive: Boolean = false): List<SubtitleCue> {
        if (search.isEmpty()) return cues
        return cues.map { cue ->
            val newText = if (caseSensitive) {
                cue.text.replace(search, replacement)
            } else {
                cue.text.replace(Regex(Regex.escape(search), RegexOption.IGNORE_CASE), replacement)
            }
            cue.copy(text = newText)
        }
    }

    /**
     * Splits a single cue at its midpoint into two sequential cues.
     */
    fun splitCue(cues: List<SubtitleCue>, index: Int): List<SubtitleCue> {
        if (index !in cues.indices) return cues
        val target = cues[index]
        val midTime = (target.startTimeMs + target.endTimeMs) / 2L
        val words = target.text.split(" ").filter { it.isNotBlank() }
        val half = words.size / 2
        val firstText = if (half > 0) words.take(half).joinToString(" ") else target.text
        val secondText = if (half > 0 && half < words.size) words.drop(half).joinToString(" ") else "..."

        val cue1 = SubtitleCue(target.startTimeMs, midTime, firstText)
        val cue2 = SubtitleCue(midTime, target.endTimeMs, secondText)

        val result = cues.toMutableList()
        result.removeAt(index)
        result.add(index, cue2)
        result.add(index, cue1)
        return result
    }
}
