package com.albirich.RealmsAI.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.albirich.RealmsAI.R
import com.albirich.RealmsAI.models.AreaPreview

class AreaPreviewAdapter(
    private val context: Context,
    private var previews: List<AreaPreview>,
    private val onClick: (AreaPreview) -> Unit,
    private val onLongClick: (AreaPreview) -> Unit
) : RecyclerView.Adapter<AreaPreviewAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val coverImage: ImageView = view.findViewById(R.id.areaCoverImage)
        val nameText: TextView = view.findViewById(R.id.areaTitle)
        val infoText: TextView = view.findViewById(R.id.areaPreview)
        val locationCount: TextView = view.findViewById(R.id.areaLocCount)
        val scrollView: ScrollView = view.findViewById(R.id.descScrollView) // <-- Grab the ScrollView

        init {
            // 1. Assign clicks ONLY to the main card (this lets touches bubble up natively)
            view.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onClick(previews[adapterPosition])
                }
            }

            view.setOnLongClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onLongClick(previews[adapterPosition])
                }
                true
            }

            // 2. The exact touch listener from your Chat adapter to stop RecyclerView swipe-stealing
            scrollView.setOnTouchListener { v, _ ->
                v.parent.requestDisallowInterceptTouchEvent(true)
                false
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_area_preview, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val area = previews[position]

        holder.nameText.text = area.name.ifBlank { "Unnamed Area" }

        val locWord = if (area.locationCount == 1) "Location" else "Locations"
        holder.locationCount.text = "${area.locationCount} $locWord"

        // Load the public info into the ScrollView text
        holder.infoText.text = area.publicInfo

        // Load Cover Image
        if (!area.coverImageUri.isNullOrBlank()) {
            Glide.with(context)
                .load(area.coverImageUri)
                .centerCrop()
                .into(holder.coverImage)
        } else {
            holder.coverImage.setImageResource(R.drawable.placeholder_avatar)
        }
    }

    override fun getItemCount(): Int = previews.size

    fun updateList(newList: List<AreaPreview>) {
        previews = newList
        notifyDataSetChanged()
    }
}