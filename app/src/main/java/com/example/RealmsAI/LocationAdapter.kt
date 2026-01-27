package com.example.RealmsAI

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.models.LocationSlot
import com.bumptech.glide.Glide

class LocationAdapter(
    private val locations: MutableList<LocationSlot>,
    private val onPickImage: (LocationSlot) -> Unit,
    private val readonly: Boolean = false
) : RecyclerView.Adapter<LocationAdapter.VH>() {

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.locationImg)
        val nameEt: EditText = itemView.findViewById(R.id.locationLabel)
        val pickImageBtn: ImageButton = itemView.findViewById(R.id.locationImg)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_location, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = locations.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val location = locations[position]

        holder.nameEt.setText(location.name)
        holder.nameEt.setSelection(holder.nameEt.text.length)
        holder.nameEt.isEnabled = !readonly
        holder.nameEt.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) location.name = holder.nameEt.text.toString()
        }

        if (!location.uri.isNullOrBlank()) {
            Glide.with(holder.imageView.context)
                .load(location.uri)
                .placeholder(R.drawable.placeholder_background) // use your placeholder resource
                .error(R.drawable.placeholder_background)       // use your error resource
                .into(holder.imageView)
        } else {
            holder.imageView.setImageResource(R.drawable.placeholder_background)
        }

        if (readonly) {
            holder.imageView.isClickable = false
            holder.imageView.setOnClickListener(null)
            // Optional: Remove the "button" look if it's just an image
            holder.imageView.background = null
        } else {
            holder.imageView.isClickable = true
            holder.imageView.setOnClickListener { onPickImage(location) }
            // Restore button look if needed (from XML)
        }
    }
}
