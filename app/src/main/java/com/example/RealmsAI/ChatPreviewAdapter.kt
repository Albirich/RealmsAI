package com.example.RealmsAI

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson

class ChatPreviewAdapter(
    private val context: Context,
    private var chatList: List<ChatPreview>,
    private val onClick: (ChatPreview) -> Unit,
    private val onLongClick: (ChatPreview) -> Unit = {}
) : RecyclerView.Adapter<ChatPreviewAdapter.PreviewViewHolder>() {

    inner class PreviewViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title       : TextView    = itemView.findViewById(R.id.chatTitle)
        private val description : TextView    = itemView.findViewById(R.id.chatPreview)
        private val avatar1     : ImageView   = itemView.findViewById(R.id.chatAvatar1)
        private val avatar2     : ImageView   = itemView.findViewById(R.id.chatAvatar2)
        private val badge       : TextView    = itemView.findViewById(R.id.previewTypeBadge)
        private val ratingText  : TextView    = itemView.findViewById(R.id.chatRating)

        fun bind(preview: ChatPreview) {
            title.text       = preview.title
            description.text = preview.description
            ratingText.text  = "★ %.1f".format(preview.rating)
            badge.text       = preview.mode.name

            // Avatar #1
            preview.avatar1Uri
                ?.takeIf { it.isNotBlank() }
                ?.let { avatar1.setImageURI(Uri.parse(it)) }
                ?: avatar1.setImageResource(preview.avatar1ResId)

            // Avatar #2
            preview.avatar2Uri
                ?.takeIf { it.isNotBlank() }
                ?.let { avatar2.setImageURI(Uri.parse(it)) }
                ?: avatar2.setImageResource(preview.avatar2ResId)

            itemView.setOnClickListener {
                onClick(preview)
            }

            // only attach if caller supplied one
            itemView.setOnLongClickListener {
                onLongClick?.invoke(preview)
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PreviewViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.chat_preview_item, parent, false)
        return PreviewViewHolder(view)
    }

    override fun onBindViewHolder(holder: PreviewViewHolder, position: Int) {
        holder.bind(chatList[position])
    }

    override fun getItemCount(): Int = chatList.size

    fun updateList(newList: List<ChatPreview>) {
        chatList = newList
        notifyDataSetChanged()
    }
}

