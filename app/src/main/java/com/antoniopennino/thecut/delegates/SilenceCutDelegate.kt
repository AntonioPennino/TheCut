package com.antoniopennino.thecut.delegates

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.antoniopennino.thecut.R
import com.antoniopennino.thecut.models.EditOperation
import com.antoniopennino.thecut.usecases.ApplySilenceCutUseCase
import com.antoniopennino.thecut.utils.SilenceInterval
import com.antoniopennino.thecut.utils.SpeechInterval
import com.antoniopennino.thecut.viewmodels.VideoEditingViewModel
import com.antoniopennino.thecut.viewmodels.applySilenceCut
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.slider.Slider
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Delegate responsible for silence detection, interactive parameters tuning,
 * and applying automatic silence cutting across sequence clips.
 */
class SilenceCutDelegate(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val viewModel: VideoEditingViewModel,
    private val applySilenceCutUseCase: ApplySilenceCutUseCase = ApplySilenceCutUseCase(),
    private val callbacks: Callbacks
) {
    interface Callbacks {
        fun getSelectedVideoIndex(): Int?
        fun getSequenceItems(): List<EditOperation.MergeItem>
        fun onSilencesApplied()
    }

    fun showCutSilencesDialog() {
        val targetIndex = callbacks.getSelectedVideoIndex() ?: 0
        val sequenceItems = callbacks.getSequenceItems()
        if (targetIndex !in sequenceItems.indices) {
            Toast.makeText(context, R.string.str_toast_no_video_for_silence, Toast.LENGTH_SHORT).show()
            return
        }

        val targetClip = sequenceItems[targetIndex]
        val clipStartMs = targetClip.trimStartMs
        val clipEndMs = targetClip.trimEndMs
        val clipDurationMs = targetClip.trimmedDurationMs

        val bottomSheet = BottomSheetDialog(context)
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_cut_silences, null)
        bottomSheet.setContentView(view)

        val btnClose = view.findViewById<ImageButton>(R.id.btnCloseCutSilences)
        val sliderThreshold = view.findViewById<Slider>(R.id.sliderNoiseThreshold)
        val tvThresholdVal = view.findViewById<TextView>(R.id.tvNoiseThresholdValue)
        val sliderMinDuration = view.findViewById<Slider>(R.id.sliderMinSilenceDuration)
        val tvMinDurationVal = view.findViewById<TextView>(R.id.tvMinSilenceValue)
        val sliderPadding = view.findViewById<Slider>(R.id.sliderSafetyPadding)
        val tvPaddingVal = view.findViewById<TextView>(R.id.tvSafetyPaddingValue)
        val tvStatus = view.findViewById<TextView>(R.id.tvSilenceStatus)
        val pbAnalyzing = view.findViewById<ProgressBar>(R.id.pbSilenceAnalyzing)
        val btnAnalyze = view.findViewById<Button>(R.id.btnAnalyzeSilences)
        val btnApply = view.findViewById<Button>(R.id.btnApplyCutSilences)

        var detectedSilences: List<SilenceInterval> = emptyList()
        var calculatedSpeechSegments: List<SpeechInterval> = emptyList()

        sliderThreshold.addOnChangeListener { _, value, _ ->
            tvThresholdVal.text = "${value.toInt()} dB"
            btnApply.isEnabled = false
        }
        sliderMinDuration.addOnChangeListener { _, value, _ ->
            tvMinDurationVal.text = String.format(Locale.US, "%.2f s", value)
            btnApply.isEnabled = false
        }
        sliderPadding.addOnChangeListener { _, value, _ ->
            tvPaddingVal.text = "${value.toInt()} ms"
            if (detectedSilences.isNotEmpty()) {
                calculatedSpeechSegments = applySilenceCutUseCase.recalculate(
                    clipStartMs, clipEndMs, detectedSilences, value.toLong()
                )
                val totalRemovedMs = (clipDurationMs - calculatedSpeechSegments.sumOf { it.durationMs }).coerceAtLeast(0L)
                tvStatus.text = context.getString(
                    R.string.str_silence_preview,
                    detectedSilences.size,
                    String.format(Locale.US, "%.2f", totalRemovedMs / 1000.0),
                    calculatedSpeechSegments.size
                )
                btnApply.isEnabled = detectedSilences.isNotEmpty()
            }
        }

        btnClose.setOnClickListener { bottomSheet.dismiss() }

        fun runAnalysis() {
            pbAnalyzing.visibility = View.VISIBLE
            btnAnalyze.isEnabled = false
            btnApply.isEnabled = false
            tvStatus.text = context.getString(R.string.str_silence_scanning)

            val thresholdDb = sliderThreshold.value.toInt()
            val minDurationSec = sliderMinDuration.value
            val paddingMs = sliderPadding.value.toLong()

            val params = ApplySilenceCutUseCase.Params(
                mediaUri = targetClip.uri,
                clipStartMs = clipStartMs,
                clipEndMs = clipEndMs,
                totalDurationMs = targetClip.durationMs,
                thresholdDb = thresholdDb,
                minDurationSec = minDurationSec,
                paddingMs = paddingMs
            )

            lifecycleOwner.lifecycleScope.launch {
                val result = applySilenceCutUseCase.detect(context, params)
                pbAnalyzing.visibility = View.GONE
                btnAnalyze.isEnabled = true
                detectedSilences = result.detectedSilences
                calculatedSpeechSegments = result.speechSegments

                if (detectedSilences.isEmpty()) {
                    tvStatus.text = context.getString(R.string.str_silence_none)
                    btnApply.isEnabled = false
                } else {
                    val totalRemovedMs = (clipDurationMs - calculatedSpeechSegments.sumOf { it.durationMs }).coerceAtLeast(0L)
                    val removedSec = totalRemovedMs / 1000.0
                    tvStatus.text = context.getString(
                        R.string.str_silence_result,
                        detectedSilences.size,
                        String.format(Locale.US, "%.2f", removedSec),
                        calculatedSpeechSegments.size
                    )
                    btnApply.isEnabled = true
                }
            }
        }

        btnAnalyze.setOnClickListener { runAnalysis() }

        btnApply.setOnClickListener {
            if (calculatedSpeechSegments.isNotEmpty()) {
                viewModel.applySilenceCut(targetIndex, calculatedSpeechSegments, targetClip.uri, targetClip.durationMs)
                bottomSheet.dismiss()
                val totalRemovedMs = (clipDurationMs - calculatedSpeechSegments.sumOf { it.durationMs }).coerceAtLeast(0L)
                val removedSec = totalRemovedMs / 1000.0
                Toast.makeText(
                    context,
                    context.getString(R.string.str_toast_silences_removed, detectedSilences.size, String.format(Locale.US, "%.1f", removedSec)),
                    Toast.LENGTH_LONG
                ).show()
                callbacks.onSilencesApplied()
            }
        }

        bottomSheet.show()
    }
}
