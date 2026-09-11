package com.antoniopennino.thecut.delegates

import android.app.ProgressDialog
import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.antoniopennino.thecut.R
import com.antoniopennino.thecut.adapters.BulkSubtitlesAdapter
import com.antoniopennino.thecut.models.EditOperation
import com.antoniopennino.thecut.models.SubtitleCue
import com.antoniopennino.thecut.usecases.GenerateSubtitlesUseCase
import com.antoniopennino.thecut.utils.AutoSubtitleHelper
import com.antoniopennino.thecut.utils.SubtitleParser
import com.antoniopennino.thecut.viewmodels.VideoEditingViewModel
import com.antoniopennino.thecut.viewmodels.addOperation
import com.antoniopennino.thecut.viewmodels.updateOperation
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.slider.Slider
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Delegate responsible for speech transcription, subtitle studio management,
 * bulk cue editing, styling, and single cue fine-tuning.
 */
class SubtitleStudioDelegate(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val viewModel: VideoEditingViewModel,
    private val generateSubtitlesUseCase: GenerateSubtitlesUseCase,
    private val callbacks: Callbacks
) {
    interface Callbacks {
        fun getCurrentPlaybackPosition(): Long
        fun onSeekAndPlay(positionMs: Long)
        fun onSubtitleOpChanged(op: EditOperation.AddSubtitles)
        fun onSubtitleCuesUpdated(cues: List<SubtitleCue>)
        fun refreshSubtitlesUi()
    }

    /**
     * Generates subtitles on-device using offline whisper.cpp engine,
     * with automatic fallback to energy-based silence detection if needed.
     */
    fun generateAutoSubtitles(mediaUri: Uri, totalDurationMs: Long, wordLevel: Boolean = false) {
        val progressDialog = ProgressDialog(context).apply {
            setMessage(context.getString(R.string.str_analyzing_audio))
            setCancelable(false)
            show()
        }

        lifecycleOwner.lifecycleScope.launch {
            try {
                // 1. Try real offline speech-to-text with Whisper
                val result = generateSubtitlesUseCase(
                    context = context,
                    mediaUri = mediaUri,
                    language = "auto",
                    wordLevel = wordLevel
                )
                var cues = result.cues

                // 2. Fallback to AutoSubtitleHelper if Whisper returned nothing
                if (cues.isEmpty()) {
                    cues = suspendCancellableCoroutine { continuation ->
                        AutoSubtitleHelper.generateAutoSubtitles(
                            context = context,
                            mediaUri = mediaUri,
                            totalDurationMs = totalDurationMs
                        ) { fallbackCues ->
                            if (continuation.isActive) {
                                continuation.resume(fallbackCues)
                            }
                        }
                    }
                }

                try {
                    progressDialog.dismiss()
                } catch (ignored: Exception) {}

                if (cues.isEmpty()) {
                    Toast.makeText(context, R.string.str_toast_no_speech_detected, Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val srtContent = SubtitleParser.generateSrt(cues)
                val newSubOp = EditOperation.AddSubtitles(
                    subtitlesUri = mediaUri,
                    srtContent = srtContent,
                    fileName = context.getString(R.string.str_auto_subtitles_title, cues.size),
                    cues = cues,
                    fontSize = 22,
                    color = "#FFFFFF"
                )

                val existing = viewModel.project.value?.operations?.find { it is EditOperation.AddSubtitles } as? EditOperation.AddSubtitles
                if (existing != null) {
                    viewModel.updateOperation(newSubOp.copy(id = existing.id))
                } else {
                    viewModel.addOperation(newSubOp)
                }

                callbacks.onSubtitleOpChanged(newSubOp)
                callbacks.onSubtitleCuesUpdated(cues)
                callbacks.refreshSubtitlesUi()

                Toast.makeText(context, context.getString(R.string.str_toast_subtitles_generated, cues.size), Toast.LENGTH_SHORT).show()
                showBulkSubtitlesDialog()
            } catch (e: Throwable) {
                try {
                    progressDialog.dismiss()
                } catch (ignored: Exception) {}
                Toast.makeText(context, "Error generating subtitles: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun showBulkSubtitlesDialog() {
        val project = viewModel.project.value
        val subOp = project?.operations?.find { it is EditOperation.AddSubtitles } as? EditOperation.AddSubtitles
        if (subOp == null || subOp.cues.isEmpty()) {
            Toast.makeText(context, R.string.str_toast_no_subtitles_edit, Toast.LENGTH_SHORT).show()
            return
        }

        val bottomSheet = BottomSheetDialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_subtitles_bulk_edit, null)
        bottomSheet.setContentView(view)

        val tvBadge = view.findViewById<TextView>(R.id.tvBulkCueCountBadge)
        val btnClose = view.findViewById<ImageButton>(R.id.btnCloseBulkSubtitles)
        val btnAdd = view.findViewById<Button>(R.id.btnAddCue)
        val btnGlobalStyle = view.findViewById<Button>(R.id.btnToggleGlobalStyle)
        val btnShiftTiming = view.findViewById<Button>(R.id.btnToggleShiftTiming)
        val btnSearchReplace = view.findViewById<Button>(R.id.btnToggleSearchReplace)
        val panelGlobalStyle = view.findViewById<View>(R.id.panelGlobalStyle)
        val panelShiftTiming = view.findViewById<View>(R.id.panelShiftTiming)
        val panelSearchReplace = view.findViewById<View>(R.id.panelSearchReplace)
        val rvCues = view.findViewById<RecyclerView>(R.id.rvBulkSubtitleCues)
        val btnCancel = view.findViewById<Button>(R.id.btnCancelBulkSubtitles)
        val btnSave = view.findViewById<Button>(R.id.btnSaveBulkSubtitles)

        val workingCues = subOp.cues.toMutableList()
        var workingColor = subOp.color
        var workingFontSize = subOp.fontSize

        fun updateBadge() {
            tvBadge.text = context.getString(R.string.str_captions_count, workingCues.size)
        }
        updateBadge()

        lateinit var adapter: BulkSubtitlesAdapter
        adapter = BulkSubtitlesAdapter(
            cues = workingCues,
            onPlayCue = { cue ->
                callbacks.onSeekAndPlay(cue.startTimeMs)
            },
            onEditCueDetails = { index, cue ->
                showSingleSubtitleDialog(index) { updatedCue ->
                    workingCues[index] = updatedCue
                    adapter.notifyItemChanged(index)
                    updateBadge()
                }
            },
            onDeleteCue = { index ->
                if (index in workingCues.indices) {
                    workingCues.removeAt(index)
                    adapter.notifyItemRemoved(index)
                    adapter.notifyItemRangeChanged(index, workingCues.size - index)
                    updateBadge()
                }
            },
            onTextChanged = { index, newText ->
                if (index in workingCues.indices) {
                    workingCues[index] = workingCues[index].copy(text = newText)
                }
            }
        )

        rvCues.layoutManager = LinearLayoutManager(context)
        rvCues.adapter = adapter

        btnClose.setOnClickListener { bottomSheet.dismiss() }
        btnCancel.setOnClickListener { bottomSheet.dismiss() }

        // Panels toggle
        btnGlobalStyle.setOnClickListener {
            panelGlobalStyle.visibility = if (panelGlobalStyle.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            panelShiftTiming.visibility = View.GONE
            panelSearchReplace.visibility = View.GONE
        }
        btnShiftTiming.setOnClickListener {
            panelShiftTiming.visibility = if (panelShiftTiming.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            panelGlobalStyle.visibility = View.GONE
            panelSearchReplace.visibility = View.GONE
        }
        btnSearchReplace.setOnClickListener {
            panelSearchReplace.visibility = if (panelSearchReplace.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            panelGlobalStyle.visibility = View.GONE
            panelShiftTiming.visibility = View.GONE
        }

        // Add Cue
        btnAdd.setOnClickListener {
            val lastEnd = workingCues.lastOrNull()?.endTimeMs ?: 0L
            val newStart = lastEnd + 200L
            val newEnd = newStart + 2500L
            val newCue = SubtitleCue(newStart, newEnd, "Nuova battuta")
            workingCues.add(newCue)
            adapter.notifyItemInserted(workingCues.size - 1)
            rvCues.scrollToPosition(workingCues.size - 1)
            updateBadge()
        }

        // Global Style sliders & swatches
        val sliderFontSize = view.findViewById<Slider>(R.id.sliderGlobalFontSize)
        val tvFontSizeVal = view.findViewById<TextView>(R.id.tvGlobalFontSizeValue)
        sliderFontSize.value = workingFontSize.toFloat().coerceIn(12f, 48f)
        tvFontSizeVal.text = "$workingFontSize"
        sliderFontSize.addOnChangeListener { _, value, _ ->
            workingFontSize = value.toInt()
            tvFontSizeVal.text = "$workingFontSize"
        }

        val colorSwatches = mapOf(
            R.id.colorWhite to "#FFFFFF",
            R.id.colorYellow to "#FFEB3B",
            R.id.colorCyan to "#00E5FF",
            R.id.colorGold to "#FFD700",
            R.id.colorRose to "#FF4081"
        )
        for ((viewId, hexColor) in colorSwatches) {
            view.findViewById<View>(viewId)?.setOnClickListener {
                workingColor = hexColor
                Toast.makeText(context, R.string.str_toast_color_selected, Toast.LENGTH_SHORT).show()
            }
        }

        // Shift timing
        view.findViewById<Button>(R.id.btnShiftMinus200)?.setOnClickListener {
            val shifted = AutoSubtitleHelper.shiftAllCues(workingCues, -200L)
            adapter.updateData(shifted)
        }
        view.findViewById<Button>(R.id.btnShiftPlus200)?.setOnClickListener {
            val shifted = AutoSubtitleHelper.shiftAllCues(workingCues, 200L)
            adapter.updateData(shifted)
        }

        // Search & Replace
        val etSearch = view.findViewById<EditText>(R.id.etSearchWord)
        val etReplace = view.findViewById<EditText>(R.id.etReplaceWord)
        view.findViewById<Button>(R.id.btnApplyReplaceAll)?.setOnClickListener {
            val s = etSearch.text.toString()
            val r = etReplace.text.toString()
            if (s.isNotEmpty()) {
                val replaced = AutoSubtitleHelper.replaceTextInAllCues(workingCues, s, r)
                adapter.updateData(replaced)
                Toast.makeText(context, R.string.str_toast_replacements_done, Toast.LENGTH_SHORT).show()
            }
        }

        // Save
        btnSave.setOnClickListener {
            val finalCues = adapter.getCues()
            val updatedSrt = SubtitleParser.generateSrt(finalCues)
            val updatedOp = subOp.copy(
                cues = finalCues,
                srtContent = updatedSrt,
                fontSize = workingFontSize,
                color = workingColor
            )
            callbacks.onSubtitleOpChanged(updatedOp)
            callbacks.onSubtitleCuesUpdated(finalCues)
            callbacks.refreshSubtitlesUi()
            bottomSheet.dismiss()
            Toast.makeText(context, R.string.str_toast_subtitles_updated, Toast.LENGTH_SHORT).show()
        }

        bottomSheet.show()
    }

    fun showCurrentSubtitleCueDialog() {
        val project = viewModel.project.value
        val subOp = project?.operations?.find { it is EditOperation.AddSubtitles } as? EditOperation.AddSubtitles
        if (subOp == null || subOp.cues.isEmpty()) {
            Toast.makeText(context, R.string.str_toast_no_subtitles, Toast.LENGTH_SHORT).show()
            return
        }

        val currentPos = callbacks.getCurrentPlaybackPosition()
        val cueIndex = subOp.cues.indexOfFirst { currentPos in it.startTimeMs..it.endTimeMs }
            .takeIf { it != -1 } ?: 0

        showSingleSubtitleDialog(cueIndex)
    }

    fun showSingleSubtitleDialog(
        cueIndex: Int,
        onCueUpdated: ((SubtitleCue) -> Unit)? = null
    ) {
        val project = viewModel.project.value
        val subOp = project?.operations?.find { it is EditOperation.AddSubtitles } as? EditOperation.AddSubtitles ?: return
        if (cueIndex !in subOp.cues.indices) return

        val targetCue = subOp.cues[cueIndex]
        val dialog = BottomSheetDialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_subtitle_single_edit, null)
        dialog.setContentView(view)

        val tvTitle = view.findViewById<TextView>(R.id.tvSingleCueTitle)
        val tvTimeBadge = view.findViewById<TextView>(R.id.tvSingleCueTime)
        val btnClose = view.findViewById<ImageButton>(R.id.btnCloseSingleCue)
        val etText = view.findViewById<EditText>(R.id.etSingleCueText)
        val tvStartVal = view.findViewById<TextView>(R.id.tvStartTimeVal)
        val tvEndVal = view.findViewById<TextView>(R.id.tvEndTimeVal)
        val btnStartMinus = view.findViewById<ImageButton>(R.id.btnStartMinus100)
        val btnStartPlus = view.findViewById<ImageButton>(R.id.btnStartPlus100)
        val btnEndMinus = view.findViewById<ImageButton>(R.id.btnEndMinus100)
        val btnEndPlus = view.findViewById<ImageButton>(R.id.btnEndPlus100)
        val btnPreview = view.findViewById<Button>(R.id.btnPreviewSingleCue)
        val btnSplit = view.findViewById<Button>(R.id.btnSplitSingleCue)
        val btnDelete = view.findViewById<Button>(R.id.btnDeleteSingleCue)
        val btnSave = view.findViewById<Button>(R.id.btnSaveSingleCue)

        var currentStart = targetCue.startTimeMs
        var currentEnd = targetCue.endTimeMs

        tvTitle.text = context.getString(R.string.str_cue_number, cueIndex + 1)
        etText.setText(targetCue.text)

        fun updateTimes() {
            val sStr = SubtitleParser.formatShortTime(currentStart)
            val eStr = SubtitleParser.formatShortTime(currentEnd)
            tvStartVal.text = sStr
            tvEndVal.text = eStr
            tvTimeBadge.text = "$sStr → $eStr"
        }
        updateTimes()

        btnStartMinus.setOnClickListener {
            currentStart = (currentStart - 100L).coerceAtLeast(0L)
            updateTimes()
        }
        btnStartPlus.setOnClickListener {
            currentStart = (currentStart + 100L).coerceAtMost(currentEnd - 100L)
            updateTimes()
        }
        btnEndMinus.setOnClickListener {
            currentEnd = (currentEnd - 100L).coerceAtLeast(currentStart + 100L)
            updateTimes()
        }
        btnEndPlus.setOnClickListener {
            currentEnd += 100L
            updateTimes()
        }

        btnPreview.setOnClickListener {
            callbacks.onSeekAndPlay(currentStart)
        }

        btnSplit.setOnClickListener {
            val splitCues = AutoSubtitleHelper.splitCue(subOp.cues, cueIndex)
            val updatedOp = subOp.copy(cues = splitCues, srtContent = SubtitleParser.generateSrt(splitCues))
            callbacks.onSubtitleOpChanged(updatedOp)
            callbacks.onSubtitleCuesUpdated(splitCues)
            callbacks.refreshSubtitlesUi()
            dialog.dismiss()
            Toast.makeText(context, R.string.str_toast_cue_split, Toast.LENGTH_SHORT).show()
        }

        btnDelete.setOnClickListener {
            val newCues = subOp.cues.toMutableList()
            if (cueIndex in newCues.indices) {
                newCues.removeAt(cueIndex)
                val updatedOp = subOp.copy(cues = newCues, srtContent = SubtitleParser.generateSrt(newCues))
                callbacks.onSubtitleOpChanged(updatedOp)
                callbacks.onSubtitleCuesUpdated(newCues)
                callbacks.refreshSubtitlesUi()
            }
            dialog.dismiss()
            Toast.makeText(context, R.string.str_toast_cue_deleted, Toast.LENGTH_SHORT).show()
        }

        btnSave.setOnClickListener {
            val updatedCue = SubtitleCue(currentStart, currentEnd, etText.text.toString())
            val newCues = subOp.cues.toMutableList()
            if (cueIndex in newCues.indices) {
                newCues[cueIndex] = updatedCue
                val updatedOp = subOp.copy(cues = newCues, srtContent = SubtitleParser.generateSrt(newCues))
                callbacks.onSubtitleOpChanged(updatedOp)
                callbacks.onSubtitleCuesUpdated(newCues)
                callbacks.refreshSubtitlesUi()
                onCueUpdated?.invoke(updatedCue)
            }
            dialog.dismiss()
            Toast.makeText(context, R.string.str_toast_cue_saved, Toast.LENGTH_SHORT).show()
        }

        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
}
