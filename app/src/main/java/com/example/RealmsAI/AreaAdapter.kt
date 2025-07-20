package com.example.RealmsAI

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
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
    private val areas: MutableList<Area>,
    private val areaColors: Map<String, Int>? = null,
    private val onPickImage: (Area, LocationSlot) -> Unit,
    val readonly: Boolean = false
) : RecyclerView.Adapter<AreaAdapter.VH>() {

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameEt: EditText = itemView.findViewById(R.id.areaNameEditText)
        val locationRecycler: RecyclerView = itemView.findViewById(R.id.locationRecycler)
        val addLocationBtn: ImageButton = itemView.findViewById(R.id.addLocationButton)
        val deleteLocationBtn: ImageButton = itemView.findViewById(R.id.deleteLocationButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_area, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = areas.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val area = areas[position]

        // Area name edit
        holder.nameEt.setText(area.name)
        holder.nameEt.setSelection(holder.nameEt.text.length)
        holder.nameEt.isEnabled = !readonly
        holder.nameEt.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) area.name = holder.nameEt.text.toString()
        }

        // --- 1. Set colored border ---
        // Example: if you have a map of area colors, else just use a random or default
        val color = areaColors?.get(area.id) ?: Color.RED
        (holder.itemView.background as? GradientDrawable)?.apply {
            setStroke(8, color)
        }
        val nameHaloView = holder.itemView.findViewById<androidx.constraintlayout.widget.ConstraintLayout>(R.id.nameHalo)
        (nameHaloView.background as? GradientDrawable)?.apply {
            setStroke(8, color)
        }

        // --- 2. Set up location recycler ---
        holder.locationRecycler.layoutManager =
            LinearLayoutManager(holder.locationRecycler.context, LinearLayoutManager.HORIZONTAL, false)
        val locationAdapter = LocationAdapter(
            area.locations,
            onPickImage = { location -> onPickImage(area, location) },
            readonly = readonly
        )
        holder.locationRecycler.adapter = locationAdapter

        // --- 3. Add/Delete Location Buttons ---
        holder.addLocationBtn.visibility = if (readonly) View.GONE else View.VISIBLE
        holder.deleteLocationBtn.visibility = if (readonly) View.GONE else View.VISIBLE

        holder.addLocationBtn.setOnClickListener {
            val newSlot = LocationSlot()
            area.locations.add(newSlot)
            locationAdapter.notifyItemInserted(area.locations.size - 1)
        }
        holder.deleteLocationBtn.setOnClickListener {
            if (area.locations.size > 1) {
                val removeIdx = area.locations.size - 1
                area.locations.removeAt(removeIdx)
                locationAdapter.notifyItemRemoved(removeIdx)
            }
        }
    }
}
