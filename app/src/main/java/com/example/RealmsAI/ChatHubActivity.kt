package com.example.RealmsAI

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.SessionManager.findSessionForUser
import com.example.RealmsAI.models.ChatProfile
import com.example.RealmsAI.models.CharacterProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.gson.Gson

class ChatHubActivity : BaseActivity() {
    private lateinit var sortSpinner: Spinner
    private lateinit var adapter: ChatPreviewAdapter
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    private var characterMap: Map<String, CharacterProfile> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_hub)
        setupBottomNav()

        val chatsRv = findViewById<RecyclerView>(R.id.chatHubRecyclerView).apply {
            layoutManager = GridLayoutManager(this@ChatHubActivity, 2)
        }

        adapter = ChatPreviewAdapter(
            context = this,
            chatList = emptyList(),
            onClick = { preview ->
                val userId = currentUserId
                if (userId == null) {
                    Toast.makeText(this, "You must be signed in to continue.", Toast.LENGTH_SHORT).show()
                    return@ChatPreviewAdapter
                }
                findSessionForUser(
                    chatId = preview.id,
                    userId = userId,
                    onResult = { sessionId ->
                        startActivity(Intent(this, SessionLandingActivity::class.java).apply {
                            putExtra("CHAT_ID", preview.id)
                            putExtra("CHAT_PROFILE_JSON", preview.rawJson)
                        })
                    },
                    onError = {
                        startActivity(Intent(this, SessionLandingActivity::class.java).apply {
                            putExtra("CHAT_ID", preview.id)
                            putExtra("CHAT_PROFILE_JSON", preview.rawJson)
                        })
                    }
                )
            }
        )
        chatsRv.adapter = adapter

        // Load all characters first (before chats)
        FirebaseFirestore.getInstance()
            .collection("characters")
            .get()
            .addOnSuccessListener { charSnap ->
                characterMap = charSnap.documents
                    .mapNotNull { it.toObject(CharacterProfile::class.java) }
                    .associateBy { it.id }
                // Only load chats AFTER characterMap is ready
                setupSpinnerAndShowChats(chatsRv)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load characters", Toast.LENGTH_SHORT).show()
                setupSpinnerAndShowChats(chatsRv)
            }
    }

    private fun setupSpinnerAndShowChats(chatsRv: RecyclerView) {
        sortSpinner = findViewById(R.id.sortSpinner)
        val options = listOf("Latest", "Hot")
        sortSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            options
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        sortSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                val field = if (options[pos] == "Latest") "timestamp" else "rating"
                showChats(chatsRv, orderBy = field)
            }
        }
        // Initial load as "Latest"
        showChats(chatsRv, orderBy = "timestamp")
    }

    /**
     * Loads chats, matches avatars using characterMap, and updates the adapter.
     */
    private fun showChats(
        chatsRv: RecyclerView,
        orderBy: String = "timestamp"
    ) {
        FirebaseFirestore.getInstance()
            .collection("chats")
            .whereNotEqualTo("mode", "ONE_ON_ONE")
            .orderBy(orderBy, Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snap ->
                val previews = snap.documents.mapNotNull { doc ->
                    val profile = doc.toObject(ChatProfile::class.java)?.copy(id = doc.id) ?: return@mapNotNull null
                    val char1Id = profile.characterIds.getOrNull(0)
                    val char2Id = profile.characterIds.getOrNull(1)
                    val char1 = characterMap[char1Id]
                    val char2 = characterMap[char2Id]
                    ChatPreview(
                        id = profile.id,
                        title = profile.title,
                        description = profile.description,
                        avatar1Uri = char1?.avatarUri ?: "",
                        avatar2Uri = char2?.avatarUri ?: "",
                        avatar1ResId = R.drawable.placeholder_avatar,
                        avatar2ResId = R.drawable.placeholder_avatar,
                        rating = profile.rating,
                        timestamp = profile.timestamp,
                        mode = profile.mode,
                        author = profile.author,
                        tags = profile.tags,
                        sfwOnly = profile.sfwOnly,
                        chatProfile = profile,
                        rawJson = Gson().toJson(profile)
                    )
                }
                adapter.updateList(previews)
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    this,
                    "Failed to load chats: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }
}
