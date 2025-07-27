package com.example.RealmsAI

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.RealmsAI.models.ParticipantPreview
import com.example.RealmsAI.models.Relationship
import com.example.RealmsAI.R

class ParticipantRelationshipAdapter(
    val participants: List<ParticipantPreview>,
    val relationships: MutableList<Relationship>,
    val onAddRelationship: (fromId: String) -> Unit,
    val onDeleteRelationship: (Relationship) -> Unit,
    val onEditLevel: (Relationship, Int) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    // Flatten for adapter: [header, rel, rel, header, rel...]
    private val items = mutableListOf<Any>()
    init { buildItemList() }
    private fun buildItemList() {
        items.clear()
        participants.forEach { participant ->
            items.add(participant)
            items.addAll(relationships.filter { it.fromId == participant.id })
        }
    }

    override fun getItemCount() = items.size

    override fun getItemViewType(position: Int) = when (items[position]) {
        is ParticipantPreview -> 0
        is Relationship -> 1
        else -> throw IllegalStateException("Unknown view type")
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
        private val name = view.findViewById<TextView>(R.id.participantName)
        private val avatar = view.findViewById<ImageView>(R.id.participantAvatar)
        private val btnAdd = view.findViewById<ImageButton>(R.id.btnAddRelationship)
        fun bind(participant: ParticipantPreview) {
            name.text = participant.name
            btnAdd.setOnClickListener { onAddRelationship(participant.id) }

            // Load avatar (null or empty fallback to a default drawable)
            if (!participant.avatarUri.isNullOrEmpty()) {
                Glide.with(avatar.context)
                    .load(participant.avatarUri)
                    .placeholder(R.drawable.placeholder_avatar)  // <-- add a placeholder in res/drawable
                    .error(R.drawable.placeholder_avatar)
                    .circleCrop()
                    .into(avatar)
            } else {
                avatar.setImageResource(R.drawable.placeholder_avatar)
            }
        }
    }
    inner class RelationshipRowHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val toNameEdit = view.findViewById<TextView>(R.id.relationshipToNameEdit)
        private val type = view.findViewById<TextView>(R.id.relationshipType)
        private val summary = view.findViewById<TextView>(R.id.relationshipSummaryEdit)
        private val btnDelete = view.findViewById<ImageButton>(R.id.btnDeleteRelationship)
        private val btnLevel = view.findViewById<ImageButton>(R.id.btnRelationshipLvl)
        fun bind(rel: Relationship) {
            toNameEdit.setText(rel.toName)
            type.text = rel.type
            summary.text = rel.description
            btnDelete.setOnClickListener { onDeleteRelationship(rel) }
            btnLevel.setOnClickListener {
                val pos = relationships.indexOf(rel)
                if (pos != -1) onEditLevel(rel, pos)
            }
        }
    }

    fun refresh() {
        buildItemList()
        notifyDataSetChanged()
    }
}
