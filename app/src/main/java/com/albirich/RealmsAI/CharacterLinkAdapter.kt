package com.albirich.RealmsAI.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.albirich.RealmsAI.R
import com.albirich.RealmsAI.models.CharacterLink

class CharacterLinkAdapter(
    private val links: MutableList<CharacterLink>,
    private val onDelete: (Int) -> Unit
) : RecyclerView.Adapter<CharacterLinkAdapter.ViewHolder>() {

    // The different link types we support
    private val linkTypes = listOf("Switch", "Inseparable", "Summon", "Time Skip", "Fusion", "Telepathy")

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val avatarImg: ImageView = view.findViewById(R.id.linkAvatarImage)
        val nameTxt: TextView = view.findViewById(R.id.linkTargetName)
        val typeSpinner: Spinner = view.findViewById(R.id.linkTypeSpinner)
        val triggerEt: EditText = view.findViewById(R.id.linkTriggerDetails)
        val deleteBtn: ImageButton = view.findViewById(R.id.deleteLinkBtn)
        val infoBtn: View = view.findViewById(R.id.infoButtonLinkType)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_character_link, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val link = links[position]

        // 1. Basic Info
        holder.nameTxt.text = link.targetName.ifBlank { "Unknown Character" }

        if (link.targetAvatar.isNotBlank()) {
            Glide.with(holder.itemView.context)
                .load(link.targetAvatar)
                .placeholder(R.drawable.placeholder_avatar) // Shows while loading
                .centerCrop()
                .into(holder.avatarImg)
        } else {
            // Fallback if they don't have an avatar set
            holder.avatarImg.setImageResource(R.drawable.placeholder_avatar)
        }

        // 2. Set up Spinner
        val spinnerAdapter = ArrayAdapter(holder.itemView.context, android.R.layout.simple_spinner_item, linkTypes)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        holder.typeSpinner.adapter = spinnerAdapter

        // Pre-select current type
        val currentTypeIndex = linkTypes.indexOfFirst { it.equals(link.type, ignoreCase = true) }
        if (currentTypeIndex >= 0) holder.typeSpinner.setSelection(currentTypeIndex)

        // 3. Dynamic Hint Logic based on Spinner selection
        holder.typeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                val selectedType = linkTypes[pos]
                link.type = selectedType.lowercase()

                // Change the EditText hint to guide the user based on the selected type!
                holder.triggerEt.hint = when (link.type) {
                    "switch" -> "What triggers the switch? (e.g., Getting knocked out)"
                    "inseparable" -> "Z-Axis (the higher the number the closer they are. min 1, max 4)"
                    "time skip" -> "Duration of skip (e.g., '3 Years Later')"
                    "summon" -> "Summoning condition or required item"
                    "fusion" -> "How do they fuse together? Who do they fuse with?"
                    "telepathy" -> "Describe the mental connection"
                    else -> "Details / Triggers"
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        holder.infoBtn.setOnClickListener {
            val explainerText = """
                • Switch/Possession: Replace this character with the linked character (One-way unless linked back!).
                • Inseparable: NOT YET IMPLEMENTED, THIS LINK WILL NOT HAVE AN EFFECT.
                • Time Skip: NOT YET IMPLEMENTED, THIS LINK WILL NOT HAVE AN EFFECT.
                • Summon: Temporarily adds the linked character to the location.
                • Fusion: Deactivate this and other characterS if they are linked to the same fusion target, temporarily adding the linked character to the roster. 
                • Telepathy: NOT YET IMPLEMENTED, THIS LINK WILL NOT HAVE AN EFFECT.
            """.trimIndent()

            androidx.appcompat.app.AlertDialog.Builder(holder.itemView.context)
                .setTitle("Link Types")
                .setMessage(explainerText)
                .setPositiveButton("Got it", null)
                .show()
        }

        // --- Dynamic Hint Logic based on Spinner selection ---
        holder.typeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                val selectedType = linkTypes[pos]
                link.type = selectedType.lowercase()

                // Just update the EditText placeholder now!
                holder.triggerEt.hint = when (link.type) {
                    "switch", "possession" -> "What triggers the switch? (e.g., Getting knocked out)"
                    "inseparable" -> "Z-Axis (the higher the number the closer they are. min 1, max 4)"
                    "time skip" -> "Duration of skip (e.g., '3 Years Later')"
                    "summon" -> "Summoning condition or required item"
                    "fusion" -> "How do they fuse together? Requirement?"
                    "telepathy" -> "Describe the mental connection"
                    else -> "Details / Triggers"
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 4. Save typing to the object
        holder.triggerEt.setText(link.trigger)
        holder.triggerEt.doAfterTextChanged { text ->
            link.trigger = text?.toString()?.trim() ?: ""
        }

        // 5. Delete Button
        holder.deleteBtn.setOnClickListener {
            val currentPos = holder.bindingAdapterPosition // Ask the view where it is right now

            // Check to make sure it hasn't already been deleted
            if (currentPos != RecyclerView.NO_POSITION) {
                onDelete(currentPos)
            }
        }
    }

    override fun getItemCount() = links.size
}