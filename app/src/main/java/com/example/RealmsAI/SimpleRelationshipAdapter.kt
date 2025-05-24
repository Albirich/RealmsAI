package com.example.RealmsAI

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Spinner
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.models.RELATIONSHIP_TYPES
import com.example.RealmsAI.models.Relationship
import androidx.core.widget.addTextChangedListener
import android.widget.ArrayAdapter

class SimpleRelationshipAdapter(
    private val relationships: MutableList<Relationship>,
    private val onDelete: (Relationship) -> Unit,
    private val onChanged: () -> Unit // Call when any field is edited, so you can save changes if needed
) : RecyclerView.Adapter<SimpleRelationshipAdapter.RelationshipRowHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RelationshipRowHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_relationship_row, parent, false)
        return RelationshipRowHolder(view)
    }

    override fun onBindViewHolder(holder: RelationshipRowHolder, position: Int) {
        holder.bind(relationships[position])
    }

    override fun getItemCount() = relationships.size

    inner class RelationshipRowHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val toNameEdit = view.findViewById<EditText>(R.id.relationshipToNameEdit)
        private val typeSpinner = view.findViewById<Spinner>(R.id.relationshipTypeSpinner)
        private val summaryEdit = view.findViewById<EditText>(R.id.relationshipSummaryEdit)
        private val btnDelete = view.findViewById<ImageButton>(R.id.btnDeleteRelationship)

        fun bind(rel: Relationship) {
            toNameEdit.setText(rel.toName)
            // Set up spinner for type
            typeSpinner.adapter = ArrayAdapter(
                itemView.context,
                android.R.layout.simple_spinner_dropdown_item,
                RELATIONSHIP_TYPES
            )
            val idx = RELATIONSHIP_TYPES.indexOf(rel.type)
            if (idx >= 0) typeSpinner.setSelection(idx)

            summaryEdit.setText(rel.description ?: "")

            // Save on change (update model & notify)
            toNameEdit.addTextChangedListener {
                rel.toName = it.toString()
                onChanged()
            }
            summaryEdit.addTextChangedListener {
                rel.description = it.toString()
                onChanged()
            }
            // Remove "holder." here—just use typeSpinner
            typeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    rel.type = RELATIONSHIP_TYPES[position]
                    onChanged()
                }

                override fun onNothingSelected(parent: AdapterView<*>) {
                    // Optional: do nothing or handle if you want
                }
            }

            btnDelete.setOnClickListener {
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onDelete(rel)
                }
            }
        }
    }
}
