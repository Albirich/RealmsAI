package com.example.emotichat

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * @param characters   list of all Character items
 * @param selectedIds  a mutable set of currently–selected character IDs
 * @param onToggle     callback invoked (charId, isNowSelected) when user taps an item
 */
class CharacterAdapter(
    private val characters: List<Character>,
    private val selectedIds: MutableSet<String>,
    private val onToggle: (String, Boolean) -> Unit
) : RecyclerView.Adapter<CharacterAdapter.VH>() {

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val avatar: ImageView = itemView.findViewById(R.id.itemCharAvatar)
        val name:   TextView  = itemView.findViewById(R.id.itemCharName)

        fun bind(char: Character) {
            // load avatar from local URI, or show placeholder
            if (!char.avatarUri.isNullOrEmpty()) {
                avatar.setImageURI(Uri.parse(char.avatarUri))
            } else {
                avatar.setImageResource(R.drawable.placeholder_avatar)
            }

            name.text = char.name

            // highlight if selected
            itemView.isSelected = selectedIds.contains(char.id)
            itemView.alpha = if (itemView.isSelected) 1f else 0.5f

            itemView.setOnClickListener {
                val now = !selectedIds.contains(char.id)
                if (now) selectedIds += char.id else selectedIds -= char.id
                notifyItemChanged(adapterPosition)
                onToggle(char.id, now)
            }
        }
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_select_character, parent, false)
        return VH(v)
    }

    override fun getItemCount() = characters.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(characters[position])
    }
}
