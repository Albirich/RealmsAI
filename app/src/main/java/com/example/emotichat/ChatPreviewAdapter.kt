package com.example.emotichat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class ChatPreview(
    val chatId: String,
    val title: String,
    val lastMessage: String,
    val avatar1ResId: Int,
    val avatar2ResId: Int
)

class ChatPreviewAdapter(
    private val chatList: List<ChatPreview>,
    private val onClick: (ChatPreview) -> Unit

) : RecyclerView.Adapter<ChatPreviewAdapter.PreviewViewHolder>() {

    inner class PreviewViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.chatTitle)
        val preview: TextView = itemView.findViewById(R.id.chatPreview)
        val avatar1: ImageView = itemView.findViewById(R.id.chatAvatar1)
        val avatar2: ImageView = itemView.findViewById(R.id.chatAvatar2)

        fun bind(chat: ChatPreview) {
            title.text = chat.title
            preview.text = chat.lastMessage
            avatar1.setImageResource(chat.avatar1ResId)
            avatar2.setImageResource(chat.avatar2ResId)

            itemView.setOnClickListener {
                onClick(chat)
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
}
