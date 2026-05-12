package com.albirich.RealmsAI

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.albirich.RealmsAI.models.CharacterProfile
import com.google.firebase.firestore.FirebaseFirestore

class ChatPreviewAdapter(
    private val context: Context,
    private var chatList: List<ChatPreview>,
    @LayoutRes private val itemLayoutRes: Int,
    private val onClick: (ChatPreview) -> Unit,
    private val onLongClick: (ChatPreview) -> Unit = {}
) : RecyclerView.Adapter<ChatPreviewAdapter.PreviewViewHolder>() {

    inner class PreviewViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title       : TextView  = itemView.findViewById(R.id.chatTitle)
        private val description : TextView  = itemView.findViewById(R.id.chatPreview)
        private val avatar1     : ImageView = itemView.findViewById(R.id.chatAvatar1)
        private val avatar2     : ImageView = itemView.findViewById(R.id.chatAvatar2)
        private val ratingText  : TextView  = itemView.findViewById(R.id.chatRating)


        fun bind(preview: ChatPreview) {
            title.text       = preview.title
            description.text = preview.description
            ratingText.text  = "★ %.1f".format(preview.rating)

            // Avatar 1
            if (!preview.avatar1Uri.isNullOrBlank()) {
                Glide.with(avatar1)
                    .load(preview.avatar1Uri)
                    .placeholder(R.drawable.placeholder_avatar)
                    .error(R.drawable.placeholder_avatar)
                    .into(avatar1)
            } else {
                avatar1.setImageResource(preview.avatar1ResId)
            }

            // Avatar 2
            if (!preview.avatar2Uri.isNullOrBlank()) {
                Glide.with(avatar2)
                    .load(preview.avatar2Uri)
                    .placeholder(R.drawable.placeholder_avatar)
                    .error(R.drawable.placeholder_avatar)
                    .into(avatar2)
            } else {
                avatar2.visibility = View.GONE
            }
            val descScrollView = itemView.findViewById<ScrollView>(R.id.descScrollView)
            descScrollView.setOnTouchListener { v, event ->
                v.parent.requestDisallowInterceptTouchEvent(true)
                false
            }
            itemView.setOnClickListener {
                onClick(preview)
            }

            itemView.setOnLongClickListener {
                onLongClick(preview)
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PreviewViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(itemLayoutRes, parent, false)
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

    fun loadAllCharacters(onLoaded: (Map<String, CharacterProfile>) -> Unit) {
        FirebaseFirestore.getInstance()
            .collection("characters")
            .get()
            .addOnSuccessListener { snapshot ->
                val characterMap = snapshot.documents
                    .mapNotNull { it.toObject(CharacterProfile::class.java) }
                    .associateBy { it.id }
                onLoaded(characterMap)
            }
    }
}
