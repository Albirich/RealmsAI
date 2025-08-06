package com.example.RealmsAI

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.models.ModeSettings.RelationshipLevel

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

            // Remove existing listeners before adding new ones if reusing view holders (optional but good for production)
            // Or use .doOnTextChanged in androidx.core

            // Level number editing
            levelNumber.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    val newVal = levelNumber.text.toString().toIntOrNull() ?: 0
                    level.level = newVal
                }
            }

            // Threshold editing
            threshold.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    val newVal = threshold.text.toString().toIntOrNull() ?: 0
                    level.threshold = newVal
                }
            }

            // Personality editing
            personality.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    level.personality = personality.text.toString()
                }
            }
        }
    }
}
