package com.example.RealmsAI

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.models.RelationshipLevel

class RelationshipLevelAdapter(
    private val levels: MutableList<RelationshipLevel>,
    private val onDelete: (Int) -> Unit
) : RecyclerView.Adapter<RelationshipLevelAdapter.LevelViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LevelViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_relationship_level, parent, false)
        return LevelViewHolder(view)
    }

    override fun getItemCount() = levels.size

    override fun onBindViewHolder(holder: LevelViewHolder, position: Int) {
        holder.bind(levels[position])
        holder.btnDelete.setOnClickListener {
            onDelete(position)
        }
    }

    inner class LevelViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val levelNumber = view.findViewById<EditText>(R.id.levelNumberEdit)
        val threshold = view.findViewById<EditText>(R.id.thresholdEdit)
        val personality = view.findViewById<EditText>(R.id.personalityEdit)
        val btnDelete = view.findViewById<ImageButton>(R.id.btnDeleteLevel)

        fun bind(level: RelationshipLevel) {
            levelNumber.setText(level.level.toString())
            threshold.setText(level.threshold.toString())
            personality.setText(level.personality)
            // Optionally add listeners to update the model as the user edits.
        }
    }
}
