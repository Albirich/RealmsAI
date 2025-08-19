package com.example.RealmsAI

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import androidx.core.widget.doOnTextChanged
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
        holder.btnDelete.setOnClickListener { onDelete(position) }
    }

    inner class LevelViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val levelNumber: EditText = view.findViewById(R.id.levelNumberEdit)
        val threshold: EditText   = view.findViewById(R.id.thresholdEdit)
        val personality: EditText = view.findViewById(R.id.personalityEdit)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDeleteLevel)

        // Guard so setText during bind doesn't trigger our listeners
        private var binding = false

        fun bind(level: RelationshipLevel) {
            binding = true
            levelNumber.setText(level.level.toString())
            threshold.setText(level.threshold.toString())
            personality.setText(level.personality)
            binding = false

            // Remove any previous editor action listeners to be safe
            levelNumber.setOnEditorActionListener(null)
            threshold.setOnEditorActionListener(null)
            personality.setOnEditorActionListener(null)

            // ---- Write as you type ----
            levelNumber.doOnTextChanged { text, _, _, _ ->
                if (binding) return@doOnTextChanged
                level.level = text?.toString()?.toIntOrNull() ?: 0
            }
            threshold.doOnTextChanged { text, _, _, _ ->
                if (binding) return@doOnTextChanged
                level.threshold = text?.toString()?.toIntOrNull() ?: 0
            }
            personality.doOnTextChanged { text, _, _, _ ->
                if (binding) return@doOnTextChanged
                level.personality = text?.toString().orEmpty()
            }

            // ---- Also commit on IME actions ----
            val commitNumber: (Int) -> Boolean = {
                level.level = levelNumber.text.toString().toIntOrNull() ?: 0; true
            }
            val commitThreshold: (Int) -> Boolean = {
                level.threshold = threshold.text.toString().toIntOrNull() ?: 0; true
            }
            val commitPersonality: (Int) -> Boolean = {
                level.personality = personality.text.toString(); true
            }

            levelNumber.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) commitNumber(actionId) else false
            }
            threshold.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) commitThreshold(actionId) else false
            }
            personality.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) commitPersonality(actionId) else false
            }
        }
    }
}
