package com.example.RealmsAI.adapters

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat.startActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.RealmsAI.CharacterSelectionActivity
import com.example.RealmsAI.R
import com.example.RealmsAI.models.CharacterCollection
import com.example.RealmsAI.models.CharacterProfile
import com.google.firebase.firestore.FirebaseFirestore

class CollectionAdapter(
    private val collections: List<CharacterCollection>,
    private val onEditClicked: (CharacterCollection) -> Unit,
    private val onDeleteClicked: () -> Unit
) : RecyclerView.Adapter<CollectionAdapter.CollectionViewHolder>() {


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CollectionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.collection_item, parent, false)
        return CollectionViewHolder(view)
    }

    override fun getItemCount(): Int = collections.size

    override fun onBindViewHolder(holder: CollectionViewHolder, position: Int) {
        holder.bind(collections[position])

    }

    inner class CollectionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.collectionNameText)
        private val characterRow: RecyclerView = itemView.findViewById(R.id.characterRecycler)
        private val editButton: ImageButton = itemView.findViewById(R.id.btnEditCollection)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.btnDeleteCollection)

        fun bind(collection: CharacterCollection) {
            nameText.text = collection.name

            characterRow.layoutManager =
                LinearLayoutManager(itemView.context, LinearLayoutManager.HORIZONTAL, false)

            fetchCharacterProfiles(collection.characterIds) { profiles ->
                characterRow.adapter = CharacterRowAdapter(
                    characters = profiles,
                    onClick = { /*-*/ }
                )
            }

            editButton.setOnClickListener {
                onEditClicked(collection)
            }

            deleteButton.setOnClickListener {
                val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener
                val db = FirebaseFirestore.getInstance()

                db.collection("users").document(userId)
                    .collection("collections").document(collection.id)
                    .delete()
                    .addOnSuccessListener {
                        // Reload list from parent context
                        (itemView.context as? android.app.Activity)?.runOnUiThread {
                            android.widget.Toast.makeText(itemView.context, "Collection deleted", android.widget.Toast.LENGTH_SHORT).show()
                            onDeleteClicked()
                            // Ideally: notify parent activity to refresh
                        }
                    }
                    .addOnFailureListener {
                        android.widget.Toast.makeText(
                            itemView.context,
                            "Delete failed",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
            }
        }

        private fun fetchCharacterProfiles(
            characterIds: List<String>,
            callback: (List<CharacterProfile>) -> Unit
        ) {
            if (characterIds.isEmpty()) {
                callback(emptyList())
                return
            }

            val db = FirebaseFirestore.getInstance()
            db.collection("characters")
                .whereIn("id", characterIds)
                .get()
                .addOnSuccessListener { result ->
                    val profiles = result.mapNotNull { it.toObject(CharacterProfile::class.java) }
                    callback(profiles)
                }
                .addOnFailureListener {
                    callback(emptyList())
                }
        }
    }

    class CharacterRowAdapter(
        private val characters: List<CharacterProfile>,
        private val onClick: (CharacterProfile) -> Unit,
        private val onBindVisuals: (CharacterProfile, View) -> Unit = { _, _ -> }
    ) : RecyclerView.Adapter<CharacterRowAdapter.HaloViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HaloViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.collection_list_item, parent, false)
            return HaloViewHolder(view)
        }

        override fun getItemCount(): Int = characters.size

        override fun onBindViewHolder(holder: HaloViewHolder, position: Int) {
            val character = characters[position]
            holder.nameText.text = character.name
            holder.slotNumberText.text = "Character ${position + 1}"

            Glide.with(holder.itemView)
                .load(character.avatarUri)
                .placeholder(R.drawable.placeholder_avatar)
                .into(holder.iconView)

            // Set up click listener
            holder.itemView.setOnClickListener {
                onClick(character)
            }

            // Set custom visuals (border, etc.)
            onBindVisuals(character, holder.itemView)
        }

        class HaloViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val iconView = view.findViewById<android.widget.ImageView>(R.id.character_image)
            val nameText = view.findViewById<TextView>(R.id.character_name)
            val slotNumberText = view.findViewById<TextView>(R.id.character_slot_number)
        }
    }

}
