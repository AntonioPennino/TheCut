package com.antoniopennino.thecut.delegates

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.RelativeLayout
import android.widget.SeekBar
import android.widget.TextView
import com.antoniopennino.thecut.R
import com.antoniopennino.thecut.utils.setBounceClickListener

/**
 * Controller responsible for managing fullscreen preview mode, controls fade timers,
 * scrubber synchronization, and canvas reparenting.
 */
class FullscreenController(
    rootView: View,
    private val callbacks: Callbacks
) {
    interface Callbacks {
        fun isVideoReady(): Boolean
        fun onPlayPauseToggle()
        fun getTotalDuration(): Long
        fun onSeekFromFullscreen(targetMs: Long)
        fun onReparentCanvas(targetContainer: ViewGroup)
        fun formatDuration(durationMs: Int): String
    }

    val btnFullscreen: ImageButton = rootView.findViewById(R.id.btnFullscreen)
    val fullscreenOverlay: FrameLayout = rootView.findViewById(R.id.fullscreenOverlay)
    val fullscreenCanvasHolder: FrameLayout = rootView.findViewById(R.id.fullscreenCanvasHolder)
    val fullscreenControls: RelativeLayout = rootView.findViewById(R.id.fullscreenControls)
    val btnFullscreenPlayPause: ImageButton = rootView.findViewById(R.id.btnFullscreenPlayPause)
    val btnExitFullscreen: ImageButton = rootView.findViewById(R.id.btnExitFullscreen)
    val btnExitFullscreenTop: ImageButton = rootView.findViewById(R.id.btnExitFullscreenTop)
    val sbFullscreenSeeker: SeekBar = rootView.findViewById(R.id.sbFullscreenSeeker)
    val tvFullscreenCurrentTime: TextView = rootView.findViewById(R.id.tvFullscreenCurrentTime)
    val tvFullscreenTotalTime: TextView = rootView.findViewById(R.id.tvFullscreenTotalTime)

    var isFullscreen: Boolean = false
        private set

    var isUserScrubbing: Boolean = false
        private set

    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideControlsRunnable = Runnable {
        if (isFullscreen) {
            fullscreenControls.animate().alpha(0f).setDuration(300).withEndAction {
                fullscreenControls.visibility = View.GONE
            }.start()
        }
    }

    init {
        setupListeners()
    }

    private fun setupListeners() {
        btnFullscreen.setBounceClickListener {
            enterFullscreenMode()
        }

        btnExitFullscreen.setBounceClickListener {
            exitFullscreenMode()
        }

        btnExitFullscreenTop.setBounceClickListener {
            exitFullscreenMode()
        }

        btnFullscreenPlayPause.setBounceClickListener {
            if (callbacks.isVideoReady()) {
                callbacks.onPlayPauseToggle()
            }
            showControlsTemporarily()
        }

        fullscreenOverlay.setOnClickListener {
            if (fullscreenControls.visibility == View.VISIBLE && fullscreenControls.alpha > 0.5f) {
                hideHandler.removeCallbacks(hideControlsRunnable)
                fullscreenControls.animate().alpha(0f).setDuration(250).withEndAction {
                    fullscreenControls.visibility = View.GONE
                }.start()
            } else {
                showControlsTemporarily()
            }
        }

        sbFullscreenSeeker.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val totalMs = callbacks.getTotalDuration()
                    if (totalMs > 0) {
                        val targetMs = ((progress.toFloat() / 1000f) * totalMs).toLong()
                        callbacks.onSeekFromFullscreen(targetMs)
                        tvFullscreenCurrentTime.text = callbacks.formatDuration(targetMs.toInt())
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserScrubbing = true
                hideHandler.removeCallbacks(hideControlsRunnable)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isUserScrubbing = false
                showControlsTemporarily()
            }
        })
    }

    fun enterFullscreenMode() {
        isFullscreen = true
        fullscreenOverlay.visibility = View.VISIBLE
        fullscreenControls.visibility = View.VISIBLE
        fullscreenControls.alpha = 1f
        callbacks.onReparentCanvas(fullscreenCanvasHolder)
        showControlsTemporarily()
    }

    fun exitFullscreenMode() {
        isFullscreen = false
        hideHandler.removeCallbacks(hideControlsRunnable)
        fullscreenOverlay.visibility = View.GONE
        callbacks.onReparentCanvas(fullscreenOverlay.rootView.findViewById(R.id.playerContainer))
    }

    fun showControlsTemporarily() {
        hideHandler.removeCallbacks(hideControlsRunnable)
        fullscreenControls.visibility = View.VISIBLE
        fullscreenControls.animate().cancel()
        fullscreenControls.alpha = 1f
        hideHandler.postDelayed(hideControlsRunnable, 3500)
    }

    fun updateDuration(current: Int, total: Int) {
        if (total <= 0) return
        tvFullscreenCurrentTime.text = callbacks.formatDuration(current)
        tvFullscreenTotalTime.text = callbacks.formatDuration(total)
        if (!isUserScrubbing) {
            val progress = ((current.toFloat() / total.toFloat()) * 1000).toInt().coerceIn(0, 1000)
            sbFullscreenSeeker.progress = progress
        }
    }

    fun updatePlayPauseIcon(isPlaying: Boolean) {
        btnFullscreenPlayPause.setImageResource(
            if (isPlaying) R.drawable.ic_pause_24 else R.drawable.ic_play_24
        )
    }

    fun destroy() {
        hideHandler.removeCallbacks(hideControlsRunnable)
    }
}
