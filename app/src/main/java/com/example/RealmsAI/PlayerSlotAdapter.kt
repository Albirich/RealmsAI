package com.example.RealmsAI

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.RealmsAI.R
import com.example.RealmsAI.models.PlayerSlot
import com.example.RealmsAI.models.PersonaProfile

class PlayerSlotAdapter(
    private val slots: MutableList<PlayerSlot>,
    private val personaProfiles: List<PersonaProfile>, // <-- add persona lookup
    private val onSlotClick: (String) -> Unit // Passes slotId when clicked
) : RecyclerView.Adapter<PlayerSlotAdapter.SlotViewHolder>() {

    class SlotViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val avatar: ImageView = view.findViewById(R.id.personaAvatar)
        val name: TextView = view.findViewById(R.id.personaName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlotViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.row_player_slot, parent, false)
        return SlotViewHolder(view)
    }

    override fun getItemCount() = slots.size

    override fun onBindViewHolder(holder: SlotViewHolder, position: Int) {
        val slot = slots[position]
        // If a persona is assigned, show persona name/avatar; otherwise default
        val persona = slot.personaId?.let { personaId ->
            personaProfiles.find { it.id == personaId }
        }

        holder.name.text = persona?.name ?: slot.name.ifBlank { "Select Persona" }
        val avatarUri = persona?.avatarUri ?: slot.avatarUri
        if (!avatarUri.isNullOrEmpty()) {
            Glide.with(holder.avatar.context)
                .load(avatarUri)
                .placeholder(R.drawable.icon_01)
                .into(holder.avatar)
        } else {
            holder.avatar.setImageResource(R.drawable.icon_01)
        }

        // Click: pick persona
        holder.itemView.setOnClickListener {
            onSlotClick(slot.slotId)
        }
    }

    /**
     * Updates the personaId (and optionally avatarUri) for a specific slot.
     * Call this after the user picks a persona for a player slot.
     */
    fun setPersonaForSlot(slotId: String, personaId: String, avatarUri: String? = null) {
        val idx = slots.indexOfFirst { it.slotId == slotId }
        if (idx != -1) {
            val oldSlot = slots[idx]
            slots[idx] = oldSlot.copy(
                personaId = personaId,
                avatarUri = avatarUri ?: oldSlot.avatarUri
            )
            notifyItemChanged(idx)
        }
    }

    /**
     * If you ever want to update all slots at once.
     */
    fun setSlots(newSlots: List<PlayerSlot>) {
        slots.clear()
        slots.addAll(newSlots)
        notifyDataSetChanged()
    }
}
