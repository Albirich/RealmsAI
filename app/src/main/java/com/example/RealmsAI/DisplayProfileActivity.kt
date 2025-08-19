package com.example.RealmsAI

import android.R.id.message
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.RealmsAI.SessionManager.findSessionForUser
import com.example.RealmsAI.models.CharacterPreview
import com.example.RealmsAI.models.CharacterProfile
import com.example.RealmsAI.models.ChatProfile
import com.example.RealmsAI.models.DirectMessage
import com.example.RealmsAI.models.MessageStatus
import com.example.RealmsAI.models.MessageType
import com.example.RealmsAI.models.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson

class DisplayProfileActivity : BaseActivity() {

    private lateinit var avatarView: ImageView
    private lateinit var nameView: TextView
    private lateinit var handleView: TextView
    private lateinit var bioView: TextView
    private lateinit var addFriendButton: ImageButton
    private lateinit var reportButton: ImageButton

    private lateinit var picksRecycler: RecyclerView
    private lateinit var charactersRecycler: RecyclerView
    private lateinit var chatsRecycler: RecyclerView

    private val db = FirebaseFirestore.getInstance()
    private val currentUserId: String? get() = FirebaseAuth.getInstance().currentUser?.uid

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.profile_display)
        setupBottomNav()

        // 1. Get userId to display (could come from intent or deep link)
        val profileUserId = intent.getStringExtra("userId") ?: run {
            Toast.makeText(this, "No user specified!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 2. Bind views
        avatarView = findViewById(R.id.displayProfileAvatar)
        nameView = findViewById(R.id.displayProfileName)
        handleView = findViewById(R.id.displayProfileHandle)
        bioView = findViewById(R.id.displayProfileBio)
        addFriendButton = findViewById(R.id.displayAddFriendButton)
        reportButton = findViewById(R.id.displayReportButton)

        charactersRecycler = findViewById(R.id.displayCharactersRecyclerView)
        chatsRecycler = findViewById(R.id.displayChatsRecyclerView)

        charactersRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        chatsRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        // 3. Hide addFriend/report if viewing own profile
        if (profileUserId == currentUserId) {
            addFriendButton.visibility = View.GONE
            reportButton.visibility = View.GONE
        }

        addFriendButton.setOnClickListener {
            Toast.makeText(this, "Clicked!", Toast.LENGTH_SHORT).show()
            Log.d("DisplayProfileActivity", "Add friend clicked for $profileUserId")
            val fromId = currentUserId
            val toId = profileUserId
            if (fromId == null || toId == null) {
                Toast.makeText(this, "You must be signed in!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (fromId == toId) {
                Toast.makeText(this, "You can't friend yourself.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }


            sendFriendRequest(fromId, toId)
        }

        // 4. Load profile data
        loadProfile(profileUserId)
    }

    private fun loadProfile(userId: String) {
        db.collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                val profile = doc.toObject(UserProfile::class.java)
                if (profile == null) {
                    Toast.makeText(this, "Profile not found.", Toast.LENGTH_SHORT).show()
                    finish()
                    return@addOnSuccessListener
                }

                nameView.text = profile.name
                handleView.text = if (!profile.handle.isNullOrBlank()) "@${profile.handle}" else ""
                bioView.text = profile.bio

                if (!profile.iconUrl.isNullOrBlank()) {
                    Glide.with(this).load(profile.iconUrl).into(avatarView)
                } else {
                    avatarView.setImageResource(R.drawable.placeholder_avatar)
                }


                // Load user's public characters
                loadUserCharacters(userId)

                // Load user's public chats
                loadUserChats(userId)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error loading profile.", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun sendFriendRequest(fromId: String, toId: String) {
        val usersRef = db.collection("users")
        val fromDoc = usersRef.document(fromId)
        val toDoc = usersRef.document(toId)
        toDoc.get().addOnSuccessListener { doc ->
            val profile = doc.toObject(UserProfile::class.java)
            if (profile != null) {
                when {
                    profile.friends.contains(fromId) -> {
                        Toast.makeText(this, "You are already friends!", Toast.LENGTH_SHORT).show()
                    }
                    profile.pendingFriends.contains(fromId) -> {
                        Toast.makeText(this, "Request already sent!", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        // Add fromId to pendingFriends in recipient profile
                        toDoc.update("pendingFriends", profile.pendingFriends + fromId)
                            .addOnSuccessListener {
                                Toast.makeText(this, "Friend request sent!", Toast.LENGTH_SHORT).show()
                                addFriendButton.isEnabled = false
                                sendFriendRequestMessage(fromId, toId)
                            }
                            .addOnFailureListener {
                                Toast.makeText(this, "Failed to send friend request.", Toast.LENGTH_SHORT).show()
                            }
                    }
                }
            } else {
                Toast.makeText(this, "User not found.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendFriendRequestMessage(fromId: String, toId: String) {
        val db = FirebaseFirestore.getInstance()
        val messagesCollection = db.collection("users").document(toId).collection("messages")

        val newMessageRef = messagesCollection.document()
        db.collection("users").document(fromId).get()
            .addOnSuccessListener { fromDoc ->
                val senderName = fromDoc.getString("name") ?: "(unknown)"
                val message = DirectMessage(
                    id = newMessageRef.id,
                    from = senderName,
                    to = toId,
                    text = "You have a new friend request from: $senderName",
                    timestamp = com.google.firebase.Timestamp.now(),
                    status = MessageStatus.UNOPENED,
                    type = MessageType.FRIEND_REQUEST
                )
                newMessageRef.set(message)
                    .addOnSuccessListener {
                        // Optional: Toast or log here if you want
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to notify user: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
    }

    // Loads only public characters authored by this user
    private fun loadUserCharacters(userId: String) {
        db.collection("characters")
            .whereEqualTo("author", userId)
            .whereEqualTo("private", false)
            .get()
            .addOnSuccessListener { snap ->
                val list = snap.documents.mapNotNull { doc ->
                    val cp = doc.toObject(CharacterProfile::class.java)?.copy(id = doc.id) ?: return@mapNotNull null
                    CharacterPreview(
                        id = cp.id,
                        name = cp.name,
                        summary = cp.summary.orEmpty(),
                        avatarUri = cp.avatarUri,
                        avatarResId = cp.avatarResId ?: R.drawable.placeholder_avatar,
                        author = cp.author,
                        rawJson = Gson().toJson(cp)
                    )
                }
                val adapter = CharacterPreviewAdapter(
                    context = this,
                    items = list,
                    onClick = { preview ->
                        startActivity(Intent(this, CharacterProfileActivity::class.java)
                            .putExtra("characterId", preview.id))
                    },
                    itemLayoutRes = R.layout.profile_character_preview_item,
                    onLongClick = { preview ->
                        AlertDialog.Builder(this)
                            .setTitle(preview.name)
                            .setItems(arrayOf("Profile", "Creator")) { _, which ->
                                when (which) {
                                    0 -> startActivity(Intent(this, CharacterProfileActivity::class.java)
                                        .putExtra("characterId", preview.id))
                                    1 -> startActivity(Intent(this, DisplayProfileActivity::class.java)
                                        .putExtra("userId", preview.author))
                                }
                            }
                            .show()
                    }
                )
                charactersRecycler.adapter = adapter
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load characters: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // Loads only public chats authored by this user
    private fun loadUserChats(userId: String) {
        db.collection("chats")
            .whereEqualTo("author", userId)
            .whereEqualTo("private", false)
            .get()
            .addOnSuccessListener { snap ->
                val chatList = mutableListOf<ChatPreview>()
                val characterRefs = mutableMapOf<String, CharacterProfile>()

                val allCharacterIds = snap.documents.flatMap { doc ->
                    (doc.get("characterIds") as? List<String>).orEmpty()
                }.distinct()

                // Fetch all needed CharacterProfiles first!
                if (allCharacterIds.isNotEmpty()) {
                    db.collection("characters")
                        .whereIn(FieldPath.documentId(), allCharacterIds)
                        .get()
                        .addOnSuccessListener { charSnap ->
                            for (charDoc in charSnap.documents) {
                                charDoc.toObject(CharacterProfile::class.java)?.let {
                                    characterRefs[charDoc.id] = it
                                }
                            }
                            // Now build chat previews
                            for (doc in snap.documents) {
                                val chatProfile = doc.toObject(ChatProfile::class.java)?.copy(id = doc.id) ?: continue
                                val ids = chatProfile.characterIds
                                val char1 = characterRefs[ids.getOrNull(0) ?: ""]
                                val char2 = characterRefs[ids.getOrNull(1) ?: ""]

                                chatList.add(
                                    ChatPreview(
                                        id = chatProfile.id,
                                        title = chatProfile.title,
                                        description = chatProfile.description,
                                        avatar1Uri = char1?.avatarUri.orEmpty(),
                                        avatar2Uri = char2?.avatarUri.orEmpty(),
                                        avatar1ResId = char1?.avatarResId ?: R.drawable.placeholder_avatar,
                                        avatar2ResId = char2?.avatarResId ?: R.drawable.placeholder_avatar,
                                        rating = chatProfile.rating,
                                        timestamp = chatProfile.timestamp,
                                        author = chatProfile.author,
                                        tags = chatProfile.tags,
                                        sfwOnly = chatProfile.sfwOnly,
                                        chatProfile = chatProfile,
                                        rawJson = Gson().toJson(chatProfile)
                                    )
                                )
                            }
                            val adapter = ChatPreviewAdapter(
                                context = this,
                                chatList = chatList,
                                itemLayoutRes = R.layout.profile_chat_preview_item,
                                onClick = { /* ... */ },
                                onLongClick = { /* ... */ }
                            )
                            chatsRecycler.adapter = adapter
                        }
                } else {
                    // No characters in any chats, just show with placeholders
                    chatsRecycler.adapter = ChatPreviewAdapter(
                        context = this,
                        chatList = emptyList(),
                        itemLayoutRes = R.layout.profile_chat_preview_item,
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
                        },
                        onLongClick = { preview ->
                            AlertDialog.Builder(this)
                                .setTitle(preview.title)
                                .setItems(arrayOf("Profile", "Creator")) { _, which ->
                                    when (which) {
                                        0 -> { // --- Profile ---
                                            startActivity(
                                                Intent(this, ChatProfileActivity::class.java)
                                                    .putExtra("chatId", preview.id)
                                            )
                                        }
                                        1 -> { // --- Creator ---
                                            startActivity(
                                                Intent(this, DisplayProfileActivity::class.java)
                                                    .putExtra("userId", preview.author)
                                            )
                                        }
                                    }
                                }
                                .show()
                        }
                    )
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load chats: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }


}
