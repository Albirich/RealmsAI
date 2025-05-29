package com.example.RealmsAI

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.models.CharacterProfile

/**
 * @param characters   list of all Character items
 * @param selectedIds  a mutable set of currently–selected character IDs
 * @param onToggle     callback invoked (charId, isNowSelected) when user taps an item
 */
class CharacterSelectAdapter(
    private val characters: List<CharacterProfile>,
    private val selectedIds: Set<String>,
    private val onToggle: (String, Boolean) -> Unit,
    private val loadAvatar: (ImageView, String?) -> Unit
) : RecyclerView.Adapter<CharacterSelectAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val avatar: ImageView = itemView.findViewById(R.id.chatAvatar1)
        val name: TextView = itemView.findViewById(R.id.chatTitle)
        val summary: TextView = itemView.findViewById(R.id.chatPreview)

        fun bind(character: CharacterProfile) {
            loadAvatar(avatar, character.avatarUri)
            name.text = character.name
            summary.text = character.summary ?: character.personality.take(40)

            val isSelected = selectedIds.contains(character.id)
            itemView.isSelected = isSelected

            itemView.setOnClickListener {
                onToggle(character.id, !isSelected)
                notifyItemChanged(adapterPosition)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.character_preview_item, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(characters[position])
    }

    override fun getItemCount(): Int = characters.size
}
