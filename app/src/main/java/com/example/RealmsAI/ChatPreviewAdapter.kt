package com.example.RealmsAI

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.RealmsAI.models.CharacterProfile
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson

class ChatPreviewAdapter(
    private val context: Context,
    private var chatList: List<ChatPreview>,
    private val onClick: (ChatPreview) -> Unit,
    private val onLongClick: (ChatPreview) -> Unit = {}
) : RecyclerView.Adapter<ChatPreviewAdapter.PreviewViewHolder>() {

    inner class PreviewViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title       : TextView  = itemView.findViewById(R.id.chatTitle)
        private val description : TextView  = itemView.findViewById(R.id.chatPreview)
        private val avatar1     : ImageView = itemView.findViewById(R.id.chatAvatar1)
        private val avatar2     : ImageView = itemView.findViewById(R.id.chatAvatar2)
        private val badge       : TextView  = itemView.findViewById(R.id.previewTypeBadge)
        private val ratingText  : TextView  = itemView.findViewById(R.id.chatRating)


        fun bind(preview: ChatPreview) {
            title.text       = preview.title
            description.text = preview.description
            ratingText.text  = "★ %.1f".format(preview.rating)
            badge.text       = preview.mode.name

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
                avatar2.setImageResource(preview.avatar2ResId)
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
fun loadChatPreviewsAndDisplay(
    adapter: ChatPreviewAdapter,
    context: Context
) {
    val db = FirebaseFirestore.getInstance()

    // 1. Load all characters
    db.collection("characters")
        .get()
        .addOnSuccessListener { charSnap ->
            val characterMap = charSnap.documents
                .mapNotNull { it.toObject(com.example.RealmsAI.models.CharacterProfile::class.java) }
                .associateBy { it.id }

            // 2. Now load all chats
            db.collection("chats")
                .get()
                .addOnSuccessListener { chatSnap ->
                    val previews = chatSnap.documents.mapNotNull { doc ->
                        val chat = doc.toObject(com.example.RealmsAI.models.ChatProfile::class.java) ?: return@mapNotNull null

                        // Look up avatars
                        val char1Id = chat.characterIds.getOrNull(0)
                        val char2Id = chat.characterIds.getOrNull(1)
                        val char1 = characterMap[char1Id]
                        val char2 = characterMap[char2Id]

                        com.example.RealmsAI.ChatPreview(
                            id = chat.id,
                            title = chat.title,
                            description = chat.description,
                            avatar1Uri = char1?.avatarUri ?: "",
                            avatar1ResId = R.drawable.placeholder_avatar,
                            avatar2Uri = char2?.avatarUri ?: "",
                            avatar2ResId = R.drawable.placeholder_avatar,
                            rating = chat.rating,
                            timestamp = chat.timestamp,
                            mode = chat.mode,
                            author = chat.author,
                            tags = chat.tags,
                            sfwOnly = chat.sfwOnly,
                            chatProfile = chat,
                            rawJson = Gson().toJson(chat)
                        )
                    }
                    adapter.updateList(previews)
                }
        }
}
