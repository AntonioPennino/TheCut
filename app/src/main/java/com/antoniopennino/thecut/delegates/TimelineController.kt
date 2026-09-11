package com.antoniopennino.thecut.delegates

import android.content.Context
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import com.antoniopennino.thecut.R

/**
 * Delegate responsible for timeline scaling, zooming, expansion modes
 * (e.g. DaVinci Resolve compact monitor + multi-track layout), and scroll synchronization.
 */
class TimelineController(
    private val context: Context,
    private val rootView: View,
    private val callbacks: Callbacks
) {
    interface Callbacks {
        fun getTotalDuration(): Long
        fun getCurrentPosition(): Long
        fun onRenderTracks()
        fun onCanvasLayoutUpdateNeeded()
        fun reparentCanvas(targetContainer: ViewGroup)
        fun dpToPx(dp: Int): Int
    }

    enum class ZoomMode { FIT, MEDIUM, PRECISION }

    var currentZoomMode = ZoomMode.MEDIUM
    var pixelsPerMs: Float = 0.15f * context.resources.displayMetrics.density
    var isTimelineExpanded: Boolean = false
        private set
    var isProgrammaticScroll: Boolean = false

    val btnExpandTimeline: ImageButton? = rootView.findViewById(R.id.btnExpandTimeline)
    val timelineHorizontalScroll: HorizontalScrollView? = rootView.findViewById(R.id.timelineHorizontalScroll)
    val timelineVerticalScroll: ScrollView? = rootView.findViewById(R.id.timelineVerticalScroll)
    val timelineContainer: FrameLayout? = rootView.findViewById(R.id.timelineContainer)
    val playerContainer: FrameLayout? = rootView.findViewById(R.id.playerContainer)
    val pipPlayerContainer: FrameLayout? = rootView.findViewById(R.id.pipPlayerContainer)

    lateinit var scaleDetector: ScaleGestureDetector
        private set

    init {
        setupScaleDetector()
        btnExpandTimeline?.setOnClickListener {
            toggleTimelineExpandedMode(isLandscape = isLandscape())
        }
    }

    private fun isLandscape(): Boolean {
        return context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    }

    private fun setupScaleDetector() {
        scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scaleFactor = detector.scaleFactor
                val density = context.resources.displayMetrics.density
                val totalDuration = callbacks.getTotalDuration()
                if (totalDuration <= 0L) return true

                val scrollWidth = timelineHorizontalScroll?.width?.toFloat() ?: 1000f
                val minPixelsPerMs = maxOf(0.001f * density, (scrollWidth - callbacks.dpToPx(32)) / totalDuration)
                val maxPixelsPerMs = 1.5f * density

                val newPixelsPerMs = pixelsPerMs * scaleFactor
                pixelsPerMs = newPixelsPerMs.coerceIn(minPixelsPerMs, maxPixelsPerMs)

                callbacks.onRenderTracks()

                val currentPos = callbacks.getCurrentPosition()
                scrollToPosition(currentPos)
                return true
            }
        })
    }

    fun scrollToPosition(targetMs: Long) {
        val scroll = timelineHorizontalScroll ?: return
        isProgrammaticScroll = true
        scroll.scrollTo((targetMs * pixelsPerMs).toInt(), 0)
        isProgrammaticScroll = false
    }

    fun toggleTimelineExpandedMode(isLandscape: Boolean) {
        isTimelineExpanded = !isTimelineExpanded
        val density = context.resources.displayMetrics.density

        if (isLandscape) {
            val landscapeToolbarsHolder = rootView.findViewById<FrameLayout>(R.id.landscapeToolbarsHolder)
            if (isTimelineExpanded) {
                btnExpandTimeline?.setImageResource(R.drawable.ic_collapse_24)
                landscapeToolbarsHolder?.visibility = View.GONE
            } else {
                btnExpandTimeline?.setImageResource(R.drawable.ic_expand_24)
                landscapeToolbarsHolder?.visibility = View.VISIBLE
            }
            playerContainer?.post {
                callbacks.onCanvasLayoutUpdateNeeded()
            }
            return
        }

        val seekerContainer = rootView.findViewById<LinearLayout>(R.id.seekerContainer)
        val player = playerContainer ?: return

        if (isTimelineExpanded) {
            btnExpandTimeline?.setImageResource(R.drawable.ic_collapse_24)
            pipPlayerContainer?.visibility = View.GONE
            player.visibility = View.VISIBLE
            callbacks.reparentCanvas(player)

            // 1. Make Top Video Monitor Compact (DaVinci / FCP Style, full width, 145dp height)
            val lpPlayer = player.layoutParams as? LinearLayout.LayoutParams
            lpPlayer?.let {
                it.height = (145 * density).toInt()
                it.weight = 0f
                player.layoutParams = it
            }

            // 2. Expand Seeker & Timeline to fill all remaining vertical space
            val lpSeeker = seekerContainer?.layoutParams as? LinearLayout.LayoutParams
            lpSeeker?.let {
                it.height = 0
                it.weight = 1.0f
                seekerContainer.layoutParams = it
            }

            val lpTimeline = timelineContainer?.layoutParams as? LinearLayout.LayoutParams
            lpTimeline?.let {
                it.height = 0
                it.weight = 1.0f
                timelineContainer.layoutParams = it
            }
        } else {
            btnExpandTimeline?.setImageResource(R.drawable.ic_expand_24)
            pipPlayerContainer?.visibility = View.GONE
            player.visibility = View.VISIBLE
            callbacks.reparentCanvas(player)

            // 1. Restore Full Height Video Monitor
            val lpPlayer = player.layoutParams as? LinearLayout.LayoutParams
            lpPlayer?.let {
                it.height = 0
                it.weight = 1.0f
                player.layoutParams = it
            }

            // 2. Restore Seeker & Timeline to Wrap Content / Fixed 140dp
            val lpSeeker = seekerContainer?.layoutParams as? LinearLayout.LayoutParams
            lpSeeker?.let {
                it.height = ViewGroup.LayoutParams.WRAP_CONTENT
                it.weight = 0f
                seekerContainer.layoutParams = it
            }

            val lpTimeline = timelineContainer?.layoutParams as? LinearLayout.LayoutParams
            lpTimeline?.let {
                it.height = (140 * density).toInt()
                it.weight = 0f
                timelineContainer.layoutParams = it
            }
        }

        player.post {
            callbacks.onCanvasLayoutUpdateNeeded()
        }
    }
}
