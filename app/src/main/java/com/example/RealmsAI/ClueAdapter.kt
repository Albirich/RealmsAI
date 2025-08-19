package com.example.RealmsAI

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.models.ModeSettings

class ClueAdapter(
    private val items: MutableList<ModeSettings.MurderClue>,
    private val onEdit: (ModeSettings.MurderClue) -> Unit,
    private val onDelete: (ModeSettings.MurderClue) -> Unit
) : RecyclerView.Adapter<ClueAdapter.ClueVH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClueVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_clue_row, parent, false)
        return ClueVH(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ClueVH, position: Int) {
        holder.bind(items[position], onEdit, onDelete)
    }

    fun setData(newItems: List<ModeSettings.MurderClue>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    class ClueVH(view: View) : RecyclerView.ViewHolder(view) {
        private val titleView: TextView = view.findViewById(R.id.clueTitle)
        private val descView: TextView = view.findViewById(R.id.clueDesc)
        private val editButton: ImageButton = view.findViewById(R.id.btnEditClue)
        private val deleteButton: ImageButton = view.findViewById(R.id.btnDeleteClue)

        fun bind(
            clue: ModeSettings.MurderClue,
            onEdit: (ModeSettings.MurderClue) -> Unit,
            onDelete: (ModeSettings.MurderClue) -> Unit
        ) {
            titleView.text = clue.title
            descView.text = clue.description
            editButton.setOnClickListener { onEdit(clue) }
            deleteButton.setOnClickListener { onDelete(clue) }
        }
    }
}
