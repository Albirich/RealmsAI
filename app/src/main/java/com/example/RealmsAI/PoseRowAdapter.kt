package com.example.RealmsAI

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.RealmsAI.models.PoseSlot

class PoseRowAdapter(
    private val poses: List<PoseSlot>,
    private val onClick: (PoseSlot) -> Unit
) : RecyclerView.Adapter<PoseRowAdapter.PoseViewHolder>() {

    private var selectedPosition: Int = RecyclerView.NO_POSITION

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PoseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.pose_list_item, parent, false)
        return PoseViewHolder(view)
    }

    override fun getItemCount(): Int = poses.size

    override fun onBindViewHolder(holder: PoseViewHolder, position: Int) {
        val pose = poses[position]
        holder.nameText.text = pose.name
        Glide.with(holder.itemView)
            .load(pose.uri)
            .into(holder.iconView)

        if (position == selectedPosition) {
            holder.itemView.setBackgroundResource(R.drawable.avatar_slot_selected_bg)
        } else {
            holder.itemView.setBackgroundResource(android.R.color.transparent)
        }

        holder.itemView.setOnClickListener {
            val previous = selectedPosition
            selectedPosition = position
            notifyItemChanged(previous)
            notifyItemChanged(position)
            onClick(pose)
        }
    }

    class PoseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val iconView: ImageView = view.findViewById(R.id.pose_image)
        val nameText: TextView = view.findViewById(R.id.pose_name)
    }
}
