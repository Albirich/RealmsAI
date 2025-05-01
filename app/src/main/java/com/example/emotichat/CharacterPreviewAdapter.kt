package com.example.emotichat

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class CharacterPreviewAdapter(
    private var items: List<CharacterProfile>,
    private val onClick: (CharacterProfile) -> Unit
) : RecyclerView.Adapter<CharacterPreviewAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val avatar  : ImageView = view.findViewById(R.id.chatAvatar1)
        private val title   : TextView  = view.findViewById(R.id.chatTitle)
        private val summary : TextView  = view.findViewById(R.id.chatPreview)

        fun bind(ch: CharacterProfile) {
            // 1) Name
            title.text = ch.name

            // 2) Summary (hide if blank)
            val txt = ch.summary.orEmpty()
            summary.text = txt
            summary.visibility = if (txt.isBlank()) View.GONE else View.VISIBLE

            // 3) Avatar: prefer saved URI, else resource ID, else default
            when {
                !ch.avatarUri.isNullOrBlank() -> {
                    Glide.with(itemView)
                        .load(Uri.parse(ch.avatarUri))
                        .centerCrop()
                        .placeholder(R.drawable.icon_01)
                        .error(R.drawable.icon_01)
                        .into(avatar)
                }
                ch.avatarResId != 0 -> {
                    avatar.setImageResource(ch.avatarResId)
                }
                else -> {
                    avatar.setImageResource(R.drawable.icon_01)
                }
            }

            // 4) Click
            itemView.setOnClickListener { onClick(ch) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_character_preview, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun updateList(newItems: List<CharacterProfile>) {
        items = newItems
        notifyDataSetChanged()
    }
}
