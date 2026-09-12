package com.antoniopennino.thecut.delegates

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.antoniopennino.thecut.R
import com.antoniopennino.thecut.utils.findTextureView
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * Captures the current composited preview frame (video + visible overlays) and
 * saves it to the gallery as a PNG, honouring a user-configured export directory.
 *
 * Extracted from VideoEditingActivity.
 */
class SnapshotDelegate(
    private val activity: AppCompatActivity,
    private val callbacks: Callbacks
) {
    interface Callbacks {
        /** Close any active editing modes before capturing, so toolbars are hidden. */
        fun onRequestCloseOtherModes()
        /** The player view whose TextureView holds the current video frame. */
        fun getPlayerView(): ViewGroup
    }

    fun captureCurrentFrame() {
        callbacks.onRequestCloseOtherModes()
        val container = activity.findViewById<FrameLayout>(R.id.playerContainer)
        val tvDuration = activity.findViewById<TextView>(R.id.tvDuration)
        val tvPreviewBadge = activity.findViewById<TextView>(R.id.tvPreviewBadge)

        val durationVis = tvDuration?.visibility ?: View.GONE
        val badgeVis = tvPreviewBadge?.visibility ?: View.GONE

        tvDuration?.visibility = View.INVISIBLE
        tvPreviewBadge?.visibility = View.INVISIBLE

        container.post {
            if (container.width <= 0 || container.height <= 0) {
                tvDuration?.visibility = durationVis
                tvPreviewBadge?.visibility = badgeVis
                return@post
            }
            val bitmap = Bitmap.createBitmap(container.width, container.height, Bitmap.Config.ARGB_8888)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val location = IntArray(2)
                container.getLocationInWindow(location)
                val rect = Rect(location[0], location[1], location[0] + container.width, location[1] + container.height)

                try {
                    PixelCopy.request(activity.window, rect, bitmap, { copyResult ->
                        tvDuration?.visibility = durationVis
                        tvPreviewBadge?.visibility = badgeVis
                        if (copyResult == PixelCopy.SUCCESS) {
                            saveBitmapToGallery(bitmap)
                        } else {
                            Toast.makeText(activity, R.string.str_toast_capture_frame_failed, Toast.LENGTH_SHORT).show()
                        }
                    }, Handler(Looper.getMainLooper()))
                } catch (e: Exception) {
                    e.printStackTrace()
                    tvDuration?.visibility = durationVis
                    tvPreviewBadge?.visibility = badgeVis
                    Toast.makeText(activity, R.string.str_toast_capture_frame_failed, Toast.LENGTH_SHORT).show()
                }
            } else {
                val canvas = Canvas(bitmap)
                val bg = container.background
                if (bg != null) bg.draw(canvas) else canvas.drawColor(Color.BLACK)

                val playerView = callbacks.getPlayerView()
                val textureView = findTextureView(playerView)
                if (textureView != null) {
                    val videoBitmap = textureView.bitmap
                    if (videoBitmap != null) {
                        canvas.save()
                        canvas.translate(playerView.x + textureView.x, playerView.y + textureView.y)
                        canvas.scale(playerView.scaleX, playerView.scaleY)
                        canvas.drawBitmap(videoBitmap, 0f, 0f, null)
                        canvas.restore()
                    }
                }

                val overlays = listOf(R.id.textOverlayView, R.id.imageOverlayView, R.id.cropOverlayView)
                for (id in overlays) {
                    val view = container.findViewById<View>(id)
                    if (view != null && view.visibility == View.VISIBLE) {
                        canvas.save()
                        canvas.translate(view.x, view.y)
                        view.draw(canvas)
                        canvas.restore()
                    }
                }

                tvDuration?.visibility = durationVis
                tvPreviewBadge?.visibility = badgeVis
                saveBitmapToGallery(bitmap)
            }
        }
    }

    private fun saveBitmapToGallery(bitmap: Bitmap) {
        val filename = "TheCut_Frame_${System.currentTimeMillis()}.png"
        var fos: OutputStream? = null
        var imageUri: Uri? = null
        try {
            val sharedPreferences = activity.getSharedPreferences("thecut_prefs", Context.MODE_PRIVATE)
            val customUriString = sharedPreferences.getString("export_snapshot_directory_uri", null)

            if (customUriString != null) {
                try {
                    val treeUri = Uri.parse(customUriString)
                    val parentUri = DocumentsContract.buildDocumentUriUsingTree(
                        treeUri,
                        DocumentsContract.getTreeDocumentId(treeUri)
                    )
                    imageUri = DocumentsContract.createDocument(
                        activity.contentResolver,
                        parentUri,
                        "image/png",
                        filename
                    )
                    fos = imageUri?.let { activity.contentResolver.openOutputStream(it) }
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving snapshot to custom directory: ${e.message}, falling back to default", e)
                }
            }

            if (fos == null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = activity.contentResolver
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/TheCut")
                    }
                    imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    fos = imageUri?.let { resolver.openOutputStream(it) }
                } else {
                    val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                    val directory = File(imagesDir, "TheCut")
                    if (!directory.exists()) directory.mkdirs()
                    val image = File(directory, filename)
                    fos = FileOutputStream(image)
                    imageUri = Uri.fromFile(image)
                }
            }

            fos?.use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                Toast.makeText(activity, R.string.str_toast_frame_saved, Toast.LENGTH_SHORT).show()
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && imageUri != null) {
                activity.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, imageUri))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(activity, R.string.str_toast_save_frame_failed, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val TAG = "SnapshotDelegate"
    }
}
