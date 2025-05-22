package com.example.RealmsAI

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.models.PersonaProfile

class PersonaSelectAdapter(
    private val personas: List<PersonaProfile>,
    private val selectedIds: MutableSet<String>,
    private val onToggle: (String, Boolean) -> Unit,
    private val loadAvatar: (ImageView, String?) -> Unit // add this parameter
) : RecyclerView.Adapter<PersonaSelectAdapter.VH>() {
    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val avatar: ImageView = itemView.findViewById(R.id.itemPersonaAvatar)
        val name:   TextView  = itemView.findViewById(R.id.itemPersonaName)

        fun bind(persona: PersonaProfile) {
            loadAvatar(avatar, persona.avatarUri)
            name.text = persona.name
            itemView.isSelected = selectedIds.contains(persona.id)
            itemView.alpha = if (itemView.isSelected) 1f else 0.5f

            itemView.setOnClickListener {
                val now = !selectedIds.contains(persona.id)
                if (now) selectedIds += persona.id else selectedIds -= persona.id
                notifyItemChanged(adapterPosition)
                onToggle(persona.id, now)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_select_persona, parent, false)
        return VH(v)
    }

    override fun getItemCount() = personas.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(personas[position])
    }
}
