package com.antoniopennino.thecut.usecases

import android.content.Context
import android.net.Uri
import com.antoniopennino.thecut.models.SubtitleCue
import com.antoniopennino.thecut.repositories.TranscriptionRepository

/**
 * Use case that coordinates offline speech transcription and subtitle generation.
 * Isolated from Android UI frameworks for maximum testability and clean domain logic.
 */
class GenerateSubtitlesUseCase(
    private val transcriptionRepository: TranscriptionRepository
) {
    data class Result(
        val cues: List<SubtitleCue>,
        val executionTimeMs: Long
    )

    suspend operator fun invoke(
        context: Context,
        mediaUri: Uri,
        language: String = "auto",
        wordLevel: Boolean = false
    ): Result {
        val startTime = System.currentTimeMillis()
        val cues = transcriptionRepository.transcribeAudio(
            context = context,
            mediaUri = mediaUri,
            language = language,
            wordLevel = wordLevel
        )
        val elapsed = System.currentTimeMillis() - startTime
        return Result(cues, elapsed)
    }
}
