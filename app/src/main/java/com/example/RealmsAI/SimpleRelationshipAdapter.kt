package com.example.RealmsAI

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.models.Relationship

class SimpleRelationshipAdapter(
    private val relationships: MutableList<Relationship>,
    private val onDelete: (Relationship) -> Unit
) : RecyclerView.Adapter<SimpleRelationshipAdapter.RelationshipRowHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RelationshipRowHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_relationship_row, parent, false)
        return RelationshipRowHolder(view)
    }

    override fun onBindViewHolder(holder: RelationshipRowHolder, position: Int) {
        holder.bind(relationships[position])
    }

    override fun getItemCount() = relationships.size

    inner class RelationshipRowHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val toNameText = view.findViewById<TextView>(R.id.relationshipToNameEdit)
        private val typeText = view.findViewById<TextView>(R.id.relationshipType)
        private val summaryText = view.findViewById<TextView>(R.id.relationshipSummaryEdit)
        private val btnDelete = view.findViewById<ImageButton>(R.id.btnDeleteRelationship)

        fun bind(rel: Relationship) {
            toNameText.text = rel.toName
            typeText.text = rel.type
            summaryText.text = rel.description ?: ""

            btnDelete.setOnClickListener {
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onDelete(rel)
                }
            }
        }
    }
}