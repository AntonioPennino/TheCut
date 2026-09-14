package com.antoniopennino.thecut.delegates

import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.antoniopennino.thecut.R
import com.google.android.material.bottomsheet.BottomSheetDialog

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
}
