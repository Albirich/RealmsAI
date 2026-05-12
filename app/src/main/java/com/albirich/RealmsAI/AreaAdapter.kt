package com.albirich.RealmsAI

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.albirich.RealmsAI.models.Area
import com.albirich.RealmsAI.models.LocationSlot


class AreaAdapter(
    private val areas: MutableList<Area>,
    private val areaColors: Map<String, Int>? = null,
    private val onRemoveArea: ((Area) -> Unit)? = null,
    private val onManageLocation: (Area, LocationSlot?, RecyclerView.Adapter<*>) -> Unit,
    val readonly: Boolean = false
) : RecyclerView.Adapter<AreaAdapter.VH>() {

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameEt: EditText = itemView.findViewById(R.id.areaNameEditText)
        val locationRecycler: RecyclerView = itemView.findViewById(R.id.locationRecycler)
        val addLocationBtn: ImageButton = itemView.findViewById(R.id.addLocationButton)
        val removeAreaBtn: ImageButton = itemView.findViewById(R.id.removeAreaButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_area, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = areas.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val area = areas[position]

        holder.nameEt.tag?.let { oldWatcher ->
            holder.nameEt.removeTextChangedListener(oldWatcher as android.text.TextWatcher)
        }

        holder.nameEt.setText(area.name)

        // 2. Create and ATTACH a new listener, and store it in the tag
        val newWatcher = object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                area.name = s?.toString() ?: ""
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }
        holder.nameEt.addTextChangedListener(newWatcher)
        holder.nameEt.tag = newWatcher
        holder.nameEt.isEnabled = !readonly

        // Color styling
        val color = areaColors?.get(area.id) ?: Color.RED
        (holder.itemView.background as? GradientDrawable)?.apply { setStroke(8, color) }
        val nameHaloView = holder.itemView.findViewById<View>(R.id.nameHalo)
        (nameHaloView.background as? GradientDrawable)?.apply { setStroke(8, color) }

        // Setup Location Recycler
        holder.locationRecycler.layoutManager =
            LinearLayoutManager(holder.locationRecycler.context, LinearLayoutManager.HORIZONTAL, false)

        // Initialize your LocationAdapter
        val locationAdapter = LocationAdapter(
            locations = area.locations,
            onPickImage = { locationToEdit ->
                // Pass the location and THIS adapter so the Activity can refresh it after picking
                onManageLocation(area, locationToEdit, holder.locationRecycler.adapter!!)
            },
            readonly = readonly
        )
        holder.locationRecycler.adapter = locationAdapter

        if (onRemoveArea != null) {
            holder.removeAreaBtn.visibility = View.VISIBLE
            holder.removeAreaBtn.setOnClickListener {
                onRemoveArea.invoke(area)
            }
        } else {
            holder.removeAreaBtn.visibility = View.GONE
        }

        // Add Button
        holder.addLocationBtn.visibility = if (readonly) View.GONE else View.VISIBLE
        holder.addLocationBtn.setOnClickListener {
            // 1. LIMIT CHECK
            // (Assuming 'area.locations' is the list you are displaying)
            if (area.locations.size >= 20) {
                Toast.makeText(holder.itemView.context, "Max 20 locations per area allowed.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 2. Proceed if under limit
            onManageLocation(area, null, locationAdapter)
        }
    }
}