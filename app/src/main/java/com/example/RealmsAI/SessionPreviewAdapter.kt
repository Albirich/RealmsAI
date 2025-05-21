package com.example.RealmsAI

import SessionPreview
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class SessionPreviewAdapter(
    private val context: android.content.Context,
    private var sessionList: List<SessionPreview>,
    private val onClick: (SessionPreview) -> Unit,
    val onLongClick: (SessionPreview) -> Unit
) : RecyclerView.Adapter<SessionPreviewAdapter.SessionViewHolder>() {

    inner class SessionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView      = itemView.findViewById(R.id.sessionTitle)
        val chatId: TextView     = itemView.findViewById(R.id.sessionChatId)
        val timestamp: TextView  = itemView.findViewById(R.id.sessionTimestamp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.session_preview_item, parent, false)
        return SessionViewHolder(view)
    }

    override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
        val session = sessionList[position]
        holder.title.text = session.title
        holder.chatId.text = "Chat: ${session.chatId}"

        // Format timestamp to readable date
        val date = Date(session.timestamp * 1000) // Firestore seconds to millis
        val sdf = SimpleDateFormat("MMM dd, yyyy  HH:mm", Locale.getDefault())
        holder.timestamp.text = sdf.format(date)

        holder.itemView.setOnClickListener { onClick(session) }

        val preview = sessionList[position]
        holder.itemView.setOnClickListener { onClick(preview) }
        holder.itemView.setOnLongClickListener {
            onLongClick(preview)
            true
        }
    }

    override fun getItemCount(): Int = sessionList.size

    fun updateList(newList: List<SessionPreview>) {
        sessionList = newList
        notifyDataSetChanged()
    }

}
