package com.antoniopennino.thecut.repositories

import android.content.Context
import android.net.Uri
import android.util.Log
import com.antoniopennino.thecut.models.SubtitleCue
import com.antoniopennino.thecut.services.WhisperEngine
import com.antonkarpenko.ffmpegkit.FFmpegKit
import com.antonkarpenko.ffmpegkit.FFmpegKitConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Data repository interface for audio transcription.
 * Following Clean Architecture, this abstracts the ASR engine
 * from UI and domain use cases.
 */
interface TranscriptionRepository {
    suspend fun transcribeAudio(
        context: Context,
        mediaUri: Uri,
        language: String = "auto",
        wordLevel: Boolean = false
    ): List<SubtitleCue>
}

class WhisperTranscriptionRepository(
    private val modelAssetPath: String = "models/ggml-tiny-q5_1.bin"
) : TranscriptionRepository {

    private val tag = "WhisperTranscriptionRepo"

    override suspend fun transcribeAudio(
        context: Context,
        mediaUri: Uri,
        language: String,
        wordLevel: Boolean
    ): List<SubtitleCue> = withContext(Dispatchers.IO) {
        // Step 1: Ensure Whisper model is loaded
        if (!WhisperEngine.isModelLoaded()) {
            val loaded = WhisperEngine.initFromAsset(context.assets, modelAssetPath)
            if (!loaded) {
                Log.e(tag, "Failed to initialize WhisperEngine from asset: $modelAssetPath")
                return@withContext emptyList()
            }
        }

        // Step 2: Resolve input media path via SAF
        val inputPath = try {
            FFmpegKitConfig.getSafParameterForRead(context, mediaUri)
        } catch (e: Exception) {
            mediaUri.path ?: ""
        }

        if (inputPath.isEmpty()) {
            Log.e(tag, "Failed to resolve input path for mediaUri: $mediaUri")
            return@withContext emptyList()
        }

        // Step 3: Extract 16kHz mono 16-bit PCM using FFmpegKit
        val pcmFile = File(context.cacheDir, "whisper_audio_${System.currentTimeMillis()}.pcm")
        try {
            val cmd = "-y -i $inputPath -vn -ac 1 -ar 16000 -f s16le \"${pcmFile.absolutePath}\""
            val session = FFmpegKit.execute(cmd)
            if (!session.returnCode.isValueSuccess || !pcmFile.exists() || pcmFile.length() == 0L) {
                Log.e(tag, "FFmpeg failed to extract 16kHz PCM: ${session.failStackTrace}")
                return@withContext emptyList()
            }

            // Step 4: Convert 16-bit PCM bytes to FloatArray [-1.0f, 1.0f]
            val bytes = pcmFile.readBytes()
            val shortBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            val numSamples = shortBuffer.capacity()
            if (numSamples == 0) return@withContext emptyList()

            val floatSamples = FloatArray(numSamples)
            for (i in 0 until numSamples) {
                floatSamples[i] = (shortBuffer.get(i) / 32768.0f).coerceIn(-1.0f, 1.0f)
            }

            // Step 5: Transcribe with WhisperEngine
            val segments = WhisperEngine.transcribe(
                samples = floatSamples,
                language = language,
                numThreads = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
            )

            // Step 6: Map Whisper segments/words to SubtitleCue
            val cues = mutableListOf<SubtitleCue>()
            if (wordLevel) {
                for (seg in segments) {
                    for (word in seg.words) {
                        val trimmed = word.text.trim()
                        if (trimmed.isNotEmpty()) {
                            cues.add(SubtitleCue(word.startMs, word.endMs, trimmed))
                        }
                    }
                }
            } else {
                for (seg in segments) {
                    val trimmed = seg.text.trim()
                    if (trimmed.isNotEmpty()) {
                        cues.add(SubtitleCue(seg.startMs, seg.endMs, trimmed))
                    }
                }
            }

            cues
        } catch (e: Throwable) {
            Log.e(tag, "Error during transcription", e)
            emptyList()
        } finally {
            if (pcmFile.exists()) {
                pcmFile.delete()
            }
        }
    }
}
