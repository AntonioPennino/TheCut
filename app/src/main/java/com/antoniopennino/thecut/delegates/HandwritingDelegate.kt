package com.antoniopennino.thecut.delegates

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.antoniopennino.thecut.R
import com.antoniopennino.thecut.customviews.BrushSizeDotView
import com.antoniopennino.thecut.customviews.HandwritingCanvasView
import com.antoniopennino.thecut.dialogs.ColorPickerDialog
import com.antoniopennino.thecut.utils.setBounceClickListener
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import java.io.File
import java.io.FileOutputStream

/**
 * Owns the freehand drawing ("handwriting") mode: brush palette/size controls,
 * canvas visibility, undo/redo, and exporting the drawing to a PNG in the cache.
 * Project mutation (turning the PNG into an overlay operation) is delegated back
 * to the host via [Callbacks.onDrawingCommitted], keeping this class free of
 * ViewModel/rendering concerns.
 *
 * Extracted from VideoEditingActivity.
 */
class HandwritingDelegate(
    private val activity: AppCompatActivity,
    private val callbacks: Callbacks
) {
    interface Callbacks {
        /** Close any other active editing modes before entering drawing mode. */
        fun onRequestCloseOtherModes()
        /** Highlight (or clear, with -1) the given tool button. */
        fun setActiveToolButton(viewId: Int)
        /** Pause playback when entering drawing mode. */
        fun pausePlayback()
        /** Current on-screen video rectangle used to size the canvas. */
        fun getVideoRect(): RectF
        /** The primary video aspect ratio (width/height), or <= 0 if unknown. */
        fun getPrimaryAspectRatio(): Float
        /** A finished drawing has been written to [file]; host turns it into an overlay. */
        fun onDrawingCommitted(file: File)
    }

    var isActive: Boolean = false
        private set

    private val presetHexes = listOf(
        "#FFFFFF", "#000000", "#FF007A", "#FF3B30",
        "#FF9500", "#FFCC00", "#34C759", "#32ADE6",
        "#007AFF", "#AF52DE"
    )

    fun setupControls() {
        val btnDraw = activity.findViewById<ImageButton>(R.id.btnDraw)
        btnDraw?.setBounceClickListener {
            callbacks.setActiveToolButton(R.id.btnDraw)
            open()
        }

        val handwritingPanel = activity.findViewById<View>(R.id.handwritingPanel) ?: return
        val canvasView = activity.findViewById<HandwritingCanvasView>(R.id.handwritingCanvasView) ?: return
        val dotView = handwritingPanel.findViewById<BrushSizeDotView>(R.id.brushSizeDotView)
        val sliderBrushSize = handwritingPanel.findViewById<Slider>(R.id.sliderBrushSize)
        val btnClose = handwritingPanel.findViewById<ImageButton>(R.id.btnCloseHandwriting)
        val btnUndo = handwritingPanel.findViewById<ImageButton>(R.id.btnUndoHandwriting)
        val btnRedo = handwritingPanel.findViewById<ImageButton>(R.id.btnRedoHandwriting)
        val btnClear = handwritingPanel.findViewById<ImageButton>(R.id.btnClearHandwriting)
        val btnDone = handwritingPanel.findViewById<ImageButton>(R.id.btnDoneHandwriting)
        val btnCustomColor = handwritingPanel.findViewById<View>(R.id.btnCustomColor)
        val layoutPalette = handwritingPanel.findViewById<LinearLayout>(R.id.layoutColorPalette)

        var selectedHex = "#FF007A"

        fun updateColorSelection(hex: String) {
            selectedHex = hex
            val colorInt = try { Color.parseColor(hex) } catch (e: Exception) { Color.RED }
            canvasView.setBrushColor(colorInt)
            dotView?.setBrushColor(colorInt)

            if (layoutPalette != null) {
                for (i in 1 until layoutPalette.childCount) {
                    val child = layoutPalette.getChildAt(i) as? FrameLayout ?: continue
                    val tagHex = child.tag as? String
                    if (tagHex != null && tagHex.equals(hex, ignoreCase = true)) {
                        child.setBackgroundResource(R.drawable.bg_aspect_ratio_selected)
                        child.foreground = ContextCompat.getDrawable(activity, R.drawable.bg_transition_border_selected)
                    } else {
                        child.setBackgroundResource(R.drawable.bg_aspect_ratio_item)
                        child.foreground = null
                    }
                }
            }
        }

        if (layoutPalette != null && layoutPalette.childCount <= 1) {
            val density = activity.resources.displayMetrics.density
            val swatchSize = (36 * density).toInt()
            val innerSize = (24 * density).toInt()
            val margin = (4 * density).toInt()

            for (hex in presetHexes) {
                val frame = FrameLayout(activity).apply {
                    layoutParams = LinearLayout.LayoutParams(swatchSize, swatchSize).apply {
                        setMargins(margin, 0, margin, 0)
                    }
                    tag = hex
                    setBackgroundResource(R.drawable.bg_aspect_ratio_item)
                    isClickable = true
                    isFocusable = true
                }

                val circleView = View(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(innerSize, innerSize, Gravity.CENTER)
                    val drawable = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(try { Color.parseColor(hex) } catch (e: Exception) { Color.WHITE })
                        setStroke((1 * density).toInt(), Color.parseColor("#44FFFFFF"))
                    }
                    background = drawable
                }
                frame.addView(circleView)

                frame.setOnClickListener {
                    updateColorSelection(hex)
                }
                layoutPalette.addView(frame)
            }
        }

        val density = activity.resources.displayMetrics.density
        val minPx = 3f * density
        val maxPx = 60f * density

        sliderBrushSize?.addOnChangeListener { _, value, _ ->
            val fraction = value / 100f
            val sizePx = minPx + fraction * (maxPx - minPx)
            canvasView.setBrushSizePx(sizePx)
            dotView?.setBrushSize(sizePx, minPx, maxPx)
        }

        val initialValue = sliderBrushSize?.value ?: 25f
        val initialSizePx = minPx + (initialValue / 100f) * (maxPx - minPx)
        canvasView.setBrushSizePx(initialSizePx)
        dotView?.setBrushSize(initialSizePx, minPx, maxPx)
        updateColorSelection(selectedHex)

        canvasView.onStrokesChangedListener = {
            btnUndo?.isEnabled = canvasView.canUndo()
            btnUndo?.alpha = if (canvasView.canUndo()) 1.0f else 0.5f
            btnRedo?.isEnabled = canvasView.canRedo()
            btnRedo?.alpha = if (canvasView.canRedo()) 1.0f else 0.5f
        }

        btnUndo?.setBounceClickListener { canvasView.undo() }
        btnRedo?.setBounceClickListener { canvasView.redo() }
        btnClear?.setBounceClickListener { canvasView.clear() }

        btnClose?.setBounceClickListener {
            if (!canvasView.isEmpty()) {
                MaterialAlertDialogBuilder(activity)
                    .setTitle("Discard Handwriting?")
                    .setMessage("Are you sure you want to discard your drawing?")
                    .setPositiveButton("Discard") { _, _ -> close() }
                    .setNegativeButton("Keep Editing", null)
                    .show()
            } else {
                close()
            }
        }

        btnDone?.setBounceClickListener {
            saveAndCommit()
        }

        btnCustomColor?.setBounceClickListener {
            ColorPickerDialog.show(activity, selectedHex) { customHex ->
                updateColorSelection(customHex)
            }
        }
    }

    fun open() {
        callbacks.onRequestCloseOtherModes()
        isActive = true
        callbacks.pausePlayback()

        val canvasView = activity.findViewById<HandwritingCanvasView>(R.id.handwritingCanvasView)
        val handwritingPanel = activity.findViewById<View>(R.id.handwritingPanel)

        canvasView?.setVideoRect(callbacks.getVideoRect())
        canvasView?.clear()
        canvasView?.visibility = View.VISIBLE
        handwritingPanel?.visibility = View.VISIBLE

        activity.findViewById<android.widget.HorizontalScrollView>(R.id.editingControlsScroll)?.visibility = View.GONE
    }

    fun close() {
        isActive = false
        val canvasView = activity.findViewById<HandwritingCanvasView>(R.id.handwritingCanvasView)
        val handwritingPanel = activity.findViewById<View>(R.id.handwritingPanel)

        canvasView?.visibility = View.GONE
        handwritingPanel?.visibility = View.GONE
        activity.findViewById<android.widget.HorizontalScrollView>(R.id.editingControlsScroll)?.visibility = View.VISIBLE
        callbacks.setActiveToolButton(-1)
    }

    private fun saveAndCommit() {
        val canvasView = activity.findViewById<HandwritingCanvasView>(R.id.handwritingCanvasView) ?: return
        if (canvasView.isEmpty()) {
            Toast.makeText(activity, R.string.str_toast_draw_something_first, Toast.LENGTH_SHORT).show()
            return
        }

        val baseRatio = if (callbacks.getPrimaryAspectRatio() > 0f) callbacks.getPrimaryAspectRatio() else 16f / 9f
        val targetWidth: Int
        val targetHeight: Int
        if (baseRatio >= 1.0f) {
            targetWidth = 1920
            targetHeight = (1920f / baseRatio).toInt().coerceAtLeast(1)
        } else {
            targetHeight = 1920
            targetWidth = (1920f * baseRatio).toInt().coerceAtLeast(1)
        }

        val bitmap = canvasView.exportToBitmap(targetWidth, targetHeight)
        if (bitmap == null) {
            Toast.makeText(activity, R.string.str_toast_render_drawing_failed, Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val drawingsDir = File(activity.cacheDir, "drawings").apply { mkdirs() }
            val outFile = File(drawingsDir, "handwriting_${System.currentTimeMillis()}.png")
            FileOutputStream(outFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }

            callbacks.onDrawingCommitted(outFile)
            close()
            Toast.makeText(activity, R.string.str_toast_handwriting_added, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(activity, activity.getString(R.string.str_toast_handwriting_save_error, e.localizedMessage ?: ""), Toast.LENGTH_SHORT).show()
        }
    }
}
