package com.example.RealmsAI

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.RealmsAI.models.ParticipantPreview
import com.example.RealmsAI.models.Relationship

class ParticipantRelationshipAdapter(
    val participants: List<ParticipantPreview>,
    // Change to var so we can update it if needed, though usually referencing the same list is fine
    var relationships: MutableList<Relationship>,
    val onAddRelationshipClick: (fromId: String) -> Unit, // Renamed for clarity
    val onDeleteRelationship: (Relationship) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    // Flatten for adapter: [header, rel, rel, header, rel...]
    private val items = mutableListOf<Any>()

    init { buildItemList() }

    private fun buildItemList() {
        items.clear()
        participants.forEach { participant ->
            items.add(participant) // 0: Header
            // Find all relationships FROM this participant
            val rels = relationships.filter { it.fromId == participant.id }
            items.addAll(rels) // 1: Relationship rows
        }
    }

    // --- NEW HELPER FUNCTION ---
    fun addRelationship(rel: Relationship) {
        relationships.add(rel)
        refresh() // Rebuilds list and notifies
    }
    // ---------------------------

    fun refresh() {
        buildItemList()
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun getItemViewType(position: Int) = when (items[position]) {
        is ParticipantPreview -> 0
        is Relationship -> 1
        else -> throw IllegalStateException("Unknown view type at $position")
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        when (viewType) {
            0 -> ParticipantHeaderHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_participant_header, parent, false)
            )
            1 -> RelationshipRowHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_relationship_row, parent, false)
            )
            else -> throw IllegalStateException("Unknown view type")
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is ParticipantPreview -> (holder as ParticipantHeaderHolder).bind(item)
            is Relationship -> (holder as RelationshipRowHolder).bind(item)
        }
    }

    inner class ParticipantHeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val name: TextView = view.findViewById(R.id.participantName)
        private val avatar: ImageView = view.findViewById(R.id.participantAvatar)
        private val btnAdd: ImageButton = view.findViewById(R.id.btnAddRelationship)

        fun bind(participant: ParticipantPreview) {
            name.text = participant.name
            // Pass the ID to the activity to open the dialog
            btnAdd.setOnClickListener { onAddRelationshipClick(participant.id) }

            if (!participant.avatarUri.isNullOrEmpty()) {
                Glide.with(avatar.context)
                    .load(participant.avatarUri)
                    .placeholder(R.drawable.placeholder_avatar)
                    .error(R.drawable.placeholder_avatar)
                    .circleCrop()
                    .into(avatar)
            } else {
                avatar.setImageResource(R.drawable.placeholder_avatar)
            }
        }
    }

    inner class RelationshipRowHolder(view: View) : RecyclerView.ViewHolder(view) {
        // NOTE: Ensure your IDs in item_relationship_row.xml match these
        private val toNameTv: TextView = view.findViewById(R.id.relationshipToNameEdit)
        private val typeTv: TextView = view.findViewById(R.id.relationshipType)
        private val summaryTv: TextView = view.findViewById(R.id.relationshipSummaryEdit)
        private val btnDelete: ImageButton = view.findViewById(R.id.btnDeleteRelationship)

        fun bind(rel: Relationship) {
            toNameTv.text = "➡ ${rel.toName}" // Added arrow for visual clarity
            typeTv.text = rel.type
            summaryTv.text = rel.description
            btnDelete.setOnClickListener {
                // Remove from the MAIN list
                relationships.remove(rel)
                // Refresh the adapter
                refresh()
            }
        }
    }
}