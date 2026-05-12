package com.albirich.RealmsAI.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.albirich.RealmsAI.R
import com.albirich.RealmsAI.models.LoreEntry

class LoreEntryAdapter(
    private val entries: MutableList<LoreEntry>,
    private val onClick: (LoreEntry) -> Unit
) : RecyclerView.Adapter<LoreEntryAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameTxt: TextView = view.findViewById(R.id.entryNameTxt)
        val keysTxt: TextView = view.findViewById(R.id.entryKeysTxt)

        init {
            view.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onClick(entries[adapterPosition])
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_lore_entry, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = entries[position]
        holder.nameTxt.text = entry.name.ifBlank { "Unnamed Entry" }
        // Join the list of keys back into a readable string
        holder.keysTxt.text = "Keys: ${entry.keys.joinToString(", ")}"
    }

    override fun getItemCount(): Int = entries.size
}