package com.albirich.RealmsAI

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import androidx.recyclerview.widget.RecyclerView
import com.albirich.RealmsAI.models.ModeSettings.RelationshipLevel

class RelationshipLevelAdapter(
    private val levels: MutableList<RelationshipLevel>,
    private val onDelete: (Int) -> Unit
) : RecyclerView.Adapter<RelationshipLevelAdapter.LevelViewHolder>() {

    override fun getItemId(position: Int): Long =
        levels[position].id.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LevelViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_relationship_level, parent, false)
        return LevelViewHolder(view)
    }

    override fun getItemCount() = levels.size

    override fun onBindViewHolder(holder: LevelViewHolder, position: Int) {
        holder.bind(levels[position])
    }

    inner class LevelViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val levelNumber: EditText = view.findViewById(R.id.levelNumberEdit)
        private val threshold: EditText   = view.findViewById(R.id.thresholdEdit)
        private val personality: EditText = view.findViewById(R.id.personalityEdit)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDeleteLevel)

        // Keep references so we can remove them on rebind
        private var levelWatcher: TextWatcher? = null
        private var thresholdWatcher: TextWatcher? = null
        private var personalityWatcher: TextWatcher? = null

        private var binding = false

        fun bind(item: RelationshipLevel) {
            // 1. COMPLETELY REMOVE OLD WATCHERS FIRST
            personalityWatcher?.let { personality.removeTextChangedListener(it) }
            thresholdWatcher?.let { threshold.removeTextChangedListener(it) }

            // 2. Set the text while it's "safe"
            binding = true
            levelNumber.setText(item.level.toString())
            threshold.setText(item.threshold.toString())
            personality.setText(item.personality)

            // Hardening limits
            personality.filters = arrayOf(android.text.InputFilter.LengthFilter(200))
            binding = false

            // 3. CREATE AND ATTACH NEW WATCHERS
            thresholdWatcher = object : SimpleTextWatcher() {
                override fun afterTextChanged(s: Editable) {
                    if (binding) return
                    val pos = bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) {
                        levels[pos].threshold = s.toString().toIntOrNull() ?: 0
                    }
                }
            }.also { threshold.addTextChangedListener(it) }

            personalityWatcher = object : SimpleTextWatcher() {
                override fun afterTextChanged(s: Editable) {
                    if (binding) return
                    val pos = bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) {
                        levels[pos].personality = s.toString()
                    }
                }
            }.also { personality.addTextChangedListener(it) }

            // Button click logic
            btnDelete.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onDelete(pos)
            }
        }
    }
}

// Small helper
abstract class SimpleTextWatcher : TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    override fun afterTextChanged(s: Editable) {}
}

