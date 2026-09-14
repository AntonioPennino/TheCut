package com.antoniopennino.thecut.delegates

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.net.Uri
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.antoniopennino.thecut.R
import com.antoniopennino.thecut.utils.setBounceClickListener
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.io.File

/**
 * Owns the voice-over recorder bottom sheet: MediaRecorder lifecycle, the pulsing
 * ripple animation, and playback sync while recording. Turning the finished
 * recording into an audio-track operation is delegated to the host via
 * [Callbacks.onVoiceOverRecorded].
 *
 * Extracted from VideoEditingActivity. The RECORD_AUDIO permission gate and its
 * ActivityResult launcher remain in the Activity, which calls [showDialog] once
 * permission is granted.
 */
class VoiceOverDelegate(
    private val activity: AppCompatActivity,
    private val callbacks: Callbacks
) {
    interface Callbacks {
        /** Current global playback position, used as the voice-over start time. */
        fun getGlobalPosition(): Long
        /** Recording started: begin playback so the user records against the video. */
        fun onRecordingStarted()
        /** Recording stopped (or sheet dismissed mid-recording): pause playback. */
        fun onRecordingStopped()
        /** A recording finished; host adds it to the audio track. */
        fun onVoiceOverRecorded(audioUri: Uri, startTimeMs: Long, durationMs: Long)
        /** Clear the active tool button highlight when the sheet closes. */
        fun clearActiveToolButton()
    }

    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var voiceOverFile: File? = null
    private var voiceOverStartTimeMs: Long = 0L

    fun showDialog() {
        val bottomSheet = BottomSheetDialog(activity)
        val view = activity.layoutInflater.inflate(R.layout.dialog_voice_over, null)
        bottomSheet.setContentView(view)

        val btnClose = view.findViewById<ImageButton>(R.id.btnCloseVoiceOver)
        val btnToggle = view.findViewById<ImageButton>(R.id.btnToggleRecording)
        val tvStatus = view.findViewById<TextView>(R.id.tvRecordingStatus)
        val ripple1 = view.findViewById<View>(R.id.ripple1)
        val ripple2 = view.findViewById<View>(R.id.ripple2)

        isRecording = false

        val scaleX1 = ObjectAnimator.ofFloat(ripple1, "scaleX", 1f, 1.8f)
        val scaleY1 = ObjectAnimator.ofFloat(ripple1, "scaleY", 1f, 1.8f)
        val alpha1 = ObjectAnimator.ofFloat(ripple1, "alpha", 0.6f, 0f)

        val scaleX2 = ObjectAnimator.ofFloat(ripple2, "scaleX", 1f, 1.8f)
        val scaleY2 = ObjectAnimator.ofFloat(ripple2, "scaleY", 1f, 1.8f)
        val alpha2 = ObjectAnimator.ofFloat(ripple2, "alpha", 0.6f, 0f)

        scaleX1.repeatCount = ValueAnimator.INFINITE
        scaleY1.repeatCount = ValueAnimator.INFINITE
        alpha1.repeatCount = ValueAnimator.INFINITE

        scaleX2.repeatCount = ValueAnimator.INFINITE
        scaleY2.repeatCount = ValueAnimator.INFINITE
        alpha2.repeatCount = ValueAnimator.INFINITE

        val rippleAnimator = AnimatorSet().apply {
            playTogether(scaleX1, scaleY1, alpha1)
            duration = 1500
        }
        val rippleAnimator2 = AnimatorSet().apply {
            playTogether(scaleX2, scaleY2, alpha2)
            duration = 1500
            startDelay = 750
        }

        btnToggle.setBounceClickListener {
            if (!isRecording) {
                // Start Recording
                voiceOverFile = File(activity.cacheDir, "voice_over_${System.currentTimeMillis()}.m4a")
                try {
                    mediaRecorder = MediaRecorder().apply {
                        setAudioSource(MediaRecorder.AudioSource.MIC)
                        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                        setOutputFile(voiceOverFile?.absolutePath)
                        prepare()
                        start()
                    }

                    isRecording = true
                    tvStatus.text = activity.getString(R.string.str_recording_tap_stop)
                    btnToggle.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FF4081"))

                    rippleAnimator.start()
                    rippleAnimator2.start()

                    // Play video while recording
                    voiceOverStartTimeMs = callbacks.getGlobalPosition()
                    callbacks.onRecordingStarted()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(activity, R.string.toast_failed_to_start_recording, Toast.LENGTH_SHORT).show()
                }
            } else {
                // Stop Recording
                try {
                    mediaRecorder?.stop()
                    mediaRecorder?.release()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                mediaRecorder = null
                isRecording = false

                rippleAnimator.cancel()
                rippleAnimator2.cancel()
                ripple1.alpha = 0f
                ripple2.alpha = 0f
                ripple1.scaleX = 1f
                ripple1.scaleY = 1f
                ripple2.scaleX = 1f
                ripple2.scaleY = 1f

                // Pause video
                callbacks.onRecordingStopped()

                tvStatus.text = activity.getString(R.string.str_recording_tap_start)
                btnToggle.backgroundTintList = null // Reset tint

                // Add to audio layer
                voiceOverFile?.let { file ->
                    if (file.exists()) {
                        var durationMs = 3000L
                        try {
                            val retriever = MediaMetadataRetriever()
                            retriever.setDataSource(file.absolutePath)
                            val timeStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                            if (timeStr != null) {
                                durationMs = timeStr.toLong()
                            }
                            retriever.release()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }

                        callbacks.onVoiceOverRecorded(Uri.fromFile(file), voiceOverStartTimeMs, durationMs)
                    }
                }
                bottomSheet.dismiss()
            }
        }

        btnClose.setBounceClickListener {
            bottomSheet.dismiss()
        }

        bottomSheet.setOnDismissListener {
            rippleAnimator.cancel()
            rippleAnimator2.cancel()
            if (isRecording) {
                try { mediaRecorder?.stop(); mediaRecorder?.release() } catch (e: Exception) {}
                mediaRecorder = null
                isRecording = false
                callbacks.onRecordingStopped()
            }
            callbacks.clearActiveToolButton()
        }

        bottomSheet.show()
    }
}
