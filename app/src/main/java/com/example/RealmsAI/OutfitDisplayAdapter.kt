package com.example.RealmsAI

import PoseDisplayAdapter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.models.Outfit

class OutfitDisplayAdapter(
    private val outfits: List<Outfit>
) : RecyclerView.Adapter<OutfitDisplayAdapter.OutfitViewHolder>() {

    class OutfitViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val outfitName: TextView = view.findViewById(R.id.outfitName)
        val posesRecycler: RecyclerView = view.findViewById(R.id.posesRecycler)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OutfitViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.outfit_display, parent, false)
        return OutfitViewHolder(view)
    }

    override fun onBindViewHolder(holder: OutfitViewHolder, position: Int) {
        val outfit = outfits[position]
        holder.posesRecycler.isNestedScrollingEnabled = false
        holder.outfitName.text = outfit.name

        // Set up horizontal poses RecyclerView for this outfit
        holder.posesRecycler.layoutManager = LinearLayoutManager(
            holder.itemView.context, LinearLayoutManager.HORIZONTAL, false
        )
        holder.posesRecycler.adapter = PoseDisplayAdapter(outfit.poseSlots)
    }

    override fun getItemCount() = outfits.size
}
