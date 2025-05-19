package com.example.RealmsAI

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PoseAdapter(
    private val slots: List<PoseSlot>,
    private val onPick: (position: Int) -> Unit
) : RecyclerView.Adapter<PoseAdapter.VH>() {

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val img   = itemView.findViewById<ImageButton>(R.id.poseImg)
        val label = itemView.findViewById<TextView>(R.id.poseLabel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pose, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = slots.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val slot = slots[position]

        // 1) Show picked image or placeholder
        if (slot.uri != null) {
            holder.img.setImageURI(slot.uri)
        } else {
            holder.img.setImageResource(R.drawable.placeholder_avatar)
        }

        // 2) Compute a nice capitalized label (and make sure it shows)
        val rawKey = slot.key.replace('_',' ').replace('-', ' ')
        val labelText = rawKey.replaceFirstChar {
            it.titlecase(java.util.Locale.getDefault())
        }
        holder.label.text = labelText
        holder.label.visibility = View.VISIBLE

        // 3) Wire up the click
        holder.img.setOnClickListener { onPick(position) }
    }

}
