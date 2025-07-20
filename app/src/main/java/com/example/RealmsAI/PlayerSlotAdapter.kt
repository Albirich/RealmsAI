package com.example.RealmsAI

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.RealmsAI.models.PlayerSlot
import com.example.RealmsAI.models.PersonaProfile
import com.example.RealmsAI.models.UserProfile
import com.google.firebase.firestore.FirebaseFirestore

class PlayerSlotAdapter(
    private var userIds: List<String>,
    private val onUserClick: (String) -> Unit
) : RecyclerView.Adapter<PlayerSlotAdapter.UserViewHolder>() {

    override fun getItemCount() = userIds.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.row_player_slot, parent, false)
        return UserViewHolder(view)
    }

    fun setUserIds(userIds: List<String>) {
        this.userIds = userIds
        notifyDataSetChanged()
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val userId = userIds[position]
        holder.bind(userId)
        holder.itemView.setOnClickListener { onUserClick(userId) }
    }

    class UserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val avatar: ImageView = view.findViewById(R.id.personaAvatar)
        private val name: TextView = view.findViewById(R.id.personaName)

        fun bind(userId: String) {
            // Set default values while loading
            name.text = "Loading..."
            avatar.setImageResource(R.drawable.icon_01)

            // Now fetch user profile from Firestore:
            val db = FirebaseFirestore.getInstance()
            db.collection("users").document(userId).get()
                .addOnSuccessListener { doc ->
                    val username = doc.getString("name") ?: doc.getString("handle") ?: "Player"
                    val avatarUrl = doc.getString("avatarUrl")
                    name.text = username
                    if (!avatarUrl.isNullOrEmpty()) {
                        Glide.with(avatar.context)
                            .load(avatarUrl)
                            .placeholder(R.drawable.icon_01)
                            .into(avatar)
                    } else {
                        avatar.setImageResource(R.drawable.icon_01)
                    }
                }
        }
    }


}

