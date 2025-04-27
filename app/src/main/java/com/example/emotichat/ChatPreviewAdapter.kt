package com.example.emotichat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView


class ChatPreviewAdapter(
    private var chatList: List<ChatPreview>,
    private val onClick: (ChatPreview) -> Unit
) : RecyclerView.Adapter<ChatPreviewAdapter.PreviewViewHolder>() {

    inner class PreviewViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.chatTitle)
        val description: TextView = itemView.findViewById(R.id.chatPreview)
        val avatar1: ImageView = itemView.findViewById(R.id.chatAvatar1)
        val avatar2: ImageView = itemView.findViewById(R.id.chatAvatar2)
        val ratingText: TextView = itemView.findViewById(R.id.chatRating)
        val badge         : TextView = itemView.findViewById(R.id.previewTypeBadge)



        fun bind(chat: ChatPreview) {
            title.text = chat.title
            description.text = chat.description
            avatar1.setImageResource(chat.avatar1ResId)
            avatar2.setImageResource(chat.avatar2ResId)
            ratingText.text = "★ %.1f".format(chat.rating)
            badge.text = when(chat.mode) {
                ChatMode.SANDBOX     -> "SANDBOX"
                ChatMode.RPG         -> "RPG"
                ChatMode.SLOW_BURN   -> "SLOW-BURN"
                ChatMode.VISUAL_NOVEL-> "VN"
                ChatMode.GOD         -> "GOD"
                ChatMode.CHARACTER -> "CHARACTER"
            }
            badge.visibility = View.VISIBLE

            val df = android.text.format.DateFormat.getDateFormat(itemView.context)

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

    fun updateList(newList: List<ChatPreview>) {
        chatList = newList
        notifyDataSetChanged()
    }
    override fun getItemCount(): Int = chatList.size

}
