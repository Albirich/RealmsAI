package com.example.RealmsAI

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.models.Outfit
import androidx.core.widget.doAfterTextChanged

class OutfitAdapter(
    private val data: List<Outfit>,
    private val onImageClick: (position: Int) -> Unit
) : RecyclerView.Adapter<OutfitAdapter.Holder>() {

    inner class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val image = v.findViewById<ImageView>(R.id.poseImg)
        val name  = v.findViewById<EditText>(R.id.poseLabel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_outfit, parent, false)
        return Holder(v)
    }

    override fun getItemCount() = data.size

    override fun onBindViewHolder(holder: Holder, pos: Int) {
        val item = data[pos]

        // name field
        holder.name.setText(item.name)            // initial value

        holder.name.doAfterTextChanged { editable ->
            data[pos].name = editable?.toString().orEmpty()
        }

        // image
        if (item.uri != null) {
            holder.image.setImageURI(Uri.parse(item.uri))
        } else {
            holder.image.setImageResource(R.drawable.placeholder_avatar) // placeholder
        }
        holder.image.setOnClickListener { onImageClick(pos) }
    }
}
