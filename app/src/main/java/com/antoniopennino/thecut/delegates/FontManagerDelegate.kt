package com.antoniopennino.thecut.delegates

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.antoniopennino.thecut.R
import com.antoniopennino.thecut.utils.FontItem
import com.antoniopennino.thecut.utils.FontManager
import com.antoniopennino.thecut.utils.setBounceClickListener
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Font manager bottom sheet: lists bundled + custom fonts, lets the user pick,
 * delete custom ones, or import a new .ttf/.otf.
 *
 * The document picker must be registered by the Activity
 * (registerForActivityResult), so importing is routed through
 * [Callbacks.launchFontImport]. Applying a font to the text overlay and
 * refreshing the font selectors are host concerns as well.
 *
 * Extracted from VideoEditingActivity.
 */
class FontManagerDelegate(
    private val activity: AppCompatActivity,
    private val callbacks: Callbacks
) {
    interface Callbacks {
        /** Path of the bundled default font copied to cache, if any. */
        fun getBundledFontPath(): String?
        /** Font path currently used by the text overlay being edited. */
        fun getCurrentFontPath(): String?
        /** Apply [path] to the text overlay being edited. */
        fun applyFontToTextOverlay(path: String)
        /** Rebuild the text and subtitle font selectors after the font set changed. */
        fun refreshFontSelectors()
        /** Open the system document picker; [onImported] runs once a font is imported. */
        fun launchFontImport(onImported: (FontItem) -> Unit)
    }

    fun show(onFontSelected: ((FontItem) -> Unit)? = null) {
        val bottomSheet = BottomSheetDialog(activity)
        val view = activity.layoutInflater.inflate(R.layout.dialog_font_manager, null)
        bottomSheet.setContentView(view)

        val btnClose = view.findViewById<ImageButton>(R.id.btnCloseFontManager)
        val btnImport = view.findViewById<Button>(R.id.btnImportFont)
        val rvFonts = view.findViewById<RecyclerView>(R.id.rvFonts)
        val tvEmpty = view.findViewById<TextView>(R.id.tvEmptyFonts)

        btnClose?.setBounceClickListener {
            bottomSheet.dismiss()
        }

        fun refreshFontList() {
            val allFonts = FontManager.getAllFonts(activity, callbacks.getBundledFontPath())
            if (allFonts.isEmpty()) {
                tvEmpty?.visibility = View.VISIBLE
                rvFonts?.visibility = View.GONE
            } else {
                tvEmpty?.visibility = View.GONE
                rvFonts?.visibility = View.VISIBLE

                rvFonts?.layoutManager = LinearLayoutManager(activity)
                rvFonts?.adapter = FontManagerAdapter(
                    fonts = allFonts,
                    selectedPath = callbacks.getCurrentFontPath(),
                    onSelect = { fontItem ->
                        onFontSelected?.invoke(fontItem)
                        callbacks.applyFontToTextOverlay(fontItem.path)
                        callbacks.refreshFontSelectors()
                        bottomSheet.dismiss()
                    },
                    onDelete = { fontItem ->
                        MaterialAlertDialogBuilder(activity)
                            .setTitle("Delete Font")
                            .setMessage("Are you sure you want to delete '${fontItem.name}'?")
                            .setPositiveButton("Delete") { _, _ ->
                                if (FontManager.deleteCustomFont(activity, fontItem.path)) {
                                    Toast.makeText(activity, R.string.str_toast_font_deleted, Toast.LENGTH_SHORT).show()
                                    refreshFontList()
                                    callbacks.refreshFontSelectors()
                                } else {
                                    Toast.makeText(activity, R.string.str_toast_font_delete_failed, Toast.LENGTH_SHORT).show()
                                }
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                )
            }
        }

        btnImport?.setBounceClickListener {
            callbacks.launchFontImport { importedFont ->
                onFontSelected?.invoke(importedFont)
                callbacks.applyFontToTextOverlay(importedFont.path)
                refreshFontList()
            }
        }

        refreshFontList()
        bottomSheet.show()
    }

    private class FontManagerAdapter(
        private val fonts: List<FontItem>,
        private val selectedPath: String?,
        private val onSelect: (FontItem) -> Unit,
        private val onDelete: (FontItem) -> Unit
    ) : RecyclerView.Adapter<FontManagerAdapter.FontViewHolder>() {

        class FontViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvFontName)
            val tvPreview: TextView = view.findViewById(R.id.tvFontPreview)
            val tvBadge: TextView = view.findViewById(R.id.tvFontBadge)
            val ivSelected: ImageView = view.findViewById(R.id.ivFontSelected)
            val btnDelete: ImageButton = view.findViewById(R.id.btnDeleteFont)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FontViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_font_manager, parent, false)
            return FontViewHolder(view)
        }

        override fun onBindViewHolder(holder: FontViewHolder, position: Int) {
            val font = fonts[position]
            holder.tvName.text = font.name
            holder.tvPreview.text = "Aa The quick brown fox 123"
            holder.tvPreview.typeface = font.typeface ?: Typeface.DEFAULT

            if (font.isCustom) {
                holder.tvBadge.visibility = View.VISIBLE
                holder.tvBadge.text = "Custom"
                holder.btnDelete.visibility = View.VISIBLE
                holder.btnDelete.setOnClickListener { onDelete(font) }
            } else {
                holder.tvBadge.visibility = View.GONE
                holder.btnDelete.visibility = View.GONE
            }

            val isSelected = selectedPath != null && font.path == selectedPath
            holder.ivSelected.visibility = if (isSelected) View.VISIBLE else View.GONE

            holder.itemView.setOnClickListener {
                onSelect(font)
            }
        }

        override fun getItemCount() = fonts.size
    }
}
