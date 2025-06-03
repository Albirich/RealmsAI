package com.example.RealmsAI

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.models.Area
import com.example.RealmsAI.models.LocationSlot

class AreaAdapter(
    var areas: MutableList<Area>,
    val onPickImage: (areaIdx: Int, locIdx: Int) -> Unit,
    val onDeleteLocation: (areaIdx: Int, locIdx: Int) -> Unit
) : RecyclerView.Adapter<AreaAdapter.VH>() {
    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameEt = itemView.findViewById<EditText>(R.id.areaNameEditText)
        val locationRecycler = itemView.findViewById<RecyclerView>(R.id.areaRecycler)
        val addLocationBtn = itemView.findViewById<ImageButton>(R.id.addLocationButton)
        val deleteLocationBtn = itemView.findViewById<ImageButton>(R.id.deleteLocationButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_area, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = areas.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val area = areas[position]

        // EditText for area name
        holder.nameEt.setText(area.name)
        holder.nameEt.setSelection(holder.nameEt.text.length)
        holder.nameEt.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) area.name = holder.nameEt.text.toString()
        }

        // RecyclerView for locations
        holder.locationRecycler.layoutManager =
            LinearLayoutManager(holder.locationRecycler.context, LinearLayoutManager.HORIZONTAL, false)

        val adapter = LocationAdapter(
            area.locations,
            onPickImage = { locIdx -> onPickImage(position, locIdx) },
            onDeleteLocation = { locIdx -> onDeleteLocation(position, locIdx) }
        )
        holder.locationRecycler.adapter = adapter

        // Add Location Button
        holder.addLocationBtn.setOnClickListener {
            area.locations.add(LocationSlot())
            adapter.notifyItemInserted(area.locations.size - 1)
        }

        // Delete Location Button (removes last)
        holder.deleteLocationBtn.setOnClickListener {
            if (area.locations.isNotEmpty()) {
                val removeIdx = area.locations.size - 1
                area.locations.removeAt(removeIdx)
                adapter.notifyItemRemoved(removeIdx)
            }
        }
    }
}
