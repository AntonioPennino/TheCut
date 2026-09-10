package com.antoniopennino.thecut.utils

import android.content.Context
import android.net.Uri
import com.antonkarpenko.ffmpegkit.FFmpegKit
import com.antonkarpenko.ffmpegkit.FFmpegKitConfig
import com.antonkarpenko.ffmpegkit.FFmpegSession
import com.antonkarpenko.ffmpegkit.Log
import com.antonkarpenko.ffmpegkit.ReturnCode
import java.io.Serializable
import java.util.regex.Pattern

data class SilenceInterval(
    val startMs: Long,
    val endMs: Long
) : Serializable {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
}

data class SpeechInterval(
    val startMs: Long,
    val endMs: Long
) : Serializable {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
}

object SilenceDetector {

    /**
     * Accurately detects silence intervals in the given video/audio URI using FFmpeg's silencedetect filter.
     * Runs asynchronously at 50x-200x realtime without decoding video.
     */
    fun detectSilences(
        context: Context,
        mediaUri: Uri,
        totalDurationMs: Long,
        thresholdDb: Int = -34,
        minDurationSec: Float = 0.35f,
        onComplete: (List<SilenceInterval>) -> Unit
    ) {
        val inputPath = try {
            FFmpegKitConfig.getSafParameterForRead(context, mediaUri)
        } catch (e: Exception) {
            mediaUri.path ?: ""
        }

        val silences = mutableListOf<SilenceInterval>()
        var currentSilenceStartMs: Long? = null

        val startPattern = Pattern.compile("silence_start:\\s*([0-9.]+)")
        val endPattern = Pattern.compile("silence_end:\\s*([0-9.]+)")

        // -vn disables video stream decoding for ultra-fast processing
        // -sn disables subtitles
        // -af silencedetect emits precise timestamps
        // -f null - discards output
        val cmd = "-vn -sn -i \"$inputPath\" -af silencedetect=noise=${thresholdDb}dB:d=$minDurationSec -f null -"

        FFmpegKit.executeAsync(cmd, { session: FFmpegSession ->
            if (ReturnCode.isSuccess(session.returnCode)) {
                // If a silence was open until the end of the file
                val start = currentSilenceStartMs
                if (start != null && start < totalDurationMs) {
                    silences.add(SilenceInterval(start, totalDurationMs))
                }
                // Filter out zero-length or negative silences and sort
                val cleanSilences = silences.filter { it.durationMs >= (minDurationSec * 1000L).toLong() - 50L }
                    .sortedBy { it.startMs }
                onComplete(cleanSilences)
            } else {
                onComplete(emptyList())
            }
        }, { log: Log ->
            val message = log.message ?: return@executeAsync
            val startMatcher = startPattern.matcher(message)
            if (startMatcher.find()) {
                val sec = startMatcher.group(1)?.toDoubleOrNull()
                if (sec != null) {
                    currentSilenceStartMs = (sec * 1000).toLong()
                }
            }

            val endMatcher = endPattern.matcher(message)
            if (endMatcher.find()) {
                val endSec = endMatcher.group(1)?.toDoubleOrNull()
                if (endSec != null) {
                    val endMs = (endSec * 1000).toLong()
                    val startMs = currentSilenceStartMs ?: 0L
                    if (endMs > startMs) {
                        silences.add(SilenceInterval(startMs, endMs))
                    }
                    currentSilenceStartMs = null
                }
            }
        }, null)
    }

    /**
     * Inverts a list of silence intervals into active speech segments.
     * Applies safety padding (head & tail buffer in milliseconds) so speech consonants
     * and natural breath sounds are not clipped abruptly.
     */
    fun calculateSpeechSegments(
        clipStartMs: Long,
        clipEndMs: Long,
        silences: List<SilenceInterval>,
        paddingMs: Long = 100L
    ): List<SpeechInterval> {
        if (silences.isEmpty()) {
            return listOf(SpeechInterval(clipStartMs, clipEndMs))
        }

        // Clamp detected silences strictly within the clip boundary [clipStartMs, clipEndMs]
        val clampedSilences = silences.mapNotNull {
            val s = maxOf(clipStartMs, it.startMs)
            val e = minOf(clipEndMs, it.endMs)
            if (e - s >= 150L) SilenceInterval(s, e) else null
        }.sortedBy { it.startMs }

        if (clampedSilences.isEmpty()) {
            return listOf(SpeechInterval(clipStartMs, clipEndMs))
        }

        val speechSegments = mutableListOf<SpeechInterval>()
        var currentSpeechStart = clipStartMs

        for (silence in clampedSilences) {
            // Apply padding around speech ending
            val speechEnd = (silence.startMs + paddingMs).coerceAtMost(clipEndMs)
            if (speechEnd - currentSpeechStart >= 120L) {
                speechSegments.add(SpeechInterval(currentSpeechStart, speechEnd))
            }
            // Next speech segment starts with lead-in padding before next speech
            currentSpeechStart = (silence.endMs - paddingMs).coerceAtLeast(clipStartMs)
        }

        if (clipEndMs - currentSpeechStart >= 120L) {
            speechSegments.add(SpeechInterval(currentSpeechStart, clipEndMs))
        }

        return speechSegments
    }
}
