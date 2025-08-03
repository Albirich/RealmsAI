package com.example.RealmsAI

import android.app.Activity
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.models.Relationship
import com.example.RealmsAI.models.SlotProfile

class VNRelationshipAdapter(
    private val characters: List<SlotProfile>,
    private val mainCharId: String
) : RecyclerView.Adapter<VNRelationshipAdapter.RelViewHolder>() {

    // Store relationships for each character, indexed by slotId
    private val relationships: MutableMap<String, Relationship> = mutableMapOf()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RelViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_relationship, parent, false)
        return RelViewHolder(view)
    }

    override fun getItemCount() = characters.size

    override fun onBindViewHolder(holder: RelViewHolder, position: Int) {
        val char = characters[position]
        holder.nameView.text = char.name
        val rel = relationships[char.slotId] ?: Relationship() // or pull from saved data

        // -- Add Edit Button logic here --
        holder.editButton.setOnClickListener {
            val intent = Intent(holder.itemView.context, RelationshipLevelEditorActivity::class.java)
            // put extras here
            (holder.itemView.context as Activity).startActivityForResult(intent, 101)
        }
    }

    // Helper to return all relationships on save
    fun getRelationships(): List<Relationship> = relationships.values.toList()

    inner class RelViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameView: TextView = view.findViewById(R.id.characterName)
        val editButton: Button = view.findViewById(R.id.editRelationshipBtn)
    }
}
