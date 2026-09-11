package com.antoniopennino.thecut.services

import android.content.Context
import android.content.res.AssetManager
import android.os.Build
import android.util.Log
import java.io.File

/**
 * Singleton wrapper around native whisper.cpp library.
 * Handles model loading from assets or storage, and exposes
 * transcription with segment and token-level timestamps.
 */
object WhisperEngine {
    private const val TAG = "WhisperEngine"
    private var contextPtr: Long = 0L

    init {
        loadNativeLibraries()
    }

    private fun loadNativeLibraries() {
        try {
            var loadV8fp16 = false
            var loadVfpv4 = false

            val primaryAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: ""
            if (primaryAbi == "arm64-v8a") {
                val cpuInfo = readCpuInfo()
                if (cpuInfo.contains("fphp")) {
                    loadV8fp16 = true
                }
            } else if (primaryAbi == "armeabi-v7a") {
                val cpuInfo = readCpuInfo()
                if (cpuInfo.contains("vfpv4")) {
                    loadVfpv4 = true
                }
            }

            // Pre-load ggml shared dependencies if present
            try { System.loadLibrary("ggml-base") } catch (t: Throwable) {}
            try { System.loadLibrary("ggml") } catch (t: Throwable) {}
            try { System.loadLibrary("ggml-cpu") } catch (t: Throwable) {}

            if (loadV8fp16) {
                try {
                    System.loadLibrary("whisper_v8fp16_va")
                    Log.d(TAG, "Loaded libwhisper_v8fp16_va.so")
                    return
                } catch (e: Throwable) {
                    Log.w(TAG, "Failed loading whisper_v8fp16_va, falling back: ${e.message}")
                }
            } else if (loadVfpv4) {
                try {
                    System.loadLibrary("whisper_vfpv4")
                    Log.d(TAG, "Loaded libwhisper_vfpv4.so")
                    return
                } catch (e: Throwable) {
                    Log.w(TAG, "Failed loading whisper_vfpv4, falling back: ${e.message}")
                }
            }

            System.loadLibrary("whisper")
            Log.d(TAG, "Loaded libwhisper.so")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to load whisper native library", e)
        }
    }

    private fun readCpuInfo(): String {
        return try {
            File("/proc/cpuinfo").readText()
        } catch (e: Exception) {
            ""
        }
    }

    data class WhisperWord(
        val text: String,
        val startMs: Long,
        val endMs: Long
    )

    data class WhisperSegment(
        val text: String,
        val startMs: Long,
        val endMs: Long,
        val words: List<WhisperWord> = emptyList()
    )

    @Synchronized
    fun isModelLoaded(): Boolean = contextPtr != 0L

    @Synchronized
    fun initFromAsset(assetManager: AssetManager, assetPath: String = "models/ggml-tiny-q5_1.bin"): Boolean {
        if (contextPtr != 0L) {
            freeContext(contextPtr)
            contextPtr = 0L
        }
        contextPtr = initContextFromAsset(assetManager, assetPath)
        return contextPtr != 0L
    }

    @Synchronized
    fun initFromFile(modelPath: String): Boolean {
        if (contextPtr != 0L) {
            freeContext(contextPtr)
            contextPtr = 0L
        }
        contextPtr = initContext(modelPath)
        return contextPtr != 0L
    }

    @Synchronized
    fun release() {
        if (contextPtr != 0L) {
            freeContext(contextPtr)
            contextPtr = 0L
        }
    }

    /**
     * Transcribes 16kHz mono float audio samples.
     * Language can be "auto", "it", "en", etc.
     */
    @Synchronized
    fun transcribe(
        samples: FloatArray,
        language: String = "auto",
        numThreads: Int = 4
    ): List<WhisperSegment> {
        if (contextPtr == 0L) {
            throw IllegalStateException("Whisper context not initialized. Call initFromAsset or initFromFile first.")
        }

        val res = fullTranscribe(contextPtr, numThreads, language, samples)
        if (res != 0) {
            Log.e(TAG, "whisper_full returned error $res")
            return emptyList()
        }

        val segmentCount = getTextSegmentCount(contextPtr)
        val segments = ArrayList<WhisperSegment>(segmentCount)

        for (i in 0 until segmentCount) {
            val text = getTextSegment(contextPtr, i).trim()
            val t0 = getTextSegmentT0(contextPtr, i) * 10L // whisper t0 is in units of 10ms
            val t1 = getTextSegmentT1(contextPtr, i) * 10L

            val tokenCount = getTextSegmentTokenCount(contextPtr, i)
            val words = ArrayList<WhisperWord>()

            for (w in 0 until tokenCount) {
                val tokenText = getTextSegmentTokenText(contextPtr, i, w)
                val wt0 = getTextSegmentTokenT0(contextPtr, i, w) * 10L
                val wt1 = getTextSegmentTokenT1(contextPtr, i, w) * 10L
                if (tokenText.isNotEmpty() && !tokenText.startsWith("[_TT_") && !tokenText.startsWith("<|")) {
                    words.add(WhisperWord(tokenText, wt0, wt1))
                }
            }

            if (text.isNotEmpty()) {
                segments.add(WhisperSegment(text, t0, t1, words))
            }
        }

        return segments
    }

    // Native functions declared in whisper-jni.c
    private external fun initContextFromAsset(assetManager: AssetManager, assetPath: String): Long
    private external fun initContext(modelPath: String): Long
    private external fun freeContext(contextPtr: Long)
    private external fun fullTranscribe(
        contextPtr: Long,
        numThreads: Int,
        language: String,
        audioData: FloatArray
    ): Int
    private external fun getTextSegmentCount(contextPtr: Long): Int
    private external fun getTextSegment(contextPtr: Long, index: Int): String
    private external fun getTextSegmentT0(contextPtr: Long, index: Int): Long
    private external fun getTextSegmentT1(contextPtr: Long, index: Int): Long
    private external fun getTextSegmentTokenCount(contextPtr: Long, segmentIndex: Int): Int
    private external fun getTextSegmentTokenText(contextPtr: Long, segmentIndex: Int, tokenIndex: Int): String
    private external fun getTextSegmentTokenT0(contextPtr: Long, segmentIndex: Int, tokenIndex: Int): Long
    private external fun getTextSegmentTokenT1(contextPtr: Long, segmentIndex: Int, tokenIndex: Int): Long
    external fun getSystemInfo(): String
}
