package com.example.RealmsAI

import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.RealmsAI.models.LocationSlot

class LocationAdapter(
    private val locations: MutableList<LocationSlot>,
    private val onPickImage: (Int) -> Unit,
    private val onDeleteLocation: (Int) -> Unit
) : RecyclerView.Adapter<LocationAdapter.VH>() {

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgBtn: ImageButton = itemView.findViewById(R.id.locationImg)
        val nameEt: EditText = itemView.findViewById(R.id.locationLabel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_location, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = locations.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val slot = locations[position]

        // Set the image if present
        if (!slot.uri.isNullOrBlank()) {
            Glide.with(holder.imgBtn.context)
                .load(Uri.parse(slot.uri))
                .placeholder(R.drawable.placeholder_background)
                .error(R.drawable.placeholder_background)
                .into(holder.imgBtn)
        } else {
            holder.imgBtn.setImageResource(R.drawable.placeholder_background)
        }

        // Set the name
        holder.nameEt.setText(slot.name)
        holder.nameEt.setSelection(slot.name.length)
        // Save changes to name
        holder.nameEt.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                slot.name = s?.toString() ?: ""
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // Click to pick an image
        holder.imgBtn.setOnClickListener {
            onPickImage(position)
        }

        // Long click to delete this location (optional for now)
        holder.imgBtn.setOnLongClickListener {
            onDeleteLocation(position)
            true
        }
    }
}
