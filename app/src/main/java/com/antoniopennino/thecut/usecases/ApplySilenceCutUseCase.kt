package com.antoniopennino.thecut.usecases

import android.content.Context
import android.net.Uri
import com.antoniopennino.thecut.utils.SilenceDetector
import com.antoniopennino.thecut.utils.SilenceInterval
import com.antoniopennino.thecut.utils.SpeechInterval
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Use case that encapsulates silence detection and speech segment calculation.
 */
class ApplySilenceCutUseCase {

    data class Params(
        val mediaUri: Uri,
        val clipStartMs: Long,
        val clipEndMs: Long,
        val totalDurationMs: Long,
        val thresholdDb: Int = -34,
        val minDurationSec: Float = 0.35f,
        val paddingMs: Long = 100L
    )

    data class Result(
        val detectedSilences: List<SilenceInterval>,
        val speechSegments: List<SpeechInterval>
    )

    suspend fun detect(context: Context, params: Params): Result = suspendCancellableCoroutine { continuation ->
        SilenceDetector.detectSilences(
            context = context,
            mediaUri = params.mediaUri,
            totalDurationMs = params.totalDurationMs,
            thresholdDb = params.thresholdDb,
            minDurationSec = params.minDurationSec
        ) { silences ->
            val clipSilences = silences.filter { it.startMs >= params.clipStartMs && it.endMs <= params.clipEndMs }
            val speechSegments = SilenceDetector.calculateSpeechSegments(
                params.clipStartMs,
                params.clipEndMs,
                clipSilences,
                params.paddingMs
            )
            continuation.resume(Result(clipSilences, speechSegments))
        }
    }

    fun recalculate(
        clipStartMs: Long,
        clipEndMs: Long,
        silences: List<SilenceInterval>,
        paddingMs: Long
    ): List<SpeechInterval> {
        return SilenceDetector.calculateSpeechSegments(clipStartMs, clipEndMs, silences, paddingMs)
    }
}
