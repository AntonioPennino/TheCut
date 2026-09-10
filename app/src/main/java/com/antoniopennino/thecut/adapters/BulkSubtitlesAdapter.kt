package com.antoniopennino.thecut.adapters

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.antoniopennino.thecut.R
import com.antoniopennino.thecut.models.SubtitleCue
import com.antoniopennino.thecut.utils.SubtitleParser

class BulkSubtitlesAdapter(
    private val cues: MutableList<SubtitleCue>,
    private val onPlayCue: (SubtitleCue) -> Unit,
    private val onEditCueDetails: (Int, SubtitleCue) -> Unit,
    private val onDeleteCue: (Int) -> Unit,
    private val onTextChanged: (Int, String) -> Unit
) : RecyclerView.Adapter<BulkSubtitlesAdapter.CueViewHolder>() {

    class CueViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvIndex: TextView = view.findViewById(R.id.tvCueIndex)
        val tvTimeRange: TextView = view.findViewById(R.id.tvCueTimeRange)
        val btnPlay: ImageButton = view.findViewById(R.id.btnPlayCue)
        val btnEditDetails: ImageButton = view.findViewById(R.id.btnEditCueDetails)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDeleteCue)
        val etText: EditText = view.findViewById(R.id.etCueText)
        var textWatcher: TextWatcher? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CueViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_subtitle_cue_row, parent, false)
        return CueViewHolder(view)
    }

    override fun onBindViewHolder(holder: CueViewHolder, position: Int) {
        val cue = cues[position]

        holder.tvIndex.text = "#${position + 1}"
        holder.tvTimeRange.text = "${SubtitleParser.formatShortTime(cue.startTimeMs)} → ${SubtitleParser.formatShortTime(cue.endTimeMs)}"

        // Remove old watcher before setting text to avoid infinite loops during recycling
        holder.textWatcher?.let { holder.etText.removeTextChangedListener(it) }

        holder.etText.setText(cue.text)

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val currentPos = holder.adapterPosition
                if (currentPos != RecyclerView.NO_POSITION && currentPos in cues.indices) {
                    val updatedText = s?.toString() ?: ""
                    cues[currentPos] = cues[currentPos].copy(text = updatedText)
                    onTextChanged(currentPos, updatedText)
                }
            }
        }
        holder.textWatcher = watcher
        holder.etText.addTextChangedListener(watcher)

        holder.tvTimeRange.setOnClickListener {
            onPlayCue(cues[holder.adapterPosition])
        }

        holder.btnPlay.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_POSITION && pos in cues.indices) {
                onPlayCue(cues[pos])
            }
        }

        holder.btnEditDetails.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_POSITION && pos in cues.indices) {
                onEditCueDetails(pos, cues[pos])
            }
        }

        holder.btnDelete.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_POSITION && pos in cues.indices) {
                onDeleteCue(pos)
            }
        }
    }

    override fun getItemCount(): Int = cues.size

    fun updateData(newCues: List<SubtitleCue>) {
        cues.clear()
        cues.addAll(newCues)
        notifyDataSetChanged()
    }

    fun getCues(): List<SubtitleCue> = cues.toList()
}
