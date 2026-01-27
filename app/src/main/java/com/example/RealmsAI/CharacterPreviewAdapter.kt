package com.example.RealmsAI

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.RealmsAI.models.CharacterPreview

class CharacterPreviewAdapter(
    private val context: Context,
    private var items: List<CharacterPreview>,
    @LayoutRes private val itemLayoutRes: Int,
    private val onClick:    (CharacterPreview) -> Unit,
    private val onLongClick: (CharacterPreview) -> Unit = {}
) : RecyclerView.Adapter<CharacterPreviewAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val avatar  : ImageView = view.findViewById(R.id.charAvatar1)
        private val title   : TextView  = view.findViewById(R.id.charTitle)
        private val summary : TextView  = view.findViewById(R.id.charPreview)
        private val ratingTv: TextView? = view.findViewById(R.id.charRating)

        fun bind(preview: CharacterPreview) {
            // 1) Name
            title.text = preview.name

            // 2) Summary (hide if blank)
            summary.text = preview.summary
            summary.visibility = if (preview.summary.isBlank()) View.GONE else View.VISIBLE

            // 3) Avatar: URI -> resId -> default
            when {
                !preview.avatarUri.isNullOrBlank() -> {
                    Glide.with(itemView)
                        .load(Uri.parse(preview.avatarUri))
                        .centerCrop()
                        .placeholder(R.drawable.icon_01)
                        .error(R.drawable.icon_01)
                        .into(avatar)
                }
                preview.avatarResId != 0 -> {
                    avatar.setImageResource(preview.avatarResId)
                }
                else -> {
                    avatar.setImageResource(R.drawable.icon_01)
                }
            }
            // SHOW RATING
            if (preview.rating > 0) {
                ratingTv?.visibility = View.VISIBLE
                ratingTv?.text = "★ %.1f".format(preview.rating)
            } else {
                ratingTv?.visibility = View.GONE
            }

            // 4) Click & long‑click
            itemView.setOnClickListener  { onClick(preview) }
            itemView.setOnLongClickListener {
                onLongClick(preview)
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(itemLayoutRes, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])

    }

    override fun getItemCount(): Int = items.size

    fun updateList(newItems: List<CharacterPreview>) {
        items = newItems
        notifyDataSetChanged()
    }
}
