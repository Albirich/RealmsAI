package com.example.RealmsAI

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class PersonaPreviewAdapter(
    private val context: Context,
    private var items: List<PersonaPreview>,
    private val onClick:    (PersonaPreview) -> Unit,
    private val onLongClick: (PersonaPreview) -> Unit = {}
) : RecyclerView.Adapter<PersonaPreviewAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val avatar  : ImageView = view.findViewById(R.id.personaAvatar1)
        private val title   : TextView  = view.findViewById(R.id.personaTitle)
        private val summary : TextView  = view.findViewById(R.id.personaPreview)

        fun bind(preview: PersonaPreview) {
            // 1) Name
            title.text = preview.name

            // 2) Summary (hide if blank)
            summary.text = preview.description
            summary.visibility = if (preview.description.isBlank()) View.GONE else View.VISIBLE

            // 3) Avatar: Try Uri, then resId, then default
            if (!preview.avatarUri.isNullOrBlank()) {
                Glide.with(itemView)
                    .load(Uri.parse(preview.avatarUri))
                    .centerCrop()
                    .placeholder(R.drawable.icon_01)
                    .error(R.drawable.icon_01)
                    .into(avatar)
            } else if (preview.avatarResId != 0) {
                avatar.setImageResource(preview.avatarResId)
            } else {
                avatar.setImageResource(R.drawable.icon_01)
            }

            // 4) Click & long-click
            itemView.setOnClickListener  { onClick(preview) }
            itemView.setOnLongClickListener {
                onLongClick(preview)
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.persona_preview_item, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun updateList(newItems: List<PersonaPreview>) {
        items = newItems
        notifyDataSetChanged()
    }
}
