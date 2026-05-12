package com.albirich.RealmsAI.adapters

import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.albirich.RealmsAI.AreaProfileActivity
import com.albirich.RealmsAI.CharacterProfileActivity
import com.albirich.RealmsAI.ChatProfileActivity
import com.albirich.RealmsAI.LorebookProfileActivity
import com.albirich.RealmsAI.R
import com.albirich.RealmsAI.models.FeedEvent
import com.albirich.RealmsAI.models.FeedEventType
import com.google.firebase.firestore.FirebaseFirestore

class FeedAdapter(
    private val context: Context,
    private val events: List<FeedEvent>
) : RecyclerView.Adapter<FeedAdapter.FeedViewHolder>() {

    private val db = FirebaseFirestore.getInstance()

    inner class FeedViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val avatar: ImageView = view.findViewById(R.id.feedAvatar)
        val authorName: TextView = view.findViewById(R.id.feedAuthorName)
        val timestamp: TextView = view.findViewById(R.id.feedTimestamp)
        val title: TextView = view.findViewById(R.id.feedTitle)
        val content: TextView = view.findViewById(R.id.feedContent)
        val actionButton: Button = view.findViewById(R.id.feedActionButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeedViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_feed_event, parent, false)
        return FeedViewHolder(view)
    }

    override fun onBindViewHolder(holder: FeedViewHolder, position: Int) {
        val event = events[position]

        // 1. Set Timestamp (e.g., "2 hours ago")
        val timeMs = event.timestamp?.toDate()?.time ?: System.currentTimeMillis()
        holder.timestamp.text = DateUtils.getRelativeTimeSpanString(timeMs, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)

        // 2. Set Content
        holder.content.text = event.content
        holder.title.text = event.title

        // 3. Fetch Author Details (or handle ADMIN)
        if (event.authorId == "ADMIN") {
            holder.authorName.text = "Realm System"
            holder.authorName.setTextColor(context.getColor(android.R.color.holo_orange_light))
            holder.avatar.setImageResource(android.R.drawable.ic_dialog_alert) // Or a custom logo!
            holder.actionButton.visibility = View.GONE
        } else {
            holder.authorName.setTextColor(context.getColor(android.R.color.white))
            db.collection("users").document(event.authorId).get().addOnSuccessListener { doc ->
                holder.authorName.text = doc.getString("name") ?: "Unknown User"
                val avatarUrl = doc.getString("iconUrl")
                if (!avatarUrl.isNullOrBlank()) {
                    Glide.with(context).load(avatarUrl).into(holder.avatar)
                } else {
                    holder.avatar.setImageResource(R.drawable.placeholder_avatar)
                }
            }
        }

        // 4. Configure the Action Button based on Type
        holder.actionButton.setOnClickListener(null) // Reset listener

        when (event.type) {
            FeedEventType.NEW_CHARACTER -> {
                holder.actionButton.visibility = View.VISIBLE
                holder.actionButton.text = "View Character"
                holder.actionButton.setOnClickListener {
                    event.referenceId?.let { id ->
                        context.startActivity(Intent(context, CharacterProfileActivity::class.java).putExtra("characterId", id))
                    }
                }
            }
            FeedEventType.NEW_CHAT -> {
                holder.actionButton.visibility = View.VISIBLE
                holder.actionButton.text = "View Chat"
                holder.actionButton.setOnClickListener {
                    event.referenceId?.let { id ->
                        context.startActivity(Intent(context, ChatProfileActivity::class.java).putExtra("chatId", id))
                    }
                }
            }
            FeedEventType.NEW_AREA -> {
                holder.actionButton.visibility = View.VISIBLE
                holder.actionButton.text = "Explore Area"
                holder.actionButton.setOnClickListener {
                    event.referenceId?.let { id ->
                        context.startActivity(Intent(context, AreaProfileActivity::class.java).putExtra("AREA_ID", id))
                    }
                }
            }
            FeedEventType.NEW_LOREBOOK -> {
                holder.actionButton.visibility = View.VISIBLE
                holder.actionButton.text = "Read Lorebook"
                holder.actionButton.setOnClickListener {
                    event.referenceId?.let { id ->
                        context.startActivity(Intent(context, LorebookProfileActivity::class.java).putExtra("LOREBOOK_ID", id))
                    }
                }
            }
            else -> {
                // TEXT_POST or ADMIN_ANNOUNCEMENT usually don't have buttons
                holder.actionButton.visibility = View.GONE
            }
        }
    }

    override fun getItemCount() = events.size
}