package com.empireb.haptix

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.DecimalFormat
import kotlin.math.log10
import kotlin.math.pow

class HapticFileAdapter(
    private val onFileClick: (HapticFile) -> Unit,
    private val onFileLongClick: (HapticFile) -> Unit
) : RecyclerView.Adapter<HapticFileAdapter.ViewHolder>() {

    private var files: List<HapticFile> = emptyList()

    fun submitList(newList: List<HapticFile>) {
        files = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_haptic_file, parent, false)
        return ViewHolder(view, onFileClick, onFileLongClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val file = files[position]
        holder.bind(file)
    }

    override fun getItemCount(): Int = files.size

    fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt()
        return DecimalFormat("#,##0.#").format(size / 1024.0.pow(digitGroups.toDouble())) + " " + units[digitGroups]
    }

    class ViewHolder(
        view: View,
        private val onFileClick: (HapticFile) -> Unit,
        private val onFileLongClick: (HapticFile) -> Unit
    ) : RecyclerView.ViewHolder(view) {
        private val nameText: TextView = view.findViewById(R.id.filename_text)
        private val sizeText: TextView = view.findViewById(R.id.filesize_text)

        fun bind(file: HapticFile) {
            nameText.text = file.name
            sizeText.text = formatFileSize(file.size)
            
            itemView.setOnClickListener { onFileClick(file) }
            itemView.setOnLongClickListener {
                onFileLongClick(file)
                true
            }
        }

        private fun formatFileSize(size: Long): String {
            if (size <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt()
            return DecimalFormat("#,##0.#").format(size / 1024.0.pow(digitGroups.toDouble())) + " " + units[digitGroups]
        }
    }
}
