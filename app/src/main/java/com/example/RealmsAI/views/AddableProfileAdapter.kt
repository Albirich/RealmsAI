package com.example.RealmsAI.views

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.AddableProfile

class AddableProfileAdapter(
    private val onItemClick: (AddableProfile) -> Unit
) : ListAdapter<AddableProfile, AddableProfileAdapter.ProfileViewHolder>(DIFF) {

    private var selectedPos = RecyclerView.NO_POSITION

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProfileViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_activated_1, parent, false)
        return ProfileViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProfileViewHolder, position: Int) {
        val item = getItem(position)
        holder.itemView.isActivated = selectedPos == position

        holder.bind(item)
        holder.itemView.setOnClickListener {
            val oldPos = selectedPos
            selectedPos = holder.adapterPosition
            notifyItemChanged(oldPos)
            notifyItemChanged(selectedPos)
            onItemClick(item)
        }
    }

    class ProfileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val text1: TextView = view.findViewById(android.R.id.text1)
        fun bind(profile: AddableProfile) {
            when (profile) {
                is AddableProfile.Character -> text1.text = "Character: ${profile.profile.name}"
                is AddableProfile.Persona -> text1.text = "Persona: ${profile.profile.name}"
            }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<AddableProfile>() {
            override fun areItemsTheSame(oldItem: AddableProfile, newItem: AddableProfile): Boolean =
                when {
                    oldItem is AddableProfile.Character && newItem is AddableProfile.Character ->
                        oldItem.profile.id == newItem.profile.id
                    oldItem is AddableProfile.Persona && newItem is AddableProfile.Persona ->
                        oldItem.profile.id == newItem.profile.id
                    else -> false
                }
            override fun areContentsTheSame(oldItem: AddableProfile, newItem: AddableProfile): Boolean = oldItem == newItem
        }
    }
}
