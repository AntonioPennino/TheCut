package com.antoniopennino.thecut.delegates

import android.graphics.Color
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.antoniopennino.thecut.R
import com.antoniopennino.thecut.models.EditOperation.MaskConfig
import com.antoniopennino.thecut.models.EditOperation.MaskShape
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider

/**
 * Non-modal bottom sheet for editing a mask (shape, invert, feather, size,
 * rotation) while the preview stays interactive underneath.
 *
 * What the mask is attached to — the main video clip or an image overlay — is
 * abstracted behind [MaskTarget], so this class only owns the sheet UI.
 *
 * Extracted from VideoEditingActivity.
 */
class MaskEditorDelegate(private val activity: AppCompatActivity) {

    /** The object whose mask is being edited. */
    interface MaskTarget {
        /** Mask to start editing from. */
        val initialMask: MaskConfig
        /** Called once when the sheet opens, to put the overlay in editing mode. */
        fun onEditingStarted(mask: MaskConfig)
        /** Live preview of every change. */
        fun preview(mask: MaskConfig)
        /** Persist the final mask when the sheet is dismissed. */
        fun commit(mask: MaskConfig)
    }

    fun show(target: MaskTarget) {
        val bottomSheet = BottomSheetDialog(activity)
        val view = activity.layoutInflater.inflate(R.layout.bottom_sheet_mask, null)
        bottomSheet.setContentView(view)

        val btnNone = view.findViewById<LinearLayout>(R.id.btnMaskNone)
        val btnSplit = view.findViewById<LinearLayout>(R.id.btnMaskSplit)
        val btnShutter = view.findViewById<LinearLayout>(R.id.btnMaskShutter)
        val btnEllipse = view.findViewById<LinearLayout>(R.id.btnMaskEllipse)
        val btnRectangle = view.findViewById<LinearLayout>(R.id.btnMaskRectangle)
        val btnHeart = view.findViewById<LinearLayout>(R.id.btnMaskHeart)
        val btnStar = view.findViewById<LinearLayout>(R.id.btnMaskStar)

        val bgNone = view.findViewById<FrameLayout>(R.id.bgMaskNone)
        val bgSplit = view.findViewById<FrameLayout>(R.id.bgMaskSplit)
        val bgShutter = view.findViewById<FrameLayout>(R.id.bgMaskShutter)
        val bgEllipse = view.findViewById<FrameLayout>(R.id.bgMaskEllipse)
        val bgRectangle = view.findViewById<FrameLayout>(R.id.bgMaskRectangle)
        val bgHeart = view.findViewById<FrameLayout>(R.id.bgMaskHeart)
        val bgStar = view.findViewById<FrameLayout>(R.id.bgMaskStar)

        val imgNone = view.findViewById<ImageView>(R.id.imgMaskNone)
        val imgSplit = view.findViewById<ImageView>(R.id.imgMaskSplit)
        val imgShutter = view.findViewById<ImageView>(R.id.imgMaskShutter)
        val imgEllipse = view.findViewById<ImageView>(R.id.imgMaskEllipse)
        val imgRectangle = view.findViewById<ImageView>(R.id.imgMaskRectangle)
        val imgHeart = view.findViewById<ImageView>(R.id.imgMaskHeart)
        val imgStar = view.findViewById<ImageView>(R.id.imgMaskStar)

        val switchInvert = view.findViewById<MaterialSwitch>(R.id.switchInvertMask)
        val sliderFeather = view.findViewById<Slider>(R.id.sliderMaskFeather)
        val sliderSize = view.findViewById<Slider>(R.id.sliderMaskSize)
        val sliderRotation = view.findViewById<Slider>(R.id.sliderMaskRotation)

        val tvFeatherValue = view.findViewById<TextView>(R.id.tvFeatherValue)
        val tvSizeValue = view.findViewById<TextView>(R.id.tvSizeValue)
        val tvRotationValue = view.findViewById<TextView>(R.id.tvRotationValue)
        val btnResetRotation = view.findViewById<TextView>(R.id.btnResetRotation)

        val btnResetMask = view.findViewById<View>(R.id.btnResetMask)
        val btnDoneMask = view.findViewById<View>(R.id.btnDoneMaskSheet)

        var currentMask = target.initialMask

        switchInvert.isChecked = currentMask.isInverted
        sliderFeather?.value = currentMask.feather.coerceIn(0f, 100f)
        tvFeatherValue?.text = "${currentMask.feather.toInt()}%"

        val avgScale = ((currentMask.relativeWidth + currentMask.relativeHeight) / 2f * 200f).coerceIn(10f, 200f)
        sliderSize?.value = avgScale
        tvSizeValue?.text = "${avgScale.toInt()}%"

        sliderRotation?.value = currentMask.rotationAngle.coerceIn(-180f, 180f)
        tvRotationValue?.text = "${currentMask.rotationAngle.toInt()}°"

        fun highlightShape(selectedShape: MaskShape) {
            val shapeMap = mapOf(
                MaskShape.NONE to Pair(bgNone, imgNone),
                MaskShape.SPLIT to Pair(bgSplit, imgSplit),
                MaskShape.SHUTTER to Pair(bgShutter, imgShutter),
                MaskShape.ELLIPSE to Pair(bgEllipse, imgEllipse),
                MaskShape.RECTANGLE to Pair(bgRectangle, imgRectangle),
                MaskShape.HEART to Pair(bgHeart, imgHeart),
                MaskShape.STAR to Pair(bgStar, imgStar)
            )
            val activeColor = Color.WHITE
            val inactiveColor = activity.getColor(R.color.toolTextInactive)

            for ((shape, views) in shapeMap) {
                val bg = views.first ?: continue
                val img = views.second ?: continue
                if (shape == selectedShape) {
                    bg.setBackgroundResource(R.drawable.bg_aspect_ratio_selected)
                    img.setColorFilter(activeColor)
                } else {
                    bg.setBackgroundResource(R.drawable.bg_aspect_ratio_item)
                    img.setColorFilter(inactiveColor)
                }
            }
        }

        highlightShape(currentMask.shape)
        target.onEditingStarted(currentMask)

        fun applyMaskToView(newMask: MaskConfig) {
            currentMask = newMask
            target.preview(newMask)
        }

        fun updateMaskShape(shape: MaskShape) {
            val newMask = currentMask.copy(shape = shape)
            applyMaskToView(newMask)
            highlightShape(shape)
        }

        btnNone?.setOnClickListener { updateMaskShape(MaskShape.NONE) }
        btnSplit?.setOnClickListener { updateMaskShape(MaskShape.SPLIT) }
        btnShutter?.setOnClickListener { updateMaskShape(MaskShape.SHUTTER) }
        btnEllipse?.setOnClickListener { updateMaskShape(MaskShape.ELLIPSE) }
        btnRectangle?.setOnClickListener { updateMaskShape(MaskShape.RECTANGLE) }
        btnHeart?.setOnClickListener { updateMaskShape(MaskShape.HEART) }
        btnStar?.setOnClickListener { updateMaskShape(MaskShape.STAR) }

        sliderFeather?.addOnChangeListener { _, value, _ ->
            tvFeatherValue?.text = "${value.toInt()}%"
            applyMaskToView(currentMask.copy(feather = value))
        }

        sliderSize?.addOnChangeListener { _, value, _ ->
            tvSizeValue?.text = "${value.toInt()}%"
            val relScale = value / 200f
            applyMaskToView(currentMask.copy(relativeWidth = relScale, relativeHeight = relScale))
        }

        sliderRotation?.addOnChangeListener { _, value, _ ->
            tvRotationValue?.text = "${value.toInt()}°"
            applyMaskToView(currentMask.copy(rotationAngle = value))
        }

        btnResetRotation?.setOnClickListener {
            sliderRotation?.value = 0f
        }

        switchInvert.setOnCheckedChangeListener { _, isChecked ->
            applyMaskToView(currentMask.copy(isInverted = isChecked))
        }

        btnResetMask?.setOnClickListener {
            switchInvert.isChecked = false
            sliderFeather?.value = 0f
            sliderSize?.value = 100f
            sliderRotation?.value = 0f
            tvFeatherValue?.text = "0%"
            tvSizeValue?.text = "100%"
            tvRotationValue?.text = "0°"
            updateMaskShape(MaskShape.NONE)
        }

        bottomSheet.setOnDismissListener {
            target.commit(currentMask)
        }

        btnDoneMask?.setOnClickListener {
            bottomSheet.dismiss()
        }

        // Non-modal: keep the preview underneath interactive while the sheet is open.
        bottomSheet.setCanceledOnTouchOutside(false)
        bottomSheet.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        bottomSheet.window?.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)

        bottomSheet.show()

        val touchOutside = bottomSheet.findViewById<View>(com.google.android.material.R.id.touch_outside)
        touchOutside?.setOnTouchListener { _, event ->
            activity.findViewById<View>(android.R.id.content).dispatchTouchEvent(event)
            true
        }
    }
}
