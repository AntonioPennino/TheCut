package com.antoniopennino.thecut.delegates

import android.graphics.Color
import android.graphics.drawable.AnimationDrawable
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.antoniopennino.thecut.R
import com.antoniopennino.thecut.models.EditOperation
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.slider.Slider
import java.util.Locale

/**
 * Hosts per-clip effect pickers (starting with color filters). Project mutation
 * and player-preview application are delegated to the host via [Callbacks], so
 * this class only owns the dialog UI.
 *
 * Extracted from VideoEditingActivity.
 */
class ClipEffectsDelegate(
    private val activity: AppCompatActivity,
    private val callbacks: Callbacks
) {
    interface Callbacks {
        /** Currently applied filter name for [clipIndex], or null/"none" if none. */
        fun getExistingColorFilterName(clipIndex: Int): String?
        /** Persist the chosen [filterName] for [clipIndex]. */
        fun setColorFilter(clipIndex: Int, filterName: String)
        /** Apply [filterName] to the live player preview. */
        fun applyColorFilterPreview(filterName: String)
        /** Number of clips/chunks in the sequence (for "apply to all"). */
        fun getChunkCount(): Int
        /** Re-render the timeline tracks after committing changes. */
        fun renderTracks()

        /** Existing transition at [index] as (type, durationMs), or null if none. */
        fun getExistingTransition(index: Int): Pair<String, Long>?
        /** Apply/update/remove the transition at [index] ("none" removes it). */
        fun applyTransition(index: Int, type: String, durationMs: Long)
        /** Number of transition slots between clips (chunks - 1). */
        fun getTransitionCount(): Int

        /** Existing adjustment for [clipIndex], or null if none set. */
        fun getExistingAdjust(clipIndex: Int): EditOperation.Adjust?
        /** Persist [adjust] for [clipIndex]. */
        fun setAdjust(clipIndex: Int, adjust: EditOperation.Adjust)
        /** Apply the current color filter + [adjust] to the live player preview. */
        fun applyAdjustPreview(clipIndex: Int, adjust: EditOperation.Adjust)
    }

    fun showColorFilters(clipIndex: Int) {
        val bottomSheet = BottomSheetDialog(activity)
        val view = activity.layoutInflater.inflate(R.layout.dialog_color_filters, null)
        bottomSheet.setContentView(view)

        val filtersList = view.findViewById<LinearLayout>(R.id.colorFiltersList)
        var activeFilterName = callbacks.getExistingColorFilterName(clipIndex) ?: "none"

        val filters = listOf(
            Pair("none", "None"),
            Pair("vintage", "Vintage"),
            Pair("warm", "Warm"),
            Pair("cool", "Cool"),
            Pair("contrast", "Contrast"),
            Pair("monochrome", "B&W"),
            Pair("vignette", "Vignette"),
            Pair("negative", "Negative"),
            Pair("crossprocess", "Cross P")
        )

        val itemBgs = mutableMapOf<String, FrameLayout>()
        val itemTvShorts = mutableMapOf<String, TextView>()

        fun refreshSelectionStates() {
            for ((id, bgFrame) in itemBgs) {
                val tvShort = itemTvShorts[id]
                val isSelected = (id == activeFilterName)
                if (isSelected) {
                    bgFrame.setBackgroundResource(R.drawable.bg_aspect_ratio_selected)
                    bgFrame.foreground = ContextCompat.getDrawable(activity, R.drawable.bg_transition_border_selected)
                    tvShort?.setTextColor(Color.WHITE)
                } else {
                    bgFrame.setBackgroundResource(R.drawable.bg_aspect_ratio_item)
                    bgFrame.foreground = null
                    tvShort?.setTextColor(ContextCompat.getColor(activity, R.color.textColor))
                }
            }
        }

        for ((filterId, displayName) in filters) {
            val itemView = activity.layoutInflater.inflate(R.layout.item_color_filter_option, filtersList, false)
            val tvName = itemView.findViewById<TextView>(R.id.filterName)
            val bg = itemView.findViewById<FrameLayout>(R.id.filterIconBg)
            val previewImage = itemView.findViewById<ImageView>(R.id.filterPreviewImage)
            val tvShort = itemView.findViewById<TextView>(R.id.filterShortName)

            itemBgs[filterId] = bg
            itemTvShorts[filterId] = tvShort

            tvName.text = displayName
            bg.clipToOutline = true

            if (filterId == "none") {
                previewImage.visibility = View.GONE
                tvShort.visibility = View.VISIBLE
                tvShort.text = "✖"
            } else {
                previewImage.visibility = View.VISIBLE
                tvShort.visibility = View.GONE
                val resName = "filter_preview_${filterId.lowercase()}"
                val resId = activity.resources.getIdentifier(resName, "drawable", activity.packageName)
                if (resId != 0) {
                    previewImage.setImageResource(resId)
                }
            }

            itemView.setOnClickListener {
                activeFilterName = filterId
                refreshSelectionStates()
                callbacks.setColorFilter(clipIndex, filterId)
                callbacks.applyColorFilterPreview(filterId)
            }

            filtersList.addView(itemView)
        }

        refreshSelectionStates()

        view.findViewById<View>(R.id.btnApplyToAll)?.setOnClickListener {
            val chunkCount = callbacks.getChunkCount()
            for (i in 0 until chunkCount) {
                callbacks.setColorFilter(i, activeFilterName)
            }
            callbacks.applyColorFilterPreview(activeFilterName)
            callbacks.renderTracks()
            bottomSheet.dismiss()
        }

        view.findViewById<View>(R.id.btnDone)?.setOnClickListener {
            callbacks.renderTracks()
            bottomSheet.dismiss()
        }

        bottomSheet.show()
    }

    fun showTransition(transitionIndex: Int) {
        val bottomSheet = BottomSheetDialog(activity)
        val view = activity.layoutInflater.inflate(R.layout.dialog_transitions, null)
        bottomSheet.setContentView(view)

        val transitionsList = view.findViewById<LinearLayout>(R.id.transitionsList)
        val existing = callbacks.getExistingTransition(transitionIndex)
        var activeTransitionType = existing?.first ?: "none"
        var activeDurationMs = existing?.second ?: 1000L

        val sliderDuration = view.findViewById<Slider>(R.id.sliderTransitionDuration)
        val tvDurationVal = view.findViewById<TextView>(R.id.tvTransitionDurationValue)

        val durationSec = (activeDurationMs / 1000.0f).coerceIn(0.1f, 3.0f)
        sliderDuration?.value = (Math.round(durationSec * 10f) / 10f).coerceIn(0.1f, 3.0f)
        tvDurationVal?.text = String.format(Locale.US, "%.1fs", durationSec)

        sliderDuration?.addOnChangeListener { _, value, fromUser ->
            activeDurationMs = (value * 1000f).toLong()
            tvDurationVal?.text = String.format(Locale.US, "%.1fs", value)
            if (fromUser && activeTransitionType != "none") {
                callbacks.applyTransition(transitionIndex, activeTransitionType, activeDurationMs)
            }
        }

        val transitions = listOf(
            Pair("none", "None"),
            Pair("fade", "Fade"),
            Pair("fadeblack", "Fade Black"),
            Pair("fadewhite", "Fade White"),
            Pair("dissolve", "Dissolve"),
            Pair("wipeleft", "Wipe L"),
            Pair("wiperight", "Wipe R"),
            Pair("wipeup", "Wipe Up"),
            Pair("wipedown", "Wipe Down"),
            Pair("slideleft", "Slide L"),
            Pair("slideright", "Slide R"),
            Pair("slideup", "Slide Up"),
            Pair("slidedown", "Slide Down"),
            Pair("coverleft", "Cover L"),
            Pair("coverright", "Cover R"),
            Pair("circlecrop", "Circle"),
            Pair("rectcrop", "Box"),
            Pair("zoomin", "Zoom In"),
            Pair("pixelize", "Pixelize"),
            Pair("hlslice", "H-Slice"),
            Pair("vuslice", "V-Slice")
        )

        val itemBgs = mutableMapOf<String, FrameLayout>()
        val itemTvShorts = mutableMapOf<String, TextView>()

        fun refreshSelectionStates() {
            for ((typeKey, bgFrame) in itemBgs) {
                val tvShort = itemTvShorts[typeKey]
                val isSelected = (typeKey == activeTransitionType)
                if (isSelected) {
                    bgFrame.setBackgroundResource(R.drawable.bg_aspect_ratio_selected)
                    bgFrame.foreground = ContextCompat.getDrawable(activity, R.drawable.bg_transition_border_selected)
                    tvShort?.setTextColor(Color.WHITE)
                } else {
                    bgFrame.setBackgroundResource(R.drawable.bg_aspect_ratio_item)
                    bgFrame.foreground = null
                    tvShort?.setTextColor(ContextCompat.getColor(activity, R.color.textColor))
                }
            }
        }

        for ((type, name) in transitions) {
            val itemView = activity.layoutInflater.inflate(R.layout.item_transition_option, transitionsList, false)
            val tvName = itemView.findViewById<TextView>(R.id.transitionName)
            val tvShort = itemView.findViewById<TextView>(R.id.transitionShortName)
            val bg = itemView.findViewById<FrameLayout>(R.id.transitionIconBg)

            itemBgs[type] = bg
            itemTvShorts[type] = tvShort

            tvName.text = name
            bg.clipToOutline = true

            val ivPreview = itemView.findViewById<ImageView>(R.id.transitionPreviewImage)
            val startDrawable = ContextCompat.getDrawable(activity, R.drawable.trans_frame_start)
            val endDrawable = ContextCompat.getDrawable(activity, R.drawable.trans_frame_end)

            if (startDrawable != null && endDrawable != null) {
                if (type == "none") {
                    ivPreview.setImageDrawable(ContextCompat.getDrawable(activity, R.drawable.ic_close_24))
                    ivPreview.visibility = View.VISIBLE
                    tvShort.visibility = View.GONE
                } else {
                    val animation = AnimationDrawable().apply {
                        isOneShot = false
                    }
                    val frame1Res = activity.resources.getIdentifier("trans_preview_${type}_1", "drawable", activity.packageName)
                    val frame2Res = activity.resources.getIdentifier("trans_preview_${type}_2", "drawable", activity.packageName)
                    val frame3Res = activity.resources.getIdentifier("trans_preview_${type}_3", "drawable", activity.packageName)

                    if (frame1Res != 0 && frame2Res != 0 && frame3Res != 0) {
                        val f1 = ContextCompat.getDrawable(activity, frame1Res)
                        val f2 = ContextCompat.getDrawable(activity, frame2Res)
                        val f3 = ContextCompat.getDrawable(activity, frame3Res)

                        if (f1 != null && f2 != null && f3 != null) {
                            animation.addFrame(startDrawable, 600)
                            animation.addFrame(f1, 100)
                            animation.addFrame(f2, 100)
                            animation.addFrame(f3, 100)
                            animation.addFrame(endDrawable, 600)

                            ivPreview.setImageDrawable(animation)
                            ivPreview.visibility = View.VISIBLE
                            tvShort.visibility = View.GONE
                            ivPreview.post { animation.start() }
                        } else {
                            ivPreview.visibility = View.GONE
                            tvShort.visibility = View.VISIBLE
                            tvShort.text = name.substring(0, minOf(2, name.length)).uppercase()
                        }
                    } else {
                        ivPreview.visibility = View.GONE
                        tvShort.visibility = View.VISIBLE
                        tvShort.text = name.substring(0, minOf(2, name.length)).uppercase()
                    }
                }
            } else {
                ivPreview.visibility = View.GONE
                tvShort.visibility = View.VISIBLE
                tvShort.text = name.substring(0, minOf(2, name.length)).uppercase()
            }

            itemView.setOnClickListener {
                activeTransitionType = type
                refreshSelectionStates()
                callbacks.applyTransition(transitionIndex, type, activeDurationMs)
            }

            transitionsList.addView(itemView)
        }

        refreshSelectionStates()

        view.findViewById<View>(R.id.btnApplyToAll)?.setOnClickListener {
            val transitionsCount = callbacks.getTransitionCount()
            for (i in 0 until transitionsCount) {
                callbacks.applyTransition(i, activeTransitionType, activeDurationMs)
            }
            callbacks.renderTracks()
            bottomSheet.dismiss()
        }

        view.findViewById<View>(R.id.btnDone)?.setOnClickListener {
            callbacks.renderTracks()
            bottomSheet.dismiss()
        }

        bottomSheet.show()
    }

    fun showAdjust(clipIndex: Int) {
        val bottomSheet = BottomSheetDialog(activity)
        val view = activity.layoutInflater.inflate(R.layout.dialog_adjustments, null)
        bottomSheet.setContentView(view)

        val optionsList = view.findViewById<LinearLayout>(R.id.adjustOptionsList)
        val slider = view.findViewById<Slider>(R.id.adjustSlider)
        val valueLabel = view.findViewById<TextView>(R.id.adjustValueLabel)

        var localAdjust = callbacks.getExistingAdjust(clipIndex) ?: EditOperation.Adjust(index = clipIndex)
        var selectedOptionId = "brightness" // Default selection

        val options = listOf(
            Pair("reset", "Reset All"),
            Pair("brightness", "Brightness"),
            Pair("contrast", "Contrast"),
            Pair("warmth", "Warmth"),
            Pair("shadow", "Shadow"),
            Pair("highlights", "Highlights"),
            Pair("saturation", "Saturation"),
            Pair("exposure", "Exposure"),
            Pair("sharpen", "Sharpen"),
            Pair("vignette", "Vignette")
        )

        val itemBgs = mutableMapOf<String, FrameLayout>()
        val itemTvShorts = mutableMapOf<String, TextView>()
        val itemTvNames = mutableMapOf<String, TextView>()

        fun getValForOption(id: String): Int {
            return when (id) {
                "brightness" -> localAdjust.brightness
                "contrast" -> localAdjust.contrast
                "warmth" -> localAdjust.warmth
                "shadow" -> localAdjust.shadow
                "highlights" -> localAdjust.highlights
                "saturation" -> localAdjust.saturation
                "exposure" -> localAdjust.exposure
                "sharpen" -> localAdjust.sharpen
                "vignette" -> localAdjust.vignette
                else -> 0
            }
        }

        fun refreshSelectionStates() {
            for ((id, bgFrame) in itemBgs) {
                val tvName = itemTvNames[id]
                val isSelected = (id == selectedOptionId)
                val isModified = (id != "reset" && getValForOption(id) != 0)

                if (isSelected) {
                    bgFrame.setBackgroundResource(R.drawable.bg_aspect_ratio_selected)
                    bgFrame.foreground = ContextCompat.getDrawable(activity, R.drawable.bg_transition_border_selected)
                    tvName?.setTextColor(ContextCompat.getColor(activity, R.color.colorPrimary))
                } else if (isModified) {
                    bgFrame.setBackgroundResource(R.drawable.bg_aspect_ratio_item)
                    bgFrame.foreground = ContextCompat.getDrawable(activity, R.drawable.bg_transition_border_selected)
                    tvName?.setTextColor(ContextCompat.getColor(activity, R.color.textColor))
                } else {
                    bgFrame.setBackgroundResource(R.drawable.bg_aspect_ratio_item)
                    bgFrame.foreground = null
                    tvName?.setTextColor(ContextCompat.getColor(activity, R.color.textColor))
                }
            }
        }

        fun updateSliderForSelection() {
            if (selectedOptionId == "reset") {
                slider.visibility = View.INVISIBLE
                valueLabel.text = activity.getString(R.string.str_reset_all)
            } else {
                slider.visibility = View.VISIBLE
                val currentVal = getValForOption(selectedOptionId)
                slider.value = currentVal.toFloat()
                valueLabel.text = "${selectedOptionId.replaceFirstChar { it.uppercase() }}: $currentVal"
            }
        }

        for ((id, displayName) in options) {
            val itemView = activity.layoutInflater.inflate(R.layout.item_adjust_option, optionsList, false)
            val tvName = itemView.findViewById<TextView>(R.id.adjustName)
            val bg = itemView.findViewById<FrameLayout>(R.id.adjustIconBg)
            val iconImage = itemView.findViewById<ImageView>(R.id.adjustIconImage)
            val tvShort = itemView.findViewById<TextView>(R.id.adjustShortName)

            itemBgs[id] = bg
            itemTvShorts[id] = tvShort
            itemTvNames[id] = tvName

            tvName.text = displayName
            bg.clipToOutline = true

            val resName = "ic_${id.lowercase()}_24"
            val resId = activity.resources.getIdentifier(resName, "drawable", activity.packageName)
            if (resId != 0) {
                iconImage.setImageResource(resId)
                iconImage.visibility = View.VISIBLE
                tvShort.visibility = View.GONE
            } else {
                iconImage.visibility = View.GONE
                tvShort.visibility = View.VISIBLE
                tvShort.text = displayName.substring(0, minOf(2, displayName.length)).uppercase()
            }

            itemView.setOnClickListener {
                if (id == "reset") {
                    localAdjust = EditOperation.Adjust(index = clipIndex)
                    callbacks.setAdjust(clipIndex, localAdjust)
                    callbacks.applyAdjustPreview(clipIndex, localAdjust)
                    refreshSelectionStates()
                    updateSliderForSelection()
                } else {
                    selectedOptionId = id
                    refreshSelectionStates()
                    updateSliderForSelection()
                }
            }

            optionsList.addView(itemView)
        }

        slider.addOnChangeListener { _, value, fromUser ->
            if (fromUser && selectedOptionId != "reset_all") {
                val intVal = value.toInt()
                localAdjust = when (selectedOptionId) {
                    "brightness" -> localAdjust.copy(brightness = intVal)
                    "contrast" -> localAdjust.copy(contrast = intVal)
                    "warmth" -> localAdjust.copy(warmth = intVal)
                    "shadow" -> localAdjust.copy(shadow = intVal)
                    "highlights" -> localAdjust.copy(highlights = intVal)
                    "saturation" -> localAdjust.copy(saturation = intVal)
                    "exposure" -> localAdjust.copy(exposure = intVal)
                    "sharpen" -> localAdjust.copy(sharpen = intVal)
                    "vignette" -> localAdjust.copy(vignette = intVal)
                    else -> localAdjust
                }

                valueLabel.text = "${selectedOptionId.replaceFirstChar { it.uppercase() }}: $intVal"

                callbacks.setAdjust(clipIndex, localAdjust)
                callbacks.applyAdjustPreview(clipIndex, localAdjust)
                refreshSelectionStates()
            }
        }

        refreshSelectionStates()
        updateSliderForSelection()

        view.findViewById<View>(R.id.btnApplyToAll)?.setOnClickListener {
            val chunkCount = callbacks.getChunkCount()
            for (i in 0 until chunkCount) {
                callbacks.setAdjust(i, localAdjust.copy(index = i))
            }
            callbacks.applyAdjustPreview(clipIndex, localAdjust)
            callbacks.renderTracks()
            bottomSheet.dismiss()
        }

        view.findViewById<View>(R.id.btnDone)?.setOnClickListener {
            callbacks.renderTracks()
            bottomSheet.dismiss()
        }

        bottomSheet.show()
    }
}
