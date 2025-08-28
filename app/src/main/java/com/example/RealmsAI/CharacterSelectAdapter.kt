package com.example.RealmsAI

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.models.CharacterProfile

class CharacterSelectAdapter(
    private val characters: List<CharacterProfile>,
    private val selectedIds: Set<String>,
    private val onToggle: (String) -> Unit,
    private val loadAvatar: (ImageView, String?) -> Unit
) : RecyclerView.Adapter<CharacterSelectAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val avatar: ImageView = itemView.findViewById(R.id.charAvatar1)
        val name: TextView = itemView.findViewById(R.id.charTitle)
        val summary: TextView = itemView.findViewById(R.id.charPreview)
        val bg: ImageView = itemView.findViewById(R.id.charCardBackground)

        fun bind(character: CharacterProfile) {
            name.text = character.name
            summary.text = character.summary ?: character.personality.take(40)
            loadAvatar(avatar, character.avatarUri)

            val isSelected = selectedIds.contains(character.id)
            bg.setImageResource(
                if (isSelected) R.drawable.chat_card_selected else R.drawable.chat_card
            )
            itemView.setOnClickListener {
                onToggle(character.id)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.character_preview_item, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(characters[position])
    }

    override fun getItemCount(): Int = characters.size
}
