package com.example.RealmsAI

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.RealmsAI.models.CharacterProfile

class CharacterChipAdapter(
    private val characters: List<CharacterProfile>
) : RecyclerView.Adapter<CharacterChipAdapter.CharViewHolder>() {

    class CharViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val avatar: ImageView = view.findViewById(R.id.characterAvatar)
        val name: TextView = view.findViewById(R.id.characterName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CharViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.character_chip_display, parent, false)
        return CharViewHolder(view)
    }

    override fun onBindViewHolder(holder: CharViewHolder, position: Int) {
        val char = characters[position]
        holder.name.text = char.name

        // Find first pose of first outfit
        val poseUri = char.outfits.firstOrNull()?.poseSlots?.firstOrNull()?.uri
        Glide.with(holder.avatar.context)
            .load(poseUri ?: char.avatarUri) // fallback to main avatar
            .placeholder(R.drawable.placeholder_avatar)
            .into(holder.avatar)
    }

    override fun getItemCount() = characters.size
}
