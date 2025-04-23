package com.example.emotichat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class EmotionAdapter(
    private val slots: List<EmotionSlot>,
    private val onPick: (position: Int) -> Unit
) : RecyclerView.Adapter<EmotionAdapter.VH>() {

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val img   = itemView.findViewById<ImageButton>(R.id.emotionImg)
        val label = itemView.findViewById<TextView>(R.id.emotionLabel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_emotion, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = slots.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val slot = slots[position]
        // show picked URI or placeholder
        if (slot.uri != null) {
            holder.img.setImageURI(slot.uri)
        } else {
            holder.img.setImageResource(R.drawable.placeholder_avatar)
        }
        // label = capitalized key
        holder.label.text = slot.key.replaceFirstChar { it.uppercaseChar() }

        holder.img.setOnClickListener {
            onPick(position)
        }
    }
}
