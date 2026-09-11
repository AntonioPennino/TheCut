package com.antoniopennino.thecut.delegates

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.antoniopennino.thecut.R
import com.antoniopennino.thecut.viewmodels.VideoEditingViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.ChipGroup
import com.google.android.material.materialswitch.MaterialSwitch

/**
 * Delegate responsible for export quality settings dialog, directory selection,
 * export progress display, and export cancellation.
 */
class ExportDelegate(
    private val context: Context,
    private val viewModel: VideoEditingViewModel,
    private val exportScreen: View?,
    private val callbacks: Callbacks
) {
    interface Callbacks {
        fun onCommitActiveEdits()
        fun isShowingPreview(): Boolean
        fun onDismissPreview()
        fun onPickDirectoryRequested()
        fun onSaveProjectRequested()
        fun onCancelExportJob()
    }

    private var activeDirectoryTitleView: TextView? = null
    private var activeDirectoryPathView: TextView? = null

    init {
        exportScreen?.findViewById<View>(R.id.btnCancelExport)?.setOnClickListener {
            cancelExport()
        }
    }

    fun updateExportProgress(progress: Int) {
        exportScreen?.findViewById<TextView>(R.id.tvExportPercentage)?.text = "$progress%"
        exportScreen?.findViewById<ProgressBar>(R.id.exportProgressBar)?.progress = progress
    }

    fun setExportScreenVisible(visible: Boolean) {
        exportScreen?.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun showQualitySettingsDialog() {
        callbacks.onCommitActiveEdits()
        if (callbacks.isShowingPreview()) {
            callbacks.onDismissPreview()
        }

        val bottomSheetDialog = BottomSheetDialog(context)
        val inflater = LayoutInflater.from(context)
        val sheetView = inflater.inflate(R.layout.export_quality_bottom_sheet_dialog, null)

        val cgResolution = sheetView.findViewById<ChipGroup>(R.id.cgResolution)
        val cgFps = sheetView.findViewById<ChipGroup>(R.id.cgFps)
        val switchAudioOnly = sheetView.findViewById<MaterialSwitch>(R.id.switchAudioOnly)
        val btnClose = sheetView.findViewById<ImageButton>(R.id.btnCloseSheet)

        // Initialize state
        val currentRes = viewModel.exportResolution.value
        val currentFps = viewModel.exportFps.value
        val isAudioOnly = viewModel.exportAudioOnly.value

        when (currentRes) {
            360 -> cgResolution.check(R.id.chipRes360)
            480 -> cgResolution.check(R.id.chipRes480)
            720 -> cgResolution.check(R.id.chipRes720)
            1080 -> cgResolution.check(R.id.chipRes1080)
            1440 -> cgResolution.check(R.id.chipRes1440)
            2160 -> cgResolution.check(R.id.chipRes2160)
            else -> cgResolution.check(R.id.chipRes1080)
        }

        when (currentFps) {
            24 -> cgFps.check(R.id.chipFps24)
            25 -> cgFps.check(R.id.chipFps25)
            30 -> cgFps.check(R.id.chipFps30)
            50 -> cgFps.check(R.id.chipFps50)
            60 -> cgFps.check(R.id.chipFps60)
            else -> cgFps.check(R.id.chipFps30)
        }

        switchAudioOnly.isChecked = isAudioOnly

        val layoutExportDirectory = sheetView.findViewById<LinearLayout>(R.id.layoutExportDirectory)
        val tvExportDirectoryTitle = sheetView.findViewById<TextView>(R.id.tvExportDirectoryTitle)
        val tvExportDirectoryPath = sheetView.findViewById<TextView>(R.id.tvExportDirectoryPath)

        activeDirectoryTitleView = tvExportDirectoryTitle
        activeDirectoryPathView = tvExportDirectoryPath
        updateExportDirectoryUi(tvExportDirectoryTitle, tvExportDirectoryPath)

        fun saveSettings() {
            val res = when (cgResolution.checkedChipId) {
                R.id.chipRes360 -> 360
                R.id.chipRes480 -> 480
                R.id.chipRes720 -> 720
                R.id.chipRes1080 -> 1080
                R.id.chipRes1440 -> 1440
                R.id.chipRes2160 -> 2160
                else -> 1080
            }
            val fps = when (cgFps.checkedChipId) {
                R.id.chipFps24 -> 24
                R.id.chipFps25 -> 25
                R.id.chipFps30 -> 30
                R.id.chipFps50 -> 50
                R.id.chipFps60 -> 60
                else -> 30
            }
            viewModel.setExportSettings(res, fps, switchAudioOnly.isChecked)
            updateExportDirectoryUi(tvExportDirectoryTitle, tvExportDirectoryPath)
        }

        cgResolution.setOnCheckedStateChangeListener { _, _ -> saveSettings() }
        cgFps.setOnCheckedStateChangeListener { _, _ -> saveSettings() }
        switchAudioOnly.setOnCheckedChangeListener { _, _ -> saveSettings() }

        btnClose.setOnClickListener {
            bottomSheetDialog.dismiss()
        }

        layoutExportDirectory.setOnClickListener {
            callbacks.onPickDirectoryRequested()
        }

        bottomSheetDialog.setOnDismissListener {
            activeDirectoryTitleView = null
            activeDirectoryPathView = null
        }

        sheetView.findViewById<View>(R.id.btnSaveProject)?.setOnClickListener {
            bottomSheetDialog.dismiss()
            callbacks.onSaveProjectRequested()
        }

        bottomSheetDialog.setContentView(sheetView)
        bottomSheetDialog.show()
    }

    fun onDirectoryPicked(treeUri: Uri) {
        val sharedPreferences = context.getSharedPreferences("thecut_prefs", Context.MODE_PRIVATE)
        val isAudioOnly = viewModel.exportAudioOnly.value
        val prefKey = if (isAudioOnly) "export_audio_directory_uri" else "export_directory_uri"
        sharedPreferences.edit().putString(prefKey, treeUri.toString()).apply()

        val activeTitle = activeDirectoryTitleView
        val activePath = activeDirectoryPathView
        if (activeTitle != null && activePath != null) {
            updateExportDirectoryUi(activeTitle, activePath)
        }
        Toast.makeText(context, R.string.toast_export_location_updated_succes, Toast.LENGTH_SHORT).show()
    }

    fun updateExportDirectoryUi(titleView: TextView, pathView: TextView) {
        val sharedPreferences = context.getSharedPreferences("thecut_prefs", Context.MODE_PRIVATE)
        val isAudioOnly = viewModel.exportAudioOnly.value
        val prefKey = if (isAudioOnly) "export_audio_directory_uri" else "export_directory_uri"
        val customUriString = sharedPreferences.getString(prefKey, null)

        if (customUriString != null) {
            val customUri = Uri.parse(customUriString)
            val displayName = getDocumentFolderName(customUri) ?: "Custom Folder"
            titleView.text = displayName
            pathView.text = customUri.path ?: customUriString
        } else {
            titleView.text = "TheCut (Default)"
            pathView.text = if (isAudioOnly) "Music/TheCut" else "Movies/TheCut"
        }
    }

    private fun getDocumentFolderName(uri: Uri): String? {
        return try {
            val documentUri = DocumentsContract.buildDocumentUriUsingTree(
                uri,
                DocumentsContract.getTreeDocumentId(uri)
            )
            context.contentResolver.query(documentUri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) cursor.getString(index) else null
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun cancelExport() {
        callbacks.onCancelExportJob()
        viewModel.exportError("Export cancelled")
        Toast.makeText(context, R.string.toast_export_cancelled, Toast.LENGTH_SHORT).show()
    }
}
